package com.tnc.tnc.life.routes;

import com.tnc.tnc.life.botanical.BotanicalBlock;
import com.tnc.tnc.life.botanical.BotanicalPlantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;

@GameTestHolder("tncroute") @PrefixGameTestTemplate(false)
public final class WildPlantCommunityGameTests {
    @GameTest(template="empty",timeoutTicks=120)
    public static void wildPaletteAndSeededSpacing(GameTestHelper h) {
        var ids = new HashSet<String>();
        for (var habitat : WildPlantCommunities.Habitat.values()) {
            ids.addAll(habitat.dominant);
            ids.addAll(habitat.accents);
        }
        h.assertTrue(ids.size()==38 && !ids.contains("steam_lotus"), "38 living wild forms; mutation remains player-grown");
        for (String id : ids)
            h.assertTrue(ForgeRegistries.BLOCKS.containsKey(ResourceLocation.fromNamespaceAndPath("tnc",id)), "Registered wild form: "+id);
        int selected=0, differences=0;
        for (int x=-24;x<24;x++) for (int z=-24;z<24;z++) {
            ChunkPos cp=new ChunkPos(x,z);
            boolean pass=WildPlantCommunities.eligible(9121,cp);
            if (pass) selected++;
            if (pass!=WildPlantCommunities.eligible(9122,cp)) differences++;
        }
        h.assertTrue(selected>630 && selected<900 && differences>600, "Distribution and world-seed variation in 2304 sample chunks");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=120)
    public static void seededCommunitiesGrowAsWalkableGroups(GameTestHelper h) {
        var level=h.getLevel();
        var testChunk=new ChunkPos(h.absolutePos(new BlockPos(0,0,0)));
        var chunk=level.getChunk(testChunk.x+3,testChunk.z+3);
        var cp=chunk.getPos();
        // Test every potential root column above the native canopy and the 30-block cave search depth.
        int terrainTop=level.getSeaLevel();
        for (int x=0;x<16;x++) for (int z=0;z<16;z++) {
            terrainTop=Math.max(terrainTop,chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z));
        }
        int y=terrainTop+40;
        h.assertTrue(y+4<level.getMaxBuildHeight(),"The test plateau must fit above the local terrain");
        var soils=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var planted=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var seen=new HashSet<String>();
        int groups=0, total=0, clusters=0;
        try {
            for (int x=2;x<=13;x++) for (int z=2;z<=13;z++) {
                var ground=new BlockPos(cp.getMinBlockX()+x,y,cp.getMinBlockZ()+z);
                soils.put(ground,level.getBlockState(ground));
                planted.put(ground.above(),level.getBlockState(ground.above()));
                level.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),18);
            }
            for (long seed=300;seed<396;seed++) {
                planted.keySet().forEach(p -> level.setBlock(p,Blocks.AIR.defaultBlockState(),18));
                int count=WildPlantCommunities.populateAtSeed(level,chunk,seed);
                h.assertTrue(count<=5,"No chunk generates a carpet of plants");
                int actual=0;
                for(var p:planted.keySet()) {
                    var key=ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock());
                    if(key!=null && key.getNamespace().equals("tnc")) {actual++;seen.add(key.getPath());}
                }
                h.assertTrue(actual==count,"All roots present on plateau at y="+y+" for seed "+seed+": counted "+count+", found "+actual);
                if(count>0) {groups++;total+=count;}
                if(count>=2) clusters++;
            }
            h.assertTrue(groups>=14 && groups<=54 && clusters>=10 && seen.size()>=3,"96 seed walks: patches="+groups+", clusters="+clusters+", species="+seen.size());
            com.mojang.logging.LogUtils.getLogger().info("[TN-C Wild Garden] 96 seed trials on {}: {} patches, {} clustered patches, {} roots, {} visible species",cp,groups,clusters,total,seen.size());
        } finally {
            planted.forEach((p,s)->level.setBlock(p,s,18));
            soils.forEach((p,s)->level.setBlock(p,s,18));
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=120)
    public static void shoreDepthSeedsAndNoOverwrite(GameTestHelper h) {
        var level=h.getLevel();
        var cp=new ChunkPos(h.absolutePos(new BlockPos(2,2,2)));
        var p=new BlockPos(cp.getMiddleBlockX(),h.absolutePos(new BlockPos(0,6,0)).getY(),cp.getMiddleBlockZ());
        var chunk=level.getChunkAt(p);
        level.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        level.setBlock(p.below(2),Blocks.CLAY.defaultBlockState(),18);
        level.setBlock(p.below(),Blocks.WATER.defaultBlockState(),18);
        h.assertTrue(WildPlantCommunities.habitat(level,chunk,p)==WildPlantCommunities.Habitat.POOL,"Shallow still water is a lotus basin");
        h.assertTrue(WildPlantCommunities.plant(level,p,"mirror_lotus"),"Naturally placed lotus");
        h.assertTrue(level.getBlockState(p).getValue(BotanicalBlock.WILD) && level.getBlockEntity(p) instanceof BotanicalPlantEntity plant && plant.wild && plant.growth>0,"Root and saved data both mark a mature wild specimen");
        h.assertTrue(!WildPlantCommunities.plant(level,p,"mirror_lotus"),"Do not replace an existing specimen");
        var bank=p.east();
        level.setBlock(bank,Blocks.AIR.defaultBlockState(),18);
        level.setBlock(bank.below(),Blocks.DIRT.defaultBlockState(),18);
        var shore=WildPlantCommunities.habitat(level,chunk,bank);
        h.assertTrue(shore==WildPlantCommunities.Habitat.BANK || shore==WildPlantCommunities.Habitat.COAST,"A physical pond produces a shoreline even in a non-river biome");
        h.assertTrue(WildPlantCommunities.plant(level,bank,"wild_tide_reed"),"Bank gets the wild parent block");
        level.setBlock(bank,Blocks.CHEST.defaultBlockState(),18);
        h.assertTrue(!WildPlantCommunities.plant(level,bank,"wish_puff") && level.getBlockState(bank).is(Blocks.CHEST),"Keep occupied blocks and their entities");
        level.setBlock(bank,Blocks.AIR.defaultBlockState(),18);
        h.assertTrue(!WildPlantCommunities.plant(level,bank,"steam_lotus"),"Water/fire mutation is excluded from natural placement");
        level.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        level.setBlock(p.below(2),Blocks.WATER.defaultBlockState(),18);
        h.assertTrue(WildPlantCommunities.habitat(level,chunk,p)==null,"Deep water remains open");
        h.assertTrue(!WildPlantCommunities.interior(cp,cp.getMinBlockX()+1,cp.getMinBlockZ()+3),"No neighbor probes at the chunk edge");
        h.succeed();
    }
}
