package com.tnc.tnc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.IOException;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class PortalArchitectureGameTests {
    private static final ResourceLocation GATE = ResourceLocation.tryParse("tnc:sky_island/portal/ritual_gate");

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void initialPortalAcceptsOriginalIslandWildflowers(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(736,200,112);
        // Actual failure: source town_piece_1_0_6 has a dandelion at portal-local [7,1,8].
        for (int x=0;x<15;x++) for (int z=0;z<15;z++)
            level.setBlock(origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),18);
        level.setBlock(origin.offset(7,1,8),Blocks.DANDELION.defaultBlockState(),18);
        level.setBlock(origin.offset(8,1,7),Blocks.AZURE_BLUET.defaultBlockState(),18);
        var plan = PortalArchitecture.prepare(level,GATE,origin,false);
        plan.apply(level);
        if (level.getBlockState(origin.offset(7,1,8)).is(Blocks.DANDELION)
                || !level.getBlockState(origin.offset(7,16,7)).is(Blocks.SHROOMLIGHT))
            throw new IllegalStateException("Flowered source ground prevented portal completion");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void allLegacyAltarsUpgradeWithoutLeavingOldBlocks(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        for (int revision = 0; revision < 3; revision++) {
            BlockPos origin = new BlockPos(512 + revision * 32, 200, 112);
            var id = ResourceLocation.tryParse("tnc:sky_island/portal/legacy_v" + revision);
            var old = level.getStructureManager().get(id).orElseThrow();
            old.placeInWorld(level, origin, origin, new StructurePlaceSettings().setKnownShape(true), RandomSource.create(1), 18);
            PortalArchitecture.prepare(level, GATE, origin, true).apply(level);
            // A repeated operator command or interrupted finalization must be safe and idempotent.
            PortalArchitecture.prepare(level, GATE, origin, true).apply(level);
            if (!level.getBlockState(origin.offset(7,16,7)).is(Blocks.SHROOMLIGHT))
                throw new IllegalStateException("Missing raised core for revision " + revision);
            if (!level.getBlockState(origin.offset(4,10,4)).is(Blocks.RED_STAINED_GLASS))
                throw new IllegalStateException("Laser emitter and renderer do not align");
            for (int y=2; y<13; y++) if (!level.getBlockState(origin.offset(7,y,7)).isAir())
                throw new IllegalStateException("Old crystal/altar blocked the open center");
        }
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void portalRefreshRejectsContainersAndPlayerChangesBeforeWriting(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(640,200,112);
        PortalArchitecture.prepare(level,GATE,origin,false).apply(level);
        PortalArchitecture.prepare(level,GATE,origin,false).apply(level); // Crash retry is idempotent.
        BlockPos chest = origin.offset(4,10,4);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(),18);
        boolean rejected = false;
        try { PortalArchitecture.prepare(level,GATE,origin,true); }
        catch (IOException expected) { rejected = true; }
        if (!rejected || !level.getBlockState(chest).is(Blocks.CHEST))
            throw new IllegalStateException("Refresh did not preserve container");
        level.setBlock(chest, Blocks.GOLD_BLOCK.defaultBlockState(),18);
        rejected = false;
        try { PortalArchitecture.prepare(level,GATE,origin,true); }
        catch (IOException expected) { rejected = true; }
        if (!rejected || !level.getBlockState(chest).is(Blocks.GOLD_BLOCK))
            throw new IllegalStateException("Refresh did not preserve player replacement");
        level.setBlock(chest, Blocks.RED_STAINED_GLASS.defaultBlockState(),18);
        BlockPos floor = origin.offset(7,1,7);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(),18);
        rejected = false;
        try { PortalArchitecture.prepare(level,GATE,origin,true); }
        catch (IOException expected) { rejected = true; }
        if (!rejected || !level.getBlockState(floor).is(Blocks.STONE))
            throw new IllegalStateException("Refresh accepted a player's natural-material floor edit");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void freshPortalRejectsOverheadObstacleWithoutPartialPlacement(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(688,200,112);
        level.setBlock(origin.offset(7,20,7),Blocks.GOLD_BLOCK.defaultBlockState(),18);
        boolean rejected = false;
        try { PortalArchitecture.prepare(level,GATE,origin,false); }
        catch (IOException expected) { rejected = true; }
        if (!rejected || !level.getBlockState(origin.offset(7,1,7)).isAir())
            throw new IllegalStateException("Obstructed new portal partially placed");
        helper.succeed();
    }
}
