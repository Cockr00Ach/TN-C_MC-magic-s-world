package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 连招缓冲的判定规则。
 *
 * <p>这里钉的是"玩家按了什么 → 到底算不算连上"：命中即清空、按错只丢前面、
 * 超时整段作废、缓冲有上限。界面上的"还差哪几下"也直接读这里，所以进度计算
 * 必须和命中判定用同一套前缀规则。
 */
class ComboTrackerTest {

    private static final List<WarriorSkill> CATALOG = WarriorCombos.forFamily(WarriorWeaponFamily.LONGSWORD);

    private static ComboTracker.Progress progress(List<WarriorMove> buffer) {
        return ComboTracker.progressOf(buffer, CATALOG);
    }

    @Test
    void threeInputOpenerFiresBreakStanceAndClearsBuffer() {
        ComboTracker tracker = new ComboTracker();
        assertNull(tracker.push(WarriorMove.LEFT, 0, CATALOG));
        assertNull(tracker.push(WarriorMove.HORIZONTAL, 2, CATALOG));
        WarriorSkill hit = tracker.push(WarriorMove.RIGHT, 4, CATALOG);
        assertNotNull(hit, "QZE 三下必须命中破锋式");
        assertEquals("sword_combo_1", hit.id());
        assertTrue(tracker.isEmpty(), "命中后缓冲清空，下一串从零开始");
    }

    @Test
    void sixInputSpinOnlyMatchesOnTheLastInput() {
        ComboTracker tracker = new ComboTracker();
        List<WarriorMove> sequence = WarriorCombos.byId("sword_combo_2").sequence();
        long tick = 0;
        for (int i = 0; i < sequence.size() - 1; i++) {
            assertNull(tracker.push(sequence.get(i), tick, CATALOG), "第 " + (i + 1) + " 段不应提前命中");
            tick += 4;
        }
        WarriorSkill hit = tracker.push(sequence.get(sequence.size() - 1), tick, CATALOG);
        assertNotNull(hit);
        assertEquals("sword_combo_2", hit.id());
        assertTrue(tracker.isEmpty());
    }

    @Test
    void wrongInputKeepsOnlyTheLongestPrefixedSuffix() {
        ComboTracker tracker = new ComboTracker();
        tracker.push(WarriorMove.LEFT, 0, CATALOG);          // Q
        tracker.push(WarriorMove.HORIZONTAL, 2, CATALOG);     // Z
        // QZZ：整段不是任何连招的前缀，但后缀 ZZ 是逆鳞斩的开头；
        // 所以只把前面的 Q 丢掉，而不是整段作废（否则玩家按错一下就要从头来）
        assertNull(tracker.push(WarriorMove.HORIZONTAL, 4, CATALOG));
        assertEquals(List.of(WarriorMove.HORIZONTAL, WarriorMove.HORIZONTAL), tracker.moves());
        ComboTracker.Progress progress = tracker.progress(CATALOG);
        assertNotNull(progress);
        assertEquals(2, progress.matchedCount());
        assertEquals(WarriorMove.LEFT, progress.next(), "从 ZZ 起手，下一步应提示 Q");
    }

    @Test
    void staleInputsAreDroppedByTheWindow() {
        ComboTracker tracker = new ComboTracker();
        tracker.push(WarriorMove.LEFT, 0, CATALOG);
        // 第二下隔了 41 tick（窗口 40）：第一下作废，只剩刚落下的这一下
        // （E 现在是振刀（EQE）的起手，所以它自己会留下，但 Q 必须被削掉）
        assertNull(tracker.push(WarriorMove.RIGHT, ComboTracker.WINDOW_TICKS + 1, CATALOG));
        assertEquals(List.of(WarriorMove.RIGHT), tracker.moves(), "超时的前半段必须被削掉");
        ComboTracker.Progress progress = tracker.progress(CATALOG);
        assertNotNull(progress);
        assertEquals("sword_combo_4", progress.skill().id(), "同长度前缀按槽位顺序提示振刀");
        assertEquals(1, progress.matchedCount());
    }

    @Test
    void bufferNeverGrowsBeyondItsCap() {
        ComboTracker tracker = new ComboTracker();
        for (int i = 0; i < ComboTracker.MAX_BUFFER * 3; i++) {
            tracker.push(WarriorMove.LEFT, i, CATALOG);
        }
        assertTrue(tracker.size() <= ComboTracker.MAX_BUFFER, "缓冲最多留 " + ComboTracker.MAX_BUFFER + " 段");
    }

    @Test
    void longestFullMatchWinsWhenSequencesOverlap() {
        // 防御性用例：即使以后目录里出现"短顺序是长顺序后缀"的条目，也应命中更长的那条
        WarriorSkill shortSkill = new WarriorSkill("short", "short", "短", WarriorMove.parse("z"),
                10, 20, 1f, 1.0, 90, 0, 0, "",
                WarriorWeaponFamily.LONGSWORD, WarriorChain.ATTACK, 0, false, false, 0,
                WarriorAnimation.SLASH_LEFT, List.of(false), 0, 0, 0);
        WarriorSkill longSkill = new WarriorSkill("long", "long", "长", WarriorMove.parse("qz"),
                11, 20, 1f, 1.0, 90, 0, 0, "",
                WarriorWeaponFamily.LONGSWORD, WarriorChain.ATTACK, 0, false, false, 0,
                WarriorAnimation.SLASH_LEFT, List.of(false, false), 0, 0, 0);
        List<WarriorSkill> skills = List.of(shortSkill, longSkill);
        ComboTracker tracker = new ComboTracker();
        assertNull(tracker.push(WarriorMove.LEFT, 0, skills));
        WarriorSkill hit = tracker.push(WarriorMove.HORIZONTAL, 2, skills);
        assertNotNull(hit);
        assertEquals("long", hit.id(), "两条都能完整命中时取更长的那条");
    }

    @Test
    void progressPrefersTheShorterChainForTheSamePrefix() {
        // Q 同时是破锋式（QZE）和旋转斩击（QEZZZZ）的开头；界面优先提示更短的
        ComboTracker.Progress progress = progress(List.of(WarriorMove.LEFT));
        assertNotNull(progress);
        assertEquals("sword_combo_1", progress.skill().id());
        assertEquals("Q · ·", progress.display());
    }

    @Test
    void progressDisplayMarksMatchedAndRemainingInputs() {
        // Q E 是旋转斩击（QE + Q ZZZ）的开头；振刀改成 E Q + 长按 E 之后不再抢这个前缀
        ComboTracker.Progress progress = progress(List.of(WarriorMove.LEFT, WarriorMove.RIGHT));
        assertNotNull(progress);
        assertEquals("sword_combo_2", progress.skill().id());
        assertEquals("Q E ·", progress.display());
        assertEquals(WarriorMove.HORIZONTAL, progress.next());
    }

    @Test
    void tapEStaysPendingForTheHoldAndThenMatchesTheStance() {
        ComboTracker tracker = new ComboTracker();
        assertNull(tracker.push(WarriorMove.RIGHT, 0, CATALOG));
        assertNull(tracker.push(WarriorMove.LEFT, 2, CATALOG));
        assertNull(tracker.push(WarriorMove.RIGHT, 4, CATALOG), "轻点 E 不该提前放振刀");
        ComboTracker.Progress pending = tracker.progress(CATALOG);
        assertNotNull(pending, "轻点 E 要留在缓冲里等长按");
        assertEquals("sword_combo_4", pending.skill().id());
        assertTrue(pending.waitingForHold(), "HUD 要在此时提示按住");
        WarriorSkill hit = tracker.push(WarriorInput.hold(WarriorMove.RIGHT), 6, CATALOG);
        assertNotNull(hit, "长按 E 应立刻接出振刀");
        assertEquals("sword_combo_4", hit.id());
        assertTrue(tracker.isEmpty(), "命中后缓冲清空");
    }

    @Test
    void longPressReplacesTheTapInsteadOfAddingAnExtraInput() {
        ComboTracker tracker = new ComboTracker();
        tracker.push(WarriorMove.RIGHT, 0, CATALOG);
        tracker.push(WarriorMove.LEFT, 2, CATALOG);
        tracker.push(WarriorMove.RIGHT, 4, CATALOG);
        // 长按包在 10 tick 内到达：应当替换末尾的轻点，而不是多记一段 E
        tracker.push(WarriorInput.hold(WarriorMove.RIGHT), 8, CATALOG);
        assertTrue(tracker.isEmpty(), "命中后缓冲清空，说明是替换而不是追加");
    }

    @Test
    void tapChainsStillFireOnPlainTaps() {
        ComboTracker tracker = new ComboTracker();
        assertNull(tracker.push(WarriorMove.RIGHT, 0, CATALOG));
        assertNull(tracker.push(WarriorMove.LEFT, 2, CATALOG));
        // E Q E 轻点既不匹配振刀（要长按），也不该被长按逻辑吞掉
        assertNull(tracker.push(WarriorMove.RIGHT, 4, CATALOG));
        assertNotNull(tracker.progress(CATALOG));
    }

    @Test
    void holdStillCompletesTapOnlyChains() {
        // 长按是轻点的超集：玩家按住 Q 时，QZE 这类轻点连招照常成立，不会被"按住"卡死
        ComboTracker tracker = new ComboTracker();
        tracker.push(WarriorInput.hold(WarriorMove.LEFT), 0, CATALOG);
        tracker.push(WarriorMove.HORIZONTAL, 2, CATALOG);
        WarriorSkill hit = tracker.push(WarriorMove.RIGHT, 4, CATALOG);
        assertNotNull(hit, "长按 Q 不该卡死破锋式");
        assertEquals("sword_combo_1", hit.id());
    }

    @Test
    void emptyBufferHasNoProgress() {
        assertNull(progress(List.of()));
        assertNull(ComboTracker.progressOf(null, CATALOG));
    }

    @Test
    void sequenceParsingRejectsUnknownKeys() {
        assertEquals(List.of(WarriorMove.LEFT, WarriorMove.RIGHT, WarriorMove.HORIZONTAL),
                WarriorMove.parse("QEZ"));
        assertNull(WarriorMove.parse("qex"));
        assertNull(WarriorMove.parse(""));
        assertNull(WarriorMove.parse(null));
        assertEquals("QEZ", WarriorMove.label(WarriorMove.parse("qez")));
    }
}
