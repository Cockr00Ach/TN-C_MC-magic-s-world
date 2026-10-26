package com.tnc.tnc.warrior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemperingRulesTest {
    @Test void reductionHasDiminishingReturnsAndCannotGrantImmunity() {
        assertEquals(0, TemperingRules.reduction(0));
        assertEquals(.10, TemperingRules.reduction(1000), 1e-9);
        assertEquals(.25, TemperingRules.reduction(10000), 1e-9);
        double previousGain = 1;
        for (int p = 100; p <= 10000; p += 100) {
            double gain = TemperingRules.reduction(p) - TemperingRules.reduction(p-100);
            assertTrue(gain > 0 && gain < previousGain); previousGain = gain;
        }
        assertEquals(.25, TemperingRules.reduction(Integer.MAX_VALUE), 1e-9);
    }
    @Test void corruptedValuesAndOverflowStayBounded() {
        assertEquals(0, TemperingRules.clamp(Integer.MIN_VALUE));
        assertEquals(10000, TemperingRules.add(9999, Integer.MAX_VALUE));
        assertEquals(100, TemperingRules.add(100, -1));
        assertEquals(0, TemperingRules.killReward(Float.NaN));
        assertEquals(20, TemperingRules.killReward(100000));
    }
    @Test void rankBoundariesAreStable() {
        assertEquals(0, TemperingRules.rank(199));
        assertEquals(1, TemperingRules.rank(200));
        assertEquals(2, TemperingRules.rank(1000));
        assertEquals(3, TemperingRules.rank(3000));
        assertEquals(4, TemperingRules.rank(10000));
    }
}
