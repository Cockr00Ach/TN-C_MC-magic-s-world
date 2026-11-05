package com.tnc.tnc.warrior;

import java.util.List;

/**
 * 长剑连招的三种斩击输入。
 *
 * <p>键位是<b>默认值</b>：Q = 左斩击、E = 右斩击、左键（Z）= 横斩击。
 * 玩家可以在「选项 → 控制 → TN-C」里改键；这里的 {@link #key()} 只用于界面显示连招串
 * （例如 {@code QEZ}），<b>不是</b>运行时读键的依据 —— 运行时永远走
 * {@code WarriorComboKeys} 的 KeyMapping，改键后才不会出现"界面写 Q、实际按 R"的错位。
 *
 * <p>枚举顺序就是网络包里的序号，<b>不要调整</b>；新增招式只往末尾加。
 */
public enum WarriorMove {

    /** Q · 左斩击：从右上往左下劈。 */
    LEFT('q', "左斩击"),
    /** E · 右斩击：从左上往右下劈。 */
    RIGHT('e', "右斩击"),
    /** Z · 横斩击：平着横扫。 */
    HORIZONTAL('z', "横斩击");

    private final char key;
    private final String cn;

    WarriorMove(char key, String cn) {
        this.key = key;
        this.cn = cn;
    }

    /** 连招串里的字母（小写）。 */
    public char key() {
        return key;
    }

    /** 界面上的中文名。 */
    public String cn() {
        return cn;
    }

    /** 界面上的单字母（大写）。 */
    public String label() {
        return String.valueOf(Character.toUpperCase(key));
    }

    /** 解析连招串里的一个字母；非法返回 {@code null}。 */
    public static WarriorMove byKey(char key) {
        char lower = Character.toLowerCase(key);
        for (WarriorMove move : values()) {
            if (move.key == lower) {
                return move;
            }
        }
        return null;
    }

    /** 网络包序号解析；越界返回 {@code null}（不要抛异常，坏包不该踢人）。 */
    public static WarriorMove byOrdinal(int ordinal) {
        WarriorMove[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** 把一串输入拼成界面上的连招串，例如 {@code [LEFT, RIGHT] -> "QE"}。 */
    public static String label(List<WarriorMove> moves) {
        StringBuilder out = new StringBuilder(moves.size());
        for (WarriorMove move : moves) {
            out.append(move.label());
        }
        return out.toString();
    }

    /** 把连招串（大小写都行）解析成输入列表；含非法字母返回 {@code null}。 */
    public static List<WarriorMove> parse(String sequence) {
        if (sequence == null || sequence.isBlank()) {
            return null;
        }
        WarriorMove[] out = new WarriorMove[sequence.length()];
        for (int i = 0; i < sequence.length(); i++) {
            WarriorMove move = byKey(sequence.charAt(i));
            if (move == null) {
                return null;
            }
            out[i] = move;
        }
        return List.of(out);
    }
}
