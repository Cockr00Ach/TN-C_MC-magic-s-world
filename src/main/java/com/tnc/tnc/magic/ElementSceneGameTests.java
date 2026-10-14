package com.tnc.tnc.magic;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class ElementSceneGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void giantOrbPullsEnemiesButLeavesItsOwnerUntouched(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(6,2,4));enemy.setNoAi(true);
        var centre=h.absoluteVec(new net.minecraft.world.phys.Vec3(4,2,4));
        var orb=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());
        orb.configure(caster,2,"earth_orb",centre.add(0,8,0),centre.add(0,12,0),12,12,0,110,3,8,h.getLevel().damageSources().magic());
        orb.damageCentre=centre;var own=caster.getDeltaMovement();float hp=enemy.getHealth();
        orb.tickCount=20;orb.tick();
        h.assertTrue(enemy.getDeltaMovement().dot(orb.position().subtract(enemy.getBoundingBox().getCenter()))>0,"Giant orb pulls toward its current airborne center");
        h.assertTrue(enemy.getDeltaMovement().y>.16,"Upward suction must overcome gravity, not just move targets horizontally");
        h.assertTrue(caster.getDeltaMovement().equals(own)&&enemy.getHealth()==hp,"Attraction leaves owner untouched and deals no early damage");
        caster.discard();enemy.discard();orb.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void highOrbCapturesGroundEnemiesAndPullsAirborneEnemiesDown(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var ground=h.spawn(EntityType.ZOMBIE,new BlockPos(6,2,4));ground.setNoAi(true);
        var above=h.spawn(EntityType.ZOMBIE,new BlockPos(6,2,5));above.setNoAi(true);
        var centre=h.absoluteVec(new net.minecraft.world.phys.Vec3(4,2,4));
        above.setPos(centre.add(1,23,0));
        var orb=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());
        orb.configure(caster,2,"earth_orb",centre.add(0,14,0),centre.add(0,18,0),20,20,40,135,3,9,h.getLevel().damageSources().magic());
        orb.damageCentre=centre;orb.tickCount=100;orb.tick();
        h.assertTrue(ground.getDeltaMovement().y>1.2,"Triple-speed suction must lift ground targets toward the high orb");
        h.assertTrue(above.getDeltaMovement().y<-.8,"Triple-speed suction must pull targets above the ball downward");
        above.setPos(orb.position().add(1,0,0));float hp=above.getHealth();
        orb.tickCount=120;orb.tick();
        h.assertTrue(above.getHealth()<hp,"Explosion must hit enemies gathered at the airborne ball, outside the ground blast");
        caster.discard();ground.discard();above.discard();orb.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void giantSpearDropsTheWholeDistanceInOneTick(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(4,2,4));enemy.setNoAi(true);
        var e=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());float hp=enemy.getHealth();
        e.configure(caster,3,"earth_spear_sharp",enemy.position().add(0,125,0),enemy.position(),25,70,14,45,3,6,h.getLevel().damageSources().magic());
        e.tickCount=15;e.tick();h.assertTrue(e.done()&&enemy.getHealth()<hp,"Full-height spear falls and damages the target in one tick");
        caster.discard();enemy.discard();e.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void quakeSpikesRiseSlowlyBeforeHittingAndAlwaysExpire(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(4,2,4));enemy.setNoAi(true);
        var end=enemy.position();float hp=enemy.getHealth();
        var e=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());
        e.configure(caster,6,"earth_spike_sharp",end.add(0,-6,0),end,2,6,0,130,3,4,h.getLevel().damageSources().magic());
        e.tickCount=20;e.tick();
        h.assertTrue(Math.abs(e.getY()-(end.y-3))<.01&&enemy.getHealth()==hp,"Halfway through the rise, spike remains partially buried and cannot hit early");
        e.tickCount=40;e.tick();h.assertTrue(e.position().distanceTo(end)<.01&&enemy.getHealth()<hp,"Fully raised spike hits the target");
        e.tickCount=131;e.tick();h.assertTrue(e.isRemoved(),"No permanent spike remains");
        caster.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void raisedSphereHitsGroundTargetsAndDoesNotDamageOwnerOrTerrain(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(4,2,4));enemy.setNoAi(true);
        var e=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());
        float health=enemy.getHealth(),own=caster.getHealth();
        var block=h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,1,4)));
        e.configure(caster,2,"earth_orb",enemy.position().add(0,8,0),enemy.position().add(0,11,0),10,10,0,110,3,5,h.getLevel().damageSources().magic());
        e.damageCentre=enemy.position();e.tickCount=80;e.tick();
        h.assertTrue(e.done()&&enemy.getHealth()<health,"Raised orb explosion reaches the ground target");
        h.assertTrue(caster.getHealth()==own,"No owner damage");
        h.assertTrue(block==h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,1,4))),"Explosion cannot change terrain");
        e.tickCount=106;e.tick();h.assertTrue(e.isRemoved(),"Exploded ball cleans up after the visible debris finishes");
        caster.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void volleyFollowupSpikeRisesFromTheImpactGround(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(4,2,4));enemy.setNoAi(true);
        var end=enemy.position();float hp=enemy.getHealth();
        var e=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());
        e.configure(caster,7,"earth_spike_sharp",end.add(0,-10,0),end,6,18,0,100,3,7,h.getLevel().damageSources().magic());
        e.tickCount=8;e.tick();h.assertTrue(e.getY()<end.y-1&&enemy.getHealth()==hp,"Huge followup spike remains underground while rising");
        e.tickCount=18;e.tick();
        h.assertTrue(e.done(),"Huge followup spike must finish its rise and burst");
        h.assertTrue(e.position().distanceTo(end)<.01,"Huge followup spike must finish exactly at the impact ground; distance="+e.position().distanceTo(end));
        h.assertTrue(enemy.getHealth()<hp,"Huge followup spike must damage the enemy; before="+hp+" after="+enemy.getHealth());
        caster.discard();enemy.discard();e.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="element_scenes")
    public static void stoneCannonStopsAtWallAndHasAFiniteLife(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,2));caster.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(6,2,2));enemy.setNoAi(true);
        h.setBlock(new BlockPos(2,3,2),Blocks.STONE);
        var e=WindEarthVisualMechanics.ATTACK.get().create(h.getLevel());float hp=enemy.getHealth();
        e.configure(caster,5,"earth_shell",caster.getEyePosition(),enemy.getEyePosition(),2,2,0,100,3,1,h.getLevel().damageSources().magic());
        e.tickCount=1;e.tick();h.assertTrue(e.done(),"Cannon projectile hits the first solid wall");
        h.assertTrue(enemy.getHealth()==hp,"Wall blocks the projectile");
        caster.discard();enemy.discard();e.discard();h.succeed();
    }
}
