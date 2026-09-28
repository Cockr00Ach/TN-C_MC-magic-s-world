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
    @Test void continuousBarrageHasEightDistinctStrikesAndNoMoreThanTwoVisibleSwords() {
        assertEquals(8,SeaGodSwordRules.COUNT);
        int impacts=0;
        for(int age=0;age<=600;age++) {
            int index=SeaGodSwordRules.impactIndex(age);
            if(index>=0){assertEquals(140+impacts*60,age);assertEquals(impacts++,index);}
            int visible=0;
            for(int i=0;i<SeaGodSwordRules.COUNT;i++)if(SeaGodSwordRules.visible(age-i*SeaGodSwordRules.INTERVAL))visible++;
            assertTrue(visible<=2);
            if(age>=100&&age<600)assertTrue(visible>=1,"No empty interval in the barrage");
        }
        assertEquals(8,impacts);
        assertFalse(SeaGodSwordRules.cue(580,SeaGodSwordRules.APPEAR_TICK),"No ninth warning for a strike after field expiration");
    }
}
