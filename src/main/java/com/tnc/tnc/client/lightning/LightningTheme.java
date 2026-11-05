package com.tnc.tnc.client.lightning;

/**
 * 雷系配色（作者 2026-11-05："雷电可以有黄的也可以有蓝也可以有紫的，但我不希望是纯色雷电，
 * 你可以中心是白的，渐变到边，然后发光"）。
 *
 * <p>所以每一档主题不是"一个颜色"，而是<b>三档</b>：
 * <ul>
 *   <li>{@link #glow(int)} —— 最外圈的大范围辉光（暗、脏一点的颜色，负责"照亮空气"）；</li>
 *   <li>{@link #mid(int)} —— 主色（看得出这是黄雷 / 蓝雷 / 紫雷）；</li>
 *   <li>{@link #edge(int)} —— 靠近白芯的那一圈（偏白，负责"白热"的过渡）。</li>
 * </ul>
 * 真正的<b>中心永远是纯白</b>（{@link LightningBoltGeometry} 里写死）⇒ 横截面的观感是
 * <b>白 → 浅色 → 主题色 → 透明</b>，而不是一根纯色的塑料棒 ✗。
 *
 * <p>都是 0..1 的线性色（顶点色直接乘），不预乘/不 gamma —— 加色混合下这样最稳 ✓。
 */
public final class LightningTheme {

    /** 金黄（雷暴里最"炸"的那一道）。 */
    public static final int GOLD = 0;
    /** 天蓝（稻妻那种冷白偏蓝的雷）。 */
    public static final int BLUE = 1;
    /** 紫（主色，最像"天雷"的一档）。 */
    public static final int VIOLET = 2;
    /** 暗色（黑暗衍专用：紫黑、中间仍然留一点冷白）。 */
    public static final int DARK = 3;

    /** 主题数量（随机取色用）。 */
    public static final int COUNT = 3;

    private static final float[][] MID = {
            {1.00F, 0.86F, 0.42F},   // GOLD
            {0.62F, 0.86F, 1.00F},   // BLUE
            {0.84F, 0.68F, 1.00F},   // VIOLET
            {0.66F, 0.42F, 0.98F},   // DARK（暗雷也还是紫，不是纯黑 —— 纯黑在夜里看不见 ✗）
    };

    private static final float[][] GLOW = {
            {1.00F, 0.66F, 0.14F},   // GOLD
            {0.20F, 0.52F, 1.00F},   // BLUE
            {0.55F, 0.24F, 1.00F},   // VIOLET
            {0.26F, 0.05F, 0.46F},   // DARK
    };

    private static final float[][] EDGE = {
            {1.00F, 0.94F, 0.66F},   // GOLD
            {0.84F, 0.95F, 1.00F},   // BLUE
            {0.93F, 0.84F, 1.00F},   // VIOLET
            {0.88F, 0.80F, 1.00F},   // DARK
    };

    /** 纯白（所有闪电的芯）。 */
    public static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    private LightningTheme() {
    }

    /** 把任意 int 夹到合法主题（存档/网络里来的值不可信）。 */
    public static int clamp(int theme) {
        return theme < 0 || theme > DARK ? VIOLET : theme;
    }

    /** 主题主色。 */
    public static float[] mid(int theme) {
        return MID[clamp(theme)];
    }

    /** 外圈辉光色。 */
    public static float[] glow(int theme) {
        return GLOW[clamp(theme)];
    }

    /** 靠近白芯的过渡色。 */
    public static float[] edge(int theme) {
        return EDGE[clamp(theme)];
    }
}
