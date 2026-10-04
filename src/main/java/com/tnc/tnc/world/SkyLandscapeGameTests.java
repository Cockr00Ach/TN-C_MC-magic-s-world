package com.tnc.tnc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class SkyLandscapeGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void townDoorDisplayMustNotDeadlockTavernConstruction(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,3,3));var display=net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(l);
        display.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);l.addFreshEntity(display);
        var tile=new SkyLandscapeUpgrade.Tile(List.of(new SkyLandscapeUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState())),BlockPos.ZERO,new BlockPos(3,3,3));
        h.assertTrue(!SkyLandscapeUpgrade.occupied(l,p,tile),"Nonphysical town door label cannot block the update that creates its tavern");display.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void realLandscapeResourceLoadsWithStableDoorAndNoPortalOverlap(GameTestHelper h) throws Exception {
        var p=SkyLandscapeUpgrade.load(h.getLevel());
        if(p.tiles().size()<200||p.tiles().size()>500||!p.tavern().equals(new BlockPos(419,90,291)))throw new IllegalStateException("Unexpected landscape resource");
        for(var tile:p.tiles())for(var c:tile.changes()) {
            var v=c.local();
            if(v.getX()>=185&&v.getX()<=233&&v.getZ()>=400&&v.getZ()<=448)throw new IllegalStateException("Portal reservation touched");
        }
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void boundedPlacementCompletesAndRecoveryRescans(GameTestHelper h) {
        var l=h.getLevel();var origin=new BlockPos(5056,200,112);
        var changes=new java.util.ArrayList<SkyLandscapeUpgrade.Change>();
        for(int y=0;y<4;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            var p=new BlockPos(x,y,z);l.setBlock(origin.offset(p),Blocks.STONE.defaultBlockState(),18);
            changes.add(new SkyLandscapeUpgrade.Change(p,Blocks.STONE.defaultBlockState(),Blocks.DIRT.defaultBlockState()));
        }
        var plan=new SkyLandscapeUpgrade.Plan("fixture",List.of(new SkyLandscapeUpgrade.Tile(changes,BlockPos.ZERO,new BlockPos(15,3,15))),BlockPos.ZERO);
        var state=new SkyLandscapeUpgrade.State();state.origin=origin;state.hash="fixture";
        h.succeedWhen(()->{
            try {
                long before=changes.stream().filter(c->l.getBlockState(origin.offset(c.local())).is(Blocks.DIRT)).count();
                SkyLandscapeUpgrade.step(l,state,plan);
                long after=changes.stream().filter(c->l.getBlockState(origin.offset(c.local())).is(Blocks.DIRT)).count();
                if(after-before>512)throw new IllegalStateException("Tick budget exceeded");
                if(state.phase!=2)throw new net.minecraft.gametest.framework.GameTestAssertException("Waiting for incremental placement");
                for(var c:changes)if(!l.getBlockState(origin.offset(c.local())).is(Blocks.DIRT))throw new IllegalStateException("Incomplete overlay");
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void alreadyPlacedCampfireRetainsCookingInventory(GameTestHelper h) throws Exception {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));var state=Blocks.CAMPFIRE.defaultBlockState();
        l.setBlock(p,state,18);
        var fire=(net.minecraft.world.level.block.entity.CampfireBlockEntity)l.getBlockEntity(p);
        fire.getItems().set(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTATO,1));
        var change=new SkyLandscapeUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),state);
        var tile=new SkyLandscapeUpgrade.Tile(List.of(change),BlockPos.ZERO,BlockPos.ZERO);
        SkyLandscapeUpgrade.validate(l,p,tile);
        if(fire.getItems().get(0).isEmpty())throw new IllegalStateException("Campfire inventory lost");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void hangingDecorationsBlockLandscapePlacement(GameTestHelper h) {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));
        var frame=new net.minecraft.world.entity.decoration.ItemFrame(l,p,net.minecraft.core.Direction.NORTH);
        l.addFreshEntity(frame);
        var tile=new SkyLandscapeUpgrade.Tile(List.of(),BlockPos.ZERO,new BlockPos(2,2,2));
        if(!SkyLandscapeUpgrade.occupied(l,p,tile))throw new IllegalStateException("Hanging decoration not protected");
        frame.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void naturalUpdatesDoNotPermitSourceWaterOrPlayerEdits(GameTestHelper h) {
        var leaves=Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true);
        var c=new SkyLandscapeUpgrade.Change(BlockPos.ZERO,leaves,Blocks.DIRT.defaultBlockState());
        if(!SkyLandscapeUpgrade.compatible(leaves.setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,4),c))throw new IllegalStateException("Natural leaf distance rejected");
        var air=new SkyLandscapeUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState());
        if(SkyLandscapeUpgrade.compatible(Blocks.WATER.defaultBlockState(),air)||SkyLandscapeUpgrade.compatible(Blocks.GOLD_BLOCK.defaultBlockState(),air))throw new IllegalStateException("Source/player edit accepted");
        if(!SkyLandscapeUpgrade.compatible(Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,3),air))throw new IllegalStateException("Flow update rejected");
        var coveredGrass=new SkyLandscapeUpgrade.Change(BlockPos.ZERO,Blocks.GRASS_BLOCK.defaultBlockState(),Blocks.STONE.defaultBlockState());
        if(!SkyLandscapeUpgrade.compatible(Blocks.DIRT.defaultBlockState(),coveredGrass))throw new IllegalStateException("Natural covered grass rejected during fresh terrain construction");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void completedVoxelsAreIdempotentAndChangedVoxelsRejected(GameTestHelper h) throws Exception {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));
        var change=new SkyLandscapeUpgrade.Change(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),Blocks.DIRT.defaultBlockState());
        var tile=new SkyLandscapeUpgrade.Tile(List.of(change),BlockPos.ZERO,BlockPos.ZERO);
        l.setBlock(p,change.before(),18);SkyLandscapeUpgrade.validate(l,p,tile);
        l.setBlock(p,change.after(),18);SkyLandscapeUpgrade.validate(l,p,tile);
        l.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        boolean stopped=false;try{SkyLandscapeUpgrade.validate(l,p,tile);}catch(java.io.IOException expected){stopped=true;}
        if(!stopped||!l.getBlockState(p).is(Blocks.GOLD_BLOCK))throw new IllegalStateException("Player edit was not protected");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void airGuardsProtectInteriorAndContainers(GameTestHelper h) throws Exception {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));
        var guard=new SkyLandscapeUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState());
        var tile=new SkyLandscapeUpgrade.Tile(List.of(guard),BlockPos.ZERO,BlockPos.ZERO);
        l.setBlock(p,Blocks.CHEST.defaultBlockState(),18);
        ((net.minecraft.world.Container)l.getBlockEntity(p)).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        boolean stopped=false;try{SkyLandscapeUpgrade.validate(l,p,tile);}catch(java.io.IOException expected){stopped=true;}
        if(!stopped||((net.minecraft.world.Container)l.getBlockEntity(p)).getItem(0).getCount()!=7)throw new IllegalStateException("Chest not protected");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void partialRecoveryAlwaysResurveysWithoutMovingIsland(GameTestHelper h) {
        var s=new SkyLandscapeUpgrade.State();s.phase=1;s.tile=27;s.hash="fixture";s.origin=new BlockPos(-123,90,456);
        var recovered=SkyLandscapeUpgrade.State.load(s.save(new CompoundTag()));
        if(recovered.phase!=0||recovered.tile!=0||!recovered.origin.equals(s.origin)||!recovered.hash.equals(s.hash))
            throw new IllegalStateException("Recovery cursor or identity incorrect");
        s.phase=2;recovered=SkyLandscapeUpgrade.State.load(s.save(new CompoundTag()));
        if(recovered.phase!=2)throw new IllegalStateException("Completed overlay restarted");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="island_story_actor_fixture",timeoutTicks=200)
    public static void everyIslandStoryActorWaitsForCompleteTerrainAndWorkersAvoidAllRealTiles(GameTestHelper h)throws Exception{
        var level=h.getLevel();var site=h.absolutePos(new BlockPos(4,2,4));var center=new net.minecraft.world.level.ChunkPos(site);var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var c=new net.minecraft.world.level.ChunkPos(center.x+x,center.z+z);if(!level.getForcedChunks().contains(c.toLong())){forced.add(c);level.setChunkForced(c.x,c.z,true);}level.getChunk(c.x,c.z);}
        // Entity section loading is asynchronous. Run separately from fixtures
        // that replace the same SavedData, and create actors only after loading.
        h.succeedWhen(()->{
        h.assertTrue(level.isPositionEntityTicking(site)&&forced.stream().allMatch(c->level.areEntitiesLoaded(c.toLong())),"Waiting for actual NPC fixture entity sections");
        var old=StateBackup.capture(level);var sky=new SkyIslandSavedData();sky.phase=SkyIslandSavedData.Phase.COMPLETE;sky.layoutReady=true;sky.originX=h.absolutePos(BlockPos.ZERO).getX();sky.originY=h.absolutePos(BlockPos.ZERO).getY();sky.originZ=h.absolutePos(BlockPos.ZERO).getZ();level.getDataStorage().set("tnc_sky_island_v5",sky);var landscape=new SkyLandscapeUpgrade.State();landscape.phase=1;level.getDataStorage().set("tnc_sky_landscape_v1",landscape);
        try{var positions=new com.tnc.tnc.npc.NpcPlacementSavedData();h.setBlock(new BlockPos(4,1,4),Blocks.STONE);for(String id:List.of("self","cava","huai","zhuangquerang","zuowang")){var p=new com.tnc.tnc.npc.NpcPlacementSavedData.Placement(id,"ORIGIN",4,2,4);h.assertTrue(!positions.ensureOne(level,p),"Every anchored actor waits for terrain, not only Self: "+id);}
            var data=com.tnc.tnc.adventure.AdventureSavedData.get(level.getServer());var before=data.housing.copy();try{data.housing.remove("Residents");var expected=data.housing.copy();com.tnc.tnc.home.McaResidents.tick(level);h.assertTrue(expected.equals(data.housing),"Native neighbors do not spawn or write identities during new-island construction");}finally{data.housing=before;}
            SkyLandscapeUpgrade.Plan plan;try{plan=SkyLandscapeUpgrade.load(level);}catch(Exception e){throw new IllegalStateException(e);}for(var post:com.tnc.tnc.adventure.TownServices.POSTS){if(post.role().equals("guild"))continue;for(var tile:plan.tiles()){var box=new net.minecraft.world.phys.AABB(tile.min(),tile.max().offset(1,1,1)).inflate(1);var room=post.room();var workerArea=new net.minecraft.world.phys.AABB(room.minX()-.3,room.y(),room.minZ()-.3,room.maxX()+1.3,room.y()+2,room.maxZ()+1.3);h.assertTrue(!box.intersects(workerArea),"Independent "+post.role()+" room cannot obstruct any real landscape tile");}}
            landscape.phase=2;for(String id:List.of("self","cava","huai","zhuangquerang","zuowang")){var p=new com.tnc.tnc.npc.NpcPlacementSavedData.Placement(id,"ORIGIN",4,2,4);h.assertTrue(positions.ensureOne(level,p),"Same fresh fixture really spawns known story actor after terrain completion: "+id);var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.parse("tnc:"+id));var npc=com.tnc.tnc.npc.NpcPlacementSavedData.findNear(level,type,h.absolutePos(new BlockPos(4,2,4)),4);h.assertTrue(npc!=null,"Spawned actor is present, not a pending id");npc.discard();}
            for(var c:forced)level.setChunkForced(c.x,c.z,false);
        }finally{old.restore(level);}
        });
    }
    private record StateBackup(SkyIslandSavedData island,SkyLandscapeUpgrade.State landscape){
        static StateBackup capture(net.minecraft.server.level.ServerLevel l){return new StateBackup(SkyIslandSavedData.get(l),SkyLandscapeUpgrade.State.get(l));}
        void restore(net.minecraft.server.level.ServerLevel l){l.getDataStorage().set("tnc_sky_island_v5",island);l.getDataStorage().set("tnc_sky_landscape_v1",landscape);}
    }
}
