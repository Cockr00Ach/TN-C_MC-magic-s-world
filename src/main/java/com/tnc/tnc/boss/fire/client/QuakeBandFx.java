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
 * 大地震波的“波带”：以 Boss 为中心向外扩散的透明自发光环带。
 *
 * <p>与打击判定共用同一组服务端同步数据（{@code quakeWaveStart/quakeWaveCenter}）与
 * 同一个半径公式 {@code R(t) = QUAKE_RADIUS * age / QUAKE_RING_TICKS}，
 * 所以看到的波带位置就是实际结算位置。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>整条波带由 128 段环带拼成，每段贴地取样高度，因此能顺着坡地起伏；</li>
 *   <li>每段是“有厚度”的壳体：外壁 + 内壁 + 顶面，宽度 {@link #WIDTH} 格、高度 {@link #HEIGHT} 格；</li>
 *   <li>用 {@link RenderType#entityTranslucentEmissive} —— 半透明但不吃光照，关光影也清晰；</li>
 *   <li>Oculus 会把 {@code mc.renderBuffers().bufferSource()} 换成 no-op 的分段缓冲，
 *       所以这里用自建的 immediate 缓冲，并在 {@code AFTER_WEATHER} 阶段提交。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuakeBandFx {
    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/quake-band");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/quake_band.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    private static final int SEGMENTS = 128;
    /** 环带径向厚度（格）。 */
    private static final float WIDTH = 2.6F;
    /** 环带高度（格）。 */
    private static final float HEIGHT = 1.8F;
    /** 整体不透明度。 */
    private static final float ALPHA = 0.72F;
    /** 圆周方向贴图重复次数。 */
    private static final float UV_REPEAT = 10.0F;
    /** 竖直方向贴图滚动速度（每秒）。 */
    private static final float UV_SCROLL = 0.55F;
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
            // 到达最大半径后原地淡出，不再继续外扩
            float fade = (float) Math.min(1.0D, age / FADE_IN)
                    * (float) Math.min(1.0D, Math.max(0.0D, (life - age) / FADE_OUT));
            if (fade <= 0.0F || radius < 0.8D) {
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

    private static void drawBand(VertexConsumer buffer, Matrix4f matrix, Level level,
                                 Band band, double radius, double age, float fade) {
        double inner = Math.max(0.35D, radius - WIDTH * 0.5D);
        double outer = radius + WIDTH * 0.5D;
        float scroll = (float) ((age * UV_SCROLL / 20.0D) % 1.0D);

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            double mid = (a0 + a1) * 0.5D;
            double dirX = Math.cos(mid);
            double dirZ = Math.sin(mid);

            // 每段贴地取样：MOTION_BLOCKING_NO_LEAVES 给出地表上方的空气层，正好是地面高度
            int x = Mth.floor(band.center.x + dirX * radius);
            int z = Mth.floor(band.center.z + dirZ * radius);
            if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x, Mth.floor(band.center.y), z))) {
                continue;
            }
            double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 0.02D;
            if (groundY < level.getMinBuildHeight() + 1) {
                continue;
            }
            // 高度沿圆周缓慢起伏，避免像一圈等高的“桶壁”
            float lift = 0.86F + 0.16F * Mth.sin((float) (mid * 3.0D + age * 0.04D))
                    + 0.06F * Mth.sin((float) (mid * 7.0D + 1.7D));
            double top = groundY + HEIGHT * lift;

            float u0 = (float) (i / (double) SEGMENTS * UV_REPEAT);
            float u1 = (float) ((i + 1) / (double) SEGMENTS * UV_REPEAT);
            float vBottom = 1.0F;
            // v 越大越接近贴图底部（亮端）；滚动时让亮带缓慢向上收
            float vTopScrolled = 1.0F - 0.75F * scroll;

            double ix0 = band.center.x + Math.cos(a0) * inner;
            double iz0 = band.center.z + Math.sin(a0) * inner;
            double ix1 = band.center.x + Math.cos(a1) * inner;
            double iz1 = band.center.z + Math.sin(a1) * inner;
            double ox0 = band.center.x + Math.cos(a0) * outer;
            double oz0 = band.center.z + Math.sin(a0) * outer;
            double ox1 = band.center.x + Math.cos(a1) * outer;
            double oz1 = band.center.z + Math.sin(a1) * outer;

            // 外壁：受光、贴地最亮，向上渐透
            quad(buffer, matrix,
                    ox0, groundY, oz0, ox1, groundY, oz1, ox1, top, oz1, ox0, top, oz0,
                    u0, vBottom, u1, vBottom, u1, vTopScrolled, u0, vTopScrolled,
                    fade, 1.0F, true);
            // 内壁：稍微暗一点，避免从内侧看是双倍亮度
            quad(buffer, matrix,
                    ix1, groundY, iz1, ix0, groundY, iz0, ix0, top, iz0, ix1, top, iz1,
                    u1, vBottom, u0, vBottom, u0, vTopScrolled, u1, vTopScrolled,
                    fade * 0.75F, 1.0F, false);
            // 顶面：从上方看是一圈有宽度的发光带
            quad(buffer, matrix,
                    ix0, top, iz0, ox0, top, oz0, ox1, top, oz1, ix1, top, iz1,
                    u0, vTopScrolled, u0, vTopScrolled, u1, vTopScrolled, u1, vTopScrolled,
                    fade * 0.85F, 1.0F, false);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             float u0, float v0, float u1, float v1,
                             float u2, float v2, float u3, float v3,
                             float alpha, float alphaTop, boolean vertical) {
        float a0 = vertical ? alpha : alpha * 0.9F;
        float a1 = vertical ? alpha * 0.22F : alphaTop * alpha * 0.9F;
        vertex(buffer, matrix, x0, y0, z0, u0, v0, a0);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, a0);
        vertex(buffer, matrix, x2, y2, z2, u2, v2, a1);
        vertex(buffer, matrix, x3, y3, z3, u3, v3, a1);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               double x, double y, double z, float u, float v, float alpha) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
