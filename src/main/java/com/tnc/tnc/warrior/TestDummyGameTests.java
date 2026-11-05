package com.tnc.tnc.warrior;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class TestDummyGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void chargedArrowCanExceedVanillaIntegerDamageCeiling(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.BOW.get()));
        var dummy=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(5,3,5));
        var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),p);arrow.setDeltaMovement(0,0,3);arrow.getPersistentData().putDouble("TncZChargeDamage",1e12);
        try {
            dummy.hurt(p.damageSources().arrow(arrow,p),1);
            h.assertTrue(dummy.totalDamage()>Integer.MAX_VALUE,"Charge damage must not stop at vanilla arrow's integer ceiling");
            h.assertTrue(dummy.isAlive()&&dummy.hitCount()==1,"Dummy records huge arrow damage and survives");h.succeed();
        } finally {dummy.discard();arrow.discard();p.discard();}
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void dummyRecordsRealRapidAndLethalDamageWithoutDyingOrRewards(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());data.set(p.getUUID(),1000);
        var d=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(5,3,5));
        try {
            h.assertTrue(d.hurt(p.damageSources().playerAttack(p),6),"Dummy accepts real melee damage");
            h.assertTrue(d.hurt(p.damageSources().playerAttack(p),8),"Immediate second hit is not lost to immunity frames");
            h.assertTrue(d.hitCount()==2&&Math.abs(d.totalDamage()-14)<.01,"Actual damage is not capped to instructor's one point");
            d.hurt(p.damageSources().playerAttack(p),100000);
            h.assertTrue(d.isAlive()&&!d.isRemoved()&&d.getHealth()==d.getMaxHealth(),"Overkill cannot destroy test dummy");
            h.assertTrue(Math.abs(d.totalDamage()-100014)<.01,"Overkill is fully recorded");
            h.assertTrue(data.points(p.getUUID())==1000,"Dummy cannot farm training rewards");
            var saved=new net.minecraft.nbt.CompoundTag();d.save(saved);var restored=WarriorEntities.TEST_DUMMY.get().create(h.getLevel());restored.load(saved);
            h.assertTrue(restored.hitCount()==3&&restored.totalDamage()==d.totalDamage(),"Statistics survive reload");restored.discard();
            d.mobInteract(p,InteractionHand.MAIN_HAND);h.assertTrue(d.hitCount()==0&&d.totalDamage()==0,"Right click resets stats");
            h.succeed();
        } finally {d.discard();p.discard();}
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void dummyRejectsOtherOwnersAndStaysAnchored(GameTestHelper h){
        var owner=AdventureGameTests.player(h);var other=AdventureGameTests.player(h);var d=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(5,3,5));
        d.getPersistentData().putUUID("DummyOwner",owner.getUUID());
        try {
            h.assertTrue(!d.hurt(other.damageSources().playerAttack(other),10)&&d.hitCount()==0,"Other players cannot pollute owned stats");
            var pos=d.position();d.knockback(5,1,0);d.tick();d.setDeltaMovement(3,2,1);d.tick();
            h.assertTrue(d.position().distanceTo(pos)<.01,"Target stays on its anchor");h.succeed();
        } finally {d.discard();owner.discard();other.discard();}
    }
}
