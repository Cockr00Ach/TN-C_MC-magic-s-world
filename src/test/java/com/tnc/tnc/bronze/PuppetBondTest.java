package com.tnc.tnc.bronze;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PuppetBondTest {
    @Test void fusionRequiresFourDistinctOriginalWeaponsAtExactlyTen(){
        var kinds=java.util.List.of(BronzeProfile.GREATSWORD,BronzeProfile.SPEAR,BronzeProfile.DAO,BronzeProfile.DAGGER);
        assertTrue(BronzeFusionRecipe.ready(kinds,java.util.List.of(10,10,10,10)));
        assertFalse(BronzeFusionRecipe.ready(kinds,java.util.List.of(10,10,9,10)));
        assertFalse(BronzeFusionRecipe.ready(java.util.List.of(BronzeProfile.GREATSWORD,BronzeProfile.SPEAR,BronzeProfile.DAO,BronzeProfile.DAO),java.util.List.of(10,10,10,10)));
        assertFalse(BronzeFusionRecipe.ready(java.util.List.of(BronzeProfile.GREATSWORD,BronzeProfile.SPEAR,BronzeProfile.DAO,BronzeProfile.HALBERD),java.util.List.of(10,10,10,10)));
        assertFalse(BronzeFusionRecipe.ready(kinds.subList(0,3),java.util.List.of(10,10,10)));
    }
    @Test void onePerOwnerDoesNotBlockOtherPlayers(){
        var data=new PuppetBondData();var owner=UUID.randomUUID();var other=UUID.randomUUID();var puppet=UUID.randomUUID();
        assertTrue(data.reserve(owner,puppet,100));assertFalse(data.reserve(owner,UUID.randomUUID(),101));
        assertTrue(data.reserve(other,UUID.randomUUID(),101));assertTrue(data.owns(owner,puppet));
    }
    @Test void cooldownStartsAtActualDisappearanceRatherThanFatalHit(){
        var data=new PuppetBondData();var owner=UUID.randomUUID();var puppet=UUID.randomUUID();data.reserve(owner,puppet,0);
        data.fading(owner,puppet,130);assertTrue(data.expired(129).isEmpty());assertFalse(data.available(owner,129));
        data.vanished(owner,puppet,130);assertEquals(2530,data.bond(owner).readyAt());
        assertFalse(data.available(owner,2529));assertTrue(data.available(owner,2530));
    }
    @Test void staleEntityCannotClearAnotherSummonOrRestartCooldown(){
        var data=new PuppetBondData();var owner=UUID.randomUUID();var first=UUID.randomUUID();var second=UUID.randomUUID();
        data.reserve(owner,first,0);data.vanished(owner,first,100);data.vanished(owner,first,200);assertEquals(2500,data.bond(owner).readyAt());
        assertTrue(data.reserve(owner,second,2500));data.vanished(owner,first,2600);assertTrue(data.owns(owner,second));
    }
    @Test void tenMinuteLifetimeAndTwoMinuteCooldownUseOnlyGameTicks(){
        var data=new PuppetBondData();var owner=UUID.randomUUID();var puppet=UUID.randomUUID();data.reserve(owner,puppet,500);
        assertTrue(data.expired(12499).isEmpty());assertEquals(1,data.expired(12500).size());
        data.vanished(owner,puppet,12500);assertFalse(data.available(owner,14899));assertTrue(data.available(owner,14900));
        assertEquals(200,PuppetBondData.health(20));assertEquals(1500,PuppetBondData.health(150));
    }
    @Test void logoutChunkUnloadingAndReloadKeepOwnershipAndCooldown(){
        var data=new PuppetBondData();var owner=UUID.randomUUID();var puppet=UUID.randomUUID();data.reserve(owner,puppet,100);
        var loaded=PuppetBondData.load(data.save(new CompoundTag()));assertTrue(loaded.owns(owner,puppet));assertFalse(loaded.available(owner,200));
        loaded.vanished(owner,puppet,350);var reloaded=PuppetBondData.load(loaded.save(new CompoundTag()));assertFalse(reloaded.available(owner,2749));assertTrue(reloaded.available(owner,2750));
    }
    @Test void aiMovesAreActualExistingSwordWarriorSkills(){
        var skills=com.tnc.tnc.warrior.WarriorBronzeBridge.puppetSkills();assertFalse(skills.isEmpty());
        for(var skill:skills){assertEquals(com.tnc.tnc.warrior.WarriorWeaponFamily.LONGSWORD,skill.family());assertTrue(com.tnc.tnc.warrior.WarriorCombos.all().contains(skill));assertFalse(skill.ranged());}
    }
}
