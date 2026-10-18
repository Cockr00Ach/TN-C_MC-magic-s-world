package com.tnc.tnc.magic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DarkSiphonRulesTest {
    @Test void higherTiersExpandRadiusAndAddVisibleStreamsWithinABoundedBudget() {
        for(int tier=1;tier<=5;tier++) {
            assertTrue(DarkSiphonRules.targets(tier)*DarkSiphonRules.strands(tier)*DarkSiphonRules.BEADS<=240);
            if(tier>1) {
                assertTrue(DarkSiphonRules.range(tier)>DarkSiphonRules.range(tier-1));
                assertTrue(DarkSiphonRules.strands(tier)>DarkSiphonRules.strands(tier-1));
                assertTrue(DarkSiphonRules.targets(tier)*DarkSiphonRules.strands(tier)>
                        DarkSiphonRules.targets(tier-1)*DarkSiphonRules.strands(tier-1));
            }
        }
    }
    @Test void bloodTravelsFromEnemyToCaster() {
        assertTrue(DarkSiphonRules.flow(5,0)>DarkSiphonRules.flow(1,0));
        for(int age=0;age<60;age++)for(int bead=0;bead<DarkSiphonRules.BEADS;bead++)
            assertTrue(DarkSiphonRules.flow(age,bead)>=0&&DarkSiphonRules.flow(age,bead)<1);
    }
    @Test void handsIncreaseAndHaveSeparateStartsAndReleaseBeats() {
        for(int tier=1;tier<=5;tier++) {
            if(tier>1)assertTrue(NightHandReleaseRules.count(tier)>NightHandReleaseRules.count(tier-1));
            for(double half:new double[]{.8,1.7,3.7,8.2,18})assertTrue(NightHandReleaseRules.spacing(half)>2*half);
        }
        assertEquals(0,NightHandReleaseRules.delay(0,false));
        for(int slot=0;slot<7;slot++){assertEquals(0,NightHandReleaseRules.delay(slot,true));assertEquals(0,NightHandReleaseRules.delay(slot,false));}
    }
}
