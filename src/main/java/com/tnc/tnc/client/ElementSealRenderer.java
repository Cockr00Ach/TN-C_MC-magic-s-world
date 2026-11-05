package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 风系罗盘 / 光系白金光阵的几何渲染 ✓。
 *
 * <h2>2026-11-05：光阵从「三个环」升级成「一整座仪式阵」</h2>
 * 作者要「光污染」＋「更多的小法阵」── 以前光系只有 3 个细环 ＋ 24 道刻度 ＋ 一个六芒星 ✗，
 * 释放完地上基本只剩一圈白线，完全撑不起「天使 / 神光」的重量 ✗。现在一张主阵包含：
 * <ul>
 *   <li><b>日冕光芒</b>：20 道从 0.9R 射到 1.2R 的尖芒（加色，叠出过曝）✓；</li>
 *   <li><b>三层主环 ＋ 双层晕环</b>：宽窄交替，配合 {@code glow()} 的加色层 ⇒ 边缘自然发白 ✓；</li>
 *   <li><b>24 道符文</b>：径向刻痕 ＋ 外端菱形，外圈一整条“文字带”✓；</li>
 *   <li><b>8 颗小阵</b>：嵌在 0.70R 的内圈上，每颗自己转、带十字刻痕 ✓；</li>
 *   <li><b>八角星 ＋ 内圈刻度 ＋ 中心白核</b>：中心亮到过曝，看起来像“太阳落在地上”✓；</li>
 *   <li><b>光柱</b>：主阵中心升起 3 层柱 ＋ 3 道上升光环，前 1.5 秒最亮然后淡出 ✓
 *       （小阵不打光柱，避免一次 15 只天使时变成 15 根柱子 ✗）。</li>
 * </ul>
 *
 * <p>竖立的阵法（{@code upright}，光耀 t3 起身后那枚光环 / 黑夜之手的门）在同一套几何上
 * 先绕 Y 转向、再绕 X 立起来 ✓ —— 不这样处理的话，竖阵会变成“悬在空中的一张饼” ✗。
 *
 * <p>顶点预算：主阵约 1.9k quads、小阵约 120 quads（22 个顶点/quad ⇒ 主阵约 4.2 万顶点）✓；
 * 光耀一次最多 1 主阵 ＋ 7 小阵 ≈ 2.8k quads，和现有 TNSigilRenderer（火阵）同一量级 ✓。
 */
final class ElementSealRenderer {

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private static final float W_R = 1.00F;
    private static final float W_G = 0.98F;
    private static final float W_B = 0.90F;
    private static final float G_R = 1.00F;
    private static final float G_G = 0.84F;
    private static final float G_B = 0.45F;
    private static final float C_R = 0.86F;
    private static final float C_G = 0.95F;
    private static final float C_B = 1.00F;

    private ElementSealRenderer() {
    }

    static void render(TNMagicCircleEntity e, float partial, PoseStack p, MultiBufferSource b) {
        boolean wind = e.style() == TNMagicCircleEntity.STYLE_WIND;
        if (wind) {
            // 风系保持原样（2026-10-14 才定稿的白色罗盘，不要被光系这次改动波及 ✓）
            double age = e.tickCount + partial;
            float alpha = (float) Math.max(0.0D, Math.min(1.0D, (e.life() - age) / 18.0D));
            WindSolidModels.seal(e.radius(), age, alpha, p, b);
            return;
        }
        boolean sky = e.style() == TNMagicCircleEntity.STYLE_LIGHT_SKY;
        if (e.upright()) {
            // 竖阵：先绕 Y 转到朝向，再绕 X 立起来；几何仍按“贴地”的 XZ 平面写 ✓
            p.pushPose();
            p.mulPose(Axis.YP.rotationDegrees(e.facingDegrees()));
            p.mulPose(Axis.XP.rotationDegrees(90.0F));
            renderLight(e, partial, p, b, true, sky);
            p.popPose();
        } else {
            renderLight(e, partial, p, b, false, sky);
        }
    }

    private static float fade(TNMagicCircleEntity e, float partial) {
        double age = e.tickCount + partial;
        float in = (float) Math.max(0.0D, Math.min(1.0D, age / 4.0D));
        float out = (float) Math.max(0.0D, Math.min(1.0D, (e.life() - age) / 18.0D));
        return in * out;
    }

    private static void renderLight(TNMagicCircleEntity e, float partial, PoseStack p,
                                    MultiBufferSource b, boolean upright, boolean sky) {
        double age = e.tickCount + partial;
        double r = e.radius();
        float alpha = fade(e, partial);
        if (alpha <= 0.01F) {
            return;
        }
        boolean minor = e.style() == TNMagicCircleEntity.STYLE_LIGHT_MINOR;
        boolean veryGrand = e.style() == TNMagicCircleEntity.STYLE_LIGHT_GRAND;
        boolean grand = r >= 4.0D && !upright && !minor;
        Matrix4f m = p.last().pose();
        Vec3 c = new Vec3(0.0D, 0.06D, 0.0D);
        VertexConsumer out = b.getBuffer(WaterRenderTypes.geometry());
        VertexConsumer glow = b.getBuffer(WaterRenderTypes.glow());

        double spinOut = age * (grand ? 0.006D : 0.014D);
        double spinIn = -age * (grand ? 0.010D : 0.020D);
        float pulse = (float) (1.0D + 0.035D * Math.sin(age * 0.16D));

        // ① 加色光晕：先铺一层“过曝底”，后面的细线才像在发光 ✓
        glowWash(glow, m, r, alpha, grand);

        if (!grand) {
            simpleSeal(out, glow, m, c, r, alpha, spinOut, pulse);
            if (sky) {
                skyCurtain(out, m, r, alpha, age);
            }
            return;
        }

        // ② 日冕光芒：20 道，越长越亮（读条后的释放阵就是“光污染”本体）
        int rays = 20;
        for (int i = 0; i < rays; i++) {
            double a = spinOut * 0.7D + i * Math.PI * 2.0D / rays;
            Vec3 dir = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
            double inner = r * 1.02D;
            double outer = r * (1.08D + 0.16D * (0.5D + 0.5D * Math.sin(age * 0.10D + i)));
            Vec3 i0 = c.add(dir.scale(inner)).subtract(side.scale(r * 0.008D));
            Vec3 i1 = c.add(dir.scale(inner)).add(side.scale(r * 0.008D));
            Vec3 tip = c.add(dir.scale(outer)).add(0.0D, 0.0D, 0.0D);
            WaterGeometry.quad(out, m, i0, i1, tip, tip,
                    G_R, G_G, G_B, alpha * 0.22F);
        }

        // ③ 主环三层 ＋ 双层晕环
        WaterGeometry.ringColor(out, m, c, UP, r * 1.16D, r * 0.014D,
                W_R, W_G, W_B, alpha * 0.10F, 80);
        WaterGeometry.ringColor(out, m, c, UP, r * 1.03D, r * 0.007D,
                G_R, G_G, G_B, alpha * 0.40F * pulse, 80);
        WaterGeometry.ringColor(out, m, c, UP, r, r * 0.020D,
                W_R, W_G, W_B, alpha * 0.92F, 96);
        WaterGeometry.ringColor(out, m, c, UP, r * 0.962D, r * 0.006D,
                W_R, W_G, W_B, alpha * 0.55F, 80);
        WaterGeometry.ringColor(out, m, c, UP, r * 0.895D, r * 0.004D,
                G_R, G_G, G_B, alpha * 0.38F, 72);

        // ④ 四向饰纹（十字 ＋ 菱形），把“方向感”钉在正东南西北
        for (int i = 0; i < 4; i++) {
            double a = spinOut + i * Math.PI * 0.5D;
            filigree(out, m, c, a, r, alpha);
        }

        // ⑤ 24 道外圈符文
        for (int i = 0; i < 24; i++) {
            double a = spinOut * 0.35D + i * Math.PI / 12.0D;
            rune(out, m, c, a, r * 0.885D, r * 0.046D, r * 0.0045D,
                    G_R, G_G, G_B, alpha * (i % 2 == 0 ? 0.62F : 0.45F));
        }

        // ⑥ 8 颗内圈小阵
        for (int i = 0; i < 8; i++) {
            double a = spinIn + i * Math.PI * 0.25D;
            Vec3 at = c.add(Math.cos(a) * r * 0.70D, 0.0D, Math.sin(a) * r * 0.70D);
            double size = r * 0.058D * pulse;
            miniSeal(out, m, at, UP, size, spinOut + i, alpha * 0.82F);
        }

        // ⑦ 8 根辐条（从小阵指向内圈）
        for (int i = 0; i < 8; i++) {
            double a = spinOut * 0.5D + i * Math.PI * 0.25D;
            Vec3 from = c.add(Math.cos(a) * r * 0.28D, 0.0D, Math.sin(a) * r * 0.28D);
            Vec3 to = c.add(Math.cos(a) * r * 0.63D, 0.0D, Math.sin(a) * r * 0.63D);
            WaterGeometry.tube(out, m, from, to, Math.max(0.006D, r * 0.0035D),
                    W_R, W_G, W_B, alpha * 0.30F, 4);
        }

        // ⑧ 八角星：两枚反向旋转的方框
        square(out, m, c, r * 0.52D, spinOut * 0.9D, r * 0.0045D, alpha * 0.40F);
        square(out, m, c, r * 0.40D, spinIn * 1.2D, r * 0.0035D, alpha * 0.32F);

        // ⑨ 内圈刻度 ＋ 中心白核
        for (int i = 0; i < 12; i++) {
            double a = spinIn * 0.8D + i * Math.PI / 6.0D;
            Vec3 from = c.add(Math.cos(a) * r * 0.255D, 0.0D, Math.sin(a) * r * 0.255D);
            Vec3 to = c.add(Math.cos(a) * r * 0.305D, 0.0D, Math.sin(a) * r * 0.305D);
            WaterGeometry.tube(out, m, from, to, Math.max(0.005D, r * 0.003D),
                    W_R, W_G, W_B, alpha * 0.50F, 4);
        }
        WaterGeometry.ringColor(out, m, c, UP, r * 0.205D, r * 0.008D,
                W_R, W_G, W_B, alpha * 0.80F, 48);
        WaterGeometry.ringColor(out, m, c, UP, r * 0.120D, r * 0.010D,
                1.0F, 1.0F, 1.0F, alpha * 0.88F, 40);
        WaterGeometry.disk(glow, m, c.add(0.0D, 0.004D, 0.0D), UP, r * 0.085D,
                1.0F, 1.0F, 1.0F, alpha * 0.38F, 28);
        WaterGeometry.disk(glow, m, c.add(0.0D, 0.006D, 0.0D), UP, r * 0.038D,
                1.0F, 1.0F, 1.0F, alpha * 0.62F, 20);

        // ⑨b 高阶再套一圈外环 + 12 道向外刻痕：t4/t5 的阵要比 t3 “重”一圈
        if (veryGrand) {
            WaterGeometry.ringColor(out, m, c, UP, r * 1.10D, r * 0.006D,
                    G_R, G_G, G_B, alpha * 0.30F * pulse, 96);
            for (int i = 0; i < 12; i++) {
                double a = spinOut * 0.45D + i * Math.PI / 6.0D;
                Vec3 from = c.add(Math.cos(a) * r * 1.035D, 0.0D, Math.sin(a) * r * 1.035D);
                Vec3 to = c.add(Math.cos(a) * r * (1.085D + 0.02D * Math.sin(age * 0.08D + i)),
                        0.0D, Math.sin(a) * r * (1.085D + 0.02D * Math.sin(age * 0.08D + i)));
                WaterGeometry.tube(out, m, from, to, Math.max(0.006D, r * 0.004D),
                        W_R, W_G, W_B, alpha * 0.26F, 4);
            }
        }

        // ⑩ 光柱：只在主阵出现，前 ~1.5 秒最亮，随后淡出（避免 12 秒一直杵一根柱子）
        //    天上的阵不往上长柱子（会穿到云里），改成往下垂的光幕 ✓
        if (sky) {
            skyCurtain(out, m, r, alpha, age);
        } else {
            column(out, glow, m, r, alpha, age, spinOut);
        }
    }

    /**
     * 天上的阵：12 片向下的光幕，把“柱子要落下来”的方向感先给出来 ✓。
     *
     * <p>只挂在 {@link TNMagicCircleEntity#STYLE_LIGHT_SKY} 上；光柱本身由
     * {@code TNLightBeamEntity} 负责，所以这里只铺一层很淡的锥面，不抢戏 ✓。
     */
    private static void skyCurtain(VertexConsumer out, Matrix4f m,
                                   double r, float alpha, double age) {
        int n = 12;
        Vec3 c = new Vec3(0.0D, 0.05D, 0.0D);
        for (int i = 0; i < n; i++) {
            double a = age * 0.004D + i * Math.PI * 2.0D / n;
            Vec3 dir = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
            Vec3 topL = c.add(dir.scale(r * 0.90D)).add(side.scale(r * 0.030D));
            Vec3 topR = c.add(dir.scale(r * 0.90D)).subtract(side.scale(r * 0.030D));
            Vec3 botL = c.add(dir.scale(r * 0.55D)).add(side.scale(r * 0.055D)).add(0.0D, -2.4D, 0.0D);
            Vec3 botR = c.add(dir.scale(r * 0.55D)).subtract(side.scale(r * 0.055D)).add(0.0D, -2.4D, 0.0D);
            WaterGeometry.quad(out, m, topL, topR, botR, botL,
                    C_R, C_G, C_B, alpha * 0.10F);
        }
    }

    /** 主阵的加色底：4 层淡光圆盘 ＋ 3 圈宽晕环 ⇒ 地面像被一盏巨大的灯照亮 ✓。 */
    private static void glowWash(VertexConsumer glow, Matrix4f m, double r,
                                 float alpha, boolean grand) {
        if (grand) {
            for (int i = 0; i < 4; i++) {
                WaterGeometry.disk(glow, m, new Vec3(0.0D, 0.052D + i * 0.002D, 0.0D), UP,
                        r * (1.10D - i * 0.20D), 1.0F, 0.96F, 0.82F,
                        alpha * (0.030F + i * 0.006F), 48);
            }
            WaterGeometry.ringColor(glow, m, new Vec3(0.0D, 0.06D, 0.0D), UP,
                    r * 1.02D, r * 0.055D, 1.0F, 0.95F, 0.80F, alpha * 0.075F, 72);
            WaterGeometry.ringColor(glow, m, new Vec3(0.0D, 0.06D, 0.0D), UP,
                    r * 0.86D, r * 0.035D, G_R, G_G, G_B, alpha * 0.055F, 64);
        } else {
            WaterGeometry.disk(glow, m, new Vec3(0.0D, 0.05D, 0.0D), UP, r * 0.95D,
                    1.0F, 0.96F, 0.82F, alpha * 0.055F, 24);
            WaterGeometry.ringColor(glow, m, new Vec3(0.0D, 0.06D, 0.0D), UP,
                    r * 1.04D, r * 0.06D, G_R, G_G, G_B, alpha * 0.10F, 32);
        }
    }

    /** 小阵（半径 < 4 的卫星阵 / 召唤落点阵）：两环 ＋ 六刻痕 ＋ 亮核，便宜但够清楚 ✓。 */
    private static void simpleSeal(VertexConsumer out, VertexConsumer glow, Matrix4f m, Vec3 c,
                                   double r, float alpha, double spin, float pulse) {
        WaterGeometry.ringColor(out, m, c, UP, r, r * 0.040D, W_R, W_G, W_B, alpha * 0.90F, 40);
        WaterGeometry.ringColor(out, m, c, UP, r * 0.72D, r * 0.030D,
                G_R, G_G, G_B, alpha * 0.55F, 32);
        WaterGeometry.ringColor(glow, m, c, UP, r * 1.05D, r * 0.075D,
                1.0F, 0.95F, 0.80F, alpha * 0.10F, 32);
        for (int i = 0; i < 6; i++) {
            double a = spin + i * Math.PI / 3.0D;
            Vec3 from = c.add(Math.cos(a) * r * 0.28D, 0.0D, Math.sin(a) * r * 0.28D);
            Vec3 to = c.add(Math.cos(a) * r * 0.94D, 0.0D, Math.sin(a) * r * 0.94D);
            WaterGeometry.tube(out, m, from, to, Math.max(0.005D, r * 0.025D),
                    W_R, W_G, W_B, alpha * 0.55F, 4);
        }
        WaterGeometry.disk(glow, m, c.add(0.0D, 0.004D, 0.0D), UP, r * 0.20D * pulse,
                1.0F, 1.0F, 1.0F, alpha * 0.45F, 16);
    }

    /** 四向饰纹：一段伸出去的短臂 ＋ 末端菱形 ✓。 */
    private static void filigree(VertexConsumer out, Matrix4f m, Vec3 c, double angle,
                                 double r, float alpha) {
        Vec3 dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        Vec3 from = c.add(dir.scale(r * 1.00D));
        Vec3 to = c.add(dir.scale(r * 1.12D));
        WaterGeometry.tube(out, m, from, to, Math.max(0.006D, r * 0.006D),
                W_R, W_G, W_B, alpha * 0.55F, 4);
        Vec3 tip = c.add(dir.scale(r * 1.155D));
        Vec3 l = c.add(dir.scale(r * 1.115D)).add(side.scale(r * 0.022D));
        Vec3 rr = c.add(dir.scale(r * 1.115D)).subtract(side.scale(r * 0.022D));
        WaterGeometry.quad(out, m, l, tip, rr, rr, G_R, G_G, G_B, alpha * 0.60F);
        WaterGeometry.tube(out, m, c.add(dir.scale(r * 1.02D)).subtract(side.scale(r * 0.030D)),
                c.add(dir.scale(r * 1.02D)).add(side.scale(r * 0.030D)),
                Math.max(0.005D, r * 0.004D), W_R, W_G, W_B, alpha * 0.45F, 4);
    }

    /** 一道符文：径向短划 ＋ 外端菱形（远看就是一圈“字”）✓。 */
    private static void rune(VertexConsumer out, Matrix4f m, Vec3 c, double angle,
                             double radius, double half, double width,
                             float r, float g, float b, float alpha) {
        Vec3 dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        WaterGeometry.tube(out, m, c.add(dir.scale(radius - half)),
                c.add(dir.scale(radius + half)), width, r, g, b, alpha, 4);
        Vec3 tip = c.add(dir.scale(radius + half * 1.9D));
        Vec3 l = c.add(dir.scale(radius + half * 1.1D)).add(side.scale(width * 3.2D));
        Vec3 rr = c.add(dir.scale(radius + half * 1.1D)).subtract(side.scale(width * 3.2D));
        WaterGeometry.quad(out, m, l, tip, rr, rr, r, g, b, alpha * 0.9F);
    }

    /** 一颗嵌在主阵里的小阵 ✓。 */
    private static void miniSeal(VertexConsumer out, Matrix4f m, Vec3 c, Vec3 normal,
                                 double radius, double spin, float alpha) {
        WaterGeometry.ringColor(out, m, c, normal, radius, radius * 0.085D,
                W_R, W_G, W_B, alpha, 24);
        WaterGeometry.ringColor(out, m, c, normal, radius * 0.62D, radius * 0.065D,
                G_R, G_G, G_B, alpha * 0.85F, 20);
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI * 0.5D;
            Vec3 dir = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            WaterGeometry.tube(out, m, c.add(dir.scale(radius * 0.12D)),
                    c.add(dir.scale(radius * 0.98D)), Math.max(0.005D, radius * 0.06D),
                    W_R, W_G, W_B, alpha * 0.82F, 4);
        }
        WaterGeometry.disk(out, m, c.add(normal.scale(0.004D)), normal, radius * 0.18D,
                1.0F, 1.0F, 1.0F, alpha * 0.75F, 12);
    }

    /** 水平方框（八角星的一环）✓。 */
    private static void square(VertexConsumer out, Matrix4f m, Vec3 c, double radius,
                               double spin, double width, float alpha) {
        for (int i = 0; i < 4; i++) {
            double a0 = spin + Math.PI * 0.25D + i * Math.PI * 0.5D;
            double a1 = a0 + Math.PI * 0.5D;
            Vec3 p0 = c.add(Math.cos(a0) * radius, 0.0D, Math.sin(a0) * radius);
            Vec3 p1 = c.add(Math.cos(a1) * radius, 0.0D, Math.sin(a1) * radius);
            WaterGeometry.tube(out, m, p0, p1, width, G_R, G_G, G_B, alpha, 4);
        }
    }

    /**
     * 光柱：三层同心柱 ＋ 3 道上升光环。
     *
     * <p>只在主阵、且只在 {@code age < 90} 时可见（最后 40 tick 淡出）✓ ——
     * 光耀的阵留 11 秒，柱不能也杵 11 秒，否则整片地都糊成白的 ✗。
     */
    private static void column(VertexConsumer out, VertexConsumer glow, Matrix4f m,
                               double r, float alpha, double age, double spin) {
        float colA = alpha * (float) Math.max(0.0D, Math.min(1.0D, (90.0D - age) / 40.0D));
        if (colA <= 0.01F) {
            return;
        }
        double height = Math.min(7.0D, 1.6D + r * 0.30D);
        Vec3 from = new Vec3(0.0D, 0.08D, 0.0D);
        Vec3 to = new Vec3(0.0D, height, 0.0D);
        WaterGeometry.tube(glow, m, from, to, 0.30D, 1.0F, 0.95F, 0.80F, colA * 0.055F, 8);
        WaterGeometry.tube(out, m, from, to, 0.12D, G_R, G_G, G_B, colA * 0.10F, 8);
        WaterGeometry.tube(out, m, from, to, 0.045D, 1.0F, 1.0F, 0.98F, colA * 0.22F, 6);
        for (int i = 0; i < 3; i++) {
            double y = 0.4D + ((age * 0.055D + i * height / 3.0D) % height);
            float a = colA * (0.16F - 0.04F * i);
            WaterGeometry.ringColor(glow, m, new Vec3(0.0D, y, 0.0D), UP,
                    0.55D + 0.12D * i, 0.055D, C_R, C_G, C_B, a, 40);
            WaterGeometry.ringColor(out, m, new Vec3(0.0D, y, 0.0D), UP,
                    0.70D + 0.14D * i, 0.020D, W_R, W_G, W_B, a * 0.9F, 40);
        }
    }
}
