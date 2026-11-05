package com.tnc.tnc.warrior;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 连招输入缓冲（纯规则，不碰 Minecraft）。
 *
 * <p>每次输入调用 {@link #push}：把输入追加到缓冲区，再检查缓冲区的<b>后缀</b>是否正好等于
 * 某条连招的顺序。命中就清空缓冲并返回该技能；没命中就把缓冲区削到"最长的、仍然是某条
 * 连招前缀的后缀"，这样玩家按错一个键不会把后面的正确输入一起废掉，界面也能直接拿
 * 缓冲区画"还差哪几下"。
 *
 * <p><b>轻点与长按是两种输入</b>：{@link WarriorInput#held()} 参与匹配。
 * 按下超过 {@link #HOLD_TICKS} 会把"刚按下的那一下轻点"替换成长按（而不是多算一段），
 * 所以玩家不需要"先松开再长按"，手感上就是按住不放。
 *
 * <p>窗口 {@link #WINDOW_TICKS} 是<b>相邻两次输入</b>的间隔：超过就整段作废，
 * 不会出现"上周按的 Q 还算数"。
 */
public final class ComboTracker {

    /** 相邻两次输入的最大间隔（tick）。2 秒，够玩家看清起手再决定下一段。 */
    public static final int WINDOW_TICKS = 40;

    /** 按住多久算长按（tick）。10 tick = 0.5 秒。 */
    public static final int HOLD_TICKS = 10;

    /** 缓冲区上限：比最长的连招多留一点余量，避免无限增长。 */
    public static final int MAX_BUFFER = 8;

    private record Entry(WarriorInput input, long tick) {
    }

    private final ArrayDeque<Entry> buffer = new ArrayDeque<>();

    public void clear() {
        buffer.clear();
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public int size() {
        return buffer.size();
    }

    /** 当前缓冲区里的输入（旧 → 新，含长按标记）。 */
    public List<WarriorInput> inputs() {
        List<WarriorInput> out = new ArrayList<>(buffer.size());
        for (Entry entry : buffer) {
            out.add(entry.input());
        }
        return out;
    }

    /** 只要方向、不要长按标记的兼容视图（命令行/旧测试用）。 */
    public List<WarriorMove> moves() {
        List<WarriorMove> out = new ArrayList<>(buffer.size());
        for (Entry entry : buffer) {
            out.add(entry.input().move());
        }
        return out;
    }

    /**
     * 丢掉过期的整段输入：只要离<b>最后一次</b>输入超过窗口，前面的输入一并作废。
     *
     * <p>窗口量的是相邻两次输入之间，不是"从第一下开始计时"，否则长连招（六段）
     * 会被总时长卡死。服务端每 tick 调一次，玩家发呆时缓冲不会一直挂着。
     */
    public void prune(long now) {
        if (!buffer.isEmpty() && now - buffer.peekLast().tick() > WINDOW_TICKS) {
            buffer.clear();
        }
    }

    /** 轻点输入（旧调用）。 */
    public WarriorSkill push(WarriorMove move, long now, List<WarriorSkill> skills) {
        return move == null ? null : push(WarriorInput.tap(move), now, skills);
    }

    /**
     * 追加一次输入并判定。
     *
     * <p>长按会<b>替换</b>刚按下的那一下轻点（同方向、间隔不超过 {@link #HOLD_TICKS}），
     * 而不是多记一段；否则玩家按住 Z 会得到 Q E Z Z 四段，永远接不上 Q E Z(长按)。
     *
     * @return 命中的连招；没有命中返回 {@code null}
     */
    public WarriorSkill push(WarriorInput input, long now, List<WarriorSkill> skills) {
        if (input == null) {
            return null;
        }
        prune(now);
        if (input.held() && !buffer.isEmpty()) {
            Entry last = buffer.peekLast();
            // 允许一点网络延迟：长按包到服务端时可能已经过了一个 RTT
            if (last.input().move() == input.move() && !last.input().held()
                    && now - last.tick() <= HOLD_TICKS + 10) {
                buffer.removeLast();
            }
        }
        buffer.addLast(new Entry(input, now));
        while (buffer.size() > MAX_BUFFER) {
            buffer.removeFirst();
        }
        WarriorSkill matched = matchSuffix(inputs(), skills);
        if (matched != null) {
            buffer.clear();
            return matched;
        }
        trimToPrefixedSuffix(skills);
        return null;
    }

    /**
     * 当前缓冲区对应的"最接近的连招"：缓冲区已经是最长前缀，所以直接找以它为前缀的连招。
     * 没有输入或没有匹配时返回 {@code null}。
     */
    public Progress progress(List<WarriorSkill> skills) {
        return progressOfInputs(inputs(), skills);
    }

    /** 兼容视图：把纯方向列表当成全轻点。 */
    public static Progress progressOf(List<WarriorMove> buffer, List<WarriorSkill> skills) {
        if (buffer == null) {
            return null;
        }
        List<WarriorInput> inputs = new ArrayList<>(buffer.size());
        for (WarriorMove move : buffer) {
            inputs.add(WarriorInput.tap(move));
        }
        return progressOfInputs(inputs, skills);
    }

    /** 纯函数版：给客户端镜像算进度用（不依赖缓冲区实例）。 */
    public static Progress progressOfInputs(List<WarriorInput> buffer, List<WarriorSkill> skills) {
        if (buffer == null || buffer.isEmpty()) {
            return null;
        }
        WarriorSkill best = null;
        int bestLength = Integer.MAX_VALUE;
        for (WarriorSkill skill : skills) {
            // 同一段前缀可能同时是"短连招"和"长连招"的开头：HUD 优先提示更短的那条，
            // 玩家更容易接上；真想打长的，继续按对下一段自然会切过去。
            if (skill.length() >= buffer.size() && startsWith(skill, buffer)
                    && skill.length() < bestLength) {
                best = skill;
                bestLength = skill.length();
            }
        }
        return best == null ? null : new Progress(best, buffer.size());
    }

    /** 缓冲区命中的连招（完整匹配）；没有返回 {@code null}。 */
    public static WarriorSkill matchSuffix(List<WarriorInput> buffer, List<WarriorSkill> skills) {
        if (buffer == null || buffer.isEmpty()) {
            return null;
        }
        WarriorSkill best = null;
        for (WarriorSkill skill : skills) {
            if (skill.length() > buffer.size()) {
                continue;
            }
            if (endsWith(buffer, skill) && (best == null || skill.length() > best.length())) {
                best = skill;
            }
        }
        return best;
    }

    /** 缓冲区后缀里"最长且是某条连招前缀"的那一段长度；0 表示没有。 */
    public static int longestPrefixedSuffix(List<WarriorInput> buffer, List<WarriorSkill> skills) {
        for (int length = buffer.size(); length > 0; length--) {
            List<WarriorInput> suffix = buffer.subList(buffer.size() - length, buffer.size());
            for (WarriorSkill skill : skills) {
                if (skill.length() >= length && startsWith(skill, suffix)) {
                    return length;
                }
            }
        }
        return 0;
    }

    private void trimToPrefixedSuffix(List<WarriorSkill> skills) {
        int keep = longestPrefixedSuffix(inputs(), skills);
        while (buffer.size() > keep) {
            buffer.removeFirst();
        }
    }

    /**
     * 前缀匹配只看方向，<b>不看长按标记</b>：长按是"按下之后才确定"的，
     * 所以 Q E Z（轻点）先作为 Q E Z+ 的前缀留在缓冲里等长按；等长按包到了，
     * 末尾那一下会被替换成长按并完整命中。真正判定命中（{@link #endsWith}）才严格比对长按。
     */
    private static boolean startsWith(WarriorSkill skill, List<WarriorInput> prefix) {
        if (prefix.size() > skill.length()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (skill.sequence().get(i) != prefix.get(i).move()) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsWith(List<WarriorInput> buffer, WarriorSkill skill) {
        int offset = buffer.size() - skill.length();
        for (int i = 0; i < skill.length(); i++) {
            if (!matches(skill, i, buffer.get(offset + i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 第 {@code index} 位是否吻合：方向必须相同；<b>长按可以满足轻点要求</b>
     * （玩家按住 Q 时不该把 QZE 这类轻点连招卡死），但轻点不能满足长按要求
     * （振刀的"按住 Z"必须真的按住）。
     */
    private static boolean matches(WarriorSkill skill, int index, WarriorInput input) {
        if (skill.sequence().get(index) != input.move()) {
            return false;
        }
        return !skill.holdAt(index) || input.held();
    }

    /**
     * HUD 用的进度：正在接哪一招、已经按对几下。
     *
     * @param skill        最接近的连招
     * @param matchedCount 已按对的段数（1..length-1；完整命中时缓冲区已清空，不会出现）
     */
    public record Progress(WarriorSkill skill, int matchedCount) {

        /** 下一步该按的键；已经按满则返回 {@code null}。 */
        public WarriorMove next() {
            return matchedCount < skill.length() ? skill.sequence().get(matchedCount) : null;
        }

        /** 下一步要不要长按。 */
        public boolean nextHold() {
            return matchedCount < skill.length() && skill.holds().get(matchedCount);
        }

        /** 已经按满、但最后一段还停在"轻点"，正等玩家按住（HUD 提示"按住"）。 */
        public boolean waitingForHold() {
            return matchedCount == skill.length() && skill.holdAt(skill.length() - 1)
                    && !skill.holds().isEmpty();
        }

        /** 例如 {@code Q E Z+ · ·}：按对的显示字母（长按带 +），没按的用中点。 */
        public String display() {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < skill.length(); i++) {
                if (i > 0) {
                    out.append(' ');
                }
                if (i < matchedCount) {
                    out.append(skill.sequence().get(i).label());
                    if (skill.holds().get(i)) {
                        out.append('+');
                    }
                } else {
                    out.append('·');
                }
            }
            return out.toString();
        }
    }
}
