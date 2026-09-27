package com.tnc.tnc.magic.water;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class WaterMagicGameTests {
    private static ServerPlayer caster(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"water-test"));
        player.setGameMode(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,3,1))));return player;
    }
    private static Zombie zombie(GameTestHelper h,Vec3 pos){var z=EntityType.ZOMBIE.create(h.getLevel());z.setPos(pos);z.setNoAi(true);z.setNoGravity(true);h.getLevel().addFreshEntity(z);return z;}
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void projectileHitsOnlyFirstEnemy(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(1,4,1)));
        Zombie a=zombie(h,start.add(0,-.6,1)),b=zombie(h,start.add(0,-.6,2));
        var bolt=TNOrbEntities.WATER_BOLT.get().create(h.getLevel());bolt.configure(p,1,start,new Vec3(0,0,3));bolt.tick();
        h.assertTrue(a.getHealth()<a.getMaxHealth(),"First target must be hurt");
        h.assertTrue(b.getHealth()==b.getMaxHealth(),"Second target must not take splash/piercing damage");
        h.assertTrue(bolt.isRemoved(),"Water projectile must end on first impact");a.discard();b.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void waterCannonIsStrongerAndWallBlocksBolt(GameTestHelper h) {
        h.assertTrue(WaterSpellRules.damage(2)>WaterSpellRules.damage(1),"Cannon must be stronger");
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(1,4,1)));
        var z=zombie(h,start.add(0,-.6,3));h.getLevel().setBlockAndUpdate(BlockPos.containing(start.add(0,0,1)),Blocks.STONE.defaultBlockState());
        var bolt=TNOrbEntities.WATER_BOLT.get().create(h.getLevel());bolt.configure(p,2,start,new Vec3(0,0,4));bolt.tick();
        h.assertTrue(z.getHealth()==z.getMaxHealth(),"Wall must block projectile");h.assertTrue(bolt.isRemoved(),"Wall consumes projectile");z.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void boreCutsOnlyFiniteCylinder(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        BlockPos inside=BlockPos.containing(start.add(0,0,1)),outside=BlockPos.containing(start.add(3,0,1));
        h.getLevel().setBlockAndUpdate(inside,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(outside,Blocks.STONE.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,3);
        for(int i=0;i<8;i++)h.assertTrue(bore.tick(h.getLevel(),p)<=WaterTerrainBore.BLOCK_BUDGET,"Exceeded block budget");
        h.assertTrue(h.getLevel().getBlockState(inside).isAir(),"Stone in cylinder must be broken");
        h.assertTrue(h.getLevel().getBlockState(outside).is(Blocks.STONE),"Outside cylinder must remain");
        h.assertTrue(bore.length()<=3,"Finite range required");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void containersAndBedrockStopBore(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        BlockPos obstacle=BlockPos.containing(start.add(0,0,1));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.CHEST,Blocks.BEDROCK}) {
            h.getLevel().setBlockAndUpdate(obstacle,block.defaultBlockState());
            var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,4);for(int i=0;i<8;i++)bore.tick(h.getLevel(),p);
            h.assertTrue(bore.stopped(),"Protected block must stop beam");
            h.assertTrue(h.getLevel().getBlockState(obstacle).is(block),"Protected block must survive");
            h.assertTrue(bore.length()<=1,"Visible beam must stop before protected slice");
        }h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void claimCancellationStopsDestruction(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));BlockPos pos=BlockPos.containing(start);
        h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        Consumer<BlockEvent.BreakEvent> listener=e->{if(e.getPlayer()==p)e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,3);bore.tick(h.getLevel(),p);
            h.assertTrue(bore.stopped()&&bore.length()==0,"Claim rejection must stop beam");h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Claimed stone preserved");
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void silenceBlocksNewProjectilesThenExpires(GameTestHelper h) {
        var z=zombie(h,Vec3.atCenterOf(h.absolutePos(new BlockPos(2,4,2))));z.setNoAi(false);
        h.assertTrue(ChaosSilence.apply(z),"Silence should apply to hostile mob");
        var arrow=new Arrow(h.getLevel(),z);h.assertTrue(!h.getLevel().addFreshEntity(arrow),"Silenced caster cannot create projectile");
        h.assertTrue(!z.isNoAi(),"Silence must not disable all AI");
        h.runAfterDelay(62,()->{h.assertTrue(!ChaosSilence.silenced(z),"Silence expires after 60 ticks");var after=new Arrow(h.getLevel(),z);
            h.assertTrue(h.getLevel().addFreshEntity(after),"Projectile allowed again after expiry");after.discard();z.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void verticalAimHasStableCircleBasis(GameTestHelper h) {
        for(Vec3 dir:new Vec3[]{new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1)}) {
            var r=WaterSpellRules.right(dir);h.assertTrue(Math.abs(r.length()-1)<.0001,"Basis unit length");h.assertTrue(Math.abs(r.dot(dir))<.0001,"Basis perpendicular");
        }h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void protectedAirStopsVisualFront(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        Consumer<BlockEvent.BreakEvent> listener=e->{if(e.getPlayer()==p)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(listener);
        try {var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,3);bore.tick(h.getLevel(),p);h.assertTrue(bore.stopped()&&bore.length()==0,"Protected air must block beam");}
        finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void wetSolidIsNotFluidAndContainerSupportIsProtected(GameTestHelper h) {
        var wet=Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true);
        h.assertTrue(!WaterTerrainBore.fluidOnly(wet),"Waterlogged stairs must not count as water");
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));BlockPos pos=BlockPos.containing(start);
        h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(pos.above(),Blocks.CHEST.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),.4,3);bore.tick(h.getLevel(),p);
        h.assertTrue(bore.stopped()&&h.getLevel().getBlockState(pos).is(Blocks.STONE),"Container support must not be removed");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void weatherLeaseDetectsAdminChangesWithFrozenCycle(GameTestHelper h) {
        h.assertTrue(WaterWeather.ownsTimer(620,620,false),"Frozen timer unchanged");
        h.assertTrue(!WaterWeather.ownsTimer(620,12000,false),"Admin storm must override lease even with frozen cycle");
        h.assertTrue(WaterWeather.ownsTimer(620,619,true),"Normal timer decrement");
        h.assertTrue(!WaterWeather.ownsTimer(620,610,true),"External timer change");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void ignitedCreeperSurvivesSilenceWithoutExploding(GameTestHelper h) {
        var creeper=EntityType.CREEPER.create(h.getLevel());creeper.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2,4,2))));
        creeper.setNoGravity(true);h.getLevel().addFreshEntity(creeper);creeper.ignite();ChaosSilence.apply(creeper);
        h.runAfterDelay(50,()->{h.assertTrue(creeper.isAlive(),"Ignited creeper must not explode or suicide during silence");
            h.assertTrue(!creeper.isNoAi(),"Creeper AI still active");creeper.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void beamStopsBeforeProtectedCellFaceNotItsCenter(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        int protectedZ=BlockPos.containing(start).getZ()+2;
        Consumer<BlockEvent.BreakEvent> listener=e->{if(e.getPlayer()==p && e.getPos().getZ()>=protectedZ)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(listener);
        try {var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,4);for(int i=0;i<8;i++)bore.tick(h.getLevel(),p);
            h.assertTrue(bore.stopped(),"Expected protection boundary");h.assertTrue(start.z+bore.length()<=protectedZ,"Beam endpoint must never enter protected cell by half a block");
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void existingExplosiveProjectileIsNotErasedBySilencingOwner(GameTestHelper h) {
        var z=zombie(h,Vec3.atCenterOf(h.absolutePos(new BlockPos(2,4,2))));
        var projectile=new net.minecraft.world.entity.projectile.LargeFireball(h.getLevel(),z,0,0,1,1);
        ChaosSilence.apply(z);
        var explosion=new net.minecraft.world.level.Explosion(h.getLevel(),projectile,z.getX(),z.getY(),z.getZ(),1,false,net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
        var event=new net.minecraftforge.event.level.ExplosionEvent.Start(h.getLevel(),explosion);ChaosSilence.explosion(event);
        h.assertTrue(!event.isCanceled(),"Already-fired projectile must retain explosion");z.discard();h.succeed();
    }
}
