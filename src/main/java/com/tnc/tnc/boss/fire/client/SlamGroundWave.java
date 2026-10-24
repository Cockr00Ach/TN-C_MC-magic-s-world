package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.world.entity.Entity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 技能 B「大地震波」的地面视觉（纯客户端、不产生方块实体、不改存档）。
 *
 * <p>它是独立技能：只监听 {@link VolcanicTortoiseEntity#quakeWaveStart()} 的变化，
 * 与火环 {@code SlamRingFx} 没有任何调用关系。技能 A 不会再触发任何地表隆起。
 *
 * <p>重踏落地时沿震波路径做一次有界采样：每个角度、每 2 格半径取一个支撑面。
 * 采样以「世界实际高度图 + 施法高度」共同限定窗口，从窗口顶向下找第一个实心方块，
 * 因此既不会跑到洞穴屋顶，也不会因为是负高度或悬空平台而一个点都采不到。
 *
 * <p>每块地砖画成一个薄盒子：顶面用方块顶面贴图，四周用方块侧面贴图，
 * 抬起后能看到真实的厚度和侧面明暗，而不是一层平面颜色。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SlamGroundWave {

    /** 调试开关：-Dtnc.tortoise.attack.debug=true 时打印采样/活跃地块统计。 */
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.attack.debug");

    /** 圆周采样角度数。 */
    private static final int ANGLES = 96;
    /** 径向采样步长（格）。 */
    private static final double RADIAL_STEP = 2.0D;
    /** 径向采样层数（2..22 格）。 */
    private static final int STEPS = 11;
    /** 最大抬升（格）。 */
    private static final double MAX_LIFT = 0.80D;
    /** 一块地砖从抬起到落回原位的 tick 数。 */
    private static final int LIFE = 8;
    /** 地面比震波中心到达早多少 tick（让坡面先露出来）。 */
    private static final double LEAD = 2.0D;
    /** 单块地砖的边长（格）。 */
    private static final double PATCH = 2.05D;
    /** 相机超过这个距离就不再画地面细节。 */
    private static final double RENDER_DIST = 64.0D;
    /** 采样窗口：施法高度向上/向下各留多少格。 */
    private static final int SEARCH_UP = 8;
    private static final int SEARCH_DOWN = 4;
    /** 施法点离地表太远时，从高度图向下额外搜索的格数。 */
    private static final int SEARCH_FALLBACK = 12;

    private static final List<Wave> ACTIVE = new ArrayList<>();
    /** 每个实体上一次看到的震波编号，用来判断「有新的一发」。 */
    private static final Map<Integer, Integer> SEEN = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean warnedFailure;
    /** 采样数为 0 时只提醒一次，避免刷屏。 */
    private static boolean warnedZero;
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
        final int castId;
        final double centerX;
        final double centerY;
        final double centerZ;
        final long bornAt;
        final List<Patch> patches = new ArrayList<>();

        Wave(int castId, Vec3 center, long bornAt) {
            this.castId = castId;
            this.centerX = center.x;
            this.centerY = center.y;
            this.centerZ = center.z;
            this.bornAt = bornAt;
        }
    }

    /** 客户端 tick：发现新的震波编号就采样一次。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            SEEN.clear();
            return;
        }
        boolean any = false;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss)) {
                continue;
            }
            any = true;
            int start = boss.quakeWaveStart();
            Integer previous = SEEN.get(boss.getId());
            if (start < 0) {
                SEEN.remove(boss.getId());
                continue;
            }
            if (previous == null || previous != start) {
                SEEN.put(boss.getId(), start);
                var player = Minecraft.getInstance().player;
                if (player != null && boss.distanceToSqr(player) < RENDER_DIST * RENDER_DIST) {
                    begin(level, start, boss.quakeWaveCenter(), level.getGameTime());
                }
            }
        }
        if (!any) {
            SEEN.clear();
        }
    }

    /** 新一发震波：对本次路径做一次有界地表采样。 */
    private static void begin(ClientLevel level, int castId, Vec3 center, long bornAt) {
        if (ACTIVE.size() > 4) {
            ACTIVE.clear();
        }
        Wave wave = new Wave(castId, center, bornAt);
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < ANGLES; i++) {
            double angle = Math.PI * 2.0D * i / ANGLES;
            float dirX = (float) Math.cos(angle);
            float dirZ = (float) Math.sin(angle);
            for (int step = 1; step <= STEPS; step++) {
                double radius = step * RADIAL_STEP;
                int x = Mth.floor(center.x + dirX * radius);
                int z = Mth.floor(center.z + dirZ * radius);
                Patch patch = sample(level, x, z, radius, center.y, dirX, dirZ);
                if (patch != null) {
                    wave.patches.add(patch);
                    minY = Math.min(minY, patch.groundY());
                    maxY = Math.max(maxY, patch.groundY());
                }
            }
        }
        ACTIVE.add(wave);
        int n = wave.patches.size();
        if (n == 0 && !warnedZero) {
            warnedZero = true;
            int sx = Mth.floor(center.x + 2.0D);
            int sz = Mth.floor(center.z);
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            LOGGER.warn("[TN-C QuakeWave] sampled 0 patches: cast={} center={} heightAtR2={} minBuild={} stateAtHeight={}",
                    castId, center, h, level.getMinBuildHeight(),
                    level.getBlockState(new BlockPos(sx, h - 1, sz)));
        }
        if (DEBUG) {
            LOGGER.info("[TN-C QuakeWave] cast={} patches={} centerY={} groundY=[{}, {}] lift={}",
                    castId, n, String.format(Locale.ROOT, "%.2f", center.y),
                    n == 0 ? "-" : String.format(Locale.ROOT, "%.2f", minY),
                    n == 0 ? "-" : String.format(Locale.ROOT, "%.2f", maxY),
                    MAX_LIFT);
        }
    }

    private static Patch sample(ClientLevel level, int x, int z, double radius,
                                double centerY, float dirX, float dirZ) {
        int heightTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int top = Math.min(heightTop, Mth.floor(centerY + SEARCH_UP));
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(centerY - SEARCH_DOWN));
        if (top < bottom) {
            // 施法点离地表太远（例如站在高台上或高度图异常）时，至少从高度图向下找 12 格。
            bottom = Math.max(level.getMinBuildHeight(), Math.min(top, heightTop) - SEARCH_FALLBACK);
        }
        if (top < bottom) {
            return null;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create(42L);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
                continue;
            }
            BakedModel model = dispatcher.getBlockModel(state);
            BakedQuad topQuad = pickQuad(model, state, Direction.UP, random);
            boolean solid = state.isSolidRender(level, pos) || state.isSolid();
            if (topQuad == null) {
                if (!solid) {
                    continue;
                }
                // 有些模组地板模型没有独立的 UP 面（全部塞在无方向列表里），
                // 这里退回石头顶面贴图，保证地面波至少能显示出来。
                topQuad = stoneTop(random);
                if (topQuad == null) {
                    continue;
                }
            }
            Direction side = Math.abs(dirX) > Math.abs(dirZ)
                    ? (dirX > 0.0F ? Direction.EAST : Direction.WEST)
                    : (dirZ > 0.0F ? Direction.SOUTH : Direction.NORTH);
            BakedQuad sideQuad = pickQuad(model, state, side, random);
            if (sideQuad == null) {
                sideQuad = topQuad;
            }
            int light = LevelRenderer.getLightColor(level, pos.above());
            float liftScale = 0.72F + 0.28F * level.random.nextFloat();
            return new Patch(x + 0.5D, z + 0.5D, radius, y + 1.0D,
                    topQuad.getSprite().getU0(), topQuad.getSprite().getV0(),
                    topQuad.getSprite().getU1(), topQuad.getSprite().getV1(),
                    sideQuad.getSprite().getU0(), sideQuad.getSprite().getV0(),
                    sideQuad.getSprite().getU1(), sideQuad.getSprite().getV1(),
                    liftScale, light, dirX, dirZ);
        }
        return null;
    }

    /** 取某个方向的烘焙四边形；该方向为空时退回无方向列表（模组方块常见）。 */
    private static BakedQuad pickQuad(BakedModel model, BlockState state, Direction direction,
                                      RandomSource random) {
        List<BakedQuad> quads = model.getQuads(state, direction, random);
        if (quads.isEmpty()) {
            quads = model.getQuads(state, null, random);
        }
        return quads.isEmpty() ? null : quads.get(0);
    }

    /** 兜底：石头顶面，用于没有可用面数据的模组地板。 */
    private static BakedQuad stoneTop(RandomSource random) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        List<BakedQuad> quads = dispatcher.getBlockModel(stone).getQuads(stone, Direction.UP, random);
        return quads.isEmpty() ? null : quads.get(0);
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
                LOGGER.error("[TN-C QuakeWave] ground wave disabled after render error", t);
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
            double age = ageNow - wave.bornAt;
            if (age > VolcanicTortoiseEntity.QUAKE_TICKS + LIFE + 20) {
                iterator.remove();
                continue;
            }
            double distance = Math.hypot(wave.centerX - camera.x, wave.centerZ - camera.z);
            if (distance > RENDER_DIST) {
                continue;
            }
            cast = wave.castId;
            for (Patch patch : wave.patches) {
                double arrive = Math.max(0.0D,
                        patch.radius() / VolcanicTortoiseEntity.QUAKE_RADIUS
                                * VolcanicTortoiseEntity.QUAKE_RING_TICKS - LEAD);
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

        if (DEBUG && !ACTIVE.isEmpty()) {
            drawBeacon(buffers, matrix, ACTIVE.get(ACTIVE.size() - 1));
        }

        if (DEBUG && drawn > 0 && level.getGameTime() - lastStatsTick >= 20L) {
            lastStatsTick = level.getGameTime();
            LOGGER.info("[TN-C QuakeWave] cast={} active={} peakLift={}",
                    cast, drawn, String.format(Locale.ROOT, "%.2f", peak));
        }
    }

    /**
     * 调试用：在震波中心画一根 4 格高的白色光柱。
     * 用 {@link RenderType#lightning()}（已知可用的纯色渲染类型），
     * 用来区分“位置/矩阵不对”与“地块材质/剔除看不见”。
     */
    private static void drawBeacon(net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                   Matrix4f matrix, Wave wave) {
        RenderType type = RenderType.lightning();
        VertexConsumer buffer = buffers.getBuffer(type);
        float w = 0.6F;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.5D * i;
            float dx = (float) Math.cos(a) * w;
            float dz = (float) Math.sin(a) * w;
            beaconQuad(buffer, matrix, wave.centerX, wave.centerY, wave.centerZ, dx, dz);
        }
        buffers.endBatch(type);
    }

    private static void beaconQuad(VertexConsumer buffer, Matrix4f matrix,
                                   double cx, double cy, double cz, float dx, float dz) {
        float y0 = (float) cy;
        float y1 = (float) cy + 14.0F;
        buffer.vertex(matrix, (float) cx - dx, y0, (float) cz - dz).color(1.0F, 0.95F, 0.8F, 1.0F).endVertex();
        buffer.vertex(matrix, (float) cx + dx, y0, (float) cz + dz).color(1.0F, 0.95F, 0.8F, 1.0F).endVertex();
        buffer.vertex(matrix, (float) cx + dx, y1, (float) cz + dz).color(1.0F, 0.95F, 0.8F, 1.0F).endVertex();
        buffer.vertex(matrix, (float) cx - dx, y1, (float) cz - dz).color(1.0F, 0.95F, 0.8F, 1.0F).endVertex();
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
        // 正反两种绕序各写一遍：不依赖 RenderType 的剔除设置，也避免不同绕序约定
        // 导致整片地砖被背面剔除而完全看不见。
        vertex(buffer, matrix, x0, y0, z0, u0, v0, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x2, y2, z2, u2, v2, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x3, y3, z3, u3, v3, alpha, light, nx, ny, nz);
        vertex(buffer, matrix, x3, y3, z3, u3, v3, alpha, light, -nx, -ny, -nz);
        vertex(buffer, matrix, x2, y2, z2, u2, v2, alpha, light, -nx, -ny, -nz);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, alpha, light, -nx, -ny, -nz);
        vertex(buffer, matrix, x0, y0, z0, u0, v0, alpha, light, -nx, -ny, -nz);
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
        // 光照使用满亮度：震波是魔法冲击，夜里/雨里也必须看得见，
        // 同时避免暗处 quad 亮度接近 0 而“看不见”。
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(nx, ny, nz)
                .endVertex();
    }
}
