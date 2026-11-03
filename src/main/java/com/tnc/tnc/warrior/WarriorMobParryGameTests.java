package com.tnc.tnc.warrior;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WarriorMobParryGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void brokenHoldCannotReopenStanceFromKeepalivePackets(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());
        var state=WarriorComboService.state(p.getUUID());var skill=WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,1);
        state.heldSkill=skill;state.stanceUntil=WarriorComboService.gameTime(p)+40;state.counterAt=WarriorComboService.gameTime(p)+2;
        try {
            WarriorComboService.stagger(p,40);
            h.assertTrue(state.heldSkill==null&&state.stanceUntil==Long.MIN_VALUE&&state.counterAt==Long.MIN_VALUE,"Stagger consumes the hold and pending counter");
            WarriorComboService.input(p,WarriorMove.RIGHT,WarriorComboInputPacket.KEEPALIVE);
            h.assertTrue(state.stanceUntil==Long.MIN_VALUE,"Late keepalive cannot resurrect broken stance");
            // Even a stale held record is refused while staggered.
            state.heldSkill=skill;
            WarriorComboService.input(p,WarriorMove.RIGHT,WarriorComboInputPacket.KEEPALIVE);
            h.assertTrue(state.heldSkill==null&&state.stanceUntil<=WarriorComboService.gameTime(p),"Stun blocks all hold renewal");
            h.succeed();
        } finally {p.discard();}
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void mobStrikeConsumesStanceThenSweepsInAllDirections(GameTestHelper h){
        var p=AdventureGameTests.player(h);var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,3,5));p.setPos(pos.getX(),pos.getY(),pos.getZ());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());
        var front=h.spawn(EntityType.ZOMBIE,new net.minecraft.core.BlockPos(5,3,7));front.setNoAi(true);
        var rear=h.spawn(EntityType.ZOMBIE,new net.minecraft.core.BlockPos(5,3,3));rear.setNoAi(true);
        var giant=h.spawn(EntityType.GIANT,new net.minecraft.core.BlockPos(6,3,5));giant.setNoAi(true);
        var state=WarriorComboService.state(p.getUUID());state.stanceUntil=p.level().getGameTime()+40;
        try {
            var large=new LivingAttackEvent(p,p.damageSources().mobAttack(giant),4);WarriorComboService.mobParry(large);
            h.assertTrue(!large.isCanceled(),"Four-block-plus monsters do not trigger counter");
            var hit=new LivingAttackEvent(p,p.damageSources().mobAttack(front),4);WarriorComboService.mobParry(hit);
            h.assertTrue(hit.isCanceled()&&state.stanceUntil==Long.MIN_VALUE,"Mob melee is blocked and consumes stance");
            h.assertTrue(state.counterAt==p.level().getGameTime()+2,"Contact precedes counter by two ticks");
            var again=new LivingAttackEvent(p,p.damageSources().mobAttack(front),4);WarriorComboService.mobParry(again);
            h.assertTrue(!again.isCanceled(),"One stance cannot repeatedly trigger");
            WarriorComboService.mobCounterSweep(p);
            h.assertTrue(front.getDeltaMovement().y>=.7&&rear.getDeltaMovement().y>=.7,"Sweep launches mobs in front and behind");
            h.assertTrue(giant.getDeltaMovement().lengthSqr()<.01,"Giant is excluded from launch");
            h.succeed();
        } finally {state.counterAt=Long.MIN_VALUE;p.discard();front.discard();rear.discard();giant.discard();}
    }
}
