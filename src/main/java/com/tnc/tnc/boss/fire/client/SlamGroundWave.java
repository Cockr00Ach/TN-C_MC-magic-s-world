package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 重踏的地面视觉起伏（纯客户端、不产生方块实体、不改存档）。
 *
 * <p>在重踏开始时沿火环路径做一次有界采样：每个角度、每 2 格半径取一个「支撑面」，
 * 从施法高度向下找第一个实心方块（不是整列最高方块，所以洞穴里不会跑到屋顶），
 * 跳过流体、方块实体和贴图缺失的方块。采样结果缓存为一张贴地 quad 的贴图坐标。
 *
 * <p>渲染时只画「火环刚扫过」的那几圈：每块地砖在波到达后抬起、轻微外倾，再落回原位，
 * 生命周期 {@link #LIFE} tick。抬升量 {@link #MAX_LIFT} 0.62 格，实际按 0.6~1.0 的系数错落。
 *
 * <p>这是纯视觉副本：没有碰撞、不修改世界、不加载区块；区块没加载或距离过远时直接不画。
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
    private static final double MAX_LIFT = 0.62D;
    /** 一块地砖从抬起到落回原位的 tick 数。 */
    private static final int LIFE = 11;
    /** 单块地砖的边长（格）：略大于采样间距，保证成片不留缝。 */
    private static final double PATCH = 1.65D;
    /** 相机超过这个距离就不再画地面细节（火环粒子和伤害不受影响）。 */
    private static final double RENDER_DIST = 48.0D;
    /** 距施法地面高度的向下/向上搜索范围（格）。 */
    private static final int SEARCH_UP = 2;
    private static final int SEARCH_DOWN = 3;

    private static final List<Wave> ACTIVE = new ArrayList<>();

    private SlamGroundWave() {
    }

    /** 一块地表贴片：世界坐标 + 贴图 UV + 抬升系数 + 光照。 */
    private record Patch(double x, double z, double radius, double groundY,
                         float u0, float v0, float u1, float v1,
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
                }
            }
        }
        ACTIVE.add(wave);
    }

    private static Patch sample(ClientLevel level, int x, int z, double radius,
                                double centerY, float dirX, float dirZ) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        int top = Mth.floor(centerY + SEARCH_UP);
        int bottom = Mth.floor(centerY - SEARCH_DOWN);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            // 水面 / 岩浆表面不铺地块，避免水下出现浮空地面。
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
            List<BakedQuad> quads = model.getQuads(state, Direction.UP, RandomSource.create(42L));
            if (quads.isEmpty()) {
                return null;
            }
            var sprite = quads.get(0).getSprite();
            int light = LevelRendererLight.at(level, pos.above());
            float liftScale = 0.60F + 0.40F * level.random.nextFloat();
            return new Patch(x + 0.5D, z + 0.5D, radius, y + 1.0D,
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
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
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        double ageNow = level.getGameTime() + partial;

        RenderType type = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(type);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

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
            for (Patch patch : wave.patches) {
                double arrive = patch.radius() / SlamRingFx.MAX_RADIUS * SlamRingFx.TICKS;
                double local = age - arrive;
                if (local <= 0.0D || local > LIFE) {
                    continue;
                }
                float p = (float) (local / LIFE);
                float envelope = Mth.sin(p * (float) Math.PI);
                float lift = (float) (MAX_LIFT * patch.liftScale() * envelope);
                float alpha = Math.min(1.0F, envelope * 1.6F) * 0.92F;

                double half = PATCH * 0.5D;
                double x0 = patch.x() - half;
                double x1 = patch.x() + half;
                double z0 = patch.z() - half;
                double z1 = patch.z() + half;

                // 外侧一角抬得更高，形成向外倾的坡面；内侧几乎贴地。
                double y00 = patch.groundY() + lift * (0.72D + 0.28D * radial01(-half, -half, patch));
                double y10 = patch.groundY() + lift * (0.72D + 0.28D * radial01(half, -half, patch));
                double y11 = patch.groundY() + lift * (0.72D + 0.28D * radial01(half, half, patch));
                double y01 = patch.groundY() + lift * (0.72D + 0.28D * radial01(-half, half, patch));

                vertex(buffer, matrix, x0, y00, z0, patch.u0(), patch.v0(), alpha, patch.light());
                vertex(buffer, matrix, x1, y10, z0, patch.u1(), patch.v0(), alpha, patch.light());
                vertex(buffer, matrix, x1, y11, z1, patch.u1(), patch.v1(), alpha, patch.light());
                vertex(buffer, matrix, x0, y01, z1, patch.u0(), patch.v1(), alpha, patch.light());
            }
        }
        pose.popPose();
        buffers.endBatch(type);
    }

    /** 该角相对中心的外向程度：1 = 最外角，0 = 最内角。 */
    private static double radial01(double cornerX, double cornerZ, Patch patch) {
        double outward = cornerX * patch.dirX() + cornerZ * patch.dirZ();
        return Mth.clamp(outward / (PATCH * 0.5D) * 0.5D + 0.5D, 0.0D, 1.0D);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, double x, double y, double z,
                               float u, float v, float alpha, int light) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .uv2(light)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** 小包装：避免在主类里散落光照常量。 */
    private static final class LevelRendererLight {
        static int at(ClientLevel level, BlockPos pos) {
            return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        }
    }
}
