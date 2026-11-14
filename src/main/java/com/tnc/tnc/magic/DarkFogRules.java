package com.tnc.tnc.magic;

/**
 * ★ <b>黑雾的体积规则</b>（2026-11-11 作者：「黑雾的大小也要随着 t 级增长变大，
 * 你别只加宽度，高度也要加」＋「我要你瞬间给他拉进来」）✓
 *
 * <p>纯数学，不碰 MC 类 ⇒ 单元测试可以直接钉住"高度也跟着档位长" ✓
 * （{@code DarkFogCloudEntity} 的静态初始化会注册同步字段，测试里不能直接调它的方法 ✗）。
 *
 * <h2>为什么高度要单独拿出来</h2>
 * 以前只有 JSON 的 {@code volume.radius} 随档位变（4.5 / 5 / 6 / 7.5 / 9 格），
 * 高度是渲染器里写死的 {@code 1.1 + 档位×0.1}（1.2 → 1.6，几乎不长 ✗）⇒
 * 高档的雾变成"又宽又扁的一层" ✗。
 *
 * <p>现在半高 = <b>半径 × {@link #HEIGHT_RATIO}</b>：
 * t1 4.5 格宽 ⇒ 半高 2.25（总高 4.5）；t5 9 格宽 ⇒ 半高 4.5（总高 9）✓ ——
 * 宽度和高度一起长，雾才是个"立起来的体积" ✓。
 */
public final class DarkFogRules {

    /** 半高 / 半径 ✓（0.5 ⇒ 总高 ≈ 宽度，读起来是一团圆雾而不是一张饼）。 */
    public static final double HEIGHT_RATIO = 0.5D;

    /** 半高的下限（格）✓（最小的雾也要有 4 格高，不然贴地一张纸 ✗）。 */
    public static final double MIN_HALF_HEIGHT = 2.0D;

    /** Boss 领域雾（半径比它大）保持原样 ✓。 */
    public static final double DOMAIN_RADIUS = 64.0D;

    /** 领域雾的半高（格）—— 那是"整个战场都是雾"，长高没意义 ✓。 */
    public static final double DOMAIN_HALF_HEIGHT = 3.2D;

    /** 抓取半径相对雾半径的倍数 ✓（作者 2026-11-06：抓取要比雾范围大）。 */
    public static final double GRIP_RANGE = 1.45D;

    /** 被瞬移进雾里后，离雾心多远（半径的倍数）✓。 */
    public static final double INSIDE_RATIO = 0.45D;

    /** 瞬移后离雾心的最小距离（格）✓（太小会卡在雾心抖动）。 */
    public static final double INSIDE_MIN = 0.6D;

    private DarkFogRules() {
    }

    /** 一片雾的半高（格）✓。 */
    public static double halfHeight(double radius) {
        if (radius <= 0.0D) {
            return MIN_HALF_HEIGHT;
        }
        if (radius > DOMAIN_RADIUS) {
            return DOMAIN_HALF_HEIGHT;
        }
        return Math.max(MIN_HALF_HEIGHT, radius * HEIGHT_RATIO);
    }

    /** 一片雾的直径（宽度）✓ —— 就是 JSON 里的半径 × 2。 */
    public static double width(double radius) {
        return Math.max(0.0D, radius) * 2.0D;
    }

    /** 一片雾的总高 ✓。 */
    public static double height(double radius) {
        return halfHeight(radius) * 2.0D;
    }

    /** 抓取半径（比雾本身大）✓。 */
    public static double gripRadius(double radius) {
        if (radius <= 0.0D) {
            return 0.0D;
        }
        return radius > DOMAIN_RADIUS ? Math.min(radius * 1.15D + 8.0D, 96.0D) : radius * GRIP_RANGE;
    }

    /** 被拉进来之后停在离雾心多远（格）✓。 */
    public static double insideDistance(double radius) {
        return Math.max(INSIDE_MIN, radius * INSIDE_RATIO);
    }

    /** 这个点（相对雾心的位置）算不算在雾的圆柱里 ✓。 */
    public static boolean insideVolume(double radius, double dx, double dy, double dz) {
        double half = halfHeight(radius);
        return Math.abs(dy) <= half && (dx * dx + dz * dz) <= radius * radius;
    }
}
