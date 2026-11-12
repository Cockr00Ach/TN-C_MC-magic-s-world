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
    @Test void chargeGrowsSmoothlyAndNeverExceedsEightTimes(){
        assertEquals(1,WeaponChargeRules.visualScale(-20));assertEquals(4.5f,WeaponChargeRules.visualScale(30));
        assertEquals(8,WeaponChargeRules.visualScale(60));assertEquals(8,WeaponChargeRules.visualScale(60000));
    }
    @Test void differentWeaponShapesHaveDifferentReleaseRoutes(){
        assertEquals(WarriorAnimation.GS_SMASH,WeaponChargeRules.profile("han_iron_jian",WarriorWeaponFamily.LONGSWORD).release());
        assertEquals(360,WeaponChargeRules.profile("doom_scythe",WarriorWeaponFamily.GREATSWORD).arc());
        assertNotEquals(WeaponChargeRules.profile("red_tassel_spear",WarriorWeaponFamily.SPEAR),WeaponChargeRules.profile("qimei_staff",WarriorWeaponFamily.SPEAR));
    }
    @Test void everyFamilyHasAHoldPose(){for(var f:WarriorWeaponFamily.values())assertNotNull(WeaponChargeRules.holdAnimation(f));}
}
