package com.tnc.tnc.magic;

/**
 * 雷龙的<b>飞行轨迹</b>（纯数学，服务端/客户端共用 ✓）。
 *
 * <p>为什么单独一个类：粒子和剔除盒在服务端算（{@link TNLightningDragonEntity}），
 * 模型和雷电勾线在客户端算（{@code TNLightningDragonRenderer}）—— 两边必须是<b>同一条曲线</b>，
 * 否则"龙从这儿冲上去、电花从那儿冒" ✗。抽出来以后只要改这一处，两边一起变 ✓。
 *
 * <h2>轨迹长什么样</h2>
 * 一条<b>收紧的螺旋</b>：从落点（半径 ≈ 2 格）直冲云霄（半径 ≈ 5.4 格），
 * 高度用 ease-out（起手最快、越高越缓）—— 这正是"破土而出、一飞冲天、然后在云里盘住" ✓。
 * 龙身每一节只是把同一条曲线<b>往回延迟</b> {@code LAG} tick 取点，于是自然形成龙身拖尾 ✓。
 */
public final class LightningDragonPath {

    /** 从落点算起，龙头最高能到多少格（44 倍尺寸时约 60 格）✓。 */
    public static double sky(double scale) {
        return 52.0D + 10.0D * Math.max(0.5D, scale);
    }

    /** 冲上云霄用多少 tick（之后是盘旋/淡出 ✓）。 */
    public static final double RISE_TICKS = 78.0D;

    /** 某一 tick 龙头的高度（格，从落点算）。 */
    public static double rise(double age, double scale) {
        double t = Math.min(1.0D, Math.max(0.0D, age / RISE_TICKS));
        double ease = 1.0D - (1.0D - t) * (1.0D - t);        // ease-out：一上来就窜 ✓
        return sky(scale) * ease;
    }

    /** 螺旋角（弧度）：转得快一点才像"盘龙"，慢得像电梯 ✗。 */
    public static double angle(double age) {
        return age * 0.105D;
    }

    /** 螺旋半径：越高越外扩（贴着地面时收得很紧，破土那一下才有冲劲 ✓）。 */
    public static double radius(double age, double scale) {
        double k = Math.min(1.0D, Math.max(0.0D, rise(age, scale) / sky(scale)));
        return (1.8D + 3.6D * k) * Math.max(0.5D, scale);
    }

    /**
     * 某一 tick 龙头相对落点的坐标（x, y, z）—— y 就是"高度"，x/z 是螺旋 ✓。
     *
     * @return 长度 3 的数组（不是 Vec3：这个类会被服务端 tick 高频调用，少建对象 ✓）
     */
    public static double[] point(double age, double scale) {
        double a = angle(age);
        double r = radius(age, scale);
        return new double[]{Math.cos(a) * r, rise(age, scale), Math.sin(a) * r};
    }

    /** 龙身每一节往回延迟多少 tick（越大龙越长、越软 ✓）。 */
    public static final double LAG = 1.15D;

    /** 龙身相邻两节的<b>垂直</b>间距（格 × 尺寸倍数）—— 龙的长度靠它固定 ✓。 */
    public static final double SEGMENT_SPACING = 1.95D;

    /**
     * 反解：飞到高度 {@code y} 时是第几 tick（0..{@link #RISE_TICKS}）✓。
     *
     * <p>{@code rise(a) = sky·(1 − (1 − a/78)²)} ⇒ {@code a = 78·(1 − √(1 − y/sky))}。
     */
    public static double ageForHeight(double y, double scale) {
        double sky = sky(scale);
        double k = Math.min(1.0D, Math.max(0.0D, y / sky));
        return RISE_TICKS * (1.0D - Math.sqrt(Math.max(0.0D, 1.0D - k)));
    }

    /**
     * <b>按高度取点</b>（龙身的取法 ✓）：给定高度，返回那一刻龙头所在的螺旋位置。
     *
     * <p>为什么龙身不能按"每节晚固定 tick"取：ease-out 的轨迹越高越慢 ⇒
     * 到顶时后 22 节全挤在最后 6 格里，龙会缩成一个球 ✗。
     * 按<b>高度</b>取（每节低 1.95 格）⇒ 无论飞多快，龙身永远是均匀拉长的 43 格 ✓。
     */
    public static double[] pointAtHeight(double y, double scale) {
        double a = ageForHeight(y, scale);
        double ang = angle(a);
        double r = radius(a, scale);
        return new double[]{Math.cos(ang) * r, y, Math.sin(ang) * r};
    }

    private LightningDragonPath() {
    }
}
