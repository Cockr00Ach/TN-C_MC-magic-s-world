package com.tnc.tnc.world.stonecrest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.Blocks;

/** Explicit dev-only GameTests, not run during ordinary gameplay. */
@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class BuildingGameTests {
    @GameTest(template="building_test_empty")
    public static void importedCropDecorationsBecomePersistentVanillaFoliage(GameTestHelper helper) {
        for (String id: java.util.List.of("beautyberry_bush")) {
            var state = com.tnc.tnc.world.ConquestPlantProcessor.replacement(new ResourceLocation("conquest", id));
            if (state == null || !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT))
                throw new IllegalStateException("Replacement must not decay: " + id);
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7))
                throw new IllegalStateException("Replacement must not inherit crop support rules");
        }
        if (com.tnc.tnc.world.ConquestPlantProcessor.replacement(new ResourceLocation("conquest", "brown_granite")) != null
                || com.tnc.tnc.world.ConquestPlantProcessor.replacement(new ResourceLocation("minecraft", "wheat")) != null
                || com.tnc.tnc.world.ConquestPlantProcessor.replacement(new ResourceLocation("conquest", "hawthorn_bush")) != null
                || com.tnc.tnc.world.ConquestPlantProcessor.replacement(new ResourceLocation("conquest", "orange_tree_leaves")) != null)
            throw new IllegalStateException("Architecture or normal farming was replaced");
        var processor = com.tnc.tnc.world.ConquestPlantProcessor.INSTANCE;
        var info = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), new net.minecraft.nbt.CompoundTag());
        if (processor.processBlock(helper.getLevel(), BlockPos.ZERO, BlockPos.ZERO, info, info,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()) != info)
            throw new IllegalStateException("Unrelated template block/NBT must remain identical");
        helper.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void locateCoordinatesMatchSouthernMarkerCenter(GameTestHelper helper) {
        var registry=helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        var chunk=new net.minecraft.world.level.ChunkPos(829,42);
        for (String id:java.util.List.of("gothic_cathedral","stonecrest_fortress","end_pvp_island","elden_coastal_castle","abyss_citadel")) {
            var set=registry.get(net.minecraft.resources.ResourceLocation.tryParse("tnc:"+id));
            if (set==null || !set.placement().getLocatePos(chunk).equals(new BlockPos(13272,0,680)))
                throw new IllegalStateException("Locate offset disagrees with marker center: "+id);
        }
        helper.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void containerPreflightMigrationNeverRevivesPartialWrites(GameTestHelper helper) {
        var root=new net.minecraft.nbt.CompoundTag(); var list=new net.minecraft.nbt.ListTag();
        for (int phase=0;phase<3;phase++) {
            var t=new net.minecraft.nbt.CompoundTag(); t.putString("Asset","landmark_fixture");
            t.putLong("Origin",new BlockPos(1300+phase*64,128,112).asLong());t.putInt("Phase",-1);
            t.putString("Error","phase="+phase+" tile=108 piece=0: java.lang.IllegalStateException: Protected block entity in pending write volume");
            list.add(t);
        }
        root.put("Jobs",list); var d=LandmarkData.load(root); var jobs=new java.util.ArrayList<>(d.jobs.values());
        if (jobs.get(0).phase!=0 || jobs.get(1).phase!=-1 || jobs.get(2).phase!=-1)
            throw new IllegalStateException("Migration must only revive no-write preflight failures");
        jobs.get(0).phase=-1; jobs.get(0).error=((net.minecraft.nbt.CompoundTag)list.get(0)).getString("Error");
        if (LandmarkData.load(d.save(new net.minecraft.nbt.CompoundTag())).jobs.values().stream().anyMatch(j->j.phase!=-1))
            throw new IllegalStateException("Preflight migration retried twice");
        helper.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void existingChestIsPreservedLocallyInsteadOfCancellingEntireLandmark(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(1184,128,112);
        var chest=origin.offset(15,1,8); // Protection crosses a checkpoint/chunk boundary.
        l.setBlock(chest.below(),Blocks.GOLD_BLOCK.defaultBlockState(),18);
        l.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
        ((net.minecraft.world.Container)l.getBlockEntity(chest)).setItem(0,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        String key=new LandmarkData.Job("landmark_fixture",origin).key();
        LargeLandmarkJobs.request(l,"landmark_fixture",origin);
        boolean[] resumed={false};
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if (j!=null && j.phase<0) throw new IllegalStateException("Pre-existing chest stopped the whole structure: "+j.error);
            if (j==null || j.phase!=3) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for protected-site fixture");
            if (!l.getBlockState(chest).is(Blocks.CHEST) || !l.getBlockState(chest.below()).is(Blocks.GOLD_BLOCK)
                    || ((net.minecraft.world.Container)l.getBlockEntity(chest)).getItem(0).getCount()!=7
                    || !l.getBlockState(origin.offset(2,1,2)).is(Blocks.DIAMOND_BLOCK)
                    || !l.getBlockState(origin.offset(18,1,2)).is(Blocks.DIAMOND_BLOCK))
                throw new IllegalStateException("Container contents/support or remote architecture lost");
            if (!resumed[0]) {
                j.initialized=false; j.phase=1; resumed[0]=true;
                throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for immutable-plan recovery");
            }
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void realEndIslandProfileDoesNotRejectReportedSafeColumn(GameTestHelper helper) {
        var m=StonecrestManifest.get("end_pvp_island");
        var f=new FloatingFootprint(m.dimensions().getX(),m.dimensions().getZ(),m.dimensions().getY());
        for (var p:m.pieces()) if (p.offset().getX()==144 && p.offset().getZ()==48) {
            var manager=helper.getLevel().getStructureManager();
            var t=manager.get(p.resource()).orElseThrow();
            try { f.include(t.save(new net.minecraft.nbt.CompoundTag()),p.offset()); }
            finally { manager.remove(p.resource()); }
        }
        if (f.bottomAt(159,63)!=75 || f.intersects(170,132,159,63) || !f.intersects(207,132,159,63))
            throw new IllegalStateException("Actual island template underside regression");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void legacyFloatingPreflightFailureRetriesOnlyOnce(GameTestHelper helper) {
        var root=new net.minecraft.nbt.CompoundTag(); var jobs=new net.minecraft.nbt.ListTag();
        String[] errors={"phase=0 tile=87 piece=0: java.lang.IllegalStateException: Floating island would intersect existing terrain",
                "phase=2 tile=87 piece=0: java.lang.IllegalStateException: Floating island would intersect existing terrain",
                "phase=0 tile=87 piece=0: Protected block entity"};
        for (int i=0;i<3;i++) {
            var t=new net.minecraft.nbt.CompoundTag(); t.putString("Asset","landmark_floating_fixture");
            t.putLong("Origin",new BlockPos(992+64*i,200,112).asLong()); t.putInt("Phase",-1);t.putString("Error",errors[i]);jobs.add(t);
        }
        root.put("Jobs",jobs);
        var loaded=LandmarkData.load(root); var values=new java.util.ArrayList<>(loaded.jobs.values());
        if (values.get(0).phase!=0 || values.get(1).phase!=-1 || values.get(2).phase!=-1)
            throw new IllegalStateException("Migration revived an unsafe write or missed the legacy preflight");
        values.get(0).phase=-1; values.get(0).error=errors[0];
        var saved=loaded.save(new net.minecraft.nbt.CompoundTag());
        if (LandmarkData.load(saved).jobs.values().stream().anyMatch(j->j.phase!=-1))
            throw new IllegalStateException("Migration must not retry endlessly");
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void floatingIslandLeavesRaisedTerrainAndChestUnderItsActualUnderside(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(832,200,112);
        var under=origin.offset(4,10,4);
        l.setBlock(under,Blocks.CHEST.defaultBlockState(),18);
        String key=new LandmarkData.Job("landmark_floating_fixture",origin).key();
        LargeLandmarkJobs.request(l,"landmark_floating_fixture",origin);
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if (j!=null && j.phase<0) throw new IllegalStateException("Safe floating clearance rejected: "+j.error);
            if (j==null || j.phase!=3) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for floating fixture");
            if (!l.getBlockState(under).is(Blocks.CHEST)
                    || !l.getBlockState(origin.offset(2,17,2)).is(Blocks.DIAMOND_BLOCK)
                    || !l.getBlockState(origin.offset(18,1,2)).is(Blocks.DIAMOND_BLOCK))
                throw new IllegalStateException("Floating placement or unrelated container changed");
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void realFloatingIntersectionStopsBeforeAnyPlacement(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(896,200,112);
        var obstacle=origin.offset(18,0,2);
        for (int y=80;y<l.getMaxBuildHeight();y++) l.setBlock(new BlockPos(obstacle.getX(),y,obstacle.getZ()),Blocks.GOLD_BLOCK.defaultBlockState(),18);
        String key=new LandmarkData.Job("landmark_floating_fixture",origin).key();
        LargeLandmarkJobs.request(l,"landmark_floating_fixture",origin);
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if (j!=null && j.phase==3) throw new IllegalStateException("Real intersection was ignored");
            if (j==null || j.phase!=-1) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for collision guard");
            if (j.piece!=0 || !l.getBlockState(obstacle).is(Blocks.GOLD_BLOCK)
                    || !l.getBlockState(origin.offset(2,17,2)).isAir())
                throw new IllegalStateException("Collision guard partially wrote the island");
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void floatingTowerAvoidanceKeepsLocateXZAndSurvivesJournalRecovery(GameTestHelper helper) {
        var l=helper.getLevel();var original=new BlockPos(1568,132,112);
        var tower=original.offset(2,17,2);
        l.setBlock(tower,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        // A still higher floating tower must not be treated as solid terrain down to bedrock.
        l.setBlock(original.offset(18,117,2),Blocks.QUARTZ_BLOCK.defaultBlockState(),18);
        String key=new LandmarkData.Job("landmark_floating_fixture",original).key();
        LargeLandmarkJobs.request(l,"landmark_floating_fixture",original);
        boolean[] restored={false};
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if(j!=null && j.phase<0) throw new IllegalStateException(j.error);
            if(j==null || j.phase!=3) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for adaptive floating site");
            if(j.origin.getX()!=original.getX() || j.origin.getZ()!=original.getZ() || j.origin.getY()==original.getY()
                    || !l.getBlockState(tower).is(Blocks.GOLD_BLOCK)
                    || !l.getBlockState(j.origin.offset(2,17,2)).is(Blocks.DIAMOND_BLOCK))
                throw new IllegalStateException("Lost tower, geometry or locate identity");
            if(!restored[0]) {
                // Simulate a crash after atomic journal publication but before global origin save.
                j.origin=original;j.initialized=false;j.phase=0;restored[0]=true;
                throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for journal origin recovery");
            }
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void structureTemplatesLoadOnRealServer(GameTestHelper helper) throws Exception {
        for (String name:new String[]{"roadside_ruin","fantasy_tavern","gothic_castle","dark_fantasy_castle","abyss_citadel"}) {
            try (var stream=BuildingGameTests.class.getResourceAsStream("/data/tnc/buildings/"+name+".json")) {
                if (stream==null) throw new IllegalStateException(name);
                var root=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),JsonObject.class);
                for (var e:root.getAsJsonArray("pieces")) {
                    var p=e.getAsJsonObject();
                    var id=ResourceLocation.tryParse(p.get("resource").getAsString());
                    var template=helper.getLevel().getStructureManager().get(id).orElseThrow();
                    if (template.getSize().getY()<1) throw new IllegalStateException("Empty "+id);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=12000)
    public static void abyssTicketsMakeProgressWithoutBlocking(GameTestHelper helper) {
        // Dedicated GameTest world only. Progress assertion stops before excavation.
        var level=helper.getLevel();
        var origin=new BlockPos(2048,-64,2048);
        AbyssCitadelJobs.request(level,origin);
        helper.succeedWhen(()->{
            var j=AbyssCitadelData.get(level).jobs.get(origin.asLong());
            if (j==null || j.tile<32) throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "Waiting for nonblocking chunk preflight: "+(j==null ? "not queued" : "tile="+j.tile+" phase="+j.phase+" "+j.error));
            j.phase=-1; j.error="GameTest stopped before excavation";
            AbyssCitadelData.get(level).setDirty();
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void landmarkPlacesAndReconcilesWithoutOverwritingPlayerEdit(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(112,128,112);
        String key=new LandmarkData.Job("landmark_fixture",origin).key();
        LargeLandmarkJobs.request(l,"landmark_fixture",origin);
        boolean[] first={true};
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if (j!=null && j.phase<0) throw new IllegalStateException("Fixture failed: "+j.error);
            if (j==null || j.phase!=3) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for landmark placement");
            var edit=origin.offset(2,1,2);
            if (first[0]) {
                if (!l.getBlockState(edit).is(Blocks.DIAMOND_BLOCK) || !l.getBlockState(origin.offset(18,1,2)).is(Blocks.DIAMOND_BLOCK))
                    throw new IllegalStateException("Both chunks must contain their source architecture");
                if (!l.getBlockState(origin.offset(8,1,8)).isAir()) throw new IllegalStateException("Source room must be clear");
                l.setBlock(edit,Blocks.GOLD_BLOCK.defaultBlockState(),18);
                // Simulate global SavedData recovery. Immutable plan is reread; completed chunk bits must win.
                j.initialized=false; j.phase=1; j.tile=0; j.cursor=0; j.piece=0; first[0]=false;
                throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for recovery reconciliation");
            }
            if (!l.getBlockState(edit).is(Blocks.GOLD_BLOCK)) throw new IllegalStateException("Reconciliation overwrote player edit");
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void vanillaLargeTemplatesLoad(GameTestHelper helper) {
        // Conquest architecture is audited offline against the installed registry assets;
        // this minimal server deliberately does not pretend to provide Conquest.
        var pieces=new java.util.ArrayList<StonecrestManifest.Piece>();
        for (String asset:new String[]{"end_pvp_island","gothic_cathedral","elden_coastal_castle"})
            pieces.addAll(StonecrestManifest.get(asset).pieces());
        int[] index={0};
        helper.succeedWhen(()->{
            for (int n=0;n<4 && index[0]<pieces.size();n++,index[0]++) {
                var p=pieces.get(index[0]); var t=helper.getLevel().getStructureManager().get(p.resource()).orElseThrow();
                if (!t.getSize().equals(p.size())) throw new IllegalStateException("Wrong size "+p.resource());
                helper.getLevel().getStructureManager().remove(p.resource());
            }
            if (index[0]<pieces.size()) throw new net.minecraft.gametest.framework.GameTestAssertException("Loading large templates "+index[0]);
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void undergroundChestDoesNotRejectLandmark(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(208,128,112); var chest=new BlockPos(210,-62,114);
        l.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
        String key=new LandmarkData.Job("landmark_fixture",origin).key();
        LargeLandmarkJobs.request(l,"landmark_fixture",origin);
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if (j!=null && j.phase<0) throw new IllegalStateException("Unrelated underground chest rejected: "+j.error);
            if (j==null || j.phase!=3) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for fixture");
            if (!l.getBlockState(chest).is(Blocks.CHEST) || !l.getBlockState(origin.offset(2,1,2)).is(Blocks.DIAMOND_BLOCK))
                throw new IllegalStateException("Chest or structure was lost");
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=16000)
    public static void chestAddedAfterPreflightStopsConstruction(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(304,128,112); var chest=origin.offset(18,1,2);
        String key=new LandmarkData.Job("landmark_fixture",origin).key();
        LargeLandmarkJobs.request(l,"landmark_fixture",origin);
        boolean[] inserted={false};
        helper.succeedWhen(()->{
            var j=LandmarkData.get(l).jobs.get(key);
            if (j==null) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for fixture");
            if (!inserted[0] && j.phase==1) { l.setBlock(chest,Blocks.CHEST.defaultBlockState(),18); inserted[0]=true; }
            if (j.phase==3) throw new IllegalStateException("Protected chest did not stop construction");
            if (j.phase!=-1) throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for protection check");
            if (!inserted[0] || !j.error.contains("Protected block entity") || !l.getBlockState(chest).is(Blocks.CHEST))
                throw new IllegalStateException("Unexpected stop or chest overwritten: "+j.error);
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void sparseTemplateProtectsOnlyActualWritePositions(GameTestHelper helper) {
        var l=helper.getLevel(); var origin=new BlockPos(400,200,112);
        var untouched=origin.offset(4,2,4); l.setBlock(untouched,Blocks.CHEST.defaultBlockState(),18);
        var id=ResourceLocation.tryParse("tnc:landmark_fixture_0"); var t=l.getStructureManager().get(id).orElseThrow();
        var chunks=java.util.List.of(new net.minecraft.world.level.ChunkPos(origin));
        LargeLandmarkJobs.protectTemplate(l,origin,chunks,t);
        t.placeInWorld(l,origin,origin,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setKnownShape(true).setKeepLiquids(false).setIgnoreEntities(true),net.minecraft.util.RandomSource.create(1),18);
        if (!l.getBlockState(untouched).is(Blocks.CHEST)) throw new IllegalStateException("Sparse air overwrote a chest");
        l.setBlock(origin,Blocks.CHEST.defaultBlockState(),18);
        boolean stopped=false;
        try { LargeLandmarkJobs.protectTemplate(l,origin,chunks,t); }
        catch (IllegalStateException expected) { stopped=expected.getMessage().contains("Protected block entity"); }
        if (!stopped || !l.getBlockState(origin).is(Blocks.CHEST)) throw new IllegalStateException("Actual template write was not protected");
        l.getStructureManager().remove(id); helper.succeed();
    }
}
