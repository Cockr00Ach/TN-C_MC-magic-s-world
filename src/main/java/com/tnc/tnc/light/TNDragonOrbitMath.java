package com.tnc.tnc.light;

/**
 * <b>光龙 t2 环形几何的纯数学部分</b> ✓ —— 不碰任何 Minecraft 类型 ✓，
 * 所以离线测试（{@code DragonOrbitRingTest}）可以直接拿它跟模型/动画对账 ✓。
 *
 * <h2>为什么要有这么个小类</h2>
 * 作者 2026-10-03："<b>环绕飞龙有点卡顿，并且应该是三条龙头对尾绕成一个圆接在一起，你可以重新做个环绕转圈的动画</b>" ✓。
 * "三条首尾相接"是个**几何约束** ✓：{@code orbit} 动画必须把身体正好弯成 {@code 360/n} 度的弧，
 * 而环的半径必须正好 {@code 体长 × n / 2π} ✓ —— 这两件事分别在
 * {@code dragon.animation.json}（{@code tools/gen_dragon_orbit.ps1} 生成 ✓）和运行时的
 * {@code TNLightDragonChain} 里 ✓。数一旦对不上，表现出来就是"三条龙的脑袋和尾巴差一截" ✗
 * （而那种毛病**只有进游戏才看得见** ✗）。
 *
 * <p>所以：公式放在这里一份 ✓，生成脚本按它算、运行时按它算、测试拿模型量出来的长度再验一遍 ✓。
 */
public final class TNDragonOrbitMath {

    /**
     * 龙的**体长**（格 ✓）—— 模型实测：鼻子 {@code z=−165} → 尾焰 {@code z=+199} ⇒ 364 单位 = <b>22.75 格</b> ✓
     * （测试里会拿 geo 再量一遍 ✓，模型改了这里就要跟着改 ✓）。
     */
    public static final double BODY_LENGTH_BLOCKS = 22.75D;

    /**
     * 弯曲中心那一节（{@code body1} 的 pivot）离**鼻子**多远（格 ✓）—— 69 单位 = 4.3125 格 ✓。
     * （模型里 {@code body1} 的 pivot 是 z=−96 ✓。）
     */
    public static final double CURL_CENTER_FROM_NOSE_BLOCKS = 69.0D / 16.0D;

    private TNDragonOrbitMath() {
    }

    /**
     * ★ 环半径（格 ✓）：身体被弯成 {@code 360/count} 度的弧 ⇒
     * {@code r = 体长 × count / (2π)} ✓（0.30 个头的三条小龙 ⇒ r ≈ 3.26 格 ✓）。
     */
    public static double ringRadius(double scale, int count) {
        return BODY_LENGTH_BLOCKS * scale * count / (2.0D * Math.PI);
    }

    /** 圆心角（度 ✓）：三条 ⇒ 120° ✓。 */
    public static double arcDegrees(int count) {
        return 360.0D / count;
    }
}
