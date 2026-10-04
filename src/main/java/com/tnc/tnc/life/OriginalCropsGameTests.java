package com.tnc.tnc.life;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class OriginalCropsGameTests {
    private OriginalCropsGameTests() {}

    @GameTest(template = "building_test_empty", timeoutTicks = 30)
    public static void wildDiscoveryGivesProofAndCultivatedHarvestKeepsSeed(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        Block[] wild = {OriginalCrops.WILD_ROAD_BELL, OriginalCrops.WILD_NIGHT_GOURD,
                OriginalCrops.WILD_TIDE_REED};
        Block[] crop = {OriginalCrops.ROAD_BELL_CROP, OriginalCrops.NIGHT_GOURD_CROP,
                OriginalCrops.TIDE_REED_CROP};
        var seeds = new net.minecraft.world.item.Item[]{OriginalCrops.ROAD_BELL_SEED,
                OriginalCrops.NIGHT_GOURD_SEED, OriginalCrops.TIDE_REED_SEED};
        var produce = new net.minecraft.world.item.Item[]{OriginalCrops.ROAD_BELL_EAR,
                OriginalCrops.NIGHT_GOURD, OriginalCrops.TIDE_REED_STEM};
        var samples = new net.minecraft.world.item.Item[]{OriginalCrops.ROAD_BELL_WILD_SAMPLE,
                OriginalCrops.NIGHT_GOURD_WILD_SAMPLE, OriginalCrops.TIDE_REED_WILD_SAMPLE};
        for (int i = 0; i < wild.length; i++) {
            var found = Block.getDrops(wild[i].defaultBlockState(), h.getLevel(), pos, null);
            h.assertTrue(count(found, seeds[i]) == 1 && count(found, produce[i]) == 1
                    && count(found, samples[i]) == 1,
                    "Wild specimen supplies one seed, useful produce, and unique exploration proof: " + i);
            BlockState mature = i == 2
                    ? crop[i].defaultBlockState().setValue(OriginalCrops.TideReedCrop.AGE, 3)
                    : crop[i].defaultBlockState().setValue(CropBlock.AGE, 7);
            var harvest = Block.getDrops(mature, h.getLevel(), pos, null);
            h.assertTrue(count(harvest, seeds[i]) == 2 && count(harvest, produce[i]) == 2
                    && count(harvest, samples[i]) == 0,
                    "Cultivated harvest guarantees replanting without forging a wild sample: " + i);
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 30)
    public static void nightGourdAvoidsDaylightAndGrowsInShadeAndReedRequiresWetShore(GameTestHelper h) {
        var level = h.getLevel();
        // Keep every edit inside the GameTest template. The server caches sky
        // darkness, so changing its clock within one test tick is misleading.
        h.assertTrue(!OriginalCrops.NightGourdCrop.darkEnough(15, true)
                        && OriginalCrops.NightGourdCrop.darkEnough(15, false)
                        && OriginalCrops.NightGourdCrop.darkEnough(0, true),
                "Open day pauses growth; night or a sheltered field permits it");
        BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
        var touched = new java.util.LinkedHashMap<BlockPos, BlockState>();
        touched.put(pos, level.getBlockState(pos));
        touched.put(pos.below(), level.getBlockState(pos.below()));
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.below().relative(direction);
            touched.put(neighbor, level.getBlockState(neighbor));
        }
        try {
            h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) < 8,
                    "The underground template gives a stable sheltered growth fixture");
            level.setBlockAndUpdate(pos.below(), Blocks.FARMLAND.defaultBlockState()
                    .setValue(FarmBlock.MOISTURE, 7));
            level.setBlockAndUpdate(pos, OriginalCrops.NIGHT_GOURD_CROP.defaultBlockState());
            var random = RandomSource.create(119);
            for (int i = 0; i < 120; i++) {
                var current = level.getBlockState(pos);
                OriginalCrops.NIGHT_GOURD_CROP.randomTick(current, level, pos, random);
            }
            h.assertTrue(level.getBlockState(pos).getValue(CropBlock.AGE) == 7,
                    "A sheltered night-window gourd reaches maturity without vanilla's daylight gate");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
                level.setBlockAndUpdate(pos.below().relative(direction), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(pos.below().east(), Blocks.WATER.defaultBlockState());
            h.assertTrue(OriginalCrops.TIDE_REED_CROP.defaultBlockState().canSurvive(level, pos),
                    "Tide reed roots next to shallow water");
            level.setBlockAndUpdate(pos.below().east(), Blocks.DIRT.defaultBlockState());
            h.assertTrue(!OriginalCrops.TIDE_REED_CROP.defaultBlockState().canSurvive(level, pos),
                    "Tide reed cannot turn into an ordinary dry-field crop");
        } finally {
            touched.forEach(level::setBlockAndUpdate);
        }
        h.succeed();
    }

    private static int count(java.util.List<net.minecraft.world.item.ItemStack> stacks,
                             net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
    }
}
