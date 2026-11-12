package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * ★ <b>风刃 = 战士那种「剑气」</b>（2026-11-09 作者：「这个风刃也改一改，其实可以改成战士那里的
 * 剑气你觉得如何，体积统一一下」）✓
 *
 * <h2>为什么值得改</h2>
 * 以前风刃是一张<b>烘焙月牙模型</b>（{@code tnc:projectile/windblade}）贴半透明图，
 * 尺寸按 5 / 10 / 20 倍放大 ⇒ t5 的刃跨度 <b>23.6 格</b>，比龙卷风本身还宽 ✗，
 * 而且和战士的剑气（{@code WarriorWaveRenderer} 那种"四层渐隐的月牙光带"）完全不像一个体系 ✗。
 *
 * <p>改完以后：和战士剑气<b>同一种读法</b> —— 中间厚、两端收成尖的弯月，
 * 由外到内叠四层（外辉光 → 主色 → 白芯 → 极细高光），后面再拖两道残影，
 * 外缘加一圈"刃口"侧面（正对着看不出厚度时靠它撑住体积 ✓）。
 *
 * <h2>坐标系</h2>
 * 月牙躺在 <b>局部 XY 平面</b>、法线是 <b>+Z</b>（{@link WindBladeVisualPose} 保证
 * +Z 始终对着飞行方向 ✓）。所以这里只算 XY 上的顶点，z 只用来做残影和刃口厚度 ✓。
 *
 * <p>颜色统一用龙卷风那套（白芯 / 青绿主色 / 深青辉光）⇒ 风和雷、风和战士都是同一个美术体系 ✓。
 */
public final class WindBladeQi {

    /** 白芯色 ✓（和龙卷风同一支）。 */
    public static final float[] CORE = {1.0F, 1.0F, 1.0F};
    /** 主色：偏青的风 ✓。 */
    public static final float[] MID = {0.62F, 1.0F, 0.86F};
    /** 外辉光：深一点的青绿 ✓。 */
    public static final float[] GLOW = {0.10F, 0.72F, 0.52F};

    /** 弯月分成多少段 ✓（段越多越圆滑，40 段在 3 格宽上看不出棱角 ✓）。 */
    public static final int SEGMENTS = 40;

    /** 中间往回收多少（相对半宽）✓ —— 这就是"弯月"的弧度。 */
    public static final double BOW = 0.34D;

    /** 四层厚度（相对半宽）✓ —— 从外到内：辉光 / 主色 / 白芯 / 高光。 */
    private static final double[] WIDTHS = {0.34D, 0.22D, 0.11D, 0.035D};
    /** 四层颜色 ✓（最后一层是纯白细线）。 */
    private static final float[][] COLORS = {GLOW, MID, MID, CORE};
    /** 四层不透明度系数 ✓。 */
    private static final float[] ALPHAS = {0.16F, 0.45F, 0.85F, 0.95F};

    private static final float[] WHITE = {0.98F, 1.0F, 1.0F};

    private WindBladeQi() {
    }

    /**
     * 画一片风刃 ✓。
     *
     * @param vc        顶点消费者（加色）
     * @param m         当前 pose 矩阵（调用方已经做好 yaw/pitch/自转）
     * @param halfWidth 月牙半宽（格）✓
     * @param alpha     总不透明度（出生/消散淡入淡出）
     * @return 发了多少顶点（预算/自检用 ✓）
     */
    public static int draw(VertexConsumer vc, Matrix4f m, double halfWidth, float alpha) {
        if (halfWidth <= 1.0E-3D || alpha <= 0.004F) {
            return 0;
        }
        int vertices = 0;
        double span = halfWidth;

        // ① 两道残影（往后拖，读起来像"斩"出来的轨迹）✓ —— 先画，亮的压在后面
        for (int echo = 2; echo >= 1; echo--) {
            double z = -echo * span * 0.10D;          // 贴得近一点，免得看出"两张月牙"✗
            float a = alpha * (echo == 1 ? 0.26F : 0.12F);
            vertices += band(vc, m, span, WIDTHS[0] * 1.15D, z, GLOW, a);
            vertices += band(vc, m, span, WIDTHS[2], z, MID, a * 0.9F);
        }

        // ② 主体四层 ✓
        for (int layer = 0; layer < WIDTHS.length; layer++) {
            vertices += band(vc, m, span, WIDTHS[layer], 0.0D,
                    COLORS[layer], ALPHAS[layer] * alpha);
        }

        // ③ 刃口：外缘沿 Z 撑一道薄薄的侧面 ⇒ 正对着看（几乎没厚度）时也有体积 ✓
        vertices += rim(vc, m, span, span * 0.055D, alpha * 0.70F);

        return vertices;
    }

    /** 一层弯月（内缘 = 外缘沿 +Y 偏移 thickness）✓。 */
    private static int band(VertexConsumer vc, Matrix4f m, double span, double thickness,
                            double z, float[] color, float alpha) {
        if (alpha <= 0.004F) {
            return 0;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            double a = -1.0D + i * 2.0D / SEGMENTS;
            double b = -1.0D + (i + 1) * 2.0D / SEGMENTS;
            double[] outerA = outer(span, a, z);
            double[] outerB = outer(span, b, z);
            double[] innerA = inner(span, a, thickness, z);
            double[] innerB = inner(span, b, thickness, z);
            float fa = alpha * taper(a);
            float fb = alpha * taper(b);
            vertex(vc, m, outerA, color, fa);
            vertex(vc, m, outerB, color, fb);
            vertex(vc, m, innerB, color, fb);
            vertex(vc, m, innerA, color, fa);
        }
        return SEGMENTS * 4;
    }

    /** 外缘那道侧面（沿 Z 的薄带）✓。 */
    private static int rim(VertexConsumer vc, Matrix4f m, double span, double depth, float alpha) {
        if (alpha <= 0.004F) {
            return 0;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            double a = -1.0D + i * 2.0D / SEGMENTS;
            double b = -1.0D + (i + 1) * 2.0D / SEGMENTS;
            double[] frontA = outer(span, a, depth);
            double[] frontB = outer(span, b, depth);
            double[] backA = outer(span, a, -depth);
            double[] backB = outer(span, b, -depth);
            float fa = alpha * taper(a);
            float fb = alpha * taper(b);
            vertex(vc, m, frontA, WHITE, fa);
            vertex(vc, m, frontB, WHITE, fb);
            vertex(vc, m, backB, WHITE, fb);
            vertex(vc, m, backA, WHITE, fa);
        }
        return SEGMENTS * 4;
    }

    /** 外缘上 t∈[-1,1] 的点 ✓（中间往 −Y 收 = 弯月）。 */
    static double[] outer(double span, double t, double z) {
        double x = t * span;
        double y = -BOW * span * (1.0D - t * t);
        return new double[]{x, y, z};
    }

    /** 内缘 = 外缘往上抬 thickness ✓（两端抬 0 ⇒ 收成尖角 ✓）。 */
    static double[] inner(double span, double t, double thickness, double z) {
        double[] o = outer(span, t, z);
        o[1] += thickness * span * taperRaw(t);
        return o;
    }

    /** 两端的衰减（尖端渐隐）✓。 */
    private static float taper(double t) {
        return (float) taperRaw(t);
    }

    private static double taperRaw(double t) {
        double k = Math.max(0.0D, 1.0D - t * t);
        return Math.pow(k, 0.35D);
    }

    /**
     * 发一个顶点 ✓ —— ★ 必须把<b>整个顶点格式</b>填满，不能只发 position + color ✗。
     *
     * <p>2026-11-10 03:00 作者报的崩溃就是这个：
     * <pre>{@code
     * java.lang.IllegalStateException: Not filled all elements of the vertex
     *   at WindBladeQi.vertex / band / draw
     *   at TNWindBladeRenderer.render
     * }</pre>
     * {@link LightningRenderTypes#BOLT} 的顶点格式是
     * <b>position + color + uv + overlay + uv2 + normal</b>（和雷电光带同一套），
     * 只发 position/color 时 {@code BufferBuilder.endVertex()} 会发现少了元素 ⇒ 当场抛 ✗。
     * 所以这里和 {@code LightningBoltGeometry.vertex} 完全对齐：uv 取贴图正中间的白点、
     * overlay 用 NO_OVERLAY、uv2 用 FULL_BRIGHT（加色光带不吃光照）、normal 给个朝上的占位 ✓。
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, double[] p, float[] color, float alpha) {
        vc.vertex(m, (float) p[0], (float) p[1], (float) p[2])
                .color(color[0], color[1], color[2], Math.max(0.0F, Math.min(1.0F, alpha)))
                .uv(0.5F, 0.5F)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** 预算：每一层 SEGMENTS 段 × 4 顶点 ✓。 */
    public static int vertices() {
        int echoes = 2 * 2;          // 两道残影 × 两层
        int layers = WIDTHS.length;  // 主体四层
        int rim = 1;                 // 刃口
        return (echoes + layers + rim) * SEGMENTS * 4;
    }
}
