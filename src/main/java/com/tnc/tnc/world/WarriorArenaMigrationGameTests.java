package com.tnc.tnc.world;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.warrior.PracticeArena;
import com.tnc.tnc.warrior.TrainingGround;
import com.tnc.tnc.warrior.TrainingGroundData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercise the real island-arena migration and its two player entry points. */
@GameTestHolder("tncwarriorpractical")
@PrefixGameTestTemplate(false)
public final class WarriorArenaMigrationGameTests {
    @GameTest(template = "building_test_empty", timeoutTicks = 600)
    public static void arenaMovesItsTerminalAndAllFourPracticePads(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var overworld = server.overworld();
        var storage = overworld.getDataStorage();
        var previousIsland = SkyIslandSavedData.get(overworld);
        var previousTraining = TrainingGroundData.get(server);
        var choice = SkyIslandWorlds.Choice.get(server);
        boolean previousRealm = choice.realm;

        var island = new SkyIslandSavedData();
        island.phase = SkyIslandSavedData.Phase.COMPLETE;
        island.layoutReady = true;
        island.originX = -260;
        island.originY = 90;
        island.originZ = -260;
        island.arrivalY = 180;
        var data = new TrainingGroundData();

        try {
            // No asynchronous callbacks run with these temporary island and arena ledgers installed.
            choice.realm = true;
            storage.set(SkyIslandSavedData.FILE_ID, island);
            storage.set("tnc_training_ground_v1", data);
            var level = SkyIslandWorlds.island(server);
            String dimension = level.dimension().location().toString();
            String legacyDimension = overworld.dimension().location().toString();
            var legacyIslandSite = new BlockPos(25, 179, 163);
            var legacyGroundSite = new BlockPos(-176, 105, -4);
            var target = new BlockPos(206, 177, -24);
            var deck = target.below();
            var oldIslandArena = new TrainingGroundData.Arena(dimension, 25, 179, 163);
            var oldGroundArena = new TrainingGroundData.Arena(legacyDimension, -176, 105, -4);
            data.markArena(dimension, legacyIslandSite.getX(), legacyIslandSite.getY(), legacyIslandSite.getZ());
            data.markArena(legacyDimension, legacyGroundSite.getX(), legacyGroundSite.getY(), legacyGroundSite.getZ());
            var formerLearner = UUID.randomUUID();
            data.claim(formerLearner, oldIslandArena.key());
            data.claim(formerLearner, oldGroundArena.key());
            data.complete(formerLearner, 0, 85, 12);

            level.setBlock(legacyIslandSite, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(legacyIslandSite.above(), TNMod.PRACTICE_TERMINAL.get().defaultBlockState(), 3);
            level.setBlock(legacyIslandSite.east(), Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
            overworld.setBlock(legacyGroundSite, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            overworld.setBlock(legacyGroundSite.above(), TNMod.PRACTICE_TERMINAL.get().defaultBlockState(), 3);
            level.setBlock(deck, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            var beyondNewDeck = deck.east(31);
            level.setBlock(beyondNewDeck, Blocks.OAK_LOG.defaultBlockState(), 3);
            var approach = deck.offset(0, 4, 36);
            level.setBlock(approach, Blocks.GRASS_BLOCK.defaultBlockState(), 3);

            h.assertTrue(deck.equals(PracticeArena.site(server)), "The requested terminal sits one block above the new deck");
            level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
            h.assertTrue(!PracticeArena.ensure(server) && level.getBlockState(target).is(Blocks.CHEST)
                            && level.getBlockState(legacyIslandSite.above()).is(TNMod.PRACTICE_TERMINAL.get())
                            && data.arenas().size() == 2,
                    "A container in the construction area keeps both the terrain and the previous arena intact");
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            h.assertTrue(PracticeArena.ensure(server), "An existing island with legacy arenas builds the new main deck");
            h.assertTrue(level.getBlockState(target).is(TNMod.PRACTICE_TERMINAL.get())
                            && level.getBlockState(deck).is(Blocks.CHISELED_STONE_BRICKS),
                    "The real terminal and its base are built at 206,177,-24 in the sky island dimension");
            h.assertTrue(!level.getBlockState(legacyIslandSite.above()).is(TNMod.PRACTICE_TERMINAL.get())
                            && !overworld.getBlockState(legacyGroundSite.above()).is(TNMod.PRACTICE_TERMINAL.get()),
                    "Migration retires the obsolete terminals from both dimensions");
            h.assertTrue(level.getBlockState(legacyIslandSite).is(Blocks.CHISELED_STONE_BRICKS)
                            && level.getBlockState(legacyIslandSite.east()).is(Blocks.EMERALD_BLOCK)
                            && overworld.getBlockState(legacyGroundSite).is(Blocks.GOLD_BLOCK)
                            && level.getBlockState(beyondNewDeck).is(Blocks.OAK_LOG),
                    "Migration preserves both old grounds and the scenery beyond the new deck");
            h.assertTrue(data.arenas().size() == 1 && data.arenas().get(0).center().equals(deck)
                            && data.slot(formerLearner, oldIslandArena.key()) == -1
                            && data.slot(formerLearner, oldGroundArena.key()) == -1,
                    "Only the relocated arena and its slots remain active");
            h.assertTrue(data.record(formerLearner).completions() == 1 && data.record(formerLearner).score() == 85,
                    "Retiring old practice slots keeps the learner's results");

            int[] stairRise = {1, 2, 3, 4, 4};
            for (int i = 0; i < stairRise.length; i++) {
                var step = deck.offset(0, stairRise[i], 31 + i);
                h.assertTrue(level.getBlockState(step).is(Blocks.STONE_BRICKS) && level.isEmptyBlock(step.above())
                                && level.isEmptyBlock(step.above(2)),
                        "The south staircase climbs out of the arena toward the higher yard, with room to walk");
            }
            h.assertTrue(level.getBlockState(approach).is(Blocks.GRASS_BLOCK),
                    "The staircase meets the existing approach at its natural height");

            var padFloors = new BlockPos[]{deck.offset(-17, 0, -17), deck.offset(17, 0, -17),
                    deck.offset(-17, 0, 17), deck.offset(17, 0, 17)};
            var padMaterials = new Block[]{Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK,
                    Blocks.LAPIS_BLOCK, Blocks.SEA_LANTERN};
            for (int i = 0; i < padFloors.length; i++) {
                var floor = padFloors[i];
                var arena = data.arenaAt(dimension, floor.getX() + .5, floor.getY() + 1, floor.getZ() + .5);
                h.assertTrue(level.getBlockState(floor).is(padMaterials[i]) && level.isEmptyBlock(floor.above())
                                && level.isEmptyBlock(floor.above(2)) && arena != null && arena.center().equals(deck),
                        "Practice pad " + (i + 1) + " has a supported landing in the relocated arena");
            }

            // A later ensure call must not rebuild or erase additions to the new deck.
            var decoration = deck.offset(3, 1, 3);
            level.setBlock(decoration, Blocks.COPPER_BLOCK.defaultBlockState(), 3);
            h.assertTrue(PracticeArena.ensure(server) && data.arenas().size() == 1
                            && level.getBlockState(decoration).is(Blocks.COPPER_BLOCK),
                    "Repeated ensure does not rebuild the already migrated deck");

            var player = AdventureGameTests.player(h);
            try {
                h.assertTrue(PracticeArena.teleportIn(player) && player.serverLevel() == level
                                && player.getX() == 206.5 && player.getY() == 177 && TrainingGround.inside(player),
                        "The practice command routes into the new sky island arena");
                player.setPos(208.5, 177, -23.5);
                Vec3 standing = player.position();
                var hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
                var result = level.getBlockState(target).use(level, player, InteractionHand.MAIN_HAND, hit);
                h.assertTrue(result == InteractionResult.CONSUME && standing.equals(player.position()),
                        "Right-clicking the relocated terminal opens practice at the player's current position");
            } finally {
                player.discard();
            }
        } finally {
            storage.set(SkyIslandSavedData.FILE_ID, previousIsland);
            storage.set("tnc_training_ground_v1", previousTraining);
            choice.realm = previousRealm;
        }
        h.succeed();
    }

    private WarriorArenaMigrationGameTests() {}
}
