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
}
