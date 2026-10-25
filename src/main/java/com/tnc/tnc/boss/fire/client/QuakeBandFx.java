package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 大地震波的环形波带：一道半透明、有径向宽度和低矮竖向隆起、自发光的波峰，
 * 以砸地时锁定的 Boss 位置为中心向外扩散。
 *
 * <p>不是贴地细线、也不是竖直光墙：截面沿径向是“内缘低矮渐隐 → 中间隆起 → 外缘回落渐隐”
 * 的平滑波峰，整圈共用同一个半径 {@code R(t)}，因此只会出现一道环。</p>
 *
 * <p>数据与服务端命中完全同源：半径用 {@code QUAKE_RADIUS * age / QUAKE_RING_TICKS}，
 * 中心用 {@code quakeWaveCenter()}，本类只负责画，不参与伤害或击退。</p>
 *
 * <p>渲染：{@link RenderType#entityTranslucentEmissive} + 全亮贴图，关光影也可见；
 * 峰值不透明度约 30%，可以透过波带看到地面。Oculus 会把共享 BufferSource 换成
 * no-op 分段缓冲，所以这里用自建 immediate 缓冲，在 {@code AFTER_WEATHER} 阶段提交。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuakeBandFx {
    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/quake-band");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/quake_band.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    /** 圆周分段数：160 段在 22 格半径上每段约 0.86 格。 */
    private static final int SEGMENTS = 160;
    /** 径向分层数：决定波峰截面的平滑程度。 */
    private static final int RADIAL_STEPS = 8;
    /** 波带径向宽度（格）。 */
    private static final float WIDTH = 2.6F;
    /** 波峰最大高度（格）。 */
    private static final float PEAK_HEIGHT = 0.55F;
    /** 波峰处的峰值不透明度（20%–35% 区间）。 */
    private static final float PEAK_ALPHA = 0.20F;
    /** u 方向（圆周）贴图重复次数。 */
    private static final float UV_REPEAT = 12.0F;
    /** v 方向（径向）滚动速度：每秒推进的贴图比例。 */
    private static final float UV_SCROLL = 0.45F;
    private static final double RENDER_DIST = 96.0D;
    private static final int FADE_IN = 4;
    private static final int FADE_OUT = 10;
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.quake.debug");

    private static final List<Band> ACTIVE = new ArrayList<>();
    private static final Map<Integer, Integer> SEEN = new HashMap<>();
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(262144));

    private static long lastStatsTick;

    private record Band(int castId, Vec3 center, long bornAt) {
    }

    private QuakeBandFx() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            SEEN.clear();
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss)) {
                continue;
            }
            int start = boss.quakeWaveStart();
            Integer previous = SEEN.get(boss.getId());
            if (start < 0) {
                SEEN.remove(boss.getId());
                continue;
            }
            if (previous != null && previous == start) {
                continue;
            }
            SEEN.put(boss.getId(), start);
            var player = mc.player;
            if (player != null && boss.distanceToSqr(player) > RENDER_DIST * RENDER_DIST) {
                continue;
            }
            if (ACTIVE.size() > 4) {
                ACTIVE.clear();
            }
            ACTIVE.add(new Band(start, boss.quakeWaveCenter(), level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        try {
            draw(event, level);
        } catch (Throwable t) {
            ACTIVE.clear();
            LOGGER.error("[TN-C QuakeBand] disabled after render error", t);
        }
    }

    private static void draw(RenderLevelStageEvent event, Level level) {
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        double now = level.getGameTime() + partial;
        int life = VolcanicTortoiseEntity.QUAKE_RING_TICKS + FADE_OUT;

        Matrix4f matrix = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z);
        VertexConsumer buffer = BUFFERS.getBuffer(TYPE);
        int drawn = 0;
        int cast = -1;

        Iterator<Band> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Band band = iterator.next();
            double age = now - band.bornAt;
            if (age > life + 6) {
                iterator.remove();
                continue;
            }
            if (band.center.distanceToSqr(camera) > RENDER_DIST * RENDER_DIST) {
                continue;
            }
            cast = band.castId;
            double progress = Mth.clamp(age / VolcanicTortoiseEntity.QUAKE_RING_TICKS, 0.0D, 1.0D);
            double radius = VolcanicTortoiseEntity.QUAKE_RADIUS * progress;
            float fade = (float) Math.min(1.0D, age / FADE_IN)
                    * (float) Math.min(1.0D, Math.max(0.0D, (life - age) / FADE_OUT));
            if (fade <= 0.0F || radius < 0.6D) {
                continue;
            }
            drawn++;
            drawBand(buffer, matrix, level, band, radius, age, fade);
        }
        BUFFERS.endBatch(TYPE);

        if (DEBUG && drawn > 0 && level.getGameTime() - lastStatsTick >= 20L) {
            lastStatsTick = level.getGameTime();
            LOGGER.info("[TN-C QuakeBand] cast={} bands={}", cast, drawn);
        }
    }

    /**
     * 画一道波带：沿圆周 160 段、沿径向 8 层，拼成一条“隆起—回落”的曲面。
     * 每个四边形同时写正反两种绕序，避免从内侧或背面看时整段消失。
     */
    private static void drawBand(VertexConsumer buffer, Matrix4f matrix, Level level,
                                 Band band, double radius, double age, float fade) {
        double inner = Math.max(0.3D, radius - WIDTH * 0.5D);
        double outer = radius + WIDTH * 0.5D;
        float scroll = (float) ((age * UV_SCROLL / 20.0D) % 1.0F);

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            double mid = (a0 + a1) * 0.5D;
            double dirX = Math.cos(mid);
            double dirZ = Math.sin(mid);

            int x = Mth.floor(band.center.x + dirX * radius);
            int z = Mth.floor(band.center.z + dirZ * radius);
            if (!level.hasChunkAt(new BlockPos(x, Mth.floor(band.center.y), z))) {
                continue;
            }
            // 高度图给的是地表上方的空气层，正好等于地面顶面高度；负高度由维度构建下限保证。
            double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 0.02D;
            if (groundY < level.getMinBuildHeight() + 1) {
                continue;
            }

            // 沿圆周的轻微起伏：整体仍是一道连续冲击波
            double wave = 0.88D + 0.12D * Math.sin(mid * 3.0D + age * 0.05D);

            float u0 = (float) (i / (double) SEGMENTS * UV_REPEAT);
            float u1 = (float) ((i + 1) / (double) SEGMENTS * UV_REPEAT);

            for (int j = 0; j < RADIAL_STEPS; j++) {
                double t0 = j / (double) RADIAL_STEPS;
                double t1 = (j + 1) / (double) RADIAL_STEPS;
                float y0 = (float) (groundY + PEAK_HEIGHT * wave * profile(t0));
                float y1 = (float) (groundY + PEAK_HEIGHT * wave * profile(t1));
                float a0v = PEAK_ALPHA * fade * (float) alphaProfile(t0);
                float a1v = PEAK_ALPHA * fade * (float) alphaProfile(t1);

                double r0 = inner + (outer - inner) * t0;
                double r1 = inner + (outer - inner) * t1;

                float x00 = (float) (band.center.x + Math.cos(a0) * r0);
                float z00 = (float) (band.center.z + Math.sin(a0) * r0);
                float x10 = (float) (band.center.x + Math.cos(a1) * r0);
                float z10 = (float) (band.center.z + Math.sin(a1) * r0);
                float x01 = (float) (band.center.x + Math.cos(a0) * r1);
                float z01 = (float) (band.center.z + Math.sin(a0) * r1);
                float x11 = (float) (band.center.x + Math.cos(a1) * r1);
                float z11 = (float) (band.center.z + Math.sin(a1) * r1);

                float v0 = (float) (t0 + scroll);
                float v1 = (float) (t1 + scroll);

                quad(buffer, matrix, x00, y0, z00, x10, y0, z10, x11, y1, z11, x01, y1, z01,
                        u0, v0, u1, v0, u1, v1, u0, v1, a0v, a0v, a1v, a1v);
                // 反向绕序：从下方/内侧也能看见
                quad(buffer, matrix, x01, y1, z01, x11, y1, z11, x10, y0, z10, x00, y0, z00,
                        u0, v1, u1, v1, u1, v0, u0, v0, a1v, a1v, a0v, a0v);
            }
        }
    }

    /** 径向截面：内缘 0 → 中间隆起 1 → 外缘 0，峰值偏内一点。 */
    private static double profile(double t) {
        double peakAt = 0.42D;
        double x = t < peakAt ? t / peakAt : (1.0D - t) / (1.0D - peakAt);
        return Math.pow(Mth.clamp(x, 0.0D, 1.0D), 1.15D);
    }

    /** 透明度截面：边缘完全透明，避免硬边和方片感。 */
    private static double alphaProfile(double t) {
        return Math.pow(Math.sin(Math.PI * Mth.clamp(t, 0.0D, 1.0D)), 0.75D);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float v0, float u1, float v1,
                             float u2, float v2, float u3, float v3,
                             float a0, float a1, float a2, float a3) {
        vertex(buffer, matrix, x0, y0, z0, u0, v0, a0);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, a1);
        vertex(buffer, matrix, x2, y2, z2, u2, v2, a2);
        vertex(buffer, matrix, x3, y3, z3, u3, v3, a3);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, float u, float v, float alpha) {
        buffer.vertex(matrix, x, y, z)
                .color(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
