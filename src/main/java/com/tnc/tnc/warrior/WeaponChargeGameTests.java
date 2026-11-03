package com.tnc.tnc.warrior;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.MinecraftForge;
@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WeaponChargeGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=300)
    public static void everyWeaponWaitsForZReleaseIncludingScytheAndBow(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());
        var pos=h.absolutePos(new BlockPos(5,3,3));p.setPos(pos.getX(),pos.getY(),pos.getZ());p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.ARROW,64));
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(5,3,6));target.setPos(p.getX(),p.getY(),p.getZ()+2.5);
        Item[] items={Items.IRON_SWORD,com.tnc.tnc.TNMod.GREATSWORD.get(),com.tnc.tnc.TNMod.HAMMER.get(),com.tnc.tnc.TNMod.SPEAR.get(),com.tnc.tnc.TNMod.DOOM_SCYTHE.get(),com.tnc.tnc.TNMod.BOW.get()};
        for(int n=0;n<items.length;n++){
            final int i=n;int start=1+n*40;
            h.runAfterDelay(start,()->{target.resetStats();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items[i]));WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.CHARGE_START);});
            h.runAfterDelay(start+10,()->WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.HOLD));
            h.runAfterDelay(start+18,()->{WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.KEEPALIVE);h.assertTrue(target.hitCount()==0,"No auto attack while holding weapon "+i);});
            h.runAfterDelay(start+22,()->{
                WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
                if(i<5)h.assertTrue(target.hitCount()==1,"Melee release attacks once: "+i);
                else h.assertTrue(!p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,p.getBoundingBox().inflate(30),a->a.getOwner()==p).isEmpty(),"Bow release fires arrow");
            });
        }
        h.runAfterDelay(240,()->{target.discard();p.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void zHoldNeverAttacksUntilReleaseAndReleaseOnlyHitsOnce(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var pos=h.absolutePos(new BlockPos(5,3,3));p.setPos(pos.getX(),pos.getY(),pos.getZ());p.setYRot(0);p.setXRot(0);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(5,3,6));target.setPos(p.getX(),p.getY(),p.getZ()+2.5);
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.CHARGE_START);
        h.runAfterDelay(10,()->WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.HOLD));
        for(int at:new int[]{20,30})h.runAfterDelay(at,()->{
            WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.KEEPALIVE);
            h.assertTrue(target.hitCount()==0,"Held Z must never auto-attack");
            var event=new net.minecraftforge.event.entity.player.AttackEntityEvent(p,target);MinecraftForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled(),"Vanilla attack is suppressed while charging");
        });
        h.runAfterDelay(38,()->{
            h.assertTrue(target.hitCount()==0,"No attack before key release");
            WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
            h.assertTrue(target.hitCount()==1&&target.totalDamage()>2,"Release delivers one growing hit");
            WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
            h.assertTrue(target.hitCount()==1,"Repeated release cannot double fire");target.discard();p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void damageBeforeHoldThresholdCancelsAndLateHoldCannotRestart(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.CHARGE_START);
        h.assertTrue(WarriorBowCharge.charging(p),"Press prepares charge immediately");
        WarriorBowCharge.damaged(new net.minecraftforge.event.entity.living.LivingDamageEvent(p,p.damageSources().generic(),2));
        h.assertTrue(!WarriorBowCharge.charging(p),"Damage interrupts charge");
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.HOLD);
        h.assertTrue(!WarriorBowCharge.charging(p),"Late hold must not restart interrupted charge");
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
        h.assertTrue(p.getMainHandItem().getDamageValue()==0,"Interrupted release does not attack or spend durability");p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void shortZStillCompletesTheSharedComboWithoutLosingItsPrefix(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());data.set(p.getUUID(),10000);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var state=WarriorComboService.state(p.getUUID());WarriorComboService.input(p,WarriorMove.LEFT);
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.CHARGE_START);
        state.lastInput=Long.MIN_VALUE;WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.TAP);
        state.lastInput=Long.MIN_VALUE;WarriorComboService.input(p,WarriorMove.RIGHT);
        h.assertTrue(state.cooldowns.containsKey("sword_combo_1"),"Short Z preserves Q-Z-E combo");p.discard();h.succeed();
    }
}
