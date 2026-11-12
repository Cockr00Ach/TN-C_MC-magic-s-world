package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * ★ <b>黑线勾出来的那只手</b> —— 全项目唯一的"手"画法（2026-11-05 作者：
 * 「那个手模型可以去掉，我比较想是就是我们的这个黑线简单勾勒一个手」，
 * 参考《超凡蜘蛛侠 2》钟楼：蛛丝飞到顶，<b>顶端展开五根线</b>，像手一样 ✓）。
 *
 * <h2>为什么抽成独立类</h2>
 * 这只手现在不止一个地方要用：
 * <ul>
 *   <li>{@link TNNightGripRenderer} —— 飞出去抓住目标的那只手；</li>
 *   <li>{@link DarkMistRenderer} —— 黑雾<b>中心</b>冒出来、把雾里的敌人抓紧的手（作者 2026-11-05：
 *       「黑雾加一点那个就是，中心是不是会冒出手，把敌人抓紧黑雾之中」）✓。</li>
 * </ul>
 * 两处必须长得一模一样，否则玩家会觉得是两种东西 ✗ ⇒ 只有这一份骨架数据 ✓。
 *
 * <h2>局部坐标</h2>
 * 手腕在原点、手指朝 <b>+Y</b>、掌心朝 <b>+Z</b>、单位是"格 × 缩放前"。
 * 调用方自己 <b>push / translate / rotate / scale</b>，然后调 {@link #hand} 即可 ✓。
 *
 * <h2>clench（0..1）</h2>
 * 0 = 五指张开（伸出去的过程）；1 = 握紧（抓住）。中间值让手指"慢慢收拢" ✓ ——
 * 张开时指尖在 {@link #TIP_X}/{@link #TIP_Y}，握紧时收向掌心、往前扣
 * （{@link #CLENCH_TIP_X} 等），插值出来就是一个抓握动作 ✓。
 */
public final class DarkHandLines {

    private DarkHandLines() {
    }

    /** 指根弧（掌心上缘）的 x / y。 */
    private static final double[] PALM_X = {-0.33D, -0.16D, 0.0D, 0.16D, 0.33D};
    private static final double[] PALM_Y = {0.62D, 0.70D, 0.73D, 0.70D, 0.62D};

    /** 五指（张开时的）指根 / 中节 / 指尖。 */
    private static final double[] BASE_X = {-0.30D, -0.15D, 0.0D, 0.15D, 0.30D};
    private static final double[] JOINT_X = {-0.40D, -0.19D, 0.0D, 0.19D, 0.40D};
    private static final double[] JOINT_Y = {0.98D, 1.08D, 1.12D, 1.08D, 0.98D};
    private static final double[] TIP_X = {-0.47D, -0.23D, 0.01D, 0.23D, 0.47D};
    private static final double[] TIP_Y = {1.30D, 1.46D, 1.52D, 1.46D, 1.30D};

    /** 握紧时的中节 / 指尖（往掌心收、往 +Z 扣）。 */
    private static final double[] CLENCH_JOINT_X = {-0.26D, -0.13D, 0.0D, 0.13D, 0.26D};
    private static final double[] CLENCH_JOINT_Y = {0.84D, 0.90D, 0.93D, 0.90D, 0.84D};
    private static final double[] CLENCH_TIP_X = {-0.14D, -0.07D, 0.0D, 0.07D, 0.14D};
    private static final double[] CLENCH_TIP_Y = {0.74D, 0.78D, 0.80D, 0.78D, 0.74D};

    /** 拇指张开 / 握紧。 */
    private static final double[] THUMB_OPEN = {-0.33D, 0.46D, -0.52D, 0.66D, -0.60D, 0.90D};
    private static final double[] THUMB_CLENCH = {-0.30D, 0.46D, -0.36D, 0.56D, -0.30D, 0.66D};

    /** 第一根手指开始长的比例（0..1 的 grow，不是 tick）。 */
    private static final double FINGER_START = 0.30D;
    /** 相邻两根手指错开多少 grow。 */
    private static final double FINGER_STAGGER = 0.11D;
    /** 一根手指长完占多少 grow。 */
    private static final double FINGER_GROW = 0.55D;

    /**
     * 画一只线手。
     *
     * @param grow   0..1，整只手"长到哪了"（五指还会各自再错开一点 ✓）
     * @param alpha  整体透明度
     * @param clench 0 = 张开、1 = 握紧
     */
    public static void hand(VertexConsumer vc, Matrix4f m,
                            double camX, double camY, double camZ,
                            float grow, float alpha, float clench, long seed) {
        if (alpha <= 0.01F || grow <= 0.01F) {
            return;
        }
        float c = Mth.clamp(clench, 0.0F, 1.0F);
        double palmGrow = Math.min(1.0D, grow / 0.35D);

        // ① 指根弧（掌心下缘）：五段，先连成掌
        for (int i = 0; i < 4; i++) {
            LightningBoltGeometry.darkArcDeep(vc, m, PALM_X[i], PALM_Y[i], 0.02D,
                    PALM_X[i + 1], PALM_Y[i + 1], 0.02D,
                    seed + 11L + i, 0.042F, alpha, palmGrow, camX, camY, camZ);
        }
        // ② 两侧掌缘 → 腕
        LightningBoltGeometry.darkArcDeep(vc, m, -0.33D, 0.62D, 0.02D, -0.12D, 0.10D, 0.0D,
                seed + 21L, 0.042F, alpha, palmGrow, camX, camY, camZ);
        LightningBoltGeometry.darkArcDeep(vc, m, 0.33D, 0.62D, 0.02D, 0.12D, 0.10D, 0.0D,
                seed + 22L, 0.042F, alpha, palmGrow, camX, camY, camZ);
        // ③ 腕口
        LightningBoltGeometry.darkArcDeep(vc, m, -0.12D, 0.10D, 0.0D, 0.12D, 0.10D, 0.0D,
                seed + 23L, 0.038F, alpha, palmGrow, camX, camY, camZ);

        // ④ 五指：每根两节，错开长 ⇒ "展开五根线" ✓；clench 让它们收拢 ✓
        for (int f = 0; f < 5; f++) {
            double g = Mth.clamp((grow - FINGER_START - f * FINGER_STAGGER) / FINGER_GROW, 0.0D, 1.0D);
            if (g <= 0.01D) {
                continue;
            }
            double bx = BASE_X[f];
            double by = PALM_Y[f];
            double jx = lerp(JOINT_X[f], CLENCH_JOINT_X[f], c);
            double jy = lerp(JOINT_Y[f], CLENCH_JOINT_Y[f], c);
            double jz = lerp(0.03D, 0.18D, c);
            double tx = lerp(TIP_X[f], CLENCH_TIP_X[f], c);
            double ty = lerp(TIP_Y[f], CLENCH_TIP_Y[f], c);
            double tz = lerp(0.04D, 0.30D, c);
            double g1 = Math.min(1.0D, g / 0.6D);
            double g2 = Math.max(0.0D, (g - 0.6D) / 0.4D);
            LightningBoltGeometry.darkArcDeep(vc, m, bx, by, 0.02D, jx, jy, jz,
                    seed + 100L + f, 0.045F, alpha, g1, camX, camY, camZ);
            LightningBoltGeometry.darkArcDeep(vc, m, jx, jy, jz, tx, ty, tz,
                    seed + 200L + f, 0.038F, alpha, g2, camX, camY, camZ);
            if (g >= 0.99D) {
                // 指尖一颗小血珠 ⇒ 五根线顶端各一点，就是"手"的指尖 ✓
                LightningBoltGeometry.darkBeadDeep(vc, m, tx, ty, tz, 0.062F, alpha, camX, camY, camZ);
            }
        }

        // ⑤ 拇指：从掌侧斜着伸出去
        double gt = Mth.clamp((grow - FINGER_START - 1.2D * FINGER_STAGGER) / FINGER_GROW, 0.0D, 1.0D);
        if (gt > 0.01D) {
            double gt1 = Math.min(1.0D, gt / 0.6D);
            double gt2 = Math.max(0.0D, (gt - 0.6D) / 0.4D);
            double ax = lerp(THUMB_OPEN[0], THUMB_CLENCH[0], c);
            double ay = lerp(THUMB_OPEN[1], THUMB_CLENCH[1], c);
            double bx = lerp(THUMB_OPEN[2], THUMB_CLENCH[2], c);
            double by = lerp(THUMB_OPEN[3], THUMB_CLENCH[3], c);
            double tx = lerp(THUMB_OPEN[4], THUMB_CLENCH[4], c);
            double ty = lerp(THUMB_OPEN[5], THUMB_CLENCH[5], c);
            LightningBoltGeometry.darkArcDeep(vc, m, ax, ay, 0.0D, bx, by, lerp(0.02D, 0.14D, c),
                    seed + 301L, 0.045F, alpha, gt1, camX, camY, camZ);
            LightningBoltGeometry.darkArcDeep(vc, m, bx, by, lerp(0.02D, 0.14D, c),
                    tx, ty, lerp(0.03D, 0.24D, c),
                    seed + 302L, 0.038F, alpha, gt2, camX, camY, camZ);
        }
    }

    /**
     * 便利版：自己拿位姿、自己开 {@code DARK} 层、自己算相机量。
     *
     * <p>相机量用<b>位姿矩阵求逆</b>拿（{@code inv · (0,0,0)}）——不管调用方转了多少度、
     * 缩了多少倍，线都正对相机 ✓（这是所有暗系线的统一做法）。
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers,
                            float grow, float alpha, float clench, long seed) {
        draw(pose, buffers, LightningRenderTypes.DARK, grow, alpha, clench, seed);
    }

    /** 同上，但可以指定渲染层（黑雾里用 {@code DARK_OVERLAY}：不测深度，才看得见 ✓）。 */
    public static void draw(PoseStack pose, MultiBufferSource buffers,
                            net.minecraft.client.renderer.RenderType layer,
                            float grow, float alpha, float clench, long seed) {
        Matrix4f m = pose.last().pose();
        Matrix4f inv = new Matrix4f(m).invert();
        Vector3f camLocal = inv.transformPosition(new Vector3f());
        VertexConsumer vc = buffers.getBuffer(layer);
        hand(vc, m, camLocal.x, camLocal.y, camLocal.z, grow, alpha, clench, seed);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
