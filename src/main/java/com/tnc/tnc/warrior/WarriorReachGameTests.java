package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarriorreach") @PrefixGameTestTemplate(false)
public final class WarriorReachGameTests {
    private static ServerPlayer player(GameTestHelper h){
        var p=AdventureGameTests.player(h);var at=h.absolutePos(new BlockPos(6,4,3));
        p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);p.setYRot(0);p.setXRot(0);
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),6);data.enroll(p.getUUID());data.set(p.getUUID(),10000);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TNMod.HAMMER.get()));WarriorReach.update(p);
        // Keep the test corridor in open air; the flat test world can have terrain above template height.
        for(int z=-5;z<=55;z++)for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)
            p.serverLevel().setBlock(p.blockPosition().offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        return p;
    }
    private static TestDummyEntity target(GameTestHelper h,ServerPlayer p,double distance){
        var t=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(6,4,5));
        t.getPersistentData().putUUID("DummyOwner",p.getUUID());
        t.setPos(p.getX(),p.getY(),p.getZ()+distance);
        p.serverLevel().setChunkForced(t.blockPosition().getX()>>4,t.blockPosition().getZ()>>4,true);
        p.serverLevel().getChunk(t.blockPosition());return t;
    }
    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void nativeMouseAttackCanReachTwentyBlocksAndSwapRestoresThree(GameTestHelper h){
        var p=player(h);var far=target(h,p,20);var outside=target(h,p,25);
        h.runAfterDelay(6,()->{
        h.assertTrue(Math.abs(p.getEntityReach()-24)<1e-6,"Default native mouse reach must be 3 x 8 = 24");
        h.assertTrue(p.canReach(far,0),"Native server reach check must accept a distant target");
        h.assertTrue(!p.canReach(outside,0),"Native reach must reject beyond thirty blocks");
        p.attack(far);h.assertTrue(far.hitCount()==1,"Vanilla attack must actually damage distant target once");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));WarriorReach.update(p);
        h.assertTrue(Math.abs(p.getEntityReach()-3)<1e-6&&!p.canReach(far,0),"Switching to non-weapon removes extra reach");
        far.discard();outside.discard();p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void heavySlashAndFinisherUseEightfoldRadiusButRespectWallsAndFacing(GameTestHelper h){
        var p=player(h);var far=target(h,p,20);var outside=target(h,p,23);var behind=target(h,p,-4);
        var skill=WarriorCombos.bySlot(WarriorWeaponFamily.HAMMER,0);
        var beyond=target(h,p,skill.radius()*8.5);
        h.runAfterDelay(6,()->{
        System.out.println("REACH DIAG slash visible="+p.hasLineOfSight(far)+" valid="+WarriorComboService.validTarget(p,far)+" distance="+p.distanceTo(far)+" candidates="+p.serverLevel().getEntitiesOfClass(TestDummyEntity.class,p.getBoundingBox().inflate(27)).size());
        WarriorComboService.slash(p,WarriorMove.LEFT,false);
        h.assertTrue(far.hitCount()==1,"Q/E must hit at twenty blocks");
        h.assertTrue(outside.hitCount()==0&&behind.hitCount()==0,"Q/E must respect 21.6-block radius and facing");
        far.resetStats();
        for(int y=0;y<=3;y++)p.serverLevel().setBlock(p.blockPosition().offset(0,y,10),Blocks.STONE.defaultBlockState(),3);
        WarriorComboService.slash(p,WarriorMove.LEFT,false);
        h.assertTrue(far.hitCount()==0,"Giant weapons must not attack through walls");
        for(int y=0;y<=3;y++)p.serverLevel().setBlock(p.blockPosition().offset(0,y,10),Blocks.AIR.defaultBlockState(),3);
        outside.discard();behind.discard();
        far.setPos(p.getX(),p.getY(),p.getZ()+skill.radius()*7.5);far.resetStats();
        WarriorComboService.melee(p,skill,WarriorComboService.state(p.getUUID()),WarriorComboService.gameTime(p));
        h.assertTrue(far.hitCount()==1&&beyond.hitCount()==0,"Finisher radius must scale once by eight, not by 1.9 or 19");
        far.discard();beyond.discard();p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void chargedHammerReleaseUsesThirtyTwoBlockRadiusExactlyOnce(GameTestHelper h){
        var p=player(h);var far=target(h,p,31);var outside=target(h,p,33);
        WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.CHARGE_START);
        h.runAfterDelay(10,()->WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.HOLD));
        h.runAfterDelay(14,()->{
            h.assertTrue(far.hitCount()==0,"Charging must not deal damage before release");
            WarriorComboService.input(p,WarriorMove.HORIZONTAL,WarriorComboInputPacket.RELEASE);
            h.assertTrue(far.hitCount()==1&&outside.hitCount()==0,"Charged hammer must hit once within 4 x 8 blocks");
            far.discard();outside.discard();p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void heavyCounterSweepUsesThirtyTwoBlockRadius(GameTestHelper h){
        var p=player(h);var far=target(h,p,31);var outside=target(h,p,33);
        h.runAfterDelay(6,()->{
        WarriorComboService.mobCounterSweep(p);
        h.assertTrue(far.hitCount()==1&&outside.hitCount()==0,"Heavy counter sweep must use 4 x 8 radius");
        far.discard();outside.discard();p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void swordPhantomAndSpearShareEightfoldNativeAndComboReach(GameTestHelper h){
        var p=player(h);var far=target(h,p,20);
        h.runAfterDelay(6,()->{
            for(var item:new net.minecraft.world.item.Item[]{Items.IRON_SWORD,TNMod.SPEAR.get()}){
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));WarriorReach.update(p);
                h.assertTrue(Math.abs(p.getEntityReach()-24)<1e-6,"Phantom weapons must share native eightfold reach");
                far.resetStats();WarriorComboService.slash(p,WarriorMove.LEFT,false);
                h.assertTrue(far.hitCount()==1,"Phantom Q/E must hit at twenty blocks");
                far.resetStats();var skill=WarriorCombos.bySlot(WarriorWeapons.family(p.getMainHandItem()),0);
                WarriorComboService.melee(p,skill,WarriorComboService.state(p.getUUID()),WarriorComboService.gameTime(p));
                h.assertTrue(far.hitCount()==1,"Phantom finisher must use enlarged reach");
            }
            far.discard();p.discard();h.succeed();
        });
    }
}
