package com.tnc.tnc.magic.water;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterCombatRulesTest {
    @Test void zeroDefaultSpellPowerStillDealsBaseDamage() {
        assertEquals(1,WaterSpellRules.powerMultiplier(0));
        assertEquals(4,WaterSpellRules.damage(1)*WaterSpellRules.powerMultiplier(0));
        assertEquals(8,WaterSpellRules.damage(2)*WaterSpellRules.powerMultiplier(0));
    }
    @Test void equipmentPowerRemainsFiniteAndScaling() {
        assertEquals(3,WaterSpellRules.powerMultiplier(3));
        assertEquals(1,WaterSpellRules.powerMultiplier(Double.NaN));
        assertEquals(10000,WaterSpellRules.powerMultiplier(Double.MAX_VALUE));
    }
}
