package com.tnc.tnc.warrior;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WarriorUnifiedSlotsGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void everyWeaponCastsAttackParryAndBreakerUsingSharedInputs(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());data.set(p.getUUID(),10000);
        var at=h.absolutePos(new net.minecraft.core.BlockPos(5,3,5));p.setPos(at.getX(),at.getY(),at.getZ());
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.ARROW,64));
        try {
            for(var item:new Item[]{Items.IRON_SWORD,TNMod.GREATSWORD.get(),TNMod.HAMMER.get(),TNMod.SPEAR.get(),TNMod.BOW.get()}){
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
                var family=WarriorWeapons.family(p.getMainHandItem());
                for(int slot=0;slot<3;slot++){
                    var skill=WarriorCombos.bySlot(family,slot);var state=WarriorComboService.state(p.getUUID());state.tracker.clear();state.heldSkill=null;
                    net.minecraft.world.entity.monster.Zombie target=null;
                    if(slot==2){target=net.minecraft.world.entity.EntityType.ZOMBIE.create(p.level());target.setNoAi(true);
                        target.setPos(p.getX(),p.getY(),p.getZ()+2.9);p.setYRot(0);p.setXRot(0);p.serverLevel().addFreshEntity(target);}
                    for(int i=0;i<skill.length();i++){
                        state.lastInput=Long.MIN_VALUE;WarriorComboService.input(p,skill.sequence().get(i));
                        if(skill.holdAt(i)){state.lastInput=Long.MIN_VALUE;WarriorComboService.input(p,skill.sequence().get(i),true);}
                    }
                    h.assertTrue(state.cooldowns.containsKey(skill.id()),family+" slot "+slot+" really casts");
                    if(slot==1){h.assertTrue(state.heldSkill!=null&&state.stanceUntil>WarriorComboService.gameTime(p),family+" enters sustained parry, not bow charge");
                        WarriorComboService.input(p,WarriorMove.RIGHT,WarriorComboInputPacket.RELEASE);}
                    if(slot==2){h.assertTrue(skill.role()==WarriorComboRole.BREAKER&&skill.interrupt(),family+" has real breaker behavior");
                        h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),family+" breaker actually staggers a target at 2.9 blocks");target.discard();}
                }
            }
            h.succeed();
        } finally {p.discard();}
    }
}
