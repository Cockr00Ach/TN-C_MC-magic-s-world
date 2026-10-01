package com.tnc.tnc.home;
import com.tnc.tnc.adventure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.List;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class PropertyGameTests {
    @GameTest(template="building_test_empty",batch="properties",timeoutTicks=30)
    public static void separateHomesKeepOwnershipPriceMortgageAndPermissions(GameTestHelper h)throws Exception{
        var store=AdventureSavedData.get(h.getLevel().getServer());var old=store.housing.copy();for(var plot:PlotCatalog.ALL)store.housing.remove(plot.id());try{
            var a=AdventureGameTests.player(h);var b=AdventureGameTests.player(h);var c=AdventureGameTests.player(h);var pa=AdventureService.profile(a);var pb=AdventureService.profile(b);pa.credit(8000,"fixture");pb.credit(8000,"fixture");AdventureService.register(b);pb.addXp(AdventureRules.xpAtLevel(10));var one=PlotCatalog.find("sweet_cottage");var two=PlotCatalog.find("lamp_cottage");
            var worldA=h.absolutePos(new BlockPos(4,3,4));var worldB=h.absolutePos(new BlockPos(20,3,4));var originA=worldA.subtract(one.min());var originB=worldB.subtract(two.min());h.getLevel().setBlockAndUpdate(worldA,Blocks.OAK_PLANKS.defaultBlockState());h.getLevel().setBlockAndUpdate(worldB,Blocks.OAK_PLANKS.defaultBlockState());
            var planA=new HousingService.Blueprint(one.min(),one.min(),one.entry(),List.of(new HousingService.Cell(one.min(),Blocks.OAK_PLANKS.defaultBlockState(),false)));var planB=new HousingService.Blueprint(two.min(),two.min(),two.entry(),List.of(new HousingService.Cell(two.min(),Blocks.OAK_PLANKS.defaultBlockState(),false)));
            HousingService.buyAt(a,originA,planA,false,one);HousingService.buyAt(b,originB,planB,true,two);h.assertTrue(pa.coins()==6200&&pb.coins()==7220&&pb.bank.mortgage.remainingPrincipal()==1820,"Catalog price and mortgage apply to selected house");
            h.assertTrue(HousingService.mayDecorate(a,worldA)&&!HousingService.mayDecorate(a,worldB)&&HousingService.mayDecorate(b,worldB)&&!HousingService.mayDecorate(c,worldA),"Distinct saved origins and owners do not share permissions");long before=pb.coins();HousingService.buyAt(b,originB,planB,true,two);h.assertTrue(pb.coins()==before,"Duplicate does not debit");pb.bank.clear(pb,true);BankService.settle(b);h.assertTrue(!HousingService.home(b).getBoolean("Mortgage")&&HousingService.home(a).getUUID("Owner").equals(a.getUUID()),"Selected house mortgage clears without touching another house");h.succeed();
        }finally{store.housing=old;store.setDirty();}
    }
    @GameTest(template="building_test_empty",batch="properties",timeoutTicks=30)
    public static void selectedHouseFailedPreflightLeavesOtherOwnerUntouched(GameTestHelper h)throws Exception{
        var store=AdventureSavedData.get(h.getLevel().getServer());var old=store.housing.copy();try{var buyer=AdventureGameTests.player(h);var other=AdventureGameTests.player(h);var plot=PlotCatalog.find("book_house");var record=new CompoundTag();record.putUUID("Owner",other.getUUID());record.putLong("Origin",h.absolutePos(new BlockPos(50,0,0)).asLong());store.housing.put("sweet_cottage",record);store.housing.remove(plot.id());AdventureService.profile(buyer).credit(8000,"fixture");var target=h.absolutePos(new BlockPos(4,3,4));h.getLevel().setBlockAndUpdate(target,Blocks.DIAMOND_BLOCK.defaultBlockState());var origin=target.subtract(plot.min());var plan=new HousingService.Blueprint(plot.min(),plot.min(),plot.entry(),List.of(new HousingService.Cell(plot.min(),Blocks.OAK_PLANKS.defaultBlockState(),true)));String result=HousingService.buyAt(buyer,origin,plan,false,plot);h.assertTrue(result.contains("已有改动")&&AdventureService.profile(buyer).coins()==8000&&HousingService.home(other).getUUID("Owner").equals(other.getUUID())&&h.getLevel().getBlockState(target).is(Blocks.DIAMOND_BLOCK),"Failure neither spends nor changes property or decoration");h.succeed();}finally{store.housing=old;store.setDirty();}
    }
    @GameTest(template="building_test_empty",batch="properties",timeoutTicks=30)
    public static void missingImportSeamDoorIsRepairedOnlyAfterSuccessfulPurchase(GameTestHelper h)throws Exception{
        var store=AdventureSavedData.get(h.getLevel().getServer());var old=store.housing.copy();
        try{
            for(var p:PlotCatalog.ALL)store.housing.remove(p.id());
            var buyer=AdventureGameTests.player(h);var account=AdventureService.profile(buyer);account.credit(8000,"fixture");
            var local=new BlockPos(206,100,219);var world=h.absolutePos(new BlockPos(4,3,4));var origin=world.subtract(local);var level=h.getLevel();
            var lower=Blocks.SPRUCE_DOOR.defaultBlockState();var upper=lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
            var cells=List.of(new HousingService.Cell(local.below(),Blocks.STONE_BRICKS.defaultBlockState(),false),new HousingService.Cell(local,lower,false),new HousingService.Cell(local.above(),upper,false));
            var plan=new HousingService.Blueprint(local.below(),local.above(),local,cells);var plot=PlotCatalog.find("book_house");
            level.setBlock(world.below(),Blocks.STONE_BRICKS.defaultBlockState(),18);level.setBlock(world,Blocks.AIR.defaultBlockState(),18);level.setBlock(world.above(),Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
            String rejected=HousingService.buyAt(buyer,origin,plan,false,plot);
            h.assertTrue(rejected.contains("已有改动")&&account.coins()==8000&&level.getBlockState(world).isAir()&&level.getBlockState(world.above()).is(Blocks.DIAMOND_BLOCK),"Replacement block cancels sale and never repairs or spends");
            level.setBlock(world.above(),Blocks.AIR.defaultBlockState(),18);HousingService.buyAt(buyer,origin,plan,false,plot);
            h.assertTrue(HousingService.owned(buyer)&&account.coins()==3800&&level.getBlockState(world).equals(lower)&&level.getBlockState(world.above()).equals(upper),"Only the missing source seam door is restored after ownership is saved");
            h.succeed();
        }finally{store.housing=old;store.setDirty();}
    }
    @GameTest(template="building_test_empty",batch="properties",timeoutTicks=30)
    public static void paidLandBlocksCropTheftAndAllowsTrustedPartner(GameTestHelper h){
        var store=AdventureSavedData.get(h.getLevel().getServer());var old=store.housing.copy();try{var owner=AdventureGameTests.player(h);var other=AdventureGameTests.player(h);var plot=PlotCatalog.find("field_01");var pos=h.absolutePos(new BlockPos(4,3,4));var t=new CompoundTag();t.putUUID("Owner",owner.getUUID());t.putLong("Origin",pos.subtract(plot.min()).asLong());store.housing.put(plot.id(),t);h.getLevel().setBlockAndUpdate(pos,Blocks.WHEAT.defaultBlockState());var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(h.getLevel(),pos,h.getLevel().getBlockState(pos),other);TownProtection.breaking(event);h.assertTrue(event.isCanceled(),"Crop exception cannot bypass paid land");h.assertTrue(!TownProtection.denied(owner,pos),"Land owner can farm");HousingService.guest(owner,other,true);h.assertTrue(!TownProtection.denied(other,pos),"Land-only owner can authorize cooperative farming");HousingService.guest(owner,other,false);h.assertTrue(TownProtection.denied(other,pos),"Revoked guest cannot modify land");h.succeed();}finally{store.housing=old;store.setDirty();}
    }
}
