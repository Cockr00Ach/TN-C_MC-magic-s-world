package com.tnc.tnc.magic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EarthActionRulesTest {
    @Test void stagedShotsAndSpikeCountsMatchRequestedChains(){
        assertEquals(8,EarthActionRules.shots(3));assertEquals(10,EarthActionRules.shots(4));assertEquals(1,EarthActionRules.shots(5));
        assertEquals(3,EarthActionRules.spikes(2));assertEquals(6,EarthActionRules.spikes(3));assertEquals(36,EarthActionRules.spikes(4));assertEquals(5,EarthActionRules.spikes(5));
    }
    @Test void damageCoefficientsRemainThoseOfTheOriginalSpells(){
        assertEquals(2,EarthActionRules.coefficient(3,3));assertEquals(2.9,EarthActionRules.coefficient(3,5));assertEquals(2.3,EarthActionRules.coefficient(4,5));
    }
}
