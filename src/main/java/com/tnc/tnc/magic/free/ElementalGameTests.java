package com.tnc.tnc.magic.free;

import com.tnc.tnc.adventure.*;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.magic.fire.FireHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncacademy")
@PrefixGameTestTemplate(false)
public final class ElementalGameTests {
    @GameTest(template="building_test_empty",batch="elemental_fire_charge")
    public static void fireballWaitsThenSpendsAndHits(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,701,"火球");
        h.runAfterDelay(20,()->h.assertTrue(mana(p)==100&&t.hits==0,"Fire dealt damage or charged during formation"));
        h.runAfterDelay(46,()->{int hits=t.hits,left=mana(p);clean(p,t);h.assertTrue(hits>=1&&left==88,"Expected fireball hit and one 12-mana debit, got "+hits+"/"+left);h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_channel",timeoutTicks=140)
    public static void stopEndsChannelAndFurtherDebits(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,702,"持续喷火");int[] remaining={0};
        h.runAfterDelay(55,()->{FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.STOP,702,""));remaining[0]=mana(p);h.assertTrue(remaining[0]<86,"Channel failed to charge maintenance");});
        h.runAfterDelay(110,()->{int left=mana(p),active=FireMagicEngine.count(p);clean(p,t);h.assertTrue(left==remaining[0]&&active==0,"Stopped channel continued charging or remained active");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_point_blank")
    public static void formationDoesNotSkipANearbyEnemy(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);t.setPos(p.position().add(0,1,1.9));cast(p,709,"火球");
        h.runAfterDelay(44,()->{int hits=t.hits,left=mana(p);clean(p,t);h.assertTrue(hits>=1&&left==88,"Fireball formation spawned past a point-blank target");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_refresh",timeoutTicks=240)
    public static void durableIgnitionExtendsExistingBurn(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,703,"点燃");
        h.runAfterDelay(30,()->cast(p,704,"持久点燃"));
        h.runAfterDelay(190,()->{boolean burning=FireMagicEngine.burning(p,t);int left=mana(p);clean(p,t);h.assertTrue(burning&&left==77,"Durable refresh expired early or debited incorrectly: "+left);h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_volley")
    public static void targetedVolleyOnlyChargesDistinctAvailableEnemies(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,705,"齐发点燃");
        h.runAfterDelay(35,()->{int left=mana(p);boolean burning=FireMagicEngine.burning(p,t);clean(p,t);h.assertTrue(left==90&&burning,"Single enemy was charged for duplicate debuff targets: "+left);h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_detonate",timeoutTicks=140)
    public static void detonationConsumesExistingBurn(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);t.allowDamage=true;
        // Seed heat through the fixed spell API. Free ignition must add to the same meter.
        FireHeat.add(t,p,45);
        cast(p,706,"点燃");
        h.runAfterDelay(30,()->{
            h.assertTrue(FireHeat.ignited(t) && FireHeat.of(t) == 71, "Free ignition did not raise the shared meter to 71");
            cast(p,707,"燃爆");
        });
        h.runAfterDelay(80,()->{
            boolean burning=FireMagicEngine.burning(p,t);
            int left=mana(p);
            float heat=FireHeat.of(t), damage=t.getMaxHealth()-t.getHealth();
            clean(p,t);
            h.assertTrue(!burning && heat==0 && damage>=35 && left==72,
                    "Shared heat finisher: mark="+burning+", heat="+heat+", damage="+damage+", mana="+left);
            h.succeed();
        });
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_rejected_finisher",timeoutTicks=120)
    public static void rejectedFinisherRetainsHeatAndBurn(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,710,"点燃");
        h.runAfterDelay(30,()->cast(p,711,"燃爆"));
        h.runAfterDelay(70,()->{
            float heat=FireHeat.of(t);boolean marked=FireMagicEngine.burning(p,t);
            int remaining=mana(p);
            clean(p,t);
            h.assertTrue(marked && heat==26 && remaining==72,"Rejected damage consumed heat or mark");
            h.succeed();
        });
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_full_pool")
    public static void simultaneousLavaLandingsKeepEveryAdmittedPool(GameTestHelper h){
        var p = player(h,100);
        var target = new Target(h,p);
        var floor = p.blockPosition().offset(0,0,5);
        var previous = h.getLevel().getBlockState(floor);
        h.getLevel().setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
        Vec3 surface = Vec3.atBottomCenterOf(floor.above());
        // Outside the drops' swept bodies, inside the pools' first pulse.
        target.setPos(surface.add(1.2,0,0));
        var spray = ElementalIntent.parse("岩浆喷射",Element.FIRE).orElseThrow();
        for (int i=0;i<25;i++) FireMagicEngine.spawnDrop(p,spray,surface.add(0,.5,0),new Vec3(0,-1,0));
        h.runAfterDelay(8,()->{
            int pulses = target.hits;
            h.getLevel().setBlockAndUpdate(floor,previous);
            clean(p,target);
            h.assertTrue(pulses==24,"Expected 24 pool hits from the 24 admitted drops, got "+pulses);
            h.succeed();
        });
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_low_floor")
    public static void serpentAndMagmaFindTheLowestSupportingBlock(GameTestHelper h){
        var p = player(h,100);
        var level = h.getLevel();
        var floor = new BlockPos(p.getBlockX(),level.getMinBuildHeight(),p.getBlockZ());
        var previous = new net.minecraft.world.level.block.state.BlockState[3];
        for (int i=0;i<previous.length;i++) previous[i]=level.getBlockState(floor.above(i));
        try {
            level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(floor.above(),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(floor.above(2),Blocks.AIR.defaultBlockState());
            Vec3 expected = Vec3.atBottomCenterOf(floor.above()).add(0,.08,0);
            for (int depth : new int[]{5,6}) {
                Vec3 ground = FireMagicEngine.groundBelow(p,expected.add(0,.9,0),depth);
                h.assertTrue(ground!=null && ground.distanceToSqr(expected)<.00001,"Missed the bottom floor at depth "+depth);
            }
            h.assertTrue(FireMagicEngine.groundBelow(p,expected.add(0,-2,0),6)==null,"Created ground below the world");
        } finally {
            for(int i=0;i<previous.length;i++) level.setBlockAndUpdate(floor.above(i),previous[i]);
            clean(p);
        }
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_magma_aim",timeoutTicks=90)
    public static void centralMagmaStreamHitsTheAimedTargetAtNineBlocks(GameTestHelper h){
        var p = player(h,100);
        var t = new Target(h,p);
        // The caster floats one block above the target, as in the first-person comparison.
        // Open air below the target rules out a hit from a pool on the floor.
        t.setPos(p.position().add(0,-1,9));
        Vec3 aim = t.getBoundingBox().getCenter().subtract(p.getEyePosition());
        p.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance())));
        cast(p,712,"岩浆喷射");
        h.runAfterDelay(60,()->{
            boolean hit = t.hits>0 && FireMagicEngine.burning(p,t);
            clean(p,t);
            h.assertTrue(hit,"Magma stream missed a centered target nine blocks away without a floor");
            h.succeed();
        });
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_serpent_prey")
    public static void serpentFindsHotPreyPastTwelveCloserBodies(GameTestHelper h){
        var p = player(h,100);
        var targets = new Target[15];
        for(int i=0;i<targets.length;i++){
            targets[i]=new Target(h,p);
            targets[i].setPos(p.position().add((i%3)-1,1,2 + i/3));
        }
        var hot = targets[13];
        hot.setPos(p.position().add(0,1,10));
        FireHeat.add(hot,p,20);
        var resistant = targets[14];
        resistant.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,60));
        FireHeat.add(resistant,p,40);
        var selected = FireMagicEngine.serpentPrey(p,p.getEyePosition(),new Vec3(0,0,1));
        clean(p,targets);
        h.assertTrue(selected==hot,"Crowded tracking missed the hottest eligible visible target");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_wall")
    public static void blockedSideOfGiantVolleyRejectsBeforeDebit(GameTestHelper h){
        var p=player(h,100);Vec3 center=ElementalTargets.origin(p,3.2),side=center.add(WaterCastGeometry.right(p.getLookAngle()).scale(1.6*3.2));
        BlockPos blocked=BlockPos.containing(side);h.getLevel().setBlockAndUpdate(blocked,Blocks.STONE.defaultBlockState());
        String failure=ElementalRuntime.prepare(p,708,ElementalIntent.parse("巨大齐发火球",Element.FIRE).orElseThrow());
        h.getLevel().setBlockAndUpdate(blocked,Blocks.AIR.defaultBlockState());clean(p);
        h.assertTrue(!failure.isEmpty()&&mana(p)==100,"Blocked side projectile was allowed or charged");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_wide_wall")
    public static void wideProjectileCannotHitAcrossSideWall(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);t.setPos(p.position().add(2,1,6));
        int wallX=(int)Math.floor(p.getX())+1,y=(int)p.getY(),z=(int)p.getZ();
        for(int j=0;j<4;j++)for(int k=0;k<12;k++)h.getLevel().setBlockAndUpdate(new BlockPos(wallX,y+j,z+k),Blocks.STONE.defaultBlockState());
        var hits=ElementalTargets.segment(p,p.getEyePosition(),p.getEyePosition().add(0,0,10),2.5,java.util.Set.of());
        for(int j=0;j<4;j++)for(int k=0;k<12;k++)h.getLevel().setBlockAndUpdate(new BlockPos(wallX,y+j,z+k),Blocks.AIR.defaultBlockState());
        clean(p,t);h.assertTrue(hits.isEmpty(),"Expanded projectile bounds struck an enemy across a solid side wall");h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h,int mana){
        var p=AdventureGameTests.player(h);Vec3 base=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        p.setPos(base.x,h.getLevel().getMaxBuildHeight()-16,base.z);p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("fire_wand_3")));
        var data=MagicStone.getOrNull(p);data.setAffinity(Element.FIRE,3);data.setMaxMana(100);data.setMana(mana);return p;
    }
    private static int mana(ServerPlayer p){return MagicStone.getOrNull(p).getMana();}
    private static void cast(ServerPlayer p,long token,String text){FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,token,""));FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.FINAL,token,text));}
    private static void clean(ServerPlayer p,Target... targets){FreeMagicServer.logout(new PlayerEvent.PlayerLoggedOutEvent(p));ElementalRuntime.logout(new PlayerEvent.PlayerLoggedOutEvent(p));for(var t:targets){FireHeat.clear(t);t.discard();}}
    private static final class Target extends Pig {
        final ServerPlayer owner;int hits;boolean allowDamage;
        Target(GameTestHelper h,ServerPlayer owner){super(EntityType.PIG,h.getLevel());this.owner=owner;setNoAi(true);setNoGravity(true);getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);setHealth(getMaxHealth());setPos(owner.position().add(0,1,6));h.getLevel().addFreshEntity(this);}
        @Override public boolean hurt(DamageSource source,float damage){if(source.getEntity()==owner)hits++;return allowDamage&&super.hurt(source,damage);}
    }
}
