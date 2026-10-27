package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("tncstartup")
public final class EndIslandStartupGameTests {
    @GameTestGenerator public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("startup","end_arena_native_site","tncstartup:building_test_empty",
                Rotation.NONE,1200,0,true,EndIslandStartupGameTests::site));
    }
    private static void site(GameTestHelper helper) {
        var end=helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end!=null&&LandmarkGenerationMode.enabled(end),"New End must enable native chunks");
        var id=ResourceLocation.parse("tnc:end_pvp_island");
        var structure=end.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        helper.assertTrue(structure instanceof LargeLandmarkStructure,"Missing End arena structure");
        var plains=end.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        helper.assertTrue(!structure.biomes().contains(plains),"Arena still admits Overworld plains");
        var set=end.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id);
        var placement=(RandomSpreadStructurePlacement)set.placement();
        var generator=end.getChunkSource().getGenerator();var state=end.getChunkSource().getGeneratorState();
        for(int z=1;z<=4;z++)for(int x=1;x<=4;x++){
            var anchor=placement.getPotentialStructureChunk(state.getLevelSeed(),x*placement.spacing(),z*placement.spacing());
            var context=new Structure.GenerationContext(end.registryAccess(),generator,generator.getBiomeSource(),state.randomState(),
                    end.getStructureManager(),state.getLevelSeed(),anchor,end,structure.biomes()::contains);
            var stub=structure.findValidGenerationPoint(context);if(stub.isEmpty())continue;
            var marker=(LargeLandmarkMarker)stub.get().getPiecesBuilder().build().pieces().get(0);
            var dimensions=StonecrestManifest.get(marker.asset).dimensions();
            helper.assertTrue(marker.origin.getY()>=end.getMinBuildHeight()&&marker.origin.getY()+dimensions.getY()<=end.getMaxBuildHeight(),"Arena clipped by End height");
            var far=new net.minecraft.world.level.ChunkPos(marker.origin.offset(dimensions.getX()-1,0,dimensions.getZ()-1));
            helper.assertTrue(LandmarkSites.touching(end,far,0).stream().anyMatch(s->s.asset().equals("end_pvp_island")&&s.origin().equals(marker.origin)),"Far wing does not resolve same End island");
            com.mojang.logging.LogUtils.getLogger().info("[TN-C Startup Test] End arena native site={}, dimensions={}",marker.origin,dimensions);
            helper.succeed();return;
        }
        helper.fail("No complete End arena candidate among 16 deterministic regions");
    }
}
