package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorUnifiedSlotsTest {
    @Test void firstThreeSlotsShareKeysAndActualCombatRolesAcrossEveryFamily(){
        for(var family:WarriorWeaponFamily.values()){
            if(WarriorCombos.forFamily(family).isEmpty())continue;
            var attack=WarriorCombos.bySlot(family,0);var guard=WarriorCombos.bySlot(family,1);var breaker=WarriorCombos.bySlot(family,2);
            assertEquals("ZZZ",attack.compactLabel());assertEquals(WarriorChain.ATTACK,attack.chain());
            assertEquals("EQE+",guard.compactLabel());assertEquals(WarriorComboRole.STANCE,guard.role());assertTrue(guard.guardTicks()>0);assertFalse(guard.ranged());
            assertEquals("EQZ",breaker.compactLabel());assertEquals(WarriorComboRole.BREAKER,breaker.role());assertTrue(breaker.interrupt());assertFalse(breaker.ranged());
            assertEquals(3.0,breaker.radius());assertEquals(.65,breaker.knockback());
            assertEquals(0,attack.backstep());assertEquals(0,guard.backstep());
        }
    }
    @Test void laterSlotsRetainWeaponSpecificFunctionsAndCounts(){
        assertEquals(5,WarriorCombos.forFamily(WarriorWeaponFamily.SPEAR).size());
        assertEquals(6,WarriorCombos.forFamily(WarriorWeaponFamily.BOW).size());
        assertEquals(WarriorChain.EVADE,WarriorCombos.bySlot(WarriorWeaponFamily.SPEAR,4).chain());
        assertEquals(WarriorChain.ATTACK,WarriorCombos.bySlot(WarriorWeaponFamily.HAMMER,4).chain());
    }
}
