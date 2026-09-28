package com.tnc.tnc.magic;

import com.tnc.tnc.magic.water.SeaGodSwordRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeaGodSwordRulesTest {
    @Test void swordImpactsAtSevenSecondsAndHasFiniteVisualLife() {
        assertEquals(7*20,SeaGodSwordRules.IMPACT_TICK);
        assertFalse(SeaGodSwordRules.visible(99.99));
        assertTrue(SeaGodSwordRules.visible(140));
        assertFalse(SeaGodSwordRules.visible(180));
        assertEquals(0,SeaGodSwordRules.opacity(99));
        assertEquals(0,SeaGodSwordRules.opacity(180));
        assertEquals(1,SeaGodSwordRules.opacity(140));
    }
    @Test void giantSwordAcceleratesDownwardAndNeverPassesBelowTheGround() {
        assertEquals(64,SeaGodSwordRules.LENGTH);
        assertEquals(64,SeaGodSwordRules.tipHeight(124));
        assertEquals(0,SeaGodSwordRules.tipHeight(140));
        assertEquals(0,SeaGodSwordRules.tipHeight(160));
        double previous=SeaGodSwordRules.tipHeight(124),previousStep=0;
        for(int age=125;age<=140;age++) {
            double height=SeaGodSwordRules.tipHeight(age),step=previous-height;
            assertTrue(height>=0&&height<=previous);
            assertTrue(step>=previousStep);previousStep=step;previous=height;
        }
        assertEquals(60,SeaGodSwordRules.DAMAGE);
    }
}
