package com.tnc.tnc.client;

import net.minecraft.util.Mth;

/**
 * <b>像素风火系色板</b>（作者 2026-10-05 要求：「不要用仿真渲染，改用像素风格」✓）
 *
 * <h2>什么意思</h2>
 * <p>之前的火系特效是"仿真"画法 ✗：
 * <ul>
 *   <li>沿轴<b>连续插值</b>出无数种中间色（渐变 ✗）</li>
 *   <li>透明度从 0.9 一路衰减到 0（半透明发光 ✗）</li>
 * </ul>
 * 出来就是那种"现代特效插件"的观感 ✗，跟原版像素风不搭 ✗。
 *
 * <p>作者选的做法：<b>去掉渐变和透明，改成硬边纯色块</b> ✓
 * ⇒ 这里给一套<b>离散的</b>火系色板 ✓：
 * <ul>
 *   <li>任何 {@code t} 都被<b>量化</b>到色板里的<b>某一个</b>颜色 ✓（相邻色块之间是硬边 ✗）</li>
 *   <li>alpha 恒为 <b>1.0</b>（完全不透明 ✓）—— 色块之间不再互相透出来 ✓</li>
 * </ul>
 *
 * <p>色板顺序就是"从最热到最冷"：白热 → 亮黄 → 橙 → 深橙 → 暗红 → 焦黑 ✓
 * （正好对应火焰从核心到外围的层次 ✓，所以量化之后仍然一眼看得出是火 ✓）
 */
public final class PixelFlame {

    private PixelFlame() {
    }

    /** 色板（从最热到最冷 ✓）—— 每个都是【实心不透明】的 RGB ✓。 */
    private static final float[][] PALETTE = {
            {1.00F, 0.98F, 0.88F},   // 0 白热
            {1.00F, 0.88F, 0.42F},   // 1 亮黄
            {1.00F, 0.66F, 0.16F},   // 2 橙
            {0.90F, 0.40F, 0.06F},   // 3 深橙
            {0.62F, 0.18F, 0.04F},   // 4 暗红
            {0.30F, 0.08F, 0.03F},   // 5 焦黑
    };

    /** 色板容量 ✓。 */
    public static final int LEVELS = PALETTE.length;

    /**
     * 把 {@code t}（0 = 最热、1 = 最冷 ✓）量化成色板里的一个颜色 ✓，写进 {@code dst}。
     *
     * <p>⚠️ alpha 恒为 1.0 ✓ —— 这正是"去掉透明"的意思 ✓
     * （复用传入数组，渲染里不该分配 ✗）
     */
    public static void flat(double t, float[] dst) {
        int i = level(t);
        dst[0] = PALETTE[i][0];
        dst[1] = PALETTE[i][1];
        dst[2] = PALETTE[i][2];
        dst[3] = 1.0F;
    }

    /** 只要颜色、自己另外给 alpha（少用 ✓，大部分地方应该用 {@link #flat} 的不透明版 ✓）。 */
    public static void flat(double t, float alpha, float[] dst) {
        flat(t, dst);
        dst[3] = alpha;
    }

    /** {@code t} 落在第几档（0..LEVELS-1 ✓）。 */
    public static int level(double t) {
        return Mth.clamp((int) Math.floor(Mth.clamp(t, 0.0D, 0.9999D) * LEVELS), 0, LEVELS - 1);
    }

    /** 直接取某一档的 rgb（某个结构固定用第几档时更直观 ✓）。 */
    public static float[] rgb(int level) {
        return PALETTE[Mth.clamp(level, 0, LEVELS - 1)];
    }

    /** 某个色块的整体亮度缩放（做明暗层次用 ✓；仍然是实心的 ✓）。 */
    public static void flatShaded(double t, float shade, float[] dst) {
        flat(t, dst);
        dst[0] = Math.min(1.0F, dst[0] * shade);
        dst[1] = Math.min(1.0F, dst[1] * shade);
        dst[2] = Math.min(1.0F, dst[2] * shade);
        dst[3] = 1.0F;
    }
}
