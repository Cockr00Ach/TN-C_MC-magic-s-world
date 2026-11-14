package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 法力护盾的纯逻辑：叠层上限、60 秒独立计时、挡一次碎一层。
 *
 * <p>只钉数组运算，不碰世界 —— 实机效果（粒子/音效/真的挡住伤害）属于 gametest 范畴。
 */
class ManaShieldTest {

    private static final long T0 = 1000L;

    @Test
    void startsEmptyAndAddsUp() {
        long[] s = new long[0];
        assertEquals(0, ManaShield.active(s, T0));
        s = ManaShield.add(s, T0);
        assertEquals(1, ManaShield.active(s, T0));
        s = ManaShield.add(s, T0);
        s = ManaShield.add(s, T0);
        assertEquals(3, ManaShield.active(s, T0), "最多三层");
    }

    @Test
    void fourthCastRefreshesInsteadOfStacking() {
        long[] s = new long[0];
        s = ManaShield.add(s, T0);                 // 到期 T0+1200
        s = ManaShield.add(s, T0 + 100);           // T0+1300
        s = ManaShield.add(s, T0 + 200);           // T0+1400
        s = ManaShield.add(s, T0 + 300);           // 第 4 次：整个数组仍是 3 层
        assertEquals(3, s.length, "不能超过 CAP");
        assertTrue(s[0] >= T0 + 1200, "第 4 次应该刷新最早到期的那层，而不是什么都不做");
    }

    @Test
    void oneHitConsumesExactlyOneLayer() {
        long[] s = ManaShield.add(ManaShield.add(ManaShield.add(new long[0], T0), T0), T0);
        assertEquals(3, s.length);
        s = ManaShield.consume(s, T0);
        assertEquals(2, ManaShield.active(s, T0), "挡一下碎一层");
        s = ManaShield.consume(s, T0);
        s = ManaShield.consume(s, T0);
        assertEquals(0, ManaShield.active(s, T0), "三层都用完了");
        assertEquals(0, ManaShield.consume(new long[0], T0).length, "空数组消耗不会炸");
    }

    @Test
    void layersExpireIndependentlyAfterSixtySeconds() {
        long[] s = new long[0];
        s = ManaShield.add(s, T0);                                  // T0+1200
        s = ManaShield.add(s, T0 + 400);                            // T0+1600
        assertEquals(2, ManaShield.active(s, T0 + 1199), "59.95 秒时还在");
        assertEquals(1, ManaShield.active(s, T0 + 1201), "第一层到期，第二层还在");
        assertEquals(0, ManaShield.active(s, T0 + 1601), "都到点了");
        assertEquals(0, ManaShield.prune(s, T0 + 1601).length, "过期的层要被剔掉");
    }

    @Test
    void durationIsSixtySeconds() {
        assertEquals(1200, ManaShield.DURATION_TICKS, "60 秒 = 1200 tick");
        assertEquals(3, ManaShield.MAX_STACKS);
    }
}
