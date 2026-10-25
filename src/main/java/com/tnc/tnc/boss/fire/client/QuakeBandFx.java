package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * 大地震波的环形波带。
 *
 * <p>两层叠加：</p>
 * <ol>
 *   <li><b>方块层</b>：波带表面直接贴“它正经过的那块地的顶面贴图”，并沿截面隆起，
 *       所以看起来像是地面被抬起来一道波，而不是一圈光；</li>
 *   <li><b>发光线</b>：在波峰脊上叠一条很薄的淡金自发光带，保留“冲击波”的观感，
 *       关光影也能看清波峰位置。</li>
 * </ol>
 *
 * <p>两层共用同一份几何（半径 {@code QUAKE_RADIUS * age / QUAKE_RING_TICKS}、
 * 中心 {@code quakeWaveCenter()}），与服务端命中环带完全同源；本类只负责画，
 * 不参与伤害或击退。</p>
 *
 * <p>Oculus 会把共享 BufferSource 换成 no-op 分段缓冲，所以这里用自建 immediate 缓冲，
 * 在 {@code AFTER_WEATHER} 阶段提交。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuakeBandFx {
    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/quake-band");

    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/quake_band.png");
    private static final RenderType GLOW_TYPE = RenderType.entityTranslucentEmissive(GLOW_TEXTURE);
    private static final RenderType BLOCK_TYPE = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

    /** 圆周分段数：160 段在 22 格半径上每段约 0.86 格。 */
    private static final int SEGMENTS = 160;
    /** 径向分层数：决定波峰截面的平滑程度。 */
    private static final int RADIAL_STEPS = 8;
    /** 波带径向宽度（格）。 */
    private static final float WIDTH = 2.6F;
    /** 波峰最大高度（格）。 */
    private static final float PEAK_HEIGHT = 0.55F;
    /** 方块层不透明度：接近实体，但边缘仍然渐隐。 */
    private static final float BLOCK_ALPHA = 0.88F;
    /** 发光脊线的不透明度。 */
    private static final float GLOW_ALPHA = 0.30F;
    /** u 方向贴图重复次数（发光层）。 */
    private static final float UV_REPEAT = 12.0F;
    /** v 方向滚动速度：每秒推进的贴图比例。 */
    private static final float UV_SCROLL = 0.45F;
    private static final double RENDER_DIST = 96.0D;
    private static final int FADE_IN = 4;
    private static final int FADE_OUT = 10;
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.quake.debug");

    private static final List<Band> ACTIVE = new ArrayList<>();
    private static final Map<Integer, Integer> SEEN = new HashMap<>();
    /** 方块 → 顶面贴图在 block atlas 内的 uv 矩形，避免每帧每个格子重新取模型。 */
    private static final Map<BlockState, float[]> SPRITE_CACHE = new HashMap<>();
    private static final RandomSource MODEL_RANDOM = RandomSource.create(20261025L);
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

        List<Render> bands = new ArrayList<>();
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
            double progress = Mth.clamp(age / VolcanicTortoiseEntity.QUAKE_RING_TICKS, 0.0D, 1.0D);
            double radius = VolcanicTortoiseEntity.QUAKE_RADIUS * progress;
            float fade = (float) Math.min(1.0D, age / FADE_IN)
                    * (float) Math.min(1.0D, Math.max(0.0D, (life - age) / FADE_OUT));
            if (fade <= 0.0F || radius < 0.6D) {
                continue;
            }
            bands.add(new Render(band, radius, age, fade));
        }
        if (bands.isEmpty()) {
            return;
        }

        VertexConsumer blocks = BUFFERS.getBuffer(BLOCK_TYPE);
        for (Render r : bands) {
            drawBandBlocks(blocks, matrix, level, r);
        }
        BUFFERS.endBatch(BLOCK_TYPE);

        VertexConsumer glow = BUFFERS.getBuffer(GLOW_TYPE);
        for (Render r : bands) {
            drawBandGlow(glow, matrix, level, r);
        }
        BUFFERS.endBatch(GLOW_TYPE);

        if (DEBUG && level.getGameTime() - lastStatsTick >= 20L) {
            lastStatsTick = level.getGameTime();
            LOGGER.info("[TN-C QuakeBand] cast={} bands={}", bands.get(0).band.castId, bands.size());
        }
    }

    private record Render(Band band, double radius, double age, float fade) {
    }

    /**
     * 方块层：沿圆周逐段取“脚下那块地”的顶面贴图，拼成隆起的波带。
     * uv 按 1 格一块重复，避免整张贴图被拉伸成长条。
     */
    private static void drawBandBlocks(VertexConsumer buffer, Matrix4f matrix, Level level, Render r) {
        double inner = Math.max(0.3D, r.radius - WIDTH * 0.5D);
        double outer = r.radius + WIDTH * 0.5D;
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            double mid = (a0 + a1) * 0.5D;
            double dirX = Math.cos(mid);
            double dirZ = Math.sin(mid);

            int x = Mth.floor(r.band.center.x + dirX * r.radius);
            int z = Mth.floor(r.band.center.z + dirZ * r.radius);
            if (!level.hasChunkAt(new BlockPos(x, Mth.floor(r.band.center.y), z))) {
                continue;
            }
            int topAir = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            double groundY = topAir - 0.02D;
            if (groundY < level.getMinBuildHeight() + 1) {
                continue;
            }
            BlockPos groundPos = new BlockPos(x, topAir - 1, z);
            BlockState state = level.getBlockState(groundPos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                state = Blocks.STONE.defaultBlockState();
            }
            float[] uv = spriteUv(dispatcher, state);
            if (uv == null) {
                continue;
            }

            double wave = 0.88D + 0.12D * Math.sin(mid * 3.0D + r.age * 0.05D);
            // 每格方块贴图只取一次，沿圆周按弧长重复，贴图不会被拉成长条
            double arc = mid * r.radius;
            float tile = (float) (arc - Math.floor(arc));

            for (int j = 0; j < RADIAL_STEPS; j++) {
                double t0 = j / (double) RADIAL_STEPS;
                double t1 = (j + 1) / (double) RADIAL_STEPS;
                float y0 = (float) (groundY + PEAK_HEIGHT * wave * profile(t0));
                float y1 = (float) (groundY + PEAK_HEIGHT * wave * profile(t1));
                float a0v = BLOCK_ALPHA * r.fade * (float) alphaProfile(t0);
                float a1v = BLOCK_ALPHA * r.fade * (float) alphaProfile(t1);

                double r0 = inner + (outer - inner) * t0;
                double r1 = inner + (outer - inner) * t1;

                float x00 = (float) (r.band.center.x + Math.cos(a0) * r0);
                float z00 = (float) (r.band.center.z + Math.sin(a0) * r0);
                float x10 = (float) (r.band.center.x + Math.cos(a1) * r0);
                float z10 = (float) (r.band.center.z + Math.sin(a1) * r0);
                float x01 = (float) (r.band.center.x + Math.cos(a0) * r1);
                float z01 = (float) (r.band.center.z + Math.sin(a0) * r1);
                float x11 = (float) (r.band.center.x + Math.cos(a1) * r1);
                float z11 = (float) (r.band.center.z + Math.sin(a1) * r1);

                float u0 = uv[0] + tile * (uv[2] - uv[0]);
                float u1 = uv[0] + (float) Math.min(1.0D, tile + 0.35D) * (uv[2] - uv[0]);
                float v0 = uv[1] + (float) t0 * (uv[3] - uv[1]);
                float v1 = uv[1] + (float) t1 * (uv[3] - uv[1]);

                quad(buffer, matrix, x00, y0, z00, x10, y0, z10, x11, y1, z11, x01, y1, z01,
                        u0, v0, u1, v0, u1, v1, u0, v1, a0v, a0v, a1v, a1v, 1.0F, 0.97F, 0.92F);
                quad(buffer, matrix, x01, y1, z01, x11, y1, z11, x10, y0, z10, x00, y0, z00,
                        u0, v1, u1, v1, u1, v0, u0, v0, a1v, a1v, a0v, a0v, 1.0F, 0.97F, 0.92F);
            }
        }
    }

    /** 发光层：只在波峰脊线上叠一条淡金带，提示“这是一道冲击波”。 */
    private static void drawBandGlow(VertexConsumer buffer, Matrix4f matrix, Level level, Render r) {
        double inner = Math.max(0.3D, r.radius - WIDTH * 0.5D);
        double outer = r.radius + WIDTH * 0.5D;
        float scroll = (float) ((r.age * UV_SCROLL / 20.0D) % 1.0F);
        double t0 = 0.28D;
        double t1 = 0.58D;

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            double mid = (a0 + a1) * 0.5D;
            int x = Mth.floor(r.band.center.x + Math.cos(mid) * r.radius);
            int z = Mth.floor(r.band.center.z + Math.sin(mid) * r.radius);
            if (!level.hasChunkAt(new BlockPos(x, Mth.floor(r.band.center.y), z))) {
                continue;
            }
            double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 0.02D;
            if (groundY < level.getMinBuildHeight() + 1) {
                continue;
            }
            double wave = 0.88D + 0.12D * Math.sin(mid * 3.0D + r.age * 0.05D);
            float y0 = (float) (groundY + PEAK_HEIGHT * wave * profile(t0) + 0.03D);
            float y1 = (float) (groundY + PEAK_HEIGHT * wave * profile(t1) + 0.03D);
            float alpha = GLOW_ALPHA * r.fade;

            double rr0 = inner + (outer - inner) * t0;
            double rr1 = inner + (outer - inner) * t1;
            float x00 = (float) (r.band.center.x + Math.cos(a0) * rr0);
            float z00 = (float) (r.band.center.z + Math.sin(a0) * rr0);
            float x10 = (float) (r.band.center.x + Math.cos(a1) * rr0);
            float z10 = (float) (r.band.center.z + Math.sin(a1) * rr0);
            float x01 = (float) (r.band.center.x + Math.cos(a0) * rr1);
            float z01 = (float) (r.band.center.z + Math.sin(a0) * rr1);
            float x11 = (float) (r.band.center.x + Math.cos(a1) * rr1);
            float z11 = (float) (r.band.center.z + Math.sin(a1) * rr1);

            float u0 = (float) (i / (double) SEGMENTS * UV_REPEAT);
            float u1 = (float) ((i + 1) / (double) SEGMENTS * UV_REPEAT);
            float v0 = (float) (t0 + scroll);
            float v1 = (float) (t1 + scroll);

            quad(buffer, matrix, x00, y0, z00, x10, y0, z10, x11, y1, z11, x01, y1, z01,
                    u0, v0, u1, v0, u1, v1, u0, v1, alpha, alpha, alpha, alpha, 1.0F, 1.0F, 1.0F);
            quad(buffer, matrix, x01, y1, z01, x11, y1, z11, x10, y0, z10, x00, y0, z00,
                    u0, v1, u1, v1, u1, v0, u0, v0, alpha, alpha, alpha, alpha, 1.0F, 1.0F, 1.0F);
        }
    }

    /** 取方块顶面贴图在 atlas 中的 uv 矩形；失败时退回石头。 */
    private static float[] spriteUv(BlockRenderDispatcher dispatcher, BlockState state) {
        float[] cached = SPRITE_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        float[] uv = spriteUvOf(dispatcher, state);
        if (uv == null) {
            uv = spriteUvOf(dispatcher, Blocks.STONE.defaultBlockState());
        }
        if (uv != null) {
            if (SPRITE_CACHE.size() > 512) {
                SPRITE_CACHE.clear();
            }
            SPRITE_CACHE.put(state, uv);
        }
        return uv;
    }

    private static float[] spriteUvOf(BlockRenderDispatcher dispatcher, BlockState state) {
        BakedModel model = dispatcher.getBlockModel(state);
        List<BakedQuad> quads = model.getQuads(state, net.minecraft.core.Direction.UP, MODEL_RANDOM);
        if (quads.isEmpty()) {
            quads = model.getQuads(state, null, MODEL_RANDOM);
        }
        if (quads.isEmpty()) {
            return null;
        }
        var sprite = quads.get(0).getSprite();
        return new float[]{sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()};
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
                             float a0, float a1, float a2, float a3,
                             float cr, float cg, float cb) {
        vertex(buffer, matrix, x0, y0, z0, u0, v0, a0, cr, cg, cb);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, a1, cr, cg, cb);
        vertex(buffer, matrix, x2, y2, z2, u2, v2, a2, cr, cg, cb);
        vertex(buffer, matrix, x3, y3, z3, u3, v3, a3, cr, cg, cb);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, float u, float v, float alpha,
                               float cr, float cg, float cb) {
        buffer.vertex(matrix, x, y, z)
                .color(cr, cg, cb, Mth.clamp(alpha, 0.0F, 1.0F))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
