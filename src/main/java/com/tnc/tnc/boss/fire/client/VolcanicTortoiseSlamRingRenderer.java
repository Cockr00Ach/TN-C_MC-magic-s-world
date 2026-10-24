package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 重踏火环：一道 360° 连续闭合的环带网格，从服务端锁定的砸地中心向外推进。
 *
 * <p>服务端只同步「开始时刻 + 中心点」（{@link VolcanicTortoiseEntity#slamRingStart()} /
 * {@link VolcanicTortoiseEntity#slamRingCenter()}）。每一帧按同一个时间进度重算半径，
 * 所以屏幕上永远只有当前半径的那一道环，不会像逐 tick 撒粒子那样留下一圈圈旧火焰。
 *
 * <p>几何：环带内缘/外缘各一段竖直火墙 + 贴地环带面。竖直火墙让玩家在地面视角也能
 * 看到向上翻卷的火舌，而不是只在俯视时看到一个平面圆环。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class VolcanicTortoiseSlamRingRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/entity/volcanic_tortoise_slam_ring.png");

    /** 圆周分段：96 段在 22 格半径上每段约 1.4 格，肉眼看不出折线。 */
    private static final int SEGMENTS = 96;
    /** 环带宽度（格）：同时用于内外火墙的间距。 */
    private static final double BAND_WIDTH = 1.25D;
    /** 火墙基准高度（格），再乘 0.55–1.15 的起伏。 */
    private static final double FLAME_HEIGHT = 1.0D;
    /** 贴图在圆周方向重复几次。 */
    private static final float UV_REPEAT = 6.0F;
    /** 纹理沿圆周流动速度（圈/ tick）。 */
    private static final float UV_SCROLL = 0.010F;
    private static final float ALPHA = 0.92F;

    // Oculus 会把 Minecraft 的共享 BufferSource 换成 endBatch 无效的分段实现，
    // 与 PortalRitualRenderer 一样用自己的 immediate buffer，保证总能提交。
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(262144));

    private VolcanicTortoiseSlamRingRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        RenderType type = RenderType.entityTranslucentEmissive(TEXTURE);
        VertexConsumer out = null;
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss)) {
                continue;
            }
            int start = boss.slamRingStart();
            if (start < 0) {
                continue;
            }
            // 视觉比服务端落后半 tick：环带永远不会先于伤害到达。
            float age = (level.getGameTime() - start) + partial - 0.5F;
            if (age <= 0.0F || age > VolcanicTortoiseEntity.SLAM_RING_TICKS + 3.0F) {
                continue;
            }
            double radius = 22.0D * Math.min(age, VolcanicTortoiseEntity.SLAM_RING_TICKS)
                    / (double) VolcanicTortoiseEntity.SLAM_RING_TICKS;
            if (radius < 0.8D) {
                continue;
            }
            Vec3 center = boss.slamRingCenter();
            if (center.distanceToSqr(camera) > 96.0D * 96.0D) {
                continue;
            }
            // 最大半径附近快速淡出，随后彻底清理。
            float fade = Mth.clamp((VolcanicTortoiseEntity.SLAM_RING_TICKS + 2.0F - age) / 4.0F, 0.0F, 1.0F);
            Matrix4f pose = new Matrix4f().translation(
                    (float) (center.x - camera.x),
                    (float) (center.y - camera.y),
                    (float) (center.z - camera.z));
            if (out == null) {
                out = BUFFERS.getBuffer(type);
            }
            ring(level, out, pose, center, radius, age, fade * ALPHA);
        }
        if (out != null) {
            BUFFERS.endBatch(type);
        }
    }

    private static void ring(ClientLevel level, VertexConsumer out, Matrix4f pose, Vec3 center,
                             double radius, float age, float alpha) {
        double inner = Math.max(0.1D, radius - BAND_WIDTH * 0.5D);
        double outer = radius + BAND_WIDTH * 0.5D;
        float scroll = age * UV_SCROLL;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            double cos0 = Math.cos(a0);
            double sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1);
            double sin1 = Math.sin(a1);

            double xi0 = center.x + cos0 * inner;
            double zi0 = center.z + sin0 * inner;
            double xi1 = center.x + cos1 * inner;
            double zi1 = center.z + sin1 * inner;
            double xo0 = center.x + cos0 * outer;
            double zo0 = center.z + sin0 * outer;
            double xo1 = center.x + cos1 * outer;
            double zo1 = center.z + sin1 * outer;

            // 任何一段落在未加载区块里就整段跳过（服务端伤害同样跳过）。
            if (!loaded(level, center, xi0, zi0) || !loaded(level, center, xo1, zo1)) {
                continue;
            }
            double yIn0 = ground(level, xi0, zi0, center.y);
            double yIn1 = ground(level, xi1, zi1, center.y);
            double yOut0 = ground(level, xo0, zo0, center.y);
            double yOut1 = ground(level, xo1, zo1, center.y);

            // 火舌高度按角度起伏并量化成像素台阶，避免光滑塑料圆筒。
            float h = flameHeight(a0, age);
            float hNext = flameHeight(a1, age);

            float u0 = (float) i / SEGMENTS * UV_REPEAT + scroll;
            float u1 = (float) (i + 1) / SEGMENTS * UV_REPEAT + scroll;

            // 1) 外圈竖直火墙（主要可见面）
            wall(out, pose, xo0, yOut0, zo0, xo1, yOut1, zo1,
                    yOut0 + h, yOut1 + hNext, cos0, sin0, u0, u1, alpha, 1.0F);
            // 2) 内圈竖直火墙（矮一些，做出“向上翻卷”的厚度）
            wall(out, pose, xi0, yIn0, zi0, xi1, yIn1, zi1,
                    yIn0 + h * 0.62F, yIn1 + hNext * 0.62F, -cos0, -sin0, u0, u1, alpha, 0.82F);
            // 3) 贴地环带面：把内外缘连起来，给底部一层发亮的地面火
            band(out, pose, xi0, yIn0 + 0.03D, zi0, xi1, yIn1 + 0.03D, zi1,
                    xo1, yOut1 + 0.03D, zo1, xo0, yOut0 + 0.03D, zo0, u0, u1, alpha * 0.55F);
        }
    }

    /** 竖直火墙：每段两端高度不同，形成高低起伏的火舌轮廓。 */
    private static void wall(VertexConsumer out, Matrix4f pose,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double top0, double top1, double nx, double nz,
                             float u0, float u1, float alpha, float gain) {
        // 两遍相反绕序：半透明渲染类型会剔背面，保证从环内/环外都能看到。
        quad(out, pose, x0, y0, z0, u0, 0.0F, x1, y1, z1, u1, 0.0F,
                x1, top1, z1, u1, 1.0F, x0, top0, z0, u0, 1.0F,
                (float) nx, 0.0F, (float) nz, alpha, gain);
        quad(out, pose, x0, y0, z0, u0, 0.0F, x0, top0, z0, u0, 1.0F,
                x1, top1, z1, u1, 1.0F, x1, y1, z1, u1, 0.0F,
                (float) -nx, 0.0F, (float) -nz, alpha, gain);
    }

    private static void band(VertexConsumer out, Matrix4f pose,
                             double xi0, double yi0, double zi0, double xi1, double yi1, double zi1,
                             double xo1, double yo1, double zo1, double xo0, double yo0, double zo0,
                             float u0, float u1, float alpha) {
        quad(out, pose, xi0, yi0, zi0, u0, 0.10F, xi1, yi1, zi1, u1, 0.10F,
                xo1, yo1, zo1, u1, 0.90F, xo0, yo0, zo0, u0, 0.90F,
                0.0F, 1.0F, 0.0F, alpha, 1.0F);
    }

    private static void quad(VertexConsumer out, Matrix4f pose,
                             double x0, double y0, double z0, float u0, float v0,
                             double x1, double y1, double z1, float u1, float v1,
                             double x2, double y2, double z2, float u2, float v2,
                             double x3, double y3, double z3, float u3, float v3,
                             float nx, float ny, float nz, float alpha, float gain) {
        vertex(out, pose, x0, y0, z0, u0, v0, nx, ny, nz, alpha, gain);
        vertex(out, pose, x1, y1, z1, u1, v1, nx, ny, nz, alpha, gain);
        vertex(out, pose, x2, y2, z2, u2, v2, nx, ny, nz, alpha, gain);
        vertex(out, pose, x3, y3, z3, u3, v3, nx, ny, nz, alpha, gain);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, double x, double y, double z,
                               float u, float v, float nx, float ny, float nz, float alpha, float gain) {
        out.vertex(pose, (float) x, (float) y, (float) z)
                .color(1.0F, gain, gain * 0.94F, alpha)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0x00F000F0)
                .normal(nx, ny, nz)
                .endVertex();
    }

    /** 火舌高度：三个不同频率的正弦叠加，随时间缓慢变化，量化到 1/16 格。 */
    private static float flameHeight(double angle, float age) {
        double t = age * 0.09D;
        double v = 0.78D
                + 0.26D * Math.sin(3.0D * angle + t * 2.1D)
                + 0.16D * Math.sin(7.0D * angle - t * 3.4D)
                + 0.09D * Math.sin(11.0D * angle + t * 5.2D);
        v = Math.max(0.35D, Math.min(1.20D, v));
        return (float) (Math.round(v * 16.0D) / 16.0D) * (float) FLAME_HEIGHT;
    }

    private static boolean loaded(ClientLevel level, Vec3 center, double x, double z) {
        return level.hasChunkAt(BlockPos.containing(x, center.y, z));
    }

    private static double ground(ClientLevel level, double x, double z, double fallbackY) {
        BlockPos pos = BlockPos.containing(x, fallbackY, z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        double y = top;
        // 高差太大（悬崖/深坑）时退回 Boss 脚下高度，避免拉出一面竖墙。
        if (Math.abs(y - fallbackY) > 6.0D) {
            y = fallbackY;
        }
        return y + 0.02D;
    }
}
