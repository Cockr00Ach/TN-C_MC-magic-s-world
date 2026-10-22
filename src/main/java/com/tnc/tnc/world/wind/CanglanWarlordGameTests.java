package com.tnc.tnc.world.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncdungeon")
@PrefixGameTestTemplate(false)
public final class CanglanWarlordGameTests {
    @GameTest(template="building_test_empty", timeoutTicks=50, batch="canglan_authored_thrust")
    public static void authoredThrustStopsAtWallAndOnlyHitsOnce(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(3,1,2));
        boss.setNoAi(true);boss.setNoGravity(true);
        var target=helper.spawn(EntityType.IRON_GOLEM,new BlockPos(3,1,6));target.setNoAi(true);target.setNoGravity(true);
        // Clear the tall boss's corridor and erect a wall beyond the target.
        for(int x=0;x<7;x++)for(int y=1;y<9;y++)for(int z=0;z<13;z++)
            helper.setBlock(new BlockPos(x,y,z),z==10?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR);
        float before=target.getHealth();boss.setTarget(target);
        helper.assertTrue(boss.battle().begin(1,target),"Authored thrust did not start");
        double start=boss.getZ();
        for(int i=0;i<34;i++){target.invulnerableTime=0;boss.battle().tick();}
        double wall=helper.absolutePos(new BlockPos(3,1,10)).getZ();
        helper.assertTrue(boss.getZ()>start+1,"Root motion remained visual-only");
        helper.assertTrue(boss.getBoundingBox().maxZ<=wall+.001,"Thrust crossed wall");
        helper.assertTrue(target.getHealth()<before && target.getHealth()>=before-29,"Thrust hit repeatedly or missed");
        helper.assertTrue(!boss.battle().busy(),"Thrust did not finish with authored clip");
        boss.battle().cleanup();helper.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40, batch="canglan_echo_visibility")
    public static void tinyAnchorUsesFullBossVisibilityDistance(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));boss.setNoAi(true);
        var echo=CanglanWarlordRegistration.ECHO.create(helper.getLevel());echo.configure(boss,boss.position(),0,1);
        helper.assertTrue(echo.shouldRenderAtSqrDistance(128D*128D),"Tiny anchor still culls a visible full-sized boss mesh");
        helper.assertTrue(!echo.shouldRenderAtSqrDistance(600D*600D),"Anchor draw distance is unbounded");
        helper.assertTrue(CanglanWarlordRegistration.ECHO.clientTrackingRange()==32,"Long steps outpace anchor tracking");
        echo.discard();helper.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40, batch="canglan_seeking_cyclone")
    public static void cycloneSeeksThenStopsOnItsTarget(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));boss.setNoAi(true);boss.setNoGravity(true);
        var target=helper.spawn(EntityType.IRON_GOLEM,new BlockPos(2,1,9));target.setNoAi(true);target.setNoGravity(true);boss.setTarget(target);
        var cyclone=CanglanTempestEntity.spawn(boss,boss.position(),net.minecraft.world.phys.Vec3.ZERO,true,1,60);
        double start=cyclone.getZ();float health=target.getHealth();
        for(int i=0;i<12;i++){cyclone.tickCount++;cyclone.tick();}
        helper.assertTrue(cyclone.getZ()>start+1,"Cyclone stayed at the cast point");
        helper.assertTrue(cyclone.anchored(),"Cyclone did not stop on target contact");
        helper.assertTrue(target.getHealth()<health,"Contact stopped outside the damage area");
        var at=cyclone.position();for(int i=0;i<5;i++){cyclone.tickCount++;cyclone.tick();}
        helper.assertTrue(cyclone.position().distanceToSqr(at)<.00001,"Contact cyclone kept chasing");
        cyclone.discard();boss.battle().cleanup();helper.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40, batch="canglan_fusing_cyclone")
    public static void nearbyCyclonesMergeOnceWithoutConsumingAnotherBossSpell(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));boss.setNoAi(true);boss.setNoGravity(true);
        var other=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(5,1,2));other.setNoAi(true);other.setNoGravity(true);
        var a=CanglanTempestEntity.spawn(boss,boss.position(),net.minecraft.world.phys.Vec3.ZERO,true,1,25);
        var b=CanglanTempestEntity.spawn(boss,boss.position().add(4.4,0,0),net.minecraft.world.phys.Vec3.ZERO,true,2,25);
        var foreign=CanglanTempestEntity.spawn(other,boss.position(),net.minecraft.world.phys.Vec3.ZERO,true,1,25);
        for(int i=0;i<4;i++){a.tickCount++;a.tick();}
        helper.assertTrue(b.isRemoved()&&!a.isRemoved(),"Merge kept both source entities");
        helper.assertTrue(Math.abs(a.visualScale()-Math.sqrt(5))<.001,"Merged model did not grow");
        helper.assertTrue(!foreign.isRemoved(),"Fusion consumed another boss spell");
        for(int i=0;i<40;i++){a.tickCount++;a.tick();}
        helper.assertTrue(a.isRemoved(),"Fusion became permanent");
        foreign.discard();boss.battle().cleanup();other.battle().cleanup();helper.succeed();
    }
    // MCA replaces vanilla villagers during joining; use an ordinary real animal as damage/AI fixture.
    @GameTest(template="building_test_empty", timeoutTicks=40, batch="canglan_walking")
    public static void normalApproachIsEnabledAfterEntryButNotDuringWindStep(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));boss.setNoAi(true);
        var target=helper.spawn(EntityType.COW,new BlockPos(3,1,2));target.setNoAi(true);boss.setTarget(target);
        var goal=new com.tnc.tnc.boss.GiantApproachGoal(boss,.95,boss::canApproach);
        helper.assertTrue(!goal.canUse(),"Entry pose must not walk");
        var tag=new net.minecraft.nbt.CompoundTag();boss.saveWithoutId(tag);tag.putBoolean("CanglanWarlordSpawned",true);boss.readAdditionalSaveData(tag);
        helper.assertTrue(goal.canUse(),"A live target must enable ordinary approach");
        boss.setCanglanMove(14);helper.assertTrue(!goal.canUse(),"Wind step must own movement");
        helper.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=100, batch="canglan_registration")
    public static void firstDeathRestoresHealthAsFivefoldGhost(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));
        boss.setNoGravity(true);
        helper.assertTrue(boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue()==1200.0D,"Boss attributes missing");
        helper.assertTrue(CanglanWarlordRegistration.EGG!=null,"Spawn egg missing");
        var target=helper.spawn(EntityType.COW,new BlockPos(3,1,2));
        target.setNoAi(true);
        boss.setTarget(target);
        boss.setHealth(1.0F);
        boss.die(helper.getLevel().damageSources().generic());
        helper.runAfterDelay(65,()->{
            helper.assertTrue(boss.isAlive(),"Boss died during initial ticks");
            helper.assertTrue(boss.weaponForm()==CanglanWarlordAttackRules.FORM_SPEAR,"Lethal phase transition failed");
            helper.assertTrue(boss.battleScale()==5F && Math.abs(boss.getBbWidth()-14)<.1,"Ghost dimensions not scaled");
            helper.succeed();
        });
    }
    @GameTest(template="building_test_empty", timeoutTicks=80, batch="canglan_anchors")
    public static void tenAnchorsRecallAttackAndCleanUp(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));
        boss.setNoAi(true);boss.setNoGravity(true);
        var target=helper.spawn(EntityType.COW,new BlockPos(3,1,2));target.setNoAi(true);boss.setTarget(target);
        for(int i=0;i<12;i++)boss.battle().addAnchor(boss.position());
        helper.assertTrue(boss.anchorCount()==10,"Shared anchor pool exceeded ten");
        boss.battle().tick();
        var echoes=helper.getLevel().getEntitiesOfClass(CanglanEchoEntity.class,boss.getBoundingBox().inflate(1));
        helper.assertTrue(echoes.size()==10 && echoes.stream().allMatch(CanglanEchoEntity::active),"Recall did not activate all echoes together");
        for(int i=0;i<35;i++)boss.battle().tick();
        helper.assertTrue(boss.anchorCount()==0 && echoes.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),"Recall failed to release anchors");
        boss.battle().cleanup();helper.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40, batch="canglan_targeting")
    public static void bossVolleyCanTargetPlayersAndPlayerVolleyKeepsProtection(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));boss.setNoAi(true);
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        helper.assertTrue(com.tnc.tnc.magic.wind.WindChainSpells.enemy(player,boss),"Boss weapons still exclude players");
        helper.assertTrue(!com.tnc.tnc.magic.wind.WindChainSpells.enemy(player,player),"Player self-protection changed");
        var volley=com.tnc.tnc.magic.wind.WindChainRegistration.VOLLEY.create(helper.getLevel());
        volley.configure(boss,4,player);volley.setBossScale(5);
        helper.assertTrue(volley.bossScale()==5 && volley.count()==30,"Boss T4 formation or scale incorrect");
        volley.configure(boss,5,player);
        helper.assertTrue(volley.count()==5,"Boss T5 giant arsenal budget incorrect");helper.succeed();
    }
    @GameTest(template="building_test_empty", timeoutTicks=40, batch="canglan_damage")
    public static void modelledCycloneDealsDamageAndExpires(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));boss.setNoAi(true);boss.setNoGravity(true);
        var target=helper.spawn(EntityType.COW,new BlockPos(3,1,2));target.setNoAi(true);
        var cyclone=CanglanTempestEntity.spawn(boss,boss.position(),net.minecraft.world.phys.Vec3.ZERO,true,1,12);
        float health=target.getHealth();
        helper.runAfterDelay(8,()->helper.assertTrue(target.getHealth()<health,"Cyclone model has no gameplay damage"));
        helper.runAfterDelay(18,()->{
            helper.assertTrue(cyclone.isRemoved(),"Cyclone lifetime leaked");helper.succeed();
        });
    }
}
