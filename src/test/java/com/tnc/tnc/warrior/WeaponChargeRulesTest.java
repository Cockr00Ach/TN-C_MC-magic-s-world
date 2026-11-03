package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WeaponChargeRulesTest {
    @Test void damageKeepsGrowingWithoutAChargeCap(){
        assertEquals(1,WeaponChargeRules.multiplier(0));assertEquals(2,WeaponChargeRules.multiplier(20));
        assertEquals(61,WeaponChargeRules.multiplier(1200));
        assertTrue(WeaponChargeRules.multiplier(120000)>WeaponChargeRules.multiplier(1200));
        assertTrue(Double.isFinite(WeaponChargeRules.multiplier(Long.MAX_VALUE)));
        assertTrue(WeaponChargeRules.multiplier(Long.MAX_VALUE)>1e15);
    }
    @Test void everyFamilyHasAHoldPose(){for(var f:WarriorWeaponFamily.values())assertNotNull(WeaponChargeRules.holdAnimation(f));}
}
