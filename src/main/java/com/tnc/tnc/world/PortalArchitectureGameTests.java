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
    public static void completedRevisionFourReloadResumesPendingManualRefresh(GameTestHelper helper) {
        var data=new SkyIslandSavedData();
        data.phase=SkyIslandSavedData.Phase.COMPLETE; data.layoutReady=true; data.portalRevision=4;
        data.activeChunks.add(new net.minecraft.world.level.ChunkPos(6000,6000).toLong());
        var reloaded=SkyIslandSavedData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        if (!reloaded.isSkyIslandComplete() || !SkyIslandManager.needsPortalRefresh(reloaded))
            throw new IllegalStateException("Reload abandoned pending refresh and its retained chunk tickets");
        reloaded.activeChunks.clear();
        if (SkyIslandManager.needsPortalRefresh(reloaded))
            throw new IllegalStateException("Completed refresh should not be repeated on every login");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void missingEntitySectionsAreNotTreatedAsEmptySafeSites(GameTestHelper helper) {
        var data=new SkyIslandSavedData(); data.activeChunks.add(new net.minecraft.world.level.ChunkPos(6000,6000).toLong());
        if (SkyIslandManager.portalEntitiesReady(helper.getLevel(),data))
            throw new IllegalStateException("Unloaded saved entities incorrectly treated as an empty site");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void loadedGuardianInNewStatueCellBlocksUpgradeButCourtyardIsSafe(GameTestHelper helper) {
        var l=helper.getLevel();var origin=new BlockPos(4832,200,112);
        var chunks=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x=(origin.getX()-10)>>4;x<=(origin.getX()+24)>>4;x++)
            for (int z=(origin.getZ()-10)>>4;z<=(origin.getZ()+24)>>4;z++) {
                var c=new net.minecraft.world.level.ChunkPos(x,z);chunks.add(c);
                l.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,c,2,c);
            }
        helper.succeedWhen(()->{
            if (chunks.stream().anyMatch(c->!l.areEntitiesLoaded(c.toLong())))
                throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for entity sections");
            var guardian=new net.minecraft.world.entity.decoration.ArmorStand(l,origin.getX()+16.5,origin.getY()+12,origin.getZ()+16.5);
            l.addFreshEntity(guardian);
            boolean rejected=false;
            try {PortalArchitecture.prepare(l,GATE,origin,false);} catch (IOException expected) {rejected=true;}
            if (!rejected || !l.getBlockState(origin.offset(7,1,7)).isAir()) throw new IllegalStateException("Guardian was built into a statue");
            l.setBlock(origin.offset(7,1,7),Blocks.CRYING_OBSIDIAN.defaultBlockState(),18);
            guardian.setPos(origin.getX()+7.5,origin.getY()+2,origin.getZ()+7.5);
            try {PortalArchitecture.prepare(l,GATE,origin,false).apply(l);}
            catch (IOException failure) {throw new IllegalStateException(failure);}
            if (!guardian.isAlive()) throw new IllegalStateException("Courtyard guardian was removed");
            for (var c:chunks) l.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,c,2,c);
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void supportsNeverReplaceWaterloggedChest(GameTestHelper helper) throws Exception {
        var l=helper.getLevel();var origin=new BlockPos(4704,200,112);var chest=origin.offset(7,-1,-2);
        l.setBlock(chest,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),18);
        ((net.minecraft.world.Container)l.getBlockEntity(chest)).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,9));
        PortalArchitecture.prepareSupports(l,origin).apply(l);
        if (!l.getBlockState(chest).is(Blocks.CHEST) || ((net.minecraft.world.Container)l.getBlockEntity(chest)).getItem(0).getCount()!=9)
            throw new IllegalStateException("Foundation replaced waterlogged container");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void repeatRefreshProtectsExpandedNaturalMaterialFloorEdit(GameTestHelper helper) throws Exception {
        var l=helper.getLevel();var origin=new BlockPos(4768,200,112);
        PortalArchitecture.prepare(l,GATE,origin,false).apply(l);
        var edited=origin.offset(7,1,-2); l.setBlock(edited,Blocks.STONE.defaultBlockState(),18);
        boolean rejected=false;
        try {PortalArchitecture.prepare(l,GATE,origin,true);} catch (IOException expected) {rejected=true;}
        if (!rejected || !l.getBlockState(edited).is(Blocks.STONE)) throw new IllegalStateException("Repeated upgrade overwrote outer floor edit");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void initialPortalAcceptsOriginalIslandWildflowers(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(4448,200,112);
        // Actual failure: source town_piece_1_0_6 has a dandelion at portal-local [7,1,8].
        for (int x=0;x<15;x++) for (int z=0;z<15;z++)
            level.setBlock(origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),18);
        level.setBlock(origin.offset(7,1,8),Blocks.DANDELION.defaultBlockState(),18);
        level.setBlock(origin.offset(8,1,7),Blocks.AZURE_BLUET.defaultBlockState(),18);
        var plan = PortalArchitecture.prepare(level,GATE,origin,false);
        plan.apply(level);
        if (level.getBlockState(origin.offset(7,1,8)).is(Blocks.DANDELION)
                || !level.getBlockState(origin.offset(7,22,7)).is(Blocks.SHROOMLIGHT))
            throw new IllegalStateException("Flowered source ground prevented portal completion");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void allLegacyAltarsUpgradeWithoutLeavingOldBlocks(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        for (int revision = 0; revision < 4; revision++) {
            BlockPos origin = new BlockPos(4096 + revision * 64, 200, 112);
            var id = ResourceLocation.tryParse("tnc:sky_island/portal/legacy_v" + revision);
            var old = level.getStructureManager().get(id).orElseThrow();
            old.placeInWorld(level, origin, origin, new StructurePlaceSettings().setKnownShape(true), RandomSource.create(1), 18);
            PortalArchitecture.prepare(level, GATE, origin, true).apply(level);
            // A repeated operator command or interrupted finalization must be safe and idempotent.
            PortalArchitecture.prepare(level, GATE, origin, true).apply(level);
            if (!level.getBlockState(origin.offset(7,22,7)).is(Blocks.SHROOMLIGHT))
                throw new IllegalStateException("Missing raised core for revision " + revision);
            if (!level.getBlockState(origin.offset(1,10,1)).is(Blocks.RED_STAINED_GLASS))
                throw new IllegalStateException("Laser emitter and renderer do not align");
            for (int y=2; y<18; y++) if (!level.getBlockState(origin.offset(7,y,7)).isAir())
                throw new IllegalStateException("Old crystal/altar blocked the open center");
        }
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void portalRefreshRejectsContainersAndPlayerChangesBeforeWriting(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(4512,200,112);
        PortalArchitecture.prepare(level,GATE,origin,false).apply(level);
        PortalArchitecture.prepare(level,GATE,origin,false).apply(level); // Crash retry is idempotent.
        BlockPos chest = origin.offset(1,10,1);
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
        BlockPos origin = new BlockPos(4576,200,112);
        level.setBlock(origin.offset(7,20,7),Blocks.GOLD_BLOCK.defaultBlockState(),18);
        boolean rejected = false;
        try { PortalArchitecture.prepare(level,GATE,origin,false); }
        catch (IOException expected) { rejected = true; }
        if (!rejected || !level.getBlockState(origin.offset(7,1,7)).isAir())
            throw new IllegalStateException("Obstructed new portal partially placed");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void expansionPreservesOldPathButRejectsTallPlayerBuild(GameTestHelper helper) throws Exception {
        var l=helper.getLevel(); var origin=new BlockPos(4640,200,112);
        var path=origin.offset(7,0,-9); var fence=origin.offset(5,1,-3);
        l.setBlock(path,Blocks.MOSSY_COBBLESTONE.defaultBlockState(),18);
        l.setBlock(fence,Blocks.SPRUCE_FENCE.defaultBlockState(),18);
        PortalArchitecture.prepare(l,GATE,origin,false).apply(l);
        PortalArchitecture.prepare(l,GATE,origin,true).apply(l);
        if (!l.getBlockState(path).is(Blocks.MOSSY_COBBLESTONE) || !l.getBlockState(fence).is(Blocks.SPRUCE_FENCE))
            throw new IllegalStateException("Expanded rim overwrote original village path");
        var obstruction=origin.offset(16,12,16); l.setBlock(obstruction,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        boolean rejected=false;
        try {PortalArchitecture.prepare(l,GATE,origin,true);} catch (IOException expected) {rejected=true;}
        if (!rejected || !l.getBlockState(obstruction).is(Blocks.GOLD_BLOCK))
            throw new IllegalStateException("Expanded statue replaced player's build");
        helper.succeed();
    }
}
