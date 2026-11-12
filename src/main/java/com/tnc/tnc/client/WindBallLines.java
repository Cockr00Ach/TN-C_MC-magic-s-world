package com.tnc.tnc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ★ <b>风球的漩涡线</b>（2026-11-10 作者：「既然是在原来改动上弄，那就没必要还是那样组成一个球，
 * 你可以给我漩涡的那种感觉。并且线条数可以变为现在的三倍，不过我不要一样的线条，
 * 你可以多做一些渐变线条」）✓
 *
 * <h2>为什么不再"组成一个球"</h2>
 * 上一版把风线绕成一个球壳 ✗ —— 可风球身上**原本就有球的色带模型**（{@code WindConstructModels.orb}），
 * 线条再围一个球就是重复画同一件事 ✗。
 * 作者要的是<b>外面裹一层漩涡</b>：线从上往下收、越往下越窄，读起来像"风在球外面打旋"✓。
 *
 * <h2>漩涡的构造</h2>
 * 每条线是一段<b>螺旋漏斗</b>：
 * <ul>
 *   <li>高度 {@code y = R·1.65·(u − 0.5)}（以球心为原点，上下各一半）✓</li>
 *   <li>半径 {@code r = R·(窄 + (宽 − 窄)·u)} —— 下窄上宽，就是漩涡的"口" ✓</li>
 *   <li>角度 {@code θ = spin + phase + drift·u + u·turns·2π} —— 绕轴打旋 ✓</li>
 * </ul>
 *
 * <h2>三倍数量 + 渐变</h2>
 * 条数是上一层（球壳）的三倍：{@code 3 × (4 + tier)} ⇒ t1 <b>15</b> 条、t5 <b>27</b> 条 ✓。
 * 但**不是一样的线** ✗：每条线自己有一组渐变参数
 * （线宽 0.45~1.70 倍、亮度 0.30~1.00 倍、三档颜色 深青→青绿→白）⇒
 * 近处是亮白细丝、远处是淡青宽扫，叠在一起才有"风的层次" ✓。
 *
 * <p>预算：1/3 是"主线"（16 段）、2/3 是"淡线"（8 段）⇒ t5 一共 288 段 ≈ 6912 顶点 ✓。
 */
public final class WindBallLines {

    /** 主线分段数 ✓（折点少 ⇒ 看得出锐角，是"风"不是"管"）。 */
    public static final int SEGMENTS = 24;
    /** 淡线分段数 ✓（更省，也更飘）。 */
    public static final int FINE_SEGMENTS = 10;

    /** 漩涡的口径／高度（相对球半径）✓。 */
    public static final double VORTEX_WIDE = 1.05D;     // 顶部半径 ＝ R × 1.05
    public static final double VORTEX_NARROW = 0.28D;   // 底部半径 ＝ R × 0.28
    public static final double VORTEX_HEIGHT = 2.40D;   // 总高 ＝ R × 2.40（漩涡是"竖长"的 ✓）

    /** 冲击波几道环 ✓。 */
    public static final int SHOCK_RINGS = 3;
    /** 径向风痕几条 ✓。 */
    public static final int STREAKS = 10;

    /** 一条风线（带自己的渐变参数）✓。 */
    public record Line(double[][] points, float widthScale, float alphaScale, int palette) {
    }

    /** 三档配色：0 ＝ 深青（外扫）, 1 ＝ 青绿（中间层）, 2 ＝ 白（亮丝）✓。 */
    public static final int PALETTE_GLOW = 0;
    public static final int PALETTE_MID = 1;
    public static final int PALETTE_CORE = 2;

    private WindBallLines() {
    }

    /** 这一档几根线 ✓（上一版的三倍：t1＝15 …… t5＝27）。 */
    public static int count(int tier) {
        return 3 * (4 + Math.max(1, Math.min(5, tier)));
    }

    /** 主线（更粗更亮）几条 ✓ —— 总数的 1/3。 */
    public static int boldCount(int tier) {
        return Math.max(1, count(tier) / 3);
    }

    /** 一根线绕几圈 ✓（档越高越密）。 */
    public static double turns(int tier) {
        return 1.75D + 0.16D * Math.max(1, Math.min(5, tier));
    }

    /** 线宽（格）✓ —— 跟半径成比例，三档看起来是同一种线 ✓。 */
    public static double width(double radius) {
        return Math.max(0.012D, radius * 0.018D);
    }

    /**
     * 漩涡线 ✓。
     *
     * @param radius 风球半径（格）
     * @param tier   档位
     * @param spin   自转相位（弧度）
     * @param seed   种子（同一个球整段生命用同一个 ⇒ 不闪）
     */
    public static List<Line> vortex(double radius, int tier, double spin, long seed) {
        int total = count(tier);
        int bold = boldCount(tier);
        double baseTurns = turns(tier);
        List<Line> out = new ArrayList<>(total);
        Random rnd = new Random(seed * 0x2545F4914F6CDD1DL + tier);
        for (int i = 0; i < total; i++) {
            boolean isBold = i < bold;
            int segments = isBold ? SEGMENTS : FINE_SEGMENTS;
            double phase = i * (Math.PI * 2.0D / total) + (rnd.nextDouble() - 0.5D) * 0.45D;
            // 每根线圈数略有不同 ⇒ 相邻的线不会一直平行（不然像梳子 ✗）
            double turns = baseTurns * (0.82D + rnd.nextDouble() * 0.5D) * (isBold ? 1.0D : 1.12D);
            double drift = (rnd.nextDouble() - 0.5D) * 0.35D;
            double wide = VORTEX_WIDE * (0.92D + rnd.nextDouble() * 0.20D);
            double narrow = VORTEX_NARROW * (0.85D + rnd.nextDouble() * 0.35D);
            double wobble = 0.03D + rnd.nextDouble() * 0.07D;
            double[][] points = new double[segments + 1][3];
            // 主线铺满整个高度；淡线只占其中一段（随机的短"风丝"）⇒
            // ① 淡线本身短，折线不会被拉成大斜线 ✗；② 长短不齐才有层次 ✓
            double from = isBold ? 0.0D : rnd.nextDouble() * 0.55D;
            double to = isBold ? 1.0D : Math.min(1.0D, from + 0.30D + rnd.nextDouble() * 0.25D);
            double localTurns = turns * (to - from);
            for (int k = 0; k <= segments; k++) {
                double local = k / (double) segments;
                double u = from + (to - from) * local;            // 0 ＝ 底、1 ＝ 顶 ✓
                double r = radius * (narrow + (wide - narrow) * u);
                double theta = spin + phase + drift * u + localTurns * Math.PI * 2.0D * local;
                double y = radius * VORTEX_HEIGHT * (u - 0.5D);
                double wob = 1.0D + wobble * Math.sin(u * 7.0D + phase * 3.0D);
                points[k][0] = Math.cos(theta) * r * wob;
                points[k][1] = y;
                points[k][2] = Math.sin(theta) * r * wob;
            }
            // 渐变参数：主线偏亮偏粗、淡线偏暗偏细，中间再随机打散一档 ✓
            float widthScale = isBold
                    ? 1.15F + (float) rnd.nextDouble() * 0.55F
                    : 0.45F + (float) rnd.nextDouble() * 0.45F;
            float alphaScale = isBold
                    ? 0.80F + (float) rnd.nextDouble() * 0.20F
                    : 0.30F + (float) rnd.nextDouble() * 0.40F;
            int palette = isBold
                    ? (rnd.nextDouble() < 0.55D ? PALETTE_CORE : PALETTE_MID)
                    : (rnd.nextDouble() < 0.55D ? PALETTE_GLOW : PALETTE_MID);
            out.add(new Line(points, widthScale, alphaScale, palette));
        }
        return out;
    }

    /**
     * 炸开时往外扩的冲击波环 ✓。
     *
     * @param radius   爆炸时的球半径
     * @param progress 0..1（phase 2 的进度）
     */
    public static List<double[][]> shockRings(double radius, double progress) {
        double p = Math.max(0.0D, Math.min(1.0D, progress));
        List<double[][]> out = new ArrayList<>(SHOCK_RINGS);
        for (int i = 0; i < SHOCK_RINGS; i++) {
            double local = Math.max(0.0D, Math.min(1.0D, p * 1.6D - i * 0.18D));
            double r = radius * (0.35D + 1.85D * local);
            double[][] pts = new double[SEGMENTS + 1][3];
            for (int k = 0; k <= SEGMENTS; k++) {
                double a = Math.PI * 2.0D * k / SEGMENTS;
                pts[k][0] = Math.cos(a) * r;
                pts[k][1] = Math.sin((k / (double) SEGMENTS) * Math.PI * 2.0D) * r * 0.06D;
                pts[k][2] = Math.sin(a) * r;
            }
            out.add(pts);
        }
        return out;
    }

    /** 炸开时的径向风痕 ✓（从球心往外甩的直线）。 */
    public static List<double[][]> streaks(double radius, double progress, long seed) {
        double p = Math.max(0.0D, Math.min(1.0D, progress));
        Random rnd = new Random(seed * 0x9E3779B97F4A7C15L);
        List<double[][]> out = new ArrayList<>(STREAKS);
        for (int i = 0; i < STREAKS; i++) {
            double u = rnd.nextDouble() * 2.0D - 1.0D;
            double theta = rnd.nextDouble() * Math.PI * 2.0D;
            double s = Math.sqrt(Math.max(0.0D, 1.0D - u * u));
            double dx = s * Math.cos(theta);
            double dy = u;
            double dz = s * Math.sin(theta);
            double inner = radius * (0.35D + 0.5D * p);
            double outer = radius * (0.9D + 1.7D * p);
            out.add(new double[][]{
                    {dx * inner, dy * inner, dz * inner},
                    {dx * outer, dy * outer, dz * outer}});
        }
        return out;
    }

    /** 漩涡部分的顶点预算 ✓。 */
    public static int vortexVertices(int tier) {
        int total = count(tier);
        int bold = boldCount(tier);
        int segments = bold * SEGMENTS + (total - bold) * FINE_SEGMENTS;
        return segments * 8 * 3;
    }

    /** 冲击波 / 风痕的顶点预算 ✓。 */
    public static int shockVertices() {
        return (SHOCK_RINGS * SEGMENTS + STREAKS) * 8 * 3;
    }
}
