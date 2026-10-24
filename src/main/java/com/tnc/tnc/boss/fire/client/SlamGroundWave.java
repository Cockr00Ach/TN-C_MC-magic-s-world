package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 重踏的地面视觉起伏（纯客户端、不产生方块实体、不改存档）。
 *
 * <p>重踏开始时沿火环路径做一次有界采样：每个角度、每 2 格半径取一个支撑面。
 * 采样以「世界实际高度图 + 施法高度」共同限定窗口，从窗口顶向下找第一个实心方块，
 * 因此既不会跑到洞穴屋顶，也不会因为是负高度或悬空平台而一个点都采不到。
 *
 * <p>每块地砖画成一个薄盒子：顶面用方块顶面贴图，四周用方块侧面贴图，
 * 抬起后能看到真实的厚度和侧面明暗，而不是一层平面颜色。
 *
 * <p>时间轴完全复用火环的 {@code R(t)}：地面波按各自半径的到达时间抬起、外倾、回落，
 * 比火环早 {@link #LEAD} tick 到达，让抬起的坡面在火焰外沿前方露出来。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SlamGroundWave {

    /** 圆周采样角度数。 */
    private static final int ANGLES = 96;
    /** 径向采样步长（格）。 */
    private static final double RADIAL_STEP = 2.0D;
    /** 径向采样层数（2..22 格）。 */
    private static final int STEPS = 11;
    /** 最大抬升（格）。 */
    private static final double MAX_LIFT = 0.75D;
    /** 一块地砖从抬起到落回原位的 tick 数。 */
    private static final int LIFE = 12;
    /** 地面比火环提前多少 tick 到达（让坡面在火焰前缘外露出）。 */
    private static final double LEAD = 2.4D;
    /** 单块地砖的边长（格）。 */
    private static final double PATCH = 1.75D;
    /** 相机超过这个距离就不再画地面细节。 */
    private static final double RENDER_DIST = 48.0D;
    /** 采样窗口：施法高度向上/向下各留多少格。 */
    private static final int SEARCH_UP = 8;
    private static final int SEARCH_DOWN = 4;

    private static final List<Wave> ACTIVE = new ArrayList<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean warnedFailure;
    private static long lastStatsTick = -100L;

    private SlamGroundWave() {
    }

    /** 一块地表贴片：世界坐标、顶面/侧面 UV、抬升系数与光照。 */
    private record Patch(double x, double z, double radius, double groundY,
                         float u0, float v0, float u1, float v1,
                         float su0, float sv0, float su1, float sv1,
                         float liftScale, int light, float dirX, float dirZ) {
    }

    private static final class Wave {
        final SlamRingFx.Ring ring;
        final List<Patch> patches = new ArrayList<>();

        Wave(SlamRingFx.Ring ring) {
            this.ring = ring;
        }
    }

    /** 在创建火环时调用：对本次技能路径做一次有界地表采样。 */
    static void begin(ClientLevel level, SlamRingFx.Ring ring) {
        if (ACTIVE.size() > 4) {
            ACTIVE.clear();
        }
        Wave wave = new Wave(ring);
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < ANGLES; i++) {
            double angle = Math.PI * 2.0D * i / ANGLES;
            float dirX = (float) Math.cos(angle);
            float dirZ = (float) Math.sin(angle);
            for (int step = 1; step <= STEPS; step++) {
                double radius = step * RADIAL_STEP;
                int x = Mth.floor(ring.centerX + dirX * radius);
                int z = Mth.floor(ring.centerZ + dirZ * radius);
                Patch patch = sample(level, x, z, radius, ring.centerY, dirX, dirZ);
                if (patch != null) {
                    wave.patches.add(patch);
                    minY = Math.min(minY, patch.groundY());
                    maxY = Math.max(maxY, patch.groundY());
                }
            }
        }
        ACTIVE.add(wave);
        if (SlamRingFx.DEBUG) {
            int n = wave.patches.size();
            LOGGER.info("[TN-C SlamWave] cast={} patches={} centerY={} groundY=[{}, {}] lift={}",
                    ring.serverStart, n, String.format(Locale.ROOT, "%.2f", ring.centerY),
                    n == 0 ? "-" : String.format(Locale.ROOT, "%.2f", minY),
                    n == 0 ? "-" : String.format(Locale.ROOT, "%.2f", maxY),
                    MAX_LIFT);
        }
    }

    private static Patch sample(ClientLevel level, int x, int z, double radius,
                                double centerY, float dirX, float dirZ) {
        int heightmapTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        // 以世界高度图为上界，再用施法高度做局部窗口：既不会跳到洞穴屋顶，
        // 也不会在负高度/悬空平台上因为窗口离地太远而采不到东西。
        int top = Math.min(heightmapTop, Mth.floor(centerY + SEARCH_UP));
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(centerY - SEARCH_DOWN));
        if (top < bottom) {
            return null;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create(42L);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                return null;
            }
            if (state.hasBlockEntity()) {
                return null;
            }
            if (!state.isSolidRender(level, pos)) {
                continue;
            }
            BakedModel model = dispatcher.getBlockModel(state);
            List<BakedQuad> topQuads = model.getQuads(state, Direction.UP, random);
            if (topQuads.isEmpty()) {
                continue;
            }
            var topSprite = topQuads.get(0).getSprite();
            Direction side = Math.abs(dirX) > Math.abs(dirZ)
                    ? (dirX > 0.0F ? Direction.EAST : Direction.WEST)
                    : (dirZ > 0.0F ? Direction.SOUTH : Direction.NORTH);
            List<BakedQuad> sideQuads = model.getQuads(state, side, random);
            var sideSprite = sideQuads.isEmpty() ? topSprite : sideQuads.get(0).getSprite();
            int light = LevelRenderer.getLightColor(level, pos.above());
            float liftScale = 0.72F + 0.28F * level.random.nextFloat();
            return new Patch(x + 0.5D, z + 0.5D, radius, y + 1.0D,
                    topSprite.getU0(), topSprite.getV0(), topSprite.getU1(), topSprite.getV1(),
                    sideSprite.getU0(), sideSprite.getV0(), sideSprite.getU1(), sideSprite.getV1(),
                    liftScale, light, dirX, dirZ);
        }
        return null;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ACTIVE.isEmpty()) {
            if (level == null) {
                ACTIVE.clear();
            }
            return;
        }
        try {
            draw(event, level);
        } catch (Throwable t) {
            ACTIVE.clear();
            if (!warnedFailure) {
                warnedFailure = true;
                LOGGER.error("[TN-C SlamWave] ground wave disabled after render error", t);
            }
        }
    }

    private static void draw(RenderLevelStageEvent event, ClientLevel level) {
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        double ageNow = level.getGameTime() + partial;

        RenderType type = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(type);

        // 该阶段的事件 PoseStack 已含一次相机变换；这里自建「世界 → 相机相对」平移矩阵。
        Matrix4f matrix = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z);

        int drawn = 0;
        float peak = 0.0F;
        int cast = -1;
        Iterator<Wave> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Wave wave = iterator.next();
            SlamRingFx.Ring ring = wave.ring;
            double age = ageNow - ring.bornAt;
            if (ring.expired || age > SlamRingFx.TICKS + LIFE + 20) {
                iterator.remove();
                continue;
            }
            double distance = Math.hypot(ring.centerX - camera.x, ring.centerZ - camera.z);
            if (distance > RENDER_DIST) {
                continue;
            }
            cast = ring.serverStart;
            for (Patch patch : wave.patches) {
                double arrive = Math.max(0.0D,
                        patch.radius() / SlamRingFx.MAX_RADIUS * SlamRingFx.TICKS - LEAD);
                double local = age - arrive;
                if (local <= 0.0D || local > LIFE) {
                    continue;
                }
                float p = (float) (local / LIFE);
                float envelope = Mth.sin(p * (float) Math.PI);
                float lift = (float) (MAX_LIFT * patch.liftScale() * envelope);
                float alpha = Math.min(1.0F, envelope * 2.2F) * 0.95F;
                peak = Math.max(peak, lift);
                drawn++;
                drawPatch(buffer, matrix, patch, lift, alpha);
            }
        }
        buffers.endBatch(type);

        if (SlamRingFx.DEBUG && drawn > 0 && level.getGameTime() - lastStatsTick >= 20L) {
            lastStatsTick = level.getGameTime();
            LOGGER.info("[TN-C SlamWave] cast={} active={} peakLift={}",
                    cast, drawn, String.format(Locale.ROOT, "%.2f", peak));
        }
    }

    /** 一块地砖：顶面 + 四周侧面，抬升后能看出厚度。 */
    private static void drawPatch(VertexConsumer buffer, Matrix4f matrix, Patch patch,
                                  float lift, float alpha) {
        double half = PATCH * 0.5D;
        double x0 = patch.x() - half;
        double x1 = patch.x() + half;
        double z0 = patch.z() - half;
        double z1 = patch.z() + half;
        double base = patch.groundY() - 0.03D;

        // 外侧一角抬得更高，形成向外倾的坡面；内侧几乎贴地。
        float y00 = (float) (patch.groundY() + lift * (0.68F + 0.32F * radial01(-half, -half, patch)));
        float y10 = (float) (patch.groundY() + lift * (0.68F + 0.32F * radial01(half, -half, patch)));
        float y11 = (float) (patch.groundY() + lift * (0.68F + 0.32F * radial01(half, half, patch)));
        float y01 = (float) (patch.groundY() + lift * (0.68F + 0.32F * radial01(-half, half, patch)));

        // 顶面
        quad(buffer, matrix, alpha, patch.light(),
                x0, y00, z0, x1, y10, z0, x1, y11, z1, x0, y01, z1,
                patch.u0(), patch.v0(), patch.u1(), patch.v1(), patch.u0(), patch.v1(), patch.u1(), patch.v0(),
                0.0F, 1.0F, 0.0F);

        // 四个侧面（用方块侧面贴图，保证草皮/泥土层次）
        quad(buffer, matrix, alpha, patch.light(),
                x0, base, z0, x1, base, z0, x1, y10, z0, x0, y00, z0,
                patch.su0(), patch.sv1(), patch.su1(), patch.sv1(), patch.su1(), patch.sv0(), patch.su0(), patch.sv0(),
                0.0F, 0.0F, -1.0F);
        quad(buffer, matrix, alpha, patch.light(),
                x1, base, z1, x0, base, z1, x0, y01, z1, x1, y11, z1,
                patch.su0(), patch.sv1(), patch.su1(), patch.sv1(), patch.su1(), patch.sv0(), patch.su0(), patch.sv0(),
                0.0F, 0.0F, 1.0F);
        quad(buffer, matrix, alpha, patch.light(),
                x0, base, z1, x0, base, z0, x0, y00, z0, x0, y01, z1,
                patch.su0(), patch.sv1(), patch.su1(), patch.sv1(), patch.su1(), patch.sv0(), patch.su0(), patch.sv0(),
                -1.0F, 0.0F, 0.0F);
        quad(buffer, matrix, alpha, patch.light(),
                x1, base, z0, x1, base, z1, x1, y11, z1, x1, y10, z0,
                patch.su0(), patch.sv1(), patch.su1(), patch.sv1(), patch.su1(), patch.sv0(), patch.su0(), patch.sv0(),
                1.0F, 0.0F, 0.0F);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, float alpha, int light,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             float u0, float v0, float u1, float v1,
                             float u2, float v2, float u3, float v3,
                             float nx, float ny, float nz) {
        vertex(buffer, matrix, x0, y0, z0, u0, v0, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x2, y2, z2, u2, v2, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x3, y3, z3, u3, v3, alpha, light, nx, ny, nz);
    }

    /** 该角相对中心的外向程度：1 = 最外角，0 = 最内角。 */
    private static double radial01(double cornerX, double cornerZ, Patch patch) {
        double outward = cornerX * patch.dirX() + cornerZ * patch.dirZ();
        return Mth.clamp(outward / (PATCH * 0.5D) * 0.5D + 0.5D, 0.0D, 1.0D);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, double x, double y, double z,
                               float u, float v, float alpha, int light,
                               float nx, float ny, float nz) {
        // entityTranslucent 的格式是 POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL：
        // overlay 与 normal 都必须显式写入。
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(nx, ny, nz)
                .endVertex();
    }
}
