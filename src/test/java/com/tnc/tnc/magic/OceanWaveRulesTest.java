package com.tnc.tnc.magic;

import com.tnc.tnc.magic.water.OceanWaveRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OceanWaveRulesTest {
    @Test void oceanLastsThirtySecondsWithBoundedOutwardFronts() {
        assertEquals(600,OceanWaveRules.LIFE);assertEquals(64,OceanWaveRules.RADIUS);assertEquals(40,OceanWaveRules.HEIGHT);
        for(int age=0;age<600;age++) {
            int fronts=0;
            for(int i=0;i<OceanWaveRules.MAX_FRONTS;i++) {
                double r=OceanWaveRules.frontRadius(age,i);if(r<0)continue;
                fronts++;assertTrue(r<=64);assertTrue(OceanWaveRules.frontHeight(r)<=40);
            }
            // 2026-10-10：作者要求"增加频率跟密度" ⇒ PERIOD 16→10、MAX_FRONTS 4→6 ✗。
            // 这条断言本意是"波前数量有限且始终 ≥1"，原来把上限写死成 4 了 ✗ ⇒ 改成读常量 ✓。
            assertTrue(fronts>0&&fronts<=OceanWaveRules.MAX_FRONTS,"Continuous finite front count");
        }
        assertEquals(-1,OceanWaveRules.frontRadius(600,0));
        assertTrue(OceanWaveRules.frontRadius(9,0)>OceanWaveRules.frontRadius(8,0));
    }
    @Test void crestActuallyCurlsAndStaysInsideTheFiniteDomain() {
        double turn=OceanWaveRules.profileRadius(9,30,40,64),lip=OceanWaveRules.profileRadius(11,30,40,64);
        assertTrue(turn>lip,"Wave lip curls back behind the forward turning point");
        assertTrue(OceanWaveRules.profileHeight(7,40)>OceanWaveRules.profileHeight(11,40));
        for(int i=0;i<OceanWaveRules.profilePoints();i++)assertTrue(OceanWaveRules.profileRadius(i,62,40,64)<=64);
    }
    @Test void damageFollowsPresentFrontsNotFutureWaterOrUnderground() {
        assertFalse(OceanWaveRules.hits(56,0,1));
        assertTrue(OceanWaveRules.hits(56,0,40));
        assertFalse(OceanWaveRules.hits(65,0,40));
        assertFalse(OceanWaveRules.hits(56,-4,40));
        assertFalse(OceanWaveRules.hits(56,42,40));
        assertFalse(OceanWaveRules.hits(56,0,600));
    }
    @Test void flyingTargetAboveLowTrailingFootIsNotHitByFullCrestHeight() {
        assertFalse(OceanWaveRules.hits(18,39,20),"Low sloping foot cannot hit a flying target at full crest height");
        assertTrue(OceanWaveRules.hits(18,0,20),"The same trailing foot still hits a grounded target");
    }
}
