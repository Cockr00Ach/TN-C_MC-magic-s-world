package com.tnc.tnc.journey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开局配点规则：作者 2026-11-15 口述的四条 —— 亲和共 12 点、每系 1~6、
 * 最多一个 6、最高亲和 + 战士天赋 ≤ 6。
 *
 * <p>注意：在「7 系 × 至少 1 点 × 总计 12 点」这三个条件同时成立时，
 * 两个 6 在数学上不可能出现（6+6+5=17 > 12），这条规则是<b>防御性</b>的；
 * 测试里仍然钉住它，防止以后有人把 MIN 改成 0 而没人发现。
 */
class JourneyProfileRulesTest {

    private static int[] of(int... v) {
        return v;
    }

    @Test
    void defaultsAreLegal() {
        int[] d = JourneyProfileRules.defaults();
        assertEquals(JourneyProfileRules.TOTAL, JourneyProfileRules.sum(d), "默认分配必须刚好 12 点");
        assertNull(JourneyProfileRules.validate(d, JourneyProfileRules.defaultTalent()),
                "默认配点必须合法，否则玩家一进来就被卡住");
        assertTrue(JourneyProfileRules.max(d) + JourneyProfileRules.defaultTalent()
                <= JourneyProfileRules.BUDGET, "默认配点不能超过预算");
    }

    @Test
    void totalMustBeExactlyTwelve() {
        assertNotNull(JourneyProfileRules.validate(of(1, 1, 1, 1, 1, 1, 1), 0), "只有 7 点：不合法");
        assertNotNull(JourneyProfileRules.validate(of(3, 3, 3, 3, 3, 1, 1), 0), "15 点：不合法");
        assertNull(JourneyProfileRules.validate(of(2, 2, 2, 2, 2, 1, 1), 2), "12 点：合法");
    }

    @Test
    void aSingleSixIsAllowed() {
        // 6 + 五个 1 = 11，还差 1 点 → 必须再补一个 1：6,1,1,1,1,1,1 正好 12
        assertNull(JourneyProfileRules.validate(of(6, 1, 1, 1, 1, 1, 1), 0),
                "只有一个 6、总计 12、天赋 0 → 合法（6+0=6 刚好卡住预算）");
    }

    @Test
    void twoSixesAreRejected() {
        // 6+6 再用 5 个 1 补满最小：总和 17 ⇒ 同时违反「总和 12」和「只能一个 6」，必须被拒
        assertNotNull(JourneyProfileRules.validate(of(6, 6, 1, 1, 1, 1, 1), 0), "两个 6：不合法");
    }

    @Test
    void affinityPlusTalentMustStayWithinBudget() {
        assertNull(JourneyProfileRules.validate(of(4, 2, 2, 1, 1, 1, 1), 2), "4+2=6：刚好卡住上限");
        assertNotNull(JourneyProfileRules.validate(of(5, 2, 1, 1, 1, 1, 1), 2), "5+2=7：超预算");
        assertNotNull(JourneyProfileRules.validate(of(2, 2, 2, 2, 2, 1, 1), 5), "2+5=7：超预算");
    }

    @Test
    void perElementRangeIsOneToSix() {
        assertNotNull(JourneyProfileRules.validate(of(0, 2, 2, 2, 2, 2, 2), 0), "0 点亲和：不合法");
        assertNotNull(JourneyProfileRules.validate(of(7, 1, 1, 1, 1, 1, 0), 0), "7 点亲和：不合法");
    }

    @Test
    void talentRangeIsZeroToSix() {
        assertNotNull(JourneyProfileRules.validate(of(2, 2, 2, 2, 2, 1, 1), 7), "天赋 7：不合法");
        assertNotNull(JourneyProfileRules.validate(of(2, 2, 2, 2, 2, 1, 1), -1), "天赋 -1：不合法");
    }

    @Test
    void summaryShowsTheLiveNumbers() {
        String s = JourneyProfileRules.summary(of(2, 2, 2, 2, 2, 1, 1), 2);
        assertTrue(s.contains("12/12"), "总结要显示已分配/总数");
        assertTrue(s.contains("4 / 6"), "总结要显示 最高+天赋 / 预算");
    }
}
