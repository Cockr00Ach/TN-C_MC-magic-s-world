package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class LandmarkChunkGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=2400)
    public static void eldenAndPalaceUseTheSameNormalWorldSiteAsLocate(GameTestHelper helper) {
        var l=helper.getLevel(); var access=l.registryAccess();
        var generator=access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).createWorldDimensions().overworld();
        var noise=(net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)generator;
        // Reproducible terrain fixture: random GameTest world seeds can contain no eligible large plateau in this search window.
        long seed=9047476067964293855L;
        var random=net.minecraft.world.level.levelgen.RandomState.create(noise.generatorSettings().value(),
                access.registryOrThrow(net.minecraft.core.registries.Registries.NOISE).asLookup(),seed);
        var state=generator.createState(access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).asLookup(),random,seed);
        var sets=access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        for (String id:List.of("elden_coastal_castle","stonecrest_fortress")) {
            var set=sets.get(new net.minecraft.resources.ResourceLocation("tnc",id));
            var structure=set.structures().get(0).structure().value();
            var placement=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)set.placement();
            LargeLandmarkMarker found=null; ChunkPos anchor=null;
            search: for (int z=-4;z<=4;z++) for (int x=-4;x<=4;x++) {
                var candidate=placement.getPotentialStructureChunk(seed,x*placement.spacing(),z*placement.spacing());
                var context=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(access,generator,generator.getBiomeSource(),
                        random,l.getStructureManager(),seed,candidate,l,structure.biomes()::contains);
                var stub=structure.findValidGenerationPoint(context);
                if (stub.isEmpty()) continue;
                found=(LargeLandmarkMarker)stub.get().getPiecesBuilder().build().pieces().get(0); anchor=candidate; break search;
            }
            if (found==null) throw new IllegalStateException("No normal-world valid site tested: "+id);
            var expected=found;
            for (var chunk:List.of(anchor,new ChunkPos(found.origin),new ChunkPos(found.origin.offset(160,0,32)))) {
                var sites=LandmarkSites.touching(l,chunk,32,generator,state);
                if (sites.stream().noneMatch(s->s.asset().equals(expected.asset) && s.origin().equals(expected.origin)))
                    throw new IllegalStateException("Arrival recovery/native placement disagree with locate: "+id+" "+chunk);
            }
        }
        helper.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void nativeSlicesClipAtNegativeMisalignedChunkBorders(GameTestHelper helper) {
        var level=helper.getLevel(); var origin=new BlockPos(-8191,128,-8191);
        var site=new LandmarkSites.Site("landmark_fixture",origin,new ChunkPos(origin));
        var left=new ChunkPos(origin); var right=new ChunkPos(origin.offset(18,0,2));
        var sentinel=origin.offset(18,1,2);
        level.setBlock(sentinel,Blocks.EMERALD_BLOCK.defaultBlockState(),18);
        var leftRegion=new WorldGenRegion(level,List.of(level.getChunk(left.x,left.z)),ChunkStatus.FEATURES,0);
        LandmarkChunkFeature.placeSlice(leftRegion,left,site);
        if (!level.getBlockState(origin.offset(2,1,2)).is(Blocks.DIAMOND_BLOCK)
                || !level.getBlockState(sentinel).is(Blocks.EMERALD_BLOCK))
            throw new IllegalStateException("Native template missing or wrote outside generating chunk");
        var rightRegion=new WorldGenRegion(level,List.of(level.getChunk(right.x,right.z)),ChunkStatus.FEATURES,0);
        LandmarkChunkFeature.placeSlice(rightRegion,right,site);
        if (!level.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK))
            throw new IllegalStateException("Adjacent native chunk did not finish its architecture");
        helper.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void remoteWingsAreIndexedBeyondVanillaReferenceRadius(GameTestHelper helper) {
        for (String asset:List.of("heroskand_complex","elden_coastal_castle","gothic_cathedral","end_pvp_island")) {
            var origin=new BlockPos(-10003,128,-14007); var m=StonecrestManifest.get(asset);
            int tested=0;
            for (var piece:m.pieces()) {
                var pos=origin.offset(piece.offset());
                var chunk=new ChunkPos(pos);
                if (Math.abs(chunk.z-new ChunkPos(origin.offset(m.dimensions().getX()/2,0,m.dimensions().getZ()+12)).z)<=8) continue;
                if (!LandmarkChunkFeature.piecesFor(asset,origin,chunk).contains(piece))
                    throw new IllegalStateException("Far wing lost: "+piece.resource());
                tested++;
            }
            if (tested==0) throw new IllegalStateException("Test did not exercise a far wing: "+asset);
        }
        helper.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void modeSurvivesReloadAndLegacyDefaultStaysDisabled(GameTestHelper helper) {
        var old=new net.minecraft.nbt.CompoundTag();
        if (LandmarkGenerationMode.load(old).nativeChunks)
            throw new IllegalStateException("Legacy worlds cannot silently switch generation algorithm");
        var tag=new LandmarkGenerationMode(true).save(new net.minecraft.nbt.CompoundTag());
        if (!LandmarkGenerationMode.load(tag).nativeChunks) throw new IllegalStateException("Native generation mode lost on reload");
        helper.succeed();
    }
}
