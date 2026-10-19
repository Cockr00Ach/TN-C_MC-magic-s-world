package com.tnc.tnc.world.eartharena;

import com.tnc.tnc.building.ElementBossBlocks;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnceartharena") @PrefixGameTestTemplate(false)
public final class EarthArenaGameTests {
    @GameTest(template = "earth_arena_empty", timeoutTicks = 1200)
    public static void dedicatedRealmGeneratesSolidMountainsAndYellowSky(GameTestHelper helper) {
        var realm = helper.getLevel().getServer().getLevel(EarthArena.KEY);
        helper.assertTrue(realm != null, "Independent earth dimension is loaded");
        helper.assertTrue(realm.getChunkSource().getGenerator() instanceof EarthArenaChunkGenerator, "Native mountain generator is decoded");
        helper.assertTrue(realm.getMinBuildHeight() == -64 && realm.getMaxBuildHeight() == 448, "Tall mountain build range");
        helper.assertTrue(realm.dimensionType().fixedTime().orElse(-1) == 6000, "Ochre daytime remains constant");
        Set<Block> palette = new HashSet<>();
        for (int shade = 1; shade <= 8; shade++) palette.add(ElementBossBlocks.block("earth", shade));
        palette.add(EarthArena.PLATFORM);
        int[][] samples = {{8,8}, {15,15}, {16,16}, {-1,-1}, {-16,-16}, {-17,-17}, {256,128}, {-320,192}};
        CompletableFuture<?>[] loads = new CompletableFuture<?>[samples.length];
        for (int i = 0; i < samples.length; i++) loads[i] = realm.getChunkSource().getChunkFuture(samples[i][0] >> 4, samples[i][1] >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        CompletableFuture.allOf(loads).whenCompleteAsync((ignored, error) -> {
            if (error != null) { helper.fail(error.toString()); return; }
            for (int[] sample : samples) {
                int x = sample[0], z = sample[1], top = EarthArenaTerrain.height(x, z, EarthArenaTerrain.DEFAULT_SEED);
                helper.assertTrue(realm.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) == top + 1, "Generated height and base height agree");
                for (int y = -64; y <= top; y += 7) helper.assertTrue(palette.contains(realm.getBlockState(new BlockPos(x,y,z)).getBlock()), "Entire mountain uses earth boss blocks, not vanilla stone");
                helper.assertTrue(realm.getBlockState(new BlockPos(x,top + 1,z)).isAir(), "Standing space above each mountain");
            }
            var biome = realm.getBiome(new BlockPos(8,250,8)).value();
            int sky = biome.getSkyColor(), fog = biome.getFogColor();
            helper.assertTrue((sky >> 16 & 255) > (sky & 255) * 2 && (sky >> 8 & 255) > (sky & 255) * 2, "Ochre yellow sky");
            helper.assertTrue((fog >> 16 & 255) > (fog & 255), "Warm dust-colored distant fog");
            helper.assertTrue(!biome.hasPrecipitation(), "Dry mountain realm");
            helper.succeed();
        }, realm.getServer());
    }

    @GameTest(template="earth_arena_empty",timeoutTicks=1200)
    public static void legacyChunksUpgradeAndGuardianOnlyWakesInsideProtectedDisc(GameTestHelper helper){
        var realm=helper.getLevel().getServer().getLevel(EarthArena.KEY);
        realm.getChunkSource().getChunkFuture(8,0,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((loaded,error)->{
            if(error!=null){helper.fail(error.toString());return;}
            var chunk=realm.getChunk(8,0);
            EarthArenaChunkLedger.mark(chunk,0);
            var oldPeak=new BlockPos(128,165,0);
            chunk.setBlockState(oldPeak,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),false);
            EarthArenaUpgrade.ready(chunk).whenCompleteAsync((ignored,upgradeError)->{
                if(upgradeError!=null){helper.fail(upgradeError.toString());return;}
                helper.assertTrue(realm.getBlockState(oldPeak).isAir(),"Old explored peak is carved into the new basin");
                var floor=new BlockPos(128,160,0);
                helper.assertTrue(realm.getBlockState(floor).is(EarthArena.PLATFORM),"Disc uses the actual pillar amberstone");
                helper.assertTrue(EarthArenaChunkLedger.version(chunk)==EarthArenaLayout.VERSION&&EarthArenaUpgrade.ready(chunk).isDone(),"Migration commits once");
                // Use a real ServerPlayer attached to this realm, with a silent test connection.
                var player=new net.minecraft.server.level.ServerPlayer(realm.getServer(),realm,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"EarthArenaTest")){
                    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
                };
                player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(realm.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player){
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
                };
                player.setPos(8.5,158,8.5);realm.addNewPlayer(player);
                try{
                    for(int i=0;i<5;i++)EarthArenaEncounter.update(realm);
                    var state=EarthArenaEncounterState.get(realm);
                    helper.assertTrue(state.boss!=null,"One central guardian is created near the entrance");
                    var boss=(com.tnc.tnc.boss.earth.StoneLancerEntity)realm.getEntity(state.boss);
                    helper.assertTrue(!boss.arenaAwake()&&boss.isNoAi(),"Entrance outside the disc does not start combat");
                    helper.assertTrue(!boss.hurt(realm.damageSources().playerAttack(player),50),"Sleeping guardian cannot be sniped from outside");
                    player.setPos(32.5,161,.5);EarthArenaEncounter.update(realm);
                    helper.assertTrue(boss.arenaAwake()&&boss.getTarget()==player,"Crossing the circle starts the fight");
                    var anchor=boss.position();boss.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(50,12,40));
                    helper.assertTrue(boss.position().distanceToSqr(anchor)<.000001,"Guardian cannot walk or be pushed off its centre");
                    for(int i=0;i<100;i++)boss.aiStep();
                    helper.assertTrue(boss.burrowPhase()==0&&boss.position().distanceToSqr(anchor)<.000001,"Combat cannot relocate the bound boss through burrowing");
                    helper.assertTrue(boss.isCasting(),"Anchoring preserves the stationary ranged gravity channel");
                    player.setPos(8.5,158,8.5);EarthArenaEncounter.update(realm);
                    helper.assertTrue(!boss.arenaAwake()&&boss.getTarget()==null&&boss.getHealth()==boss.getMaxHealth(),"Leaving resets the encounter safely");
                    for(int i=0;i<8;i++)EarthArenaEncounter.update(realm);
                    helper.assertTrue(state.boss.equals(boss.getUUID()),"Repeated checks do not duplicate the guardian");
                    helper.assertTrue(!realm.destroyBlock(floor,true,player)&&realm.getBlockState(floor).is(EarthArena.PLATFORM),"Direct destruction is blocked by the installed world guard");
                    helper.assertTrue(!realm.setBlock(floor,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3),"Spell-style direct air writes are blocked");
                    var mountain=new BlockPos(8,EarthArenaTerrain.height(8,8,EarthArenaTerrain.DEFAULT_SEED),8);
                    helper.assertTrue(!realm.destroyBlock(mountain,false),"Protection includes terrain outside the disc");
                    var breaking=new net.minecraftforge.event.level.BlockEvent.BreakEvent(realm,floor,realm.getBlockState(floor),player);
                    helper.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breaking),"Player mining event is cancelled");
                    realm.explode(null,128.5,162,.5,4,net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
                    helper.assertTrue(realm.getBlockState(floor).is(EarthArena.PLATFORM),"Explosions preserve the battle floor");
                    var saved=EarthArenaEncounterState.load(state.save(new net.minecraft.nbt.CompoundTag()));
                    helper.assertTrue(saved.boss.equals(boss.getUUID()),"Guardian identity survives save and reload");
                    boss.discard();state.boss=null;state.setDirty();
                    helper.succeed();
                }finally{realm.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}
            },realm.getServer());
        },realm.getServer());
    }
}
