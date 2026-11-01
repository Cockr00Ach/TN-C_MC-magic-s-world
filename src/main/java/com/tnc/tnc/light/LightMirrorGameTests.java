package com.tnc.tnc.light;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("tnclightmirror") @PrefixGameTestTemplate(false)
public final class LightMirrorGameTests {
    private static TNLightMirrorEntity mirror(GameTestHelper h, LivingEntity owner, int tier, Vec3 at) {
        var m=LightMirrorRegistration.MIRROR.create(h.getLevel());m.configure(owner,tier);m.moveTo(at.x,at.y,at.z,0,0);h.getLevel().addFreshEntity(m);return m;
    }
    private static TNLightBeamEntity beam(GameTestHelper h, LivingEntity owner, Vec3 at, int style) {
        var b=TNOrbEntities.LIGHT_BEAM.get().create(h.getLevel());b.moveTo(at.x,at.y,at.z,0,style==1?90:0);
        b.configure(owner,.2,20,6,8,style,null);b.tickCount=1;h.getLevel().addFreshEntity(b);return b;
    }
    private static List<TNLightBeamEntity> children(GameTestHelper h, LivingEntity owner) {
        return h.getLevel().getEntitiesOfClass(TNLightBeamEntity.class,owner.getBoundingBox().inflate(70),b->b.casterId()==owner.getId()&&b.style()==TNLightBeamEntity.STYLE_REFRACTED);
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void pulseSplitsIntoTieredColorsAndNeverTracksCaster(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,1));owner.setNoAi(true);owner.setNoGravity(true);
        Vec3 origin=owner.getEyePosition();var mirror=mirror(h,owner,5,origin.add(0,-1.3,5));
        var b=beam(h,owner,origin,TNLightBeamEntity.STYLE_PULSE);b.tick();
        h.assertTrue(mirror.isAlive()&&children(h,owner).size()==7,"T5 retains prism and emits seven rays");
        h.assertTrue(children(h,owner).stream().map(TNLightBeamEntity::beamColor).distinct().count()==7,"Each branch has its own colour");
        h.assertTrue(b.beamColor()==0xffffff&&Math.abs(b.length()-5)<.02,"Incident beam stays white and ends at mirror");
        owner.setYRot(90);owner.setPos(origin.add(10,0,0));b.tick();
        h.assertTrue(b.position().distanceTo(origin)<.001&&b.getLookAngle().distanceTo(new Vec3(0,0,1))<.001,"Pulse is a frozen flash, not a sustained tracking laser");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void skyColumnConsumesMirrorAndCreatesExactlySixSpokes(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,2));owner.setNoAi(true);
        var m=mirror(h,owner,5,owner.position().add(0,0,5));
        Vec3 center=m.center();var b=beam(h,owner,center.add(0,10,0),TNLightBeamEntity.STYLE_DESCENT);b.tick();
        h.assertTrue(m.isRemoved(),"Descent must consume the prism");
        var rays=children(h,owner);h.assertTrue(rays.size()==6,"T4/T5 overrides mirror tier: exactly six spokes");
        for(var ray:rays) h.assertTrue(ray.position().distanceTo(center)<.001&&Math.abs(ray.getLookAngle().y)<.001,"Spokes originate at the consumed prism and travel horizontally");
        b.tick();h.assertTrue(children(h,owner).size()==6,"Persistent sky column cannot repeatedly trigger the destroyed mirror");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void opponentCannotHijackOwnedMirror(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,1));owner.setNoAi(true);
        var other=h.spawn(EntityType.VILLAGER,new BlockPos(4,3,1));other.setNoAi(true);
        Vec3 origin=owner.getEyePosition();var m=mirror(h,other,3,origin.add(0,-1.3,5));
        beam(h,owner,origin,TNLightBeamEntity.STYLE_PULSE).tick();
        h.assertTrue(m.isAlive()&&children(h,owner).isEmpty(),"Only allied/caster-owned mirrors participate");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void mirrorExpiresAndKeepsOwnershipThroughSave(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,1));owner.setNoAi(true);
        var m=mirror(h,owner,1,owner.position().add(0,0,4));
        var data=new net.minecraft.nbt.CompoundTag();m.saveWithoutId(data);
        var restored=LightMirrorRegistration.MIRROR.create(h.getLevel());restored.load(data);
        h.assertTrue(restored.tier()==1&&restored.belongsTo(owner),"Tier and owner survive a chunk save/load");
        for(int i=0;i<LightMirrorRules.lifetime(1);i++)restored.tick();
        h.assertTrue(restored.isRemoved(),"Mirror lifetime is finite");m.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void lightPulseOnlyDamagesAtInitialEmission(GameTestHelper h) {
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,1));owner.setNoAi(true);
        Vec3 origin=owner.getEyePosition();var b=beam(h,owner,origin,TNLightBeamEntity.STYLE_PULSE);b.tick();
        // The synchronized pulse afterimage remains fixed until expiry; never enters legacy homing.
        for(int i=0;i<7;i++) { owner.setYRot(i*40);b.tickCount++;b.tick(); }
        h.assertTrue(b.position().distanceTo(origin)<.001&&!b.tracking(),"No afterimage following or reacquisition");
        b.tickCount++;b.tick();h.assertTrue(b.isRemoved(),"Primary flash expires after eight ticks");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void lateEnemyCannotWalkIntoFlashAfterimageAndTakeDamage(GameTestHelper h) {
        var owner=h.spawn(TNOrbEntities.FIGHTING_ANGEL.get(),new BlockPos(2,3,1));owner.setNoAi(true);
        Vec3 from=owner.position().add(0,1,0);
        var initial=h.spawn(EntityType.ZOMBIE,new BlockPos(2,3,5));initial.setNoAi(true);initial.setNoGravity(true);
        initial.setPos(from.add(0,-1,5));
        var late=h.spawn(EntityType.ZOMBIE,new BlockPos(5,3,5));late.setNoAi(true);late.setNoGravity(true);
        float before=initial.getHealth();var b=beam(h,owner,from,TNLightBeamEntity.STYLE_PULSE);b.tick();
        h.assertTrue(initial.getHealth()<before,"Initial emission damages an intersecting enemy");
        late.setPos(from.add(0,-1,8));float lateHealth=late.getHealth();b.tickCount=2;b.tick();
        h.assertTrue(late.getHealth()==lateHealth,"Afterimage does not continue dealing damage");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void apolloSummonsThreeSupportersAndTheyDeployOwnedPrisms(GameTestHelper h) {
        var boss=h.spawn(com.tnc.tnc.npc.TNNpcs.APOLLO.get(),new BlockPos(2,3,1));boss.setNoAi(true);boss.setNoGravity(true);
        var victim=h.spawn(EntityType.IRON_GOLEM,new BlockPos(2,3,6));victim.setNoAi(true);victim.setNoGravity(true);boss.setTarget(victim);
        // Make an open corridor independent of the small template's side walls.
        for(int x=-4;x<10;x++)for(int y=2;y<9;y++)for(int z=-4;z<12;z++)h.setBlock(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR);
        h.assertTrue(boss.beginAcolyteSummon(),"Summon begins as a cast");
        for(int i=0;i<59;i++)boss.aiStep();
        h.assertTrue(ApolloAcolyteEntity.count(boss)==0,"No instant background reinforcements during windup");
        boss.aiStep();
        var followers=h.getLevel().getEntitiesOfClass(ApolloAcolyteEntity.class,boss.getBoundingBox().inflate(30),a->a.belongsTo(boss));
        h.assertTrue(followers.size()==3,"Apollo has three dedicated mirror supporters");
        h.assertTrue(!boss.beginAcolyteSummon(),"Cooldown blocks duplicate summons");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ApolloAcolyteEntity.class,boss.getBoundingBox().inflate(30),a->a.belongsTo(boss)).size()==3,"Repeated maintenance never duplicates followers");
        for(var follower:followers)for(int i=0;i<38;i++)follower.aiStep();
        h.assertTrue(!h.getLevel().getEntitiesOfClass(TNLightMirrorEntity.class,boss.getBoundingBox().inflate(40),m->m.belongsTo(boss)).isEmpty(),"Supporter deploys a mirror owned by Apollo, usable by his spells");
        boss.discard();for(var follower:followers) { follower.aiStep();h.assertTrue(follower.isRemoved(),"Followers clean up when their master dies"); }h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_mirrors")
    public static void lostTargetCancelsSummonAndReplacementHonorsCooldown(GameTestHelper h) {
        var boss=h.spawn(com.tnc.tnc.npc.TNNpcs.APOLLO.get(),new BlockPos(2,3,1));boss.setNoAi(true);boss.setNoGravity(true);
        var target=h.spawn(EntityType.IRON_GOLEM,new BlockPos(2,3,6));target.setNoAi(true);boss.setTarget(target);
        h.assertTrue(boss.beginAcolyteSummon(),"Summon starts");
        boss.setTarget(null);boss.aiStep();
        h.assertTrue(ApolloAcolyteEntity.count(boss)==0,"Cancelled cast never summons");
        boss.setTarget(target);h.assertTrue(!boss.beginAcolyteSummon(),"Interrupted cast has retry delay");
        var tag=new net.minecraft.nbt.CompoundTag();boss.addAdditionalSaveData(tag);
        h.assertTrue(tag.getInt("AcolyteSummonCooldown")==40,"Retry cooldown persists through saving");h.succeed();
    }
}
