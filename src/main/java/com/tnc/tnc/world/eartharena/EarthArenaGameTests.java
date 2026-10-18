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
}
