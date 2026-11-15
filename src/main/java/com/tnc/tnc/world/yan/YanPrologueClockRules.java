package com.tnc.tnc.world.yan;

import net.minecraft.resources.ResourceLocation;

/**
 * 处决演出里那座「时钟」的**规则**（纯数学 + 三张亮墨贴图的路径 ✓）。
 *
 * <p>作者 2026-11-15：
 * <blockquote>
 * ①「玩家被衍处决了之后屏幕会出现时钟，你把那个时钟换成我们之前水墨法阵，旋转，然后加上分针时针啥的」<br>
 * ②「时针分针一点水墨感都没有，然后不用这个白色圆盘」
 * </blockquote>
 *
 * <p>所以第二版把第一版的**宣纸白盘删掉** ✗、把 `gui.fill` 画的矩形指针也删掉 ✗，改成：
 * <ol>
 *   <li>{@link #SEAL}：墨系漩涡阵的**亮墨版**（黑墨 → 月白，纸面直接透明 ✓）——
 *       处决画面本来就暗，黑墨贴上去等于没画 ✗，而作者又不要白盘 ✗ ⇒ 只能让**墨自己发光** ✓；</li>
 *   <li>{@link #HAND_HOUR} / {@link #HAND_MINUTE}：两支**毛笔笔锋**（起笔粗、收笔尖、
 *       边缘不齐、几道飞白 ✓）—— 矩形直边一点水墨感都没有 ✗，这是作者第二版的原话；</li>
 *   <li>指针依然**倒着转** ✓（"时间倒流"的语义不能丢 ✗）。</li>
 * </ol>
 */
public final class YanPrologueClockRules {

    private YanPrologueClockRules() {
    }

    /** 亮墨漩涡阵（{@code tools/gen_prologue_clock_ink.py} 生成 ✓ 512×512，只有墨迹那部分不透明）。 */
    public static final ResourceLocation SEAL =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/gui/ink_clock_seal.png");

    /** 时针：短、粗、笔锋收得慢一点 ✓。 */
    public static final ResourceLocation HAND_HOUR =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/gui/ink_clock_hand_hour.png");
    public static final int HAND_HOUR_W = 40;
    public static final int HAND_HOUR_H = 162;

    /** 分针：长、细、笔锋更利 ✓。 */
    public static final ResourceLocation HAND_MINUTE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/gui/ink_clock_hand_minute.png");
    public static final int HAND_MINUTE_W = 30;
    public static final int HAND_MINUTE_H = 262;

    /** 外层水墨阵的转角（度 ✓）：慢慢转 ⇒ 看得出是"阵在转"，不是"贴图在抖"✗。 */
    public static float outerTurn(float timepiece) {
        return timepiece * 0.9F;
    }

    /** 内层水墨阵：反向、更快、更小 ⇒ 墨化开的感觉 ✓。 */
    public static float innerTurn(float timepiece) {
        return -timepiece * 1.45F;
    }

    /** 内层缩放（相对外层 ✓）。 */
    public static final float INNER_SCALE = 0.66F;

    /** 内层不透明度 ✓（压在外层上，别把它糊掉）。 */
    public static final float INNER_ALPHA = 0.46F;

    /** 外层水墨阵的不透明度 ✓（亮墨本来就不刺眼，可以给足 ✓）。 */
    public static final float OUTER_ALPHA = 0.92F;

    /** 外层水墨阵相对表盘半径的缩放 ✓。 */
    public static final float OUTER_SCALE = 1.0F;

    /** 时针角度（度 ✓ 0 = 指向上方，顺时针为正）。 */
    public static float hourAngle(float timepiece) {
        return 60.0F - timepiece * timepiece * 0.19F;
    }

    /** 分针角度（度 ✓）。 */
    public static float minuteAngle(float timepiece) {
        return 158.0F - timepiece * timepiece * 0.9F;
    }

    /** 表盘半径（GUI 单位 ✓ 190 单位 ≈ 整个表盘，和原来那版同一个尺度）。 */
    public static final float DIAL_RADIUS = 92.0F;

    /** 时针画多长（GUI 单位 ✓）。 */
    public static final float HOUR_LENGTH = 56.0F;

    /** 分针画多长（GUI 单位 ✓）。 */
    public static final float MINUTE_LENGTH = 88.0F;

    /** 出现淡入用多少 tick（和原来那版一致 ✓）。 */
    public static final float FADE_IN = 10.0F;
}
