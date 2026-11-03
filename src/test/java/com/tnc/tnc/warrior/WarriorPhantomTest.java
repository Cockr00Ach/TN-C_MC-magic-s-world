package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 虚影倍率：磨砺 0 时和真剑等大，练满时 1.90 倍（2026-11-03 加倍），且一路只增不减。 */
class WarriorPhantomTest {

    private static final int CAP = 3000;
    private static final double EPS = 1.0E-9;

    @Test
    void untrainedPlayerSeesAShadowTheSameSizeAsTheBlade() {
        assertEquals(1.0, WarriorPhantom.scale(0, CAP), EPS,
                "磨砺 0 时虚影要和真剑等大");
    }

    @Test
    void fullyTemperedPlayerReachesTheMaximumGrowth() {
        assertEquals(1.0 + WarriorPhantom.MAX_GROWTH, WarriorPhantom.scale(CAP, CAP), EPS,
                "练满时虚影要到最大倍率");
        assertEquals(1.90, WarriorPhantom.scale(CAP, CAP), EPS,
                "最大倍率就是 1.90 倍：练满时虚影比真剑长出近一倍");
    }

    @Test
    void growthIsLinearInPoints() {
        assertEquals(1.45, WarriorPhantom.scale(CAP / 2, CAP), EPS, "一半磨砺 = 一半增量");
    }

    @Test
    void scaleIsClampedAndSafe() {
        assertEquals(1.0, WarriorPhantom.scale(500, 0), EPS, "没有上限（没天赋）时不要除零");
        assertEquals(1.0 + WarriorPhantom.MAX_GROWTH, WarriorPhantom.scale(CAP * 4, CAP), EPS,
                "点数超过上限要处理成满倍");
        assertEquals(1.0, WarriorPhantom.scale(-100, CAP), EPS, "负数当 0");
    }

    @Test
    void growthNeverShrinks() {
        double last = 0;
        for (int points = 0; points <= CAP; points += 100) {
            double scale = WarriorPhantom.scale(points, CAP);
            assertTrue(scale >= last, "磨砺增加时虚影只能变大");
            last = scale;
        }
    }
}
