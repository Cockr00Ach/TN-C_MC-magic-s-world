package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 火球链 t1/t2 与射线链 t1/t2 的**贴图模型**（作者 2026-10-18：「修改下火球链与射线链 t1 和 t2
 * 法术的建模，同样采用真实建模」）。
 *
 * <p>为什么另开一个类：{@link TNFireBoltRenderer} 里原来那套是「车出来的光滑旋转体」——
 * 每帧按 {@code flameColor(t)} 给顶点刷一层渐变，形状是一圈圈连续的环。
 * 作者早就说过<b>不要光滑几何体</b>，而燃烧链的光环已经改成「贴图火苗 + 一块块方形面」
 * 的实体模型，这两条链的 t1/t2 自然也要同一个路子 ✓
 *
 * <p>做法：
 * <ul>
 *   <li><b>火球</b>：一层贴图球壳（{@code fire_ball.png}，斑块状火焰像素图）+ 几条向后拖的
 *       火舌贴片（{@code fire_lance.png}，越往后越透明）⇒ 远看是火球，近看每一格都有像素细节 ✓</li>
 *   <li><b>射线</b>：一根**有棱的柱体**（8 棱 × 9 段）贴 {@code fire_lance.png}，
 *       尖端白热、沿轴一节节转暗；里面再套一根白热内芯 ⇒ 是"一根烧红的棍"，不是一圈光圈 ✓</li>
 * </ul>
 *
 * <p>⚠️ 贴图必须走 {@code entityCutoutNoCull}：{@code RenderType} 的 cutout 会做 alpha 测试，
 * 像素图的硬边才不会被插值成糊边 ✓；需要渐隐的火舌走 {@code entityTranslucent} ✓
 * （cutout 不吃顶点 alpha，写多少都是 1）。
 */
public final class TNFireballModel {

    private static final ResourceLocation BALL_TEX =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/fire_ball.png");
    private static final ResourceLocation LANCE_TEX =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/fire_lance.png");

    /** 球壳的分段数（经度 × 纬度）。8×12 已经够圆，再多就是白烧面数 ✗ */
    private static final int BALL_LAT = 8;
    private static final int BALL_LON = 12;

    /** 射线的棱数与分段数。8 棱看得出"有棱"，9 段够把水滴形收出来 ✓ */
    private static final int LANCE_SIDES = 8;
    private static final int LANCE_SEGS = 9;

    /** 射线总半长（格）：作者定的是总长 3.0 格 ✓ */
    private static final double LANCE_HALF = 1.5D;

    private TNFireballModel() {
    }

    // ------------------------------------------------------------------
    //  火球（t1 火球术 / t2 大火球术）
    // ------------------------------------------------------------------

    /**
     * 一颗贴图火球 + 向后拖的火舌。
     *
     * @param radius 当前半径（调用方已经把"成形进度 / 淡出"算进去了）
     */
    public static void fireball(MultiBufferSource buffers, Matrix4f pose,
                                Vec3 dir, Vec3 right, Vec3 up,
                                double radius, double age, float fade) {
        if (radius <= 0.01D || fade <= 0.02F) {
            return;
        }
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(BALL_TEX));
        // 球壳：稍微压扁一点（沿飞行方向 0.92）⇒ 看得出在"往前撞"，不是一颗静止的珠子 ✓
        shell(solid, pose, dir, right, up, radius, radius * 0.92D, age);
        tail(buffers, pose, dir, right, up, radius, age, fade);
    }

    /** 球壳：经纬网格，每个格子一次四顶点，UV 走贴图（经度铺 2 圈，纬度铺 1 圈）。 */
    private static void shell(VertexConsumer vc, Matrix4f pose, Vec3 dir, Vec3 right, Vec3 up,
                              double radius, double alongRadius, double age) {
        for (int i = 0; i < BALL_LAT; i++) {
            double t0 = Math.PI * i / BALL_LAT;
            double t1 = Math.PI * (i + 1) / BALL_LAT;
            float v0 = (float) (i / (double) BALL_LAT);
            float v1 = (float) ((i + 1) / (double) BALL_LAT);
            for (int j = 0; j < BALL_LON; j++) {
                double a0 = 2.0D * Math.PI * j / BALL_LON;
                double a1 = 2.0D * Math.PI * (j + 1) / BALL_LON;
                float u0 = (float) (j / (double) BALL_LON * 2.0D);
                float u1 = (float) ((j + 1) / (double) BALL_LON * 2.0D);

                Vec3 p00 = spherePoint(dir, right, up, radius, alongRadius, t0, a0, age);
                Vec3 p01 = spherePoint(dir, right, up, radius, alongRadius, t0, a1, age);
                Vec3 p11 = spherePoint(dir, right, up, radius, alongRadius, t1, a1, age);
                Vec3 p10 = spherePoint(dir, right, up, radius, alongRadius, t1, a0, age);

                // 球面上越靠"赤道"越亮、两极稍暗 ⇒ 有点体积感（贴图本身已经有明暗块了）
                float lift = 0.86F + 0.14F * (float) Math.sin(t0);
                quad(vc, pose, p00, p01, p11, p10, u0, v0, u1, v1, lift, 1.0F);
            }
        }
    }

    /** 球面点：绕飞行轴（dir）旋转；半径带一点随 age 起伏 ⇒ 表面在鼓动 ✓ */
    private static Vec3 spherePoint(Vec3 dir, Vec3 right, Vec3 up,
                                    double radius, double alongRadius,
                                    double theta, double phi, double age) {
        double wobble = 1.0D + 0.055D * Math.sin(age * 1.15D + theta * 3.1D + phi * 2.0D);
        double sin = Math.sin(theta);
        double cos = Math.cos(theta);
        Vec3 radial = right.scale(Math.cos(phi) * sin).add(up.scale(Math.sin(phi) * sin));
        return radial.scale(radius * wobble).add(dir.scale(cos * alongRadius * wobble));
    }

    /** 尾巴：四条向后拖的火舌贴片（越往后越透明 ⇒ 走 translucent）。 */
    private static void tail(MultiBufferSource buffers, Matrix4f pose,
                             Vec3 dir, Vec3 right, Vec3 up,
                             double radius, double age, float fade) {
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(LANCE_TEX));
        Vec3 base = dir.scale(radius * 0.25D);
        double len = radius * 3.6D;
        for (int k = 0; k < 4; k++) {
            double a = k * (Math.PI / 4.0D) + age * 0.45D;
            Vec3 side = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 rootL = base.subtract(side.scale(radius * 0.62D));
            Vec3 rootR = base.add(side.scale(radius * 0.62D));
            Vec3 tipL = base.subtract(dir.scale(len)).subtract(side.scale(radius * 0.05D));
            Vec3 tipR = base.subtract(dir.scale(len)).add(side.scale(radius * 0.05D));
            // 火根亮、火尖暗：用颜色乘下来，alpha 让尾巴自然散掉 ✓
            quadColored(glow, pose, rootL, rootR, tipR, tipL,
                    1.00F, 0.86F, 0.46F, 0.95F,
                    0.80F, 0.20F, 0.05F, 0.10F * fade);
        }
    }

    // ------------------------------------------------------------------
    //  射线（t1 烈阳射线 / t2 爆炸射线）
    // ------------------------------------------------------------------

    /**
     * 一根贴图火柱：尖端白热、往尾部一节节转暗，里面套一根白热内芯，尾端三片小火舌。
     *
     * @param deep t2 用深一档的配色（作者 2026-10-05：「t2 的射线同步下，但颜色深一点」）
     */
    public static void lance(MultiBufferSource buffers, Matrix4f pose,
                             Vec3 dir, Vec3 right, Vec3 up,
                             double age, float fade, boolean deep) {
        if (fade <= 0.02F) {
            return;
        }
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(LANCE_TEX));
        float shift = deep ? 0.16F : 0.0F;          // 深色版：贴图往下挪一点 + 整体压暗
        float dim = deep ? 0.78F : 1.0F;

        // 外壳：8 棱柱，逐段收放 ⇒ 水滴形
        for (int s = 0; s < LANCE_SEGS; s++) {
            double t0 = s / (double) LANCE_SEGS;
            double t1 = (s + 1) / (double) LANCE_SEGS;
            double a0 = LANCE_HALF - t0 * 2.0D * LANCE_HALF;
            double a1 = LANCE_HALF - t1 * 2.0D * LANCE_HALF;
            double r0 = lanceRadius(t0, age);
            double r1 = lanceRadius(t1, age);
            Vec3 c0 = dir.scale(a0);
            Vec3 c1 = dir.scale(a1);
            float v0 = (float) (t0 * 0.72D + shift);
            float v1 = (float) (t1 * 0.72D + shift);
            for (int k = 0; k < LANCE_SIDES; k++) {
                double g0 = 2.0D * Math.PI * k / LANCE_SIDES;
                double g1 = 2.0D * Math.PI * (k + 1) / LANCE_SIDES;
                Vec3 n0 = WaterGeometry.radial(right, up, g0, 1.0D);
                Vec3 n1 = WaterGeometry.radial(right, up, g1, 1.0D);
                Vec3 p00 = c0.add(n0.scale(r0));
                Vec3 p01 = c0.add(n1.scale(r0));
                Vec3 p11 = c1.add(n1.scale(r1));
                Vec3 p10 = c1.add(n0.scale(r1));
                float u0 = (float) (k / (double) LANCE_SIDES);
                float u1 = (float) ((k + 1) / (double) LANCE_SIDES);
                quad(solid, pose, p00, p01, p11, p10, u0, v0, u1, v1, dim, 1.0F);
            }
        }

        // 内芯：细一圈的白热柱，贴图只取最上面（最白）那一段
        for (int s = 0; s < LANCE_SEGS; s++) {
            double t0 = 0.06D + s * 0.86D / LANCE_SEGS;
            double t1 = 0.06D + (s + 1) * 0.86D / LANCE_SEGS;
            double a0 = LANCE_HALF - t0 * 2.0D * LANCE_HALF;
            double a1 = LANCE_HALF - t1 * 2.0D * LANCE_HALF;
            double r0 = lanceRadius(t0, age) * 0.46D;
            double r1 = lanceRadius(t1, age) * 0.46D;
            Vec3 c0 = dir.scale(a0);
            Vec3 c1 = dir.scale(a1);
            for (int k = 0; k < 4; k++) {
                double g0 = 2.0D * Math.PI * k / 4.0D;
                double g1 = 2.0D * Math.PI * (k + 1) / 4.0D;
                Vec3 n0 = WaterGeometry.radial(right, up, g0, 1.0D);
                Vec3 n1 = WaterGeometry.radial(right, up, g1, 1.0D);
                Vec3 p00 = c0.add(n0.scale(r0));
                Vec3 p01 = c0.add(n1.scale(r0));
                Vec3 p11 = c1.add(n1.scale(r1));
                Vec3 p10 = c1.add(n0.scale(r1));
                quad(solid, pose, p00, p01, p11, p10,
                        (float) (k / 4.0D), 0.02F, (float) ((k + 1) / 4.0D), 0.26F, 1.0F, 1.0F);
            }
        }

        // 尾端三片小火舌：让轮廓不是一根死板的棍 ✓
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(LANCE_TEX));
        for (int w = 0; w < 3; w++) {
            double a = age * 0.30D + w * (Math.PI * 2.0D / 3.0D);
            Vec3 axis = WaterGeometry.radial(right, up, a, 1.0D);
            Vec3 c = dir.scale(-LANCE_HALF * 0.55D).add(axis.scale(0.10D));
            Vec3 side = right.cross(dir.cross(axis)).normalize();
            quadColored(glow, pose,
                    c.subtract(side.scale(0.06D)), c.add(side.scale(0.06D)),
                    c.add(side.scale(0.005D)).subtract(dir.scale(0.42D)),
                    c.subtract(side.scale(0.005D)).subtract(dir.scale(0.42D)),
                    1.00F, 0.88F, 0.50F, 0.90F,
                    0.75F, 0.16F, 0.04F, 0.08F * fade);
        }
    }

    /** 水滴形粗细：前 18% 收尖、后 28% 收细，中间最粗；表面再随 age 起伏 ✓ */
    private static double lanceRadius(double t, double age) {
        double taper;
        if (t < 0.18D) {
            taper = 0.52D + 0.48D * (t / 0.18D);
        } else if (t > 0.72D) {
            taper = 1.0D - 0.58D * ((t - 0.72D) / 0.28D);
        } else {
            taper = 1.0D;
        }
        double wobble = 1.0D + 0.10D * Math.sin(age * 0.85D + t * 8.5D);
        return 0.125D * taper * wobble;
    }

    // ------------------------------------------------------------------
    //  顶点工具
    // ------------------------------------------------------------------

    /** 贴图四顶点（颜色统一，alpha=1）。 */
    private static void quad(VertexConsumer vc, Matrix4f pose,
                             Vec3 p00, Vec3 p01, Vec3 p11, Vec3 p10,
                             float u0, float v0, float u1, float v1, float bright, float alpha) {
        vertex(vc, pose, p00, u0, v0, bright, bright, bright, alpha);
        vertex(vc, pose, p01, u1, v0, bright, bright, bright, alpha);
        vertex(vc, pose, p11, u1, v1, bright, bright, bright, alpha);
        vertex(vc, pose, p10, u0, v1, bright, bright, bright, alpha);
    }

    /** 四顶点（根/尖两套颜色 ⇒ 火苗从亮到暗）。 */
    private static void quadColored(VertexConsumer vc, Matrix4f pose,
                                    Vec3 rootL, Vec3 rootR, Vec3 tipR, Vec3 tipL,
                                    float r0, float g0, float b0, float a0,
                                    float r1, float g1, float b1, float a1) {
        vertex(vc, pose, rootL, 0.05F, 0.02F, r0, g0, b0, a0);
        vertex(vc, pose, rootR, 0.95F, 0.02F, r0, g0, b0, a0);
        vertex(vc, pose, tipR, 0.95F, 0.90F, r1, g1, b1, a1);
        vertex(vc, pose, tipL, 0.05F, 0.90F, r1, g1, b1, a1);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Vec3 p, float u, float v,
                               float r, float g, float b, float a) {
        Vec3 n = p.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : p.normalize();
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal((float) n.x, (float) n.y, (float) n.z)
                .endVertex();
    }


}
