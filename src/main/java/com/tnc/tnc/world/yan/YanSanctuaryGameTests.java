package com.tnc.tnc.world.yan;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder("tncyan")
@PrefixGameTestTemplate(false)
public final class YanSanctuaryGameTests {
    @GameTest(template = "empty", timeoutTicks = 120, batch = "yan_sanctuary")
    public static void dimensionHasFlatTerrainAltarAndStormFlame(GameTestHelper helper) {
        var level = realm(helper);
        var gen = level.getChunkSource().getGenerator();
        helper.assertTrue(gen instanceof YanSanctuaryGenerator, "The custom generator is active");
        for (var at : new BlockPos[]{new BlockPos(64, 64, 64), new BlockPos(-82, 64, 83), new BlockPos(136, 64, 58)}) {
            level.getChunkAt(at);
            helper.assertTrue(level.getBlockState(at).is(Blocks.OBSIDIAN), "Outside the altar the flat surface is obsidian");
            helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) == 65, "Surface is flat");
        }
        var altar = YanSanctuaryLayout.ALTAR;
        level.getChunkAt(altar);
        helper.assertTrue(level.getBlockState(altar).is(YanSanctuary.ALTAR), "The central altar must be usable");
        var arrival = YanSanctuaryLayout.ARRIVAL;
        level.getChunkAt(arrival);
        helper.assertTrue(level.getBlockState(arrival).isAir() && !level.getBlockState(arrival.below()).isAir(),
                "The south arrival has free space and solid support");
        var flame = new BlockPos(26, 80, -30);
        level.getChunkAt(flame);
        var state = level.getBlockState(flame);
        helper.assertTrue(state.is(YanSanctuary.FLAME) && state.getLightEmission() == 13, "The pylon burns with real block light");
        helper.assertTrue(state.canSurvive(level, flame) && state.getCollisionShape(level, flame).isEmpty(),
                "Storm flame has support and no collision");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "yan_sanctuary")
    public static void blueprintKeepsItsShapeWithOneMaterial(GameTestHelper helper) throws Exception {
        var level = realm(helper);
        int[] entries;
        try (var input = YanSanctuaryGameTests.class.getResourceAsStream("/data/tnc/blueprints/aurelian.nbt")) {
            entries = NbtIo.readCompressed(input).getIntArray("Blocks");
        }
        var origin = YanSanctuaryLayout.DRAGON;
        var head = origin.offset(46, 220, 242);
        var points = new HashSet<BlockPos>();
        var tested = Set.of(new ChunkPos(head), new ChunkPos(origin.offset(150, 90, 130)),
                new ChunkPos(origin.offset(10, 115, 75)), new ChunkPos(origin.offset(259, 95, 234)));
        for (int i = 0; i < entries.length; i += 4) {
            var pos = origin.offset(entries[i], entries[i + 1], entries[i + 2]);
            if (entries[i + 3] != 0 && tested.contains(new ChunkPos(pos))) points.add(pos);
        }
        helper.assertTrue(points.size() > 500, "The sampled chunks cover multiple large parts of the dragon");
        for (var chunk : tested) {
            var actual = level.getChunk(chunk.x, chunk.z);
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    int top = origin.getY();
                    for (int y = origin.getY(); y <= origin.getY() + 224; y++) {
                        var pos = new BlockPos(x, y, z);
                        var state = actual.getBlockState(pos);
                        boolean expected = points.contains(pos);
                        helper.assertTrue(expected ? state.is(YanSanctuary.OBSIDIAN) : state.isAir(),
                                "Wrong shape or material at " + pos);
                        if (expected) top = y + 1;
                    }
                    helper.assertTrue(actual.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1 == top,
                            "Dragon heightmap must match the statue");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "yan_sanctuary")
    public static void altarCreatesOneRealYanBoss(GameTestHelper helper) {
        var level = realm(helper);
        helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL, "Boss tests require a non-peaceful fixture");
        var encounter = YanSanctuaryEncounter.get(level);
        helper.assertTrue(encounter.summon(level).equals("summoned"), "The altar creates Yan");
        helper.assertTrue(encounter.summon(level).equals("active"), "The altar does not duplicate an active boss");
        var at = YanSanctuaryLayout.BOSS;
        var box = new net.minecraft.world.phys.AABB(at).inflate(12);
        helper.succeedWhen(() -> {
            var bosses = level.getEntitiesOfClass(com.tnc.tnc.boss.YanDarkBossEntity.class, box);
            helper.assertTrue(bosses.size() == 1 && bosses.get(0).isAlive(), "A live Yan is tracked on the platform");
            bosses.get(0).discard();
            helper.assertTrue(encounter.summon(level).equals("summoned"), "An explicitly removed boss can be challenged again");
            level.getEntitiesOfClass(com.tnc.tnc.boss.YanDarkBossEntity.class, box).forEach(net.minecraft.world.entity.Entity::discard);
        });
    }

    private static ServerLevel realm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(YanSanctuary.KEY);
        helper.assertTrue(level != null, "Yan's dimension is registered and loaded");
        return level;
    }
}
