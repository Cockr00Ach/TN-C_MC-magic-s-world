package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

@GameTestHolder("tncwarriorpractical") @PrefixGameTestTemplate(false)
public final class WarriorUltimateGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=160)
    public static void ultimateInvulnerabilityExpiresAndCooldownSurvivesCancel(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);var data=TemperingData.get(p.server);data.enroll(p.getUUID());data.setTalent(p.getUUID(),6);
        data.set(p.getUUID(),3001);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var art=WarriorUltimates.forId("minecraft:iron_sword");
        for(int t=1;t<132;t++)h.runAfterDelay(t,()->WarriorUltimateService.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)));
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(i*3+1,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(31,()->{
            h.assertTrue(WarriorUltimateService.active(p),"Ten real inputs start ultimate even with ordinary combos in between");
            var event=new LivingAttackEvent(p,p.damageSources().generic(),20);WarriorUltimateService.immune(event);h.assertTrue(event.isCanceled(),"Ultimate blocks actual incoming attack");
        });
        h.runAfterDelay(130,()->{
            h.assertTrue(!WarriorUltimateService.active(p),"Invulnerability ends automatically");
            var event=new LivingAttackEvent(p,p.damageSources().generic(),20);WarriorUltimateService.immune(event);h.assertTrue(!event.isCanceled(),"Normal damage restored");
            p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=90)
    public static void swappingWeaponImmediatelyDropsImmunity(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.enroll(p.getUUID());data.setTalent(p.getUUID(),6);p.setNoGravity(true);
        data.set(p.getUUID(),3001);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));var art=WarriorUltimates.forId("minecraft:iron_sword");
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(i*3+1,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(32,()->{
            h.assertTrue(WarriorUltimateService.active(p),"Ultimate begins");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
            h.assertTrue(!WarriorUltimateService.active(p),"Swap drops immunity synchronously");WarriorUltimateService.cancel(p);
            h.assertTrue(!WarriorComboService.performing(p,p.serverLevel().getGameTime()),"Cancel restores immediate normal attack animation");
        });
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(36+i*3,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(68,()->{h.assertTrue(WarriorUltimateService.active(p),"Test mode permits immediate recast");p.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=155)
    public static void scheduledStrikesDealDamageAndWaterDomainAddsFinalSlow(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);var data=TemperingData.get(p.server);data.enroll(p.getUUID());data.setTalent(p.getUUID(),6);
        data.set(p.getUUID(),3001);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,3,3));p.setPos(pos.getX(),pos.getY(),pos.getZ());p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.EARTHSHAKER_HAMMER.get()));
        var water=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","domain_water"));
        if(water==null){p.discard();h.succeed();return;}
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(water,200,0));
        var target=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new net.minecraft.core.BlockPos(5,3,5));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);target.setHealth(500);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var art=WarriorUltimates.forId("tnc:earthshaker_hammer");
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(i*3+1,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(32,()->{target.setHealth(500);target.invulnerableTime=0;});
        for(int t=33;t<125;t++)h.runAfterDelay(t,()->WarriorUltimateService.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)));
        h.runAfterDelay(115,()->{
            h.assertTrue(target.getHealth()<495,"Scheduled strike frames deal real damage");
            h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Water finale applies slow");
            WarriorUltimateService.cancel(p);target.discard();p.discard();h.succeed();
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void swordDashStopsAtWallAndDetonatesMarkedTarget(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        for(int x=2;x<=8;x++)for(int z=2;z<=12;z++)h.setBlock(new net.minecraft.core.BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.STONE);
        for(int x=2;x<=8;x++)for(int y=2;y<=5;y++)h.setBlock(new net.minecraft.core.BlockPos(x,y,9),net.minecraft.world.level.block.Blocks.STONE);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,2,3));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.setYRot(0);p.setXRot(0);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(5,2,6));target.getPersistentData().putUUID("DummyOwner",p.getUUID());target.setPos(p.getX(),p.getY(),p.getZ()+3);
        double start=p.getZ();WarriorSwordUltimate.tick(p,26);
        h.assertTrue(p.getZ()>start+3,"Dash crosses target");h.assertTrue(p.getZ()<h.absolutePos(new net.minecraft.core.BlockPos(5,2,9)).getZ(),"Dash cannot cross solid wall");
        h.assertTrue(target.hitCount()==1,"Piercing path hits once");
        h.runAfterDelay(15,()->{WarriorSwordUltimate.tick(p,84);h.assertTrue(target.hitCount()==2,"Sheathing finale hits marked enemy");WarriorSwordUltimate.clear(p);target.discard();p.discard();h.succeed();});
    }

    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void elementalUltimateEmitsTwentyArrowsAndGrantsVolley(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.MOON_BOW.get()));
        for(int age=26;age<=83;age++)WarriorSpecialUltimates.bow(p,age);
        int count=0;for(var e:p.serverLevel().getAllEntities())if(e instanceof WarriorArrowEntity a&&a.getOwner()==p)count++;
        h.assertTrue(count==20,"Exactly twenty ultimate arrows, no recursive scatter");
        h.assertTrue(p.getPersistentData().getLong("TncSeekingVolleyUntil")==p.level().getGameTime()+200,"Ten second followup volley");
        p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void heavySlamBreaksSurfaceButPreservesBedrock(GameTestHelper h){
        var p=AdventureGameTests.player(h);var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,3,5));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        h.setBlock(new net.minecraft.core.BlockPos(5,2,5),net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(new net.minecraft.core.BlockPos(6,2,5),net.minecraft.world.level.block.Blocks.BEDROCK);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(5,3,7));target.getPersistentData().putUUID("DummyOwner",p.getUUID());
        WarriorSpecialUltimates.heavy(p,54);
        h.assertTrue(p.level().getBlockState(pos.below()).isAir(),"Slam removes surface block");
        h.assertTrue(p.level().getBlockState(pos.below().east()).is(net.minecraft.world.level.block.Blocks.BEDROCK),"Unbreakable floor preserved");
        h.assertTrue(target.getDeltaMovement().y>1,"Small enemy launched upward");target.discard();p.discard();h.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void spearPiercesEachAirborneEnemyOnce(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.SPEAR.get()));
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(4,3,4));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var a=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(4,5,8));var b=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(8,6,8));
        for(var t:java.util.List.of(a,b)){t.setNoGravity(true);t.getPersistentData().putUUID("DummyOwner",p.getUUID());}
        for(int age=25;age<=65;age++)WarriorPolearmUltimates.tick(p,age);
        h.assertTrue(a.hitCount()==1&&b.hitCount()==1,"Each airborne enemy pierced exactly once");
        WarriorPolearmUltimates.tick(p,76);h.assertTrue(a.hitCount()==2&&b.hitCount()==2,"Return sweep strikes both enemies");
        WarriorPolearmUltimates.clear(p);a.discard();b.discard();p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void scytheLiftMarksEnemyAndCancelClearsSequence(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.DOOM_SCYTHE.get()));
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,3,4));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(5,3,7));target.setNoGravity(true);target.getPersistentData().putUUID("DummyOwner",p.getUUID());
        WarriorPolearmUltimates.tick(p,26);WarriorPolearmUltimates.tick(p,48);WarriorPolearmUltimates.tick(p,54);
        h.assertTrue(target.getDeltaMovement().y>0,"Hook lifts the enemy");h.assertTrue(target.getPersistentData().getLong("TncReaperUntil")>p.level().getGameTime(),"Execution leaves persistent reaper wound");
        WarriorPolearmUltimates.clear(p);target.discard();p.discard();h.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void halberdDiveLandsOnFloorAndHitsNearbyTarget(GameTestHelper h){
        // This five-by-five template runs next to other tests. Use a separate vertical plane.
        int height=96;clearUltimateTestColumn(h,height,9);
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)
            h.getLevel().setBlockAndUpdate(h.absolutePos(new net.minecraft.core.BlockPos(x,0,z)).above(height),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var p=AdventureGameTests.player(h);p.setNoGravity(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.SKY_HALBERD.get()));
        var start=ultimateTestPosition(h,1,1,2,height);p.setPos(start.x,start.y,start.z);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(3,1+height,2));
        var targetAt=ultimateTestPosition(h,3,1,2,height);target.setPos(targetAt.x,targetAt.y,targetAt.z);
        target.setNoGravity(true);target.getPersistentData().putUUID("DummyOwner",p.getUUID());
        h.assertTrue(WarriorComboService.targets(p,10).contains(target),"Landing fixture must expose its target: player="+p.position()+", target="+target.position());
        for(int age=24;age<=62;age++)WarriorPolearmUltimates.tick(p,age);
        String actual=" player="+p.position()+", target="+target.position()+", hits="+target.hitCount()+", visible="+p.hasLineOfSight(target);
        h.assertTrue(p.getY()>=start.y&&p.getY()<start.y+.2,"Dive stops above the solid floor;"+actual);
        h.assertTrue(target.hitCount()==1,"Ground contact damages nearby enemy once;"+actual);
        WarriorPolearmUltimates.clear(p);target.discard();p.discard();h.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void spearCannotTeleportIntoBlockedBackOfVisibleEnemy(GameTestHelper h){
        int height=48;clearUltimateTestColumn(h,height,4);
        var p=AdventureGameTests.player(h);p.setNoGravity(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.SPEAR.get()));
        var start=ultimateTestPosition(h,1,1,2,height);p.setPos(start.x,start.y,start.z);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(2,1+height,2));
        var targetAt=ultimateTestPosition(h,2,1,2,height);target.setPos(targetAt.x,targetAt.y,targetAt.z);
        target.getPersistentData().putUUID("DummyOwner",p.getUUID());
        // Derive facing and the back destination in world space, independent of template rotation.
        var facing=start.subtract(target.position()).multiply(1,0,1).normalize();
        target.setYRot((float)Math.toDegrees(Math.atan2(-facing.x,facing.z)));target.setXRot(0);
        var destination=target.position().subtract(target.getLookAngle().multiply(1,0,1).normalize().scale(1.5));
        var wall=net.minecraft.core.BlockPos.containing(destination);
        for(int y=0;y<3;y++)h.getLevel().setBlockAndUpdate(wall.above(y),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var blockedBox=p.getBoundingBox().move(destination.subtract(start));
        h.assertTrue(h.getLevel().getBlockCollisions(p,blockedBox).iterator().hasNext(),"Fixture must block exact back destination="+destination+", wall="+wall);
        h.assertTrue(p.hasLineOfSight(target),"Wall must remain behind visible enemy: start="+start+", target="+target.position()+", wall="+wall);
        h.assertTrue(WarriorComboService.targets(p,20).size()==1,"Spear fixture must be isolated from neighboring test enemies");
        for(int age=25;age<=65;age++)WarriorPolearmUltimates.tick(p,age);
        String actual=" start="+start+", end="+p.position()+", target="+target.position()+", destination="+destination+", hits="+target.hitCount();
        h.assertTrue(p.position().distanceToSqr(start)<.001,"Blocked destination cannot teleport player into wall;"+actual);
        h.assertTrue(target.hitCount()==0,"Failed piercing teleport cannot deal its strike damage;"+actual);
        WarriorPolearmUltimates.clear(p);target.discard();p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void scytheReacquiresSurvivingSpinVictimBeforeExecution(GameTestHelper h){
        int height=144;clearUltimateTestColumn(h,height,5);
        var p=AdventureGameTests.player(h);p.setNoGravity(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.DOOM_SCYTHE.get()));
        var start=ultimateTestPosition(h,0,1,2,height);p.setPos(start.x,start.y,start.z);
        var first=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(1,1+height,2));
        var firstAt=ultimateTestPosition(h,1,1,2,height);first.setPos(firstAt.x,firstAt.y,firstAt.z);
        first.getPersistentData().putUUID("DummyOwner",p.getUUID());
        WarriorPolearmUltimates.tick(p,26);
        var survivor=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(2,1+height,2));
        var survivorAt=ultimateTestPosition(h,2,1,2,height);survivor.setPos(survivorAt.x,survivorAt.y,survivorAt.z);
        survivor.getPersistentData().putUUID("DummyOwner",p.getUUID());
        var facing=start.subtract(survivor.position()).multiply(1,0,1).normalize();
        survivor.setYRot((float)Math.toDegrees(Math.atan2(-facing.x,facing.z)));survivor.setXRot(0);
        WarriorPolearmUltimates.tick(p,34);WarriorPolearmUltimates.tick(p,42);
        h.assertTrue(survivor.hitCount()==2,"Survivor was hit by both later spins; actual="+survivor.hitCount());
        first.discard();
        WarriorPolearmUltimates.tick(p,48);WarriorPolearmUltimates.tick(p,54);
        String actual=" player="+p.position()+", survivor="+survivor.position()+", hits="+survivor.hitCount();
        h.assertTrue(survivor.hitCount()==3,"Execution reacquires a prior-hit survivor after original enemy disappears;"+actual);
        h.assertTrue(survivor.getDeltaMovement().y>0,"Reacquired small enemy is lifted;"+actual);
        h.assertTrue(survivor.getPersistentData().getLong("TncReaperUntil")>p.level().getGameTime(),"Reacquired enemy receives reaper wound;"+actual);
        WarriorPolearmUltimates.clear(p);survivor.discard();p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void giantScytheCleaveDamagesTallEnemyAndWoundTicksAfterward(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.DOOM_SCYTHE.get()));
        int height=192;clearUltimateTestColumn(h,height,16);
        var pos=ultimateTestPosition(h,1,1,1,height);p.setPos(pos.x,pos.y,pos.z);
        h.getLevel().addNewPlayer(p); // DoT resolves its attacking player through the real entity table.
        var target=h.spawn(net.minecraft.world.entity.EntityType.GIANT,new net.minecraft.core.BlockPos(3,1+height,3));
        var targetAt=ultimateTestPosition(h,3,1,3,height);target.setPos(targetAt.x,targetAt.y,targetAt.z);
        target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.assertTrue(target.getBbHeight()>=4,"Tall enemy selects the giant-weapon branch");
        WarriorPolearmUltimates.tick(p,26);WarriorPolearmUltimates.tick(p,48);
        target.invulnerableTime=0;float before=target.getHealth();
        WarriorPolearmUltimates.tick(p,72);
        h.assertTrue(target.getHealth()<before,"Giant diagonal cleave deals real damage");
        h.assertTrue(target.getPersistentData().getLong("TncReaperUntil")>=p.level().getGameTime()+280,"Tall enemy receives long-lived black wound");
        h.assertTrue(target.getPersistentData().getUUID("TncReaperOwner").equals(p.getUUID()),"DoT keeps the correct attacker");
        float afterCleave=target.getHealth();
        h.runAfterDelay(41,()->{
            h.assertTrue(target.getHealth()<afterCleave,"Black wound continues dealing damage after the cleave");
            WarriorPolearmUltimates.clear(p);target.discard();p.discard();h.succeed();
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void swordNineThrustsClimbToElevatedEnemyAndRotateFortyDegrees(GameTestHelper h){
        var origin=swordDashTestOrigin(h,1);
        var p=swordDashTestPlayer(h,origin);
        var target=swordDashTestTarget(h,p,origin.add(0,3,6));
        h.runAfterDelay(4,()->{
        try {
            h.assertTrue(WarriorComboService.targets(p,24).size()==1,"Elevated sword fixture must be isolated from neighboring tests");
            double previousYaw=Double.NaN;
            for(int index=0;index<9;index++){
                var before=p.position();WarriorSwordUltimate.tick(p,WarriorSwordDash.tickFor(index));
                var plan=WarriorSwordUltimate.lastPlan(p);
                h.assertTrue(plan!=null,"Elevated enemy must produce a thrust plan at index="+index+", player="+before+", target="+target.position());
                assertSwordDashPlanSafe(h,p,plan,before);
                assertSwordThrustCrossesChest(h,p,target,plan,index);
                h.assertTrue(target.hitCount()==index+1,"Every elevated thrust must hit once: index="+index+", hits="+target.hitCount()+", player="+p.position()+", target="+target.position());
                h.assertTrue(p.getY()>origin.y+2,"Targeted dash must climb above old horizontal path; player="+p.position()+", origin="+origin);
                double yaw=Math.toDegrees(Math.atan2(-plan.direction().x,plan.direction().z));
                if(index>0){
                    double change=net.minecraft.util.Mth.wrapDegrees(yaw-previousYaw);
                    h.assertTrue(Math.abs(change-40)<.001,"Each actual thrust must change direction by forty degrees: index="+index+", delta="+change);
                }
                previousYaw=yaw;
            }
            h.succeed();
        } finally {WarriorSwordUltimate.clear(p);target.discard();p.discard();}
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void swordNineThrustsDescendToEnemyWithoutFloorSupport(GameTestHelper h){
        var origin=swordDashTestOrigin(h,2);
        var p=swordDashTestPlayer(h,origin.add(0,5,0));
        var target=swordDashTestTarget(h,p,origin.add(0,2,6));
        h.runAfterDelay(4,()->{
        try {
            h.assertTrue(WarriorComboService.targets(p,24).size()==1,"Descending sword fixture must be isolated");
            for(int index=0;index<9;index++){
                var before=p.position();WarriorSwordUltimate.tick(p,WarriorSwordDash.tickFor(index));
                var plan=WarriorSwordUltimate.lastPlan(p);
                h.assertTrue(plan!=null,"Lower enemy must produce a thrust plan at index="+index+", player="+before+", target="+target.position());
                assertSwordDashPlanSafe(h,p,plan,before);
                assertSwordThrustCrossesChest(h,p,target,plan,index);
                h.assertTrue(target.hitCount()==index+1,"Every descending thrust must hit once: index="+index+", hits="+target.hitCount()+", player="+p.position());
                h.assertTrue(p.getY()<origin.y+3,"Targeted dash must descend, not remain on starting Y; player="+p.position());
            }
            h.succeed();
        } finally {WarriorSwordUltimate.clear(p);target.discard();p.discard();}
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void swordApproachClimbsPlatformAndNeverCrossesSolidWall(GameTestHelper h){
        var origin=swordDashTestOrigin(h,3);
        var block=net.minecraft.core.BlockPos.containing(origin);
        // A raised enemy platform, followed by a tall wall that blocks thrust overshoot.
        for(int x=-18;x<=18;x++)for(int z=-6;z<=23;z++){
            h.getLevel().setBlockAndUpdate(block.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(int y=0;y<=12;y++)h.getLevel().setBlockAndUpdate(block.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        for(int x=-3;x<=3;x++)for(int z=4;z<=8;z++)for(int y=0;y<2;y++)
            h.getLevel().setBlockAndUpdate(block.offset(x,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        for(int x=-18;x<=18;x++)for(int y=0;y<=11;y++)
            h.getLevel().setBlockAndUpdate(block.offset(x,y,10),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var p=swordDashTestPlayer(h,origin);
        var target=swordDashTestTarget(h,p,origin.add(0,2,6));
        h.runAfterDelay(4,()->{
        try {
            h.assertTrue(p.hasLineOfSight(target),"Platform enemy must remain visible before dash");
            h.assertTrue(WarriorComboService.targets(p,24).size()==1&&WarriorComboService.inArc(p,target,110),"Platform has exactly one eligible forward target: targets="+WarriorComboService.targets(p,24).stream().map(t->t.position().toString()).toList()+", yaw="+p.getYRot()+", valid="+WarriorComboService.validTarget(p,target)+", rawLiving="+h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,p.getBoundingBox().inflate(24)).size()+", registered="+(h.getLevel().getEntity(target.getId())==target)+", alive="+target.isAlive()+", canAttack="+p.canAttack(target)+", distance="+p.distanceTo(target)+", player="+p.position()+", target="+target.position());
            for(int index=0;index<9;index++){
                var before=p.position();int hits=target.hitCount();
                WarriorSwordUltimate.tick(p,WarriorSwordDash.tickFor(index));
                var plan=WarriorSwordUltimate.lastPlan(p);
                if(index==0)h.assertTrue(plan!=null&&target.hitCount()==1,"First dash must climb the platform and hit: player="+p.position()+", target="+target.position()+", hits="+target.hitCount());
                if(target.hitCount()>hits||p.position().distanceToSqr(before)>.000001){
                    h.assertTrue(plan!=null,"Every executed movement must have a collision-checked plan");
                    assertSwordDashPlanSafe(h,p,plan,before);
                    assertSwordThrustCrossesChest(h,p,target,plan,index);
                }
                h.assertTrue(!h.getLevel().getBlockCollisions(p,p.getBoundingBox()).iterator().hasNext(),"Sword dash must never end inside platform or wall: index="+index+", player="+p.position());
            }
            h.assertTrue(target.hitCount()>0,"Raised target must be struck despite the step and the wall");
            h.succeed();
        } finally {WarriorSwordUltimate.clear(p);target.discard();p.discard();}
        });
    }
    private static net.minecraft.world.phys.Vec3 swordDashTestOrigin(GameTestHelper h,int lane){
        var template=h.absolutePos(new net.minecraft.core.BlockPos(2,3,2));
        var at=new net.minecraft.core.BlockPos(8192+lane*128,template.getY()+224,8192);
        int y=Math.min(h.getLevel().getMaxBuildHeight()-32,Math.max(h.getLevel().getMinBuildHeight()+48,at.getY()));
        at=new net.minecraft.core.BlockPos(at.getX(),y,at.getZ());
        for(int x=(at.getX()-24)>>4;x<=(at.getX()+24)>>4;x++)for(int z=(at.getZ()-24)>>4;z<=(at.getZ()+24)>>4;z++){
            h.getLevel().setChunkForced(x,z,true);h.getLevel().getChunk(x,z);
        }
        return net.minecraft.world.phys.Vec3.atBottomCenterOf(at);
    }
    private static net.minecraft.server.level.ServerPlayer swordDashTestPlayer(GameTestHelper h,net.minecraft.world.phys.Vec3 at){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);
        p.setPos(at.x,at.y,at.z);p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        return p;
    }
    private static TestDummyEntity swordDashTestTarget(GameTestHelper h,net.minecraft.server.level.ServerPlayer p,net.minecraft.world.phys.Vec3 at){
        var target=WarriorEntities.TEST_DUMMY.get().create(h.getLevel());
        if(target==null)throw new IllegalStateException("Test dummy type did not create a sword target");
        target.setPos(at.x,at.y,at.z);target.setNoGravity(true);target.getPersistentData().putUUID("DummyOwner",p.getUUID());
        h.assertTrue(h.getLevel().addFreshEntity(target),"Sword target must join world at "+at);
        return target;
    }
    private static void assertSwordThrustCrossesChest(GameTestHelper h,net.minecraft.server.level.ServerPlayer p,TestDummyEntity target,WarriorSwordDash.Plan plan,int index){
        var start=plan.strikeFrom().add(0,p.getBbHeight()*.55,0);
        var end=plan.strikeTo().add(0,p.getBbHeight()*.55,0);
        var chest=target.position().add(0,target.getBbHeight()*.55,0);
        var delta=end.subtract(start);double fraction=chest.subtract(start).dot(delta)/delta.lengthSqr();
        double error=chest.distanceTo(start.add(delta.scale(Math.max(0,Math.min(1,fraction)))));
        h.assertTrue(fraction>0&&fraction<1&&error<.08,"Actual thrust must cross enemy chest: index="+index+", fraction="+fraction+", error="+error+", from="+start+", to="+end+", chest="+chest);
        h.assertTrue(delta.normalize().distanceTo(plan.direction())<.00001,"Reported thrust direction must match actual movement");
    }
    private static void assertSwordDashPlanSafe(GameTestHelper h,net.minecraft.server.level.ServerPlayer p,WarriorSwordDash.Plan plan,net.minecraft.world.phys.Vec3 oldFeet){
        h.assertTrue(!plan.approach().isEmpty()&&plan.approach().get(0).distanceToSqr(oldFeet)<.000001,"Approach must begin at real previous position: old="+oldFeet+", route="+plan.approach());
        h.assertTrue(plan.approach().get(plan.approach().size()-1).distanceToSqr(plan.strikeFrom())<.000001,"Approach must end at actual thrust entry");
        for(int i=1;i<plan.approach().size();i++)assertSwordSegmentSafe(h,p,plan.approach().get(i-1),plan.approach().get(i));
        assertSwordSegmentSafe(h,p,plan.strikeFrom(),plan.strikeTo());
    }
    private static void assertSwordSegmentSafe(GameTestHelper h,net.minecraft.server.level.ServerPlayer p,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to){
        int samples=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.125));
        for(int i=0;i<=samples;i++){
            var at=from.lerp(to,i/(double)samples);var box=p.getBoundingBox().move(at.subtract(p.position()));
            h.assertTrue(h.getLevel().getWorldBorder().isWithinBounds(box)&&!h.getLevel().getBlockCollisions(p,box).iterator().hasNext(),"Every approach and thrust segment must clear player-sized collision: at="+at+", from="+from+", to="+to);
        }
    }

    private static net.minecraft.world.phys.Vec3 ultimateTestPosition(GameTestHelper h,int x,int y,int z,int height){
        return net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(x,y,z))).add(0,height,0);
    }
    private static void clearUltimateTestColumn(GameTestHelper h,int height,int top){
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=0;y<=top;y++)
            h.getLevel().setBlockAndUpdate(h.absolutePos(new net.minecraft.core.BlockPos(x,y,z)).above(height),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }
}
