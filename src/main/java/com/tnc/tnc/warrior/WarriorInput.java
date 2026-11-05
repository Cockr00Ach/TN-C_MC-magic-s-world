package com.tnc.tnc.warrior;

/**
 * 一次输入：按了哪个斩击方向，以及是<b>轻点</b>还是<b>长按</b>。
 *
 * <p>长按不是"另一个键"，而是同一个键按住超过 {@link ComboTracker#HOLD_TICKS}。
 * 例如防御链的振刀是 {@code Q E Z(长按)}：前两下轻点，第三下按住半秒。
 * 轻点与长按在缓冲里是两种不同的输入，所以同一串按键既能进普通连招、也能进长按招式，
 * 不会互相误触。
 */
public record WarriorInput(WarriorMove move, boolean held) {

    public WarriorInput {
        if (move == null) {
            throw new IllegalArgumentException("输入必须有斩击方向");
        }
    }

    /** 轻点一下。 */
    public static WarriorInput tap(WarriorMove move) {
        return new WarriorInput(move, false);
    }

    /** 按住不放。 */
    public static WarriorInput hold(WarriorMove move) {
        return new WarriorInput(move, true);
    }

    /** 界面上的短标签：长按显示成 {@code Z+}。 */
    public String label() {
        return held ? move.label() + "+" : move.label();
    }

    /** 给提示文案用的完整说法。 */
    public String fullLabel() {
        return held ? move.cn() + "长按" : move.cn();
    }
}
