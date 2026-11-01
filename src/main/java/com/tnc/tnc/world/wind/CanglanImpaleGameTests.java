package com.tnc.tnc.world.wind;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

/** Native server checks on attachment and cleanup; no client or formal save is needed. */
@GameTestHolder("tnccanglanimpale")
@PrefixGameTestTemplate(false)
public final class CanglanImpaleGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="impale_health_guard")
    public static void nonFiniteHealthIsRepairedButOrdinaryDeathIsNotRevived(GameTestHelper h){
        var b=boss(h);var p=player(b);p.setHealth(Float.NaN);p.setAbsorptionAmount(Float.NaN);
        h.assertTrue(CanglanExecutionHealth.repair(p),"NaN player was not recognized");
        h.assertTrue(Float.isFinite(p.getHealth())&&p.getHealth()==p.getMaxHealth()&&p.getAbsorptionAmount()==0,"Repair retained non-finite health/shield");
        p.setHealth(0);h.assertTrue(!CanglanExecutionHealth.repair(p)&&p.getHealth()==0,"Legitimate zero HP was revived");
        b.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="impale_finite_finisher")
    public static void finiteFinisherStillDefeatsAnOrdinaryLowHealthVictim(GameTestHelper h){
        var b=boss(h);var victim=h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,new BlockPos(4,2,10));
        victim.setNoAi(true);victim.setHealth(40);CanglanExecutionHealth.finish(b,victim);
        h.assertTrue(Float.isFinite(victim.getHealth())&&Float.isFinite(victim.getAbsorptionAmount()),"Finisher corrupted health or shield");
        h.assertTrue(!victim.isAlive(),"Bounded finisher failed against an ordinary victim");b.discard();h.succeed();
    }
    private static CanglanWarlordEntity boss(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(4,2,4));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk((origin.getX()>>4)+x,(origin.getZ()>>4)+z);
        for(int x=-1;x<13;x++)for(int y=2;y<19;y++)for(int z=-2;z<15;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        var b=h.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(4,2,4));b.setNoAi(true);b.setNoGravity(true);
        var tag=new net.minecraft.nbt.CompoundTag();b.saveWithoutId(tag);tag.putBoolean("CanglanWarlordSpawned",true);b.readAdditionalSaveData(tag);return b;
    }
    private static ServerPlayer player(CanglanWarlordEntity b){
        var p=FakePlayerFactory.get((net.minecraft.server.level.ServerLevel)b.level(),new GameProfile(UUID.randomUUID(),"ImpaleTest"));
        p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(false);p.setNoGravity(false);p.setHealth(9);p.setAbsorptionAmount(0);p.setPos(b.position().add(0,0,6));b.setTarget(p);return p;
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="impale_complete")
    public static void spearCapturesLiftsAndThrowsWithoutPrematureDeath(GameTestHelper h){
        var b=boss(h);var p=player(b);h.assertTrue(b.battle().begin(5,p)&&b.battle().move()==15,"Low-health sweep did not select the independent execution");
        double low=0,high=0;
        for(int tick=0;tick<=CanglanExecutionPose.RELEASE;tick++){
            b.battle().tick();
            if(tick==22){h.assertTrue(CanglanExecutionPins.held(p),"Spear did not capture victim");low=p.getY();}
            if(tick==54)high=p.getY();
            if(tick>=22&&tick<72){var expected=CanglanExecutionPose.tip(b,tick).subtract(0,p.getBbHeight()*.55,0);h.assertTrue(p.position().distanceTo(expected)<.001,"Victim drifted off actual spear tip");}
            h.assertTrue(p.isAlive(),"Victim died before the throw could be seen");
        }
        h.assertTrue(high-low>7,"Victim was not raised above the boss");h.assertTrue(!CanglanExecutionPins.held(p)&&!p.isNoGravity(),"Throw left the victim locked or weightless");
        h.assertTrue(p.getDeltaMovement().horizontalDistance()>2.5&&p.getDeltaMovement().y>1,"Throw did not impart flight velocity");
        b.battle().cleanup();b.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="impale_cancel")
    public static void cancellationRestoresGravityAndCameraAndDoesNotAffectHealthySweeps(GameTestHelper h){
        var b=boss(h);var p=player(b);
        p.setHealth(10);h.assertTrue(!b.battle().begin(15,p),"Half health allowed execution");
        h.assertTrue(b.battle().begin(5,p)&&b.battle().move()==5&&!b.battle().hasCinematic(),"Ordinary sweep opened execution camera");b.battle().cleanup();
        p.setHealth(9);h.assertTrue(b.battle().begin(15,p),"Execution failed to start");for(int i=0;i<30;i++)b.battle().tick();
        h.assertTrue(CanglanExecutionPins.held(p),"Capture never began");b.battle().cleanup();
        h.assertTrue(!CanglanExecutionPins.held(p)&&!p.isNoGravity()&&!b.battle().hasCinematic(),"Cancellation left capture state behind");
        p.setPos(b.position().add(0,0,6));p.setHealth(9);p.setNoGravity(true);b.setTarget(p);b.battle().begin(15,p);for(int i=0;i<25;i++)b.battle().tick();
        b.discard();h.assertTrue(!CanglanExecutionPins.held(p)&&p.isNoGravity(),"Removal did not preserve prior gravity state");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="impale_lock")
    public static void serverRejectsMovementAndReleasesOnLogout(GameTestHelper h){
        var b=boss(h);var p=player(b);b.battle().begin(15,p);for(int i=0;i<25;i++)b.battle().tick();
        h.assertTrue(CanglanExecutionPins.held(p),"No captured player for movement check");Vec3 anchor=p.position();p.setPos(anchor.add(4,0,0));p.setDeltaMovement(1,1,1);
        CanglanExecutionPins.playerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(p.position().distanceTo(anchor)<.001&&p.getDeltaMovement().lengthSqr()==0,"Movement escaped server-owned spear attachment");
        CanglanExecutionPins.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
        h.assertTrue(!CanglanExecutionPins.held(p)&&!p.isNoGravity(),"Logout leaked a gravity/capture flag");b.battle().cleanup();b.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="impale_escape")
    public static void healingOrLeavingBeforeContactCancelsTheImpale(GameTestHelper h){
        var b=boss(h);var p=player(b);b.battle().begin(15,p);for(int i=0;i<20;i++)b.battle().tick();p.setHealth(12);b.battle().tick();
        h.assertTrue(!b.battle().busy()&&!CanglanExecutionPins.held(p)&&!b.battle().hasCinematic(),"Healing before contact did not cancel");
        p.setHealth(9);p.setPos(b.position().add(0,0,6));b.setTarget(p);b.battle().begin(15,p);p.setPos(b.position().add(0,0,20));for(int i=0;i<23;i++)b.battle().tick();
        h.assertTrue(!b.battle().busy()&&!CanglanExecutionPins.held(p),"Missed spear remotely captured the escaping victim");b.discard();h.succeed();
    }
}
