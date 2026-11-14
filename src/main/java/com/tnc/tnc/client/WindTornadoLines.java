package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.magic.TornadoLineShape;
import org.joml.Matrix4f;

import java.util.List;

/**
 * ★ <b>龙卷风 = 一束螺旋缠绕的折线</b>（2026-11-09 作者：「我们的闪电不是一个一个线吗，
 * 我想这个线螺旋缠绕起来变成龙卷风，你看过鬼灭之刃吗，就是那种感觉，可以有明确的锐角，
 * 龙卷风极具那种线条特色」）✓
 *
 * <h2>和旧的"模型龙卷风"差在哪</h2>
 * 旧版是烘焙模型（7 层 × 4 片薄板，贴图色块）⇒ 作者看到的是"几块板在转" ✗。
 * 现在<b>一个模型都不画</b>：只画 {@link TornadoLineShape} 算出来的一束折线 ✓
 * <ul>
 *   <li>折点少（每根 15 段）⇒ 每一节都是<b>明确的锐角</b>，不是光滑曲面 ✓</li>
 *   <li>半径锯齿 + 偶发刃尖 ⇒ 轮廓上戳出尖刺，正是"线条特色" ✓</li>
 *   <li>三层加色光带（外辉光 / 青绿主色 / 白芯）⇒ 和雷电同一种"亮线"读法 ✓</li>
 *   <li>另外 6 道 V 形风切 ⇒ 像刃掠过，而不是一圈规整弹簧 ✓</li>
 * </ul>
 *
 * <h2>为什么用 {@code LightningBoltGeometry.polyline}</h2>
 * 那条光带是<b>朝向相机</b>的（侧向量 = 切线 × 视线）⇒ 从任何角度看都是"有厚度的线" ✓，
 * 而且三层宽度比和闪电完全一致 ⇒ 风和雷看起来是同一个美术体系 ✓。
 *
 * <p>⚠️ 本类<b>不自己计时、不自己转</b>：自转角全部来自
 * {@code TNTornadoEntity.visualSpin(partialTick)}（服务端同步、渲染器帧间插值）✓
 * —— 客户端自己算会和同步值错位 ✗。
 */
public final class WindTornadoLines {

    /**
     * 白芯半宽的<b>基准比例</b> ✓ —— 线宽跟着龙卷风本身的大小走（高度 × 本值）。
     *
     * <p>为什么不写死"多少格"✗：t3 的龙卷风只有 4 格高、t5 有 15 格高，
     * 固定线宽会让 t3 变成一团糊、t5 细得像蛛丝 ✗。按比例走 ⇒ 三档看起来是同一种画风 ✓。
     *
     * <p>2026-11-09 作者：「线条变粗一些」⇒ 0.018 → <b>0.028</b>（粗了约 55%）✓；
     * 同时改成两层（里一层外一层）⇒ 细线会被里层挡住，粗一点才压得住 ✓。
     */
    public static final double CORE_WIDTH_RATIO = 0.028D;

    /** 白芯色 ✓。 */
    public static final float[] CORE = {1.0F, 1.0F, 1.0F};
    /** 主色：偏青的风 ✓（和风系 UI 的 #6BE7B0 同一支）。 */
    public static final float[] MID = {0.62F, 1.0F, 0.86F};
    /** 外辉光：深一点的青绿 ✓。 */
    public static final float[] GLOW = {0.10F, 0.72F, 0.52F};

    private WindTornadoLines() {
    }

    /**
     * 画出整团龙卷风 ✓。
     *
     * @param vc      顶点消费者（加色光带那一种）
     * @param m       当前 pose 的矩阵（调用方已经 translate 到实体脚下中轴）
     * @param spinRad 自转角（弧度）
     * @param alpha   总不透明度（出生/消散时淡入淡出）
     * @param bottom  底半径（格）
     * @param top     顶半径（格）
     * @param height  高度（格，已乘档位）
     * @param camX/camY/camZ 相机相对实体的偏移（光带朝向相机要用）
     * @param seed    锯齿种子（同一发龙卷风整段生命都用同一个 ⇒ 不闪）
     * @return 画了多少个顶点（自检/预算用 ✓）
     */
    public static int draw(VertexConsumer vc, Matrix4f m,
                           double spinRad, float alpha,
                           double bottom, double top, double height,
                           double camX, double camY, double camZ, long seed) {
        if (alpha <= 0.004F) {
            return 0;
        }
        // 线宽跟着龙卷风本身的大小走 ✓（height 是局部高度，pose 上还会乘 scale ⇒
        // 世界线宽 = height × scale × 比例，三档看起来是同一种画风 ✓）
        float width = (float) (height * CORE_WIDTH_RATIO);
        int vertices = 0;

        // ★ 两层：里一层外一层（作者 2026-11-09）✓
        //   外层先画（半径 1.0、7 根），内层套在里面（半径 0.56、5 根、错开 72°）——
        //   加色混合下里层不会被外层"盖住"✗，而是**透出来** ⇒ 风壁有厚度感 ✓。
        for (TornadoLineShape.Shell shell : TornadoLineShape.SHELLS) {
            List<double[][]> strands = TornadoLineShape.strands(
                    spinRad, bottom, top, height, seed, shell);
            // 这一层的芯色：外层纯白，内层偏青绿 ⇒ 加色混合下能分出"里一层外一层" ✓
            float[] core = mix(MID, CORE, shell.whiteness());
            for (int i = 0; i < strands.size(); i++) {
                boolean bold = (i % 3 == 0);      // 每三根里有一根更粗更亮 ⇒ 主次分明 ✓
                float w = (bold ? width : width * 0.78F) * shell.widthScale();
                float a = (bold ? alpha : alpha * 0.80F) * shell.alphaScale();
                vertices += count(strands.get(i), w);
                LightningBoltGeometry.polyline(vc, m, strands.get(i), w, a, core, MID, GLOW,
                        camX, camY, camZ);
            }
        }

        for (double[][] slash : TornadoLineShape.slashes(spinRad, bottom, top, height, seed)) {
            float w = width * 0.85F;
            vertices += count(slash, w);
            LightningBoltGeometry.polyline(vc, m, slash, w, alpha * 0.90F, CORE, MID, GLOW,
                    camX, camY, camZ);
        }
        return vertices;
    }

    /**
     * 预估一条折线要发多少顶点 ✓（每段 8 个顶点 × 3 层）。
     *
     * <p>只给预算/自检用，不参与渲染 ✓。
     */
    public static int count(double[][] pts, float width) {
        if (pts == null || pts.length < 2 || width <= 0.0F) {
            return 0;
        }
        return (pts.length - 1) * 8 * 3;
    }

    /** 两色之间按 t 插值 ✓（t = 0 取 a，t = 1 取 b）。 */
    static float[] mix(float[] a, float[] b, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        return new float[]{
                a[0] + (b[0] - a[0]) * k,
                a[1] + (b[1] - a[1]) * k,
                a[2] + (b[2] - a[2]) * k};
    }
}
