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
}
