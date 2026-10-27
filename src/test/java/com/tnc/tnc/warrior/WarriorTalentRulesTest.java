package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorTalentRulesTest {
    @Test void noTalentNeverGrowsEvenWithHugeRewards() {
        assertEquals(0,WarriorTalentRules.gain(Integer.MAX_VALUE,0));
        assertEquals(0,WarriorTalentRules.cap(0));
        assertEquals(0,WarriorTalentRules.gain(-50,6));
    }
    @Test void higherTalentStrictlyImprovesSameTrainingAndCap() {
        for(int t=1;t<=6;t++) {
            assertTrue(WarriorTalentRules.gain(120,t)>WarriorTalentRules.gain(120,t-1));
            assertTrue(WarriorTalentRules.cap(t)>WarriorTalentRules.cap(t-1));
        }
        assertEquals(60,WarriorTalentRules.gain(120,1));
        assertEquals(360,WarriorTalentRules.gain(120,6));
    }
    @Test void fractionalRewardCarryIsIndependentOfRewardSplitting() {
        for(int talent=0;talent<=6;talent++) {
            long sum=0;int half=0;
            for(int i=0;i<7;i++) {
                long units=WarriorTalentRules.halfUnits(3,talent,half);
                sum+=units/2;half=(int)(units%2);
            }
            assertEquals(WarriorTalentRules.halfUnits(21,talent,0)/2,sum);
        }
        assertEquals(0,WarriorTalentRules.halfUnits(Integer.MAX_VALUE,0,1));
        assertTrue(WarriorTalentRules.halfUnits(Integer.MAX_VALUE,6,1)>Integer.MAX_VALUE);
    }
}
