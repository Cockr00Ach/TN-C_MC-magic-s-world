package com.tnc.tnc.world.ink;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncink") @PrefixGameTestTemplate(false)
public final class InkCombatV2GameTests {
    @GameTest(batch="ink_executionEligibility",template="ink_crane_empty",timeoutTicks=20)
    public static void executionEligibilityAndRelease(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);
        var tiger=h.spawn(InkBossRegistration.INK_WINGED_TIGER,new BlockPos(0,90,0));tiger.setNoAi(true);tiger.setNoGravity(true);
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(tiger.position().add(0,0,22));player.setHealth(11);
        h.assertTrue(!tiger.canExecute(player),"More than half health is ineligible");
        player.setHealth(10);h.assertTrue(tiger.canExecute(player),"Half health nearby player is eligible");
        h.assertTrue(tiger.startInkAttack(4,player)&&InkRestraints.held(player),"Execution actually grabs its victim");
        tiger.discard();h.assertTrue(!InkRestraints.held(player),"Removal releases execution grab");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setHealth(20);h.succeed();
    }
    @net.minecraft.gametest.framework.BeforeBatch(batch="ink_executionEligibility")
    public static void isolateExecution(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}
    @GameTest(batch="ink_craneHoldReleasesWhenRemoved",template="ink_crane_empty",timeoutTicks=25)
    public static void craneHoldReleasesWhenRemoved(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);
        var crane=h.spawn(InkBossRegistration.INK_CRANE_BOSS,new BlockPos(0,80,0));crane.setNoAi(true);crane.setNoGravity(true);
        var pig=h.spawn(EntityType.PIG,new BlockPos(35,80,0));pig.setNoAi(true);pig.setNoGravity(true);
        h.assertTrue(crane.startInkAttack(3,pig),"Sky attack starts");
        h.assertTrue(InkRestraints.held(pig),"Target held during sky windup");
        h.runAtTickTime(10,()->{crane.discard();h.assertTrue(!InkRestraints.held(pig),"Caster removal clears hold immediately");pig.discard();h.succeed();});
    }
    @GameTest(batch="ink_fourTigerGhostsHaveSeparatedLaunches",template="ink_crane_empty",timeoutTicks=40)
    public static void fourTigerGhostsHaveSeparatedLaunches(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);
        var tiger=h.spawn(InkBossRegistration.INK_WINGED_TIGER,new BlockPos(0,90,0));tiger.setNoAi(true);tiger.setNoGravity(true);
        var pig=h.spawn(EntityType.PIG,new BlockPos(35,90,0));pig.setNoAi(true);pig.setNoGravity(true);
        h.assertTrue(tiger.startInkAttack(2,pig),"Phantom attack starts");
        var ghosts=h.getLevel().getEntitiesOfClass(InkTigerEffectEntity.class,tiger.getBoundingBox().inflate(150),e->e.ownedBy(tiger)&&e.mode()==InkTigerEffectEntity.GHOST);
        h.assertTrue(ghosts.size()==4,"Four directional ghosts");
        h.assertTrue(ghosts.stream().map(InkTigerEffectEntity::releaseAt).sorted().toList().equals(java.util.List.of(22,38,54,70)),"Staggered releases");
        h.runAtTickTime(25,()->{
            var first=ghosts.stream().filter(e->e.releaseAt()==22).findFirst().orElseThrow();
            h.assertTrue(first.getDeltaMovement().length()>2.2,"First ghost uses doubled speed");
            h.assertTrue(ghosts.stream().filter(e->e.releaseAt()>25).allMatch(e->e.getDeltaMovement().equals(Vec3.ZERO)),"Others wait their turn");
            tiger.discard();pig.discard();ghosts.forEach(net.minecraft.world.entity.Entity::discard);h.succeed();
        });
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch="ink_craneHoldReleasesWhenRemoved")
    public static void isolate_craneHoldReleasesWhenRemoved(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}

    @net.minecraft.gametest.framework.BeforeBatch(batch="ink_fourTigerGhostsHaveSeparatedLaunches")
    public static void isolate_fourTigerGhostsHaveSeparatedLaunches(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}
}
