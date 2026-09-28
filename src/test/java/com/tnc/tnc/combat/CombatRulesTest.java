package com.tnc.tnc.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    @Test void revivedHealthScalesWithGear() {assertEquals(24,CombatRules.revivedHealth(40),.001);assertEquals(60,CombatRules.revivedHealth(100),.001);}
    @Test void soloPassiveLastsFiveMinutes() {assertFalse(CombatRules.canSoloDown(5999,6000));assertTrue(CombatRules.canSoloDown(6000,6000));}
    @Test void allDownButNotEmptyIsDefeat() {assertTrue(CombatRules.wipe(2,0));assertFalse(CombatRules.wipe(2,1));assertFalse(CombatRules.wipe(0,0));}
}
