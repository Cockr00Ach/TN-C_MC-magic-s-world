package com.tnc.tnc.home;

import com.tnc.tnc.adventure.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class HomeGameTests {
    @GameTest(template="building_test_empty",batch="source_house",timeoutTicks=150)
    public static void originalTownHouseCanBeDeliveredAfterContentsAreSafelyRemoved(GameTestHelper h)throws Exception{
        var level=h.getLevel();var store=AdventureSavedData.get(level.getServer());var original=store.housing.copy();
        var buyer=AdventureGameTests.player(h);AdventureService.profile(buyer).credit(1000,"test");
        var plan=HousingService.blueprint(level);var fixture=h.absolutePos(new BlockPos(400,5,400));
        // The published island origin is Y=90; this plot starts at Y=180.
        // Putting its omitted-air cells underground would compare natural deepslate to sky.
        var target=new BlockPos(fixture.getX(),180,fixture.getZ());var origin=target.subtract(plan.min());
        var bounds=net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(origin.offset(plan.min()).offset(-2,-2,-2),origin.offset(plan.max()).offset(2,2,2));
        var fixtureChunks=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        try{
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);
            if(!level.getForcedChunks().contains(chunk.toLong())){fixtureChunks.add(chunk);level.setChunkForced(x,z,true);}level.getChunk(x,z);
        }
        // Use the actual two source town tiles, including their block-entity contents,
        // with normal neighbor updates rather than constructing a fake house from the sale plan.
        int[][] offsets={{168,53,380},{168,101,380}};
        for(int i=0;i<2;i++){
            String file="sky_island/town/town_piece_1_"+i+"_6.nbt";
            var resource=level.getServer().getResourceManager().getResource(net.minecraft.resources.ResourceLocation.parse("tnc:structures/"+file));
            java.io.InputStream input;
            if(resource.isPresent())input=resource.get().open();
            else{
                var packs=java.nio.file.Path.of("..").toAbsolutePath().normalize().resolve("modpack");
                try(var children=java.nio.file.Files.list(packs)){
                    var source=children.map(p->p.resolve("kubejs/data/tnc/structures/"+file)).filter(java.nio.file.Files::isRegularFile).findFirst().orElseThrow();
                    input=java.nio.file.Files.newInputStream(source);
                }
            }
            try(input){
                var template=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
                template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),net.minecraft.nbt.NbtIo.readCompressed(input));
                var tile=origin.offset(offsets[i][0],offsets[i][1],offsets[i][2]);
                h.assertTrue(template.placeInWorld(level,tile,tile,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setBoundingBox(bounds).setIgnoreEntities(true).setKeepLiquids(false),net.minecraft.util.RandomSource.create(1),2),"Original town tile placed");
            }
        }
        store.housing.remove(HousingService.ID);
        h.runAfterDelay(5,()->{
            try{
                String blocked=HousingService.buyAt(buyer,origin,plan);
                h.assertTrue(blocked.contains("箱内")&&AdventureService.profile(buyer).coins()==1000,"Original filled containers block sale without debit: "+blocked);
                int filled=0;
                for(var cell:plan.cells())if(level.getBlockEntity(origin.offset(cell.local())) instanceof net.minecraft.world.Container container&&!container.isEmpty()){filled++;container.clearContent();}
                h.assertTrue(filled==3,"Source has three filled containers; fixture removes their contents as an explicit prior step");
                for(var cell:plan.cells()){
                    var state=level.getBlockState(origin.offset(cell.local()));
                    h.assertTrue(HousingService.stable(state,cell.state()),"Original template mismatch at "+cell.local()+" expected="+cell.state()+" actual="+state);
                }
                var stair=plan.cells().stream().filter(c->c.state().getBlock() instanceof net.minecraft.world.level.block.StairBlock).findFirst().orElseThrow();
                var stairPos=origin.offset(stair.local());var stairState=level.getBlockState(stairPos);
                level.setBlock(stairPos,stairState.setValue(net.minecraft.world.level.block.StairBlock.FACING,stairState.getValue(net.minecraft.world.level.block.StairBlock.FACING).getClockWise()),2);
                h.assertTrue(HousingService.buyAt(buyer,origin,plan).contains("已有改动")&&AdventureService.profile(buyer).coins()==1000,"Rotated source stair stops sale without debit");level.setBlock(stairPos,stairState,2);
                var bed=Blocks.RED_BED.defaultBlockState();h.assertTrue(!HousingService.stable(bed,bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD)),"Bed part remains strict");
                h.assertTrue(!HousingService.stable(stairState,stairState.cycle(net.minecraft.world.level.block.StairBlock.HALF)),"Stair half remains strict");
                String bought=HousingService.buyAt(buyer,origin,plan);
                h.assertTrue(bought.contains("已购")&&HousingService.owned(buyer)&&AdventureService.profile(buyer).coins()==500,"Actual source house passes sale after contents are removed: "+bought);
                for(var cell:plan.cells())if(cell.clear())h.assertTrue(level.getBlockState(origin.offset(cell.local())).isAir(),"Every marked source furnishing is removed");
                h.succeed();
            }catch(Exception e){h.fail(e.toString());}finally{store.housing=original;store.setDirty();for(var chunk:fixtureChunks)level.setChunkForced(chunk.x,chunk.z,false);}
        });
        }catch(Exception|Error e){store.housing=original;store.setDirty();for(var chunk:fixtureChunks)level.setChunkForced(chunk.x,chunk.z,false);throw e;}
    }
    @GameTest(template="building_test_empty",batch="housing",timeoutTicks=100)
    public static void realPurchasePreflightUniqueOwnerAndRecovery(GameTestHelper h)throws Exception{
        var level=h.getLevel();var store=AdventureSavedData.get(level.getServer());var original=store.housing.copy();store.housing.remove(HousingService.ID);
        try{
            var a=AdventureGameTests.player(h);var b=AdventureGameTests.player(h);AdventureService.profile(a).credit(1000,"test");AdventureService.profile(b).credit(1000,"test");
            var local=new BlockPos(182,90,384);var target=h.absolutePos(new BlockPos(4,3,4));var origin=target.subtract(local);
            var cells=List.of(new HousingService.Cell(local,Blocks.CHEST.defaultBlockState(),true),new HousingService.Cell(local.east(),Blocks.OAK_PLANKS.defaultBlockState(),false),new HousingService.Cell(local.above(),Blocks.AIR.defaultBlockState(),false));
            var plan=new HousingService.Blueprint(local,local.offset(1,1,0),local,cells);level.setBlockAndUpdate(target,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(target.east(),Blocks.OAK_PLANKS.defaultBlockState());
            var chest=(net.minecraft.world.Container)level.getBlockEntity(target);chest.setItem(0,new ItemStack(Items.DIAMOND));HousingService.buyAt(a,origin,plan);
            h.assertTrue(AdventureService.profile(a).coins()==1000&&chest.getItem(0).is(Items.DIAMOND),"Full container preflight preserves funds and contents");chest.clearContent();
            level.setBlockAndUpdate(target.above(),Blocks.GOLD_BLOCK.defaultBlockState());HousingService.buyAt(a,origin,plan);h.assertTrue(AdventureService.profile(a).coins()==1000,"New player block in original air stops sale");level.setBlockAndUpdate(target.above(),Blocks.AIR.defaultBlockState());
            HousingService.buyAt(a,origin,plan);HousingService.buyAt(b,origin,plan);
            h.assertTrue(AdventureService.profile(a).coins()==500&&AdventureService.profile(b).coins()==1000&&HousingService.owned(a)&&!HousingService.owned(b),"One owner, one debit, second buyer untouched");
            h.assertTrue(level.getBlockState(target).isAir()&&level.getBlockState(target.east()).is(Blocks.OAK_PLANKS),"Furnishings cleared and structure retained");
            var state=HousingService.home(a.server);state.putBoolean("Preparing",true);state.putInt("Cursor",0);HousingService.finish(level,origin,plan,state);
            h.assertTrue(!state.getBoolean("Preparing")&&AdventureService.profile(a).coins()==500,"Journal recovery is idempotent and never debits again");
            level.setBlockAndUpdate(target,Blocks.CRAFTING_TABLE.defaultBlockState());HousingService.finish(level,origin,plan,state);h.assertTrue(level.getBlockState(target).is(Blocks.CRAFTING_TABLE),"Completed house never clears later decoration");
            h.assertTrue(HousingService.mayDecorate(a,target)&&!HousingService.mayDecorate(b,target)&&HousingService.intersectsOwned(level,target,new BlockPos(2,2,2)),"Owner permissions and template overwrite guard use same plot");
            var loaded=AdventureSavedData.load(store.save(new CompoundTag()));h.assertTrue(loaded.housing.getCompound(HousingService.ID).getUUID("Owner").equals(a.getUUID()),"Ownership survives save");h.succeed();
        }finally{store.housing=original;store.setDirty();}
    }
    @GameTest(template="building_test_empty",batch="housing_hazards",timeoutTicks=100)
    public static void actualWaterFireAndSlimePistonRespectOwnedBoundary(GameTestHelper h){
        var level=h.getLevel();var store=AdventureSavedData.get(level.getServer());var original=store.housing.copy();var target=h.absolutePos(new BlockPos(5,3,5));var origin=target.subtract(new BlockPos(182,90,384));
        var home=new CompoundTag();home.putUUID("Owner",UUID.randomUUID());home.putLong("Origin",origin.asLong());store.housing.put(HousingService.ID,home);
        try{
            for(var pos:BlockPos.betweenClosed(target.offset(-3,-1,-3),target.offset(3,-1,3)))level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            var source=target.west();level.setBlockAndUpdate(target,Blocks.REDSTONE_WIRE.defaultBlockState());level.setBlockAndUpdate(source,Blocks.WATER.defaultBlockState());
            net.minecraft.world.level.material.Fluids.WATER.tick(level,source,level.getFluidState(source));
            h.assertTrue(level.getBlockState(target).is(Blocks.REDSTONE_WIRE),"Actual fluid spread cannot replace protected decoration");level.setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(target,Blocks.OAK_PLANKS.defaultBlockState());level.setBlockAndUpdate(source.below(),Blocks.NETHERRACK.defaultBlockState());level.setBlockAndUpdate(source,Blocks.FIRE.defaultBlockState());
            var control=source.north();level.setBlockAndUpdate(control,Blocks.OAK_PLANKS.defaultBlockState());
            for(int i=0;i<300;i++)Blocks.FIRE.defaultBlockState().tick(level,source,net.minecraft.util.RandomSource.create(i));
            h.assertTrue(level.getBlockState(target).is(Blocks.OAK_PLANKS),"Actual fire ticks do not burn owned wood");
            h.assertTrue(!level.getBlockState(control).is(Blocks.OAK_PLANKS),"Unprotected control burns, proving fire logic ran");
            for(var pos:BlockPos.betweenClosed(target.offset(0,0,0),target.offset(2,1,2)))h.assertTrue(!(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock),"No new flames inside plot");
            level.setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());
            var piston=target.offset(-1,0,-1);var slime=piston.east();var protectedSide=slime.south();level.setBlockAndUpdate(protectedSide,Blocks.OAK_PLANKS.defaultBlockState());level.setBlockAndUpdate(slime,Blocks.SLIME_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(piston,Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,Direction.EAST));level.setBlockAndUpdate(piston.west(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.runAfterDelay(12,()->{try{h.assertTrue(level.getBlockState(slime).is(Blocks.SLIME_BLOCK)&&level.getBlockState(protectedSide).is(Blocks.OAK_PLANKS),"Real powered piston cannot pull protected slime side branch");h.succeed();}finally{store.housing=original;store.setDirty();}});
        }catch(Throwable e){store.housing=original;store.setDirty();throw e;}
    }
}
