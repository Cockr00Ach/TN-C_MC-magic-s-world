package com.tnc.tnc.equipment;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class PurityAmuletGameTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h){
        return com.tnc.tnc.adventure.AdventureGameTests.player(h);
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="purity_amulet")
    public static void equippedAmuletCleansesOldEffectsAndRejectsNewDebuffsButKeepsBuffs(GameTestHelper h){
        var p=player(h);
        p.addEffect(new MobEffectInstance(MobEffects.POISON,200));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,200));
        p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(PurityAmuletRegistration.ITEM));
        PurityAmulet.cleanse(p);
        h.assertTrue(!p.hasEffect(MobEffects.POISON)&&p.hasEffect(MobEffects.MOVEMENT_SPEED),"Existing poison is removed and speed remains");
        for(var effect:net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValues()){
            if(!PurityAmulet.negative(effect))continue;
            h.assertTrue(!p.addEffect(new MobEffectInstance(effect,200)),"All registered harmful/neutral effects are rejected: "+effect.getDescriptionId());
        }
        h.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,200)),"New beneficial effects still apply");
        p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.removeAllEffects();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="purity_amulet")
    public static void inventoryCopiesDoNotProtectAndRemovingTheAmuletRestoresNormalEffects(GameTestHelper h){
        var p=player(h);p.getInventory().setItem(0,new ItemStack(PurityAmuletRegistration.ITEM));
        h.assertTrue(!PurityAmulet.equipped(p)&&p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,100)),"An inventory copy does not grant immunity");
        p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(PurityAmuletRegistration.ITEM));
        PurityAmulet.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,p));
        h.assertTrue(!p.hasEffect(MobEffects.WEAKNESS),"Equipping cleanses at the start of the player tick");
        p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        h.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,100)),"Unequipping immediately restores normal status application");
        p.removeAllEffects();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="purity_amulet")
    public static void amuletAlsoRejectsTheGiantsForcedSiphonMovement(GameTestHelper h){
        var p=player(h);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(PurityAmuletRegistration.ITEM));
        var boss=com.tnc.tnc.npc.TNNpcs.DARK_GIANT.get().create(h.getLevel());
        p.setPos(boss.position().add(40,0,0));var motion=new net.minecraft.world.phys.Vec3(.2,0,.1);p.setDeltaMovement(motion);
        com.tnc.tnc.boss.DarkGiantSiphonControl.apply(boss,p);
        h.assertTrue(p.getDeltaMovement().equals(motion)&&!p.hasEffect(MobEffects.LEVITATION),"The amulet prevents both forced pull and levitation");
        p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);boss.discard();h.succeed();
    }
}
