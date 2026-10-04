package com.tnc.tnc.life.wonders;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.life.pasture.*;
import com.tnc.tnc.production.energy.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class WonderGameTests {
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void bottlesAndPulseBaseFeedNewFourMaterialForgeMouthExactlyOnce(GameTestHelper h){
        var l=h.getLevel();var player=AdventureGameTests.player(h);var at=h.absolutePos(new BlockPos(2,2,2));
        var forge=com.tnc.tnc.production.MagicForgeGameTests.build(h,at,Direction.NORTH);forge.setOwner(player.getUUID());
        var bottle=new ItemStack(PastureRegistry.item("mana_bottle"));var ledger=PastureBottleLedger.get(l);ledger.deposit(bottle,24,24);player.setItemInHand(InteractionHand.MAIN_HAND,bottle);
        var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false);l.getBlockState(at).use(l,player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(forge.charge()==24&&ledger.amount(bottle)==0,"Normal right-click injects real bottled mana through the new front furnace mouth");
        ledger.deposit(bottle,24,10);var base=at.north();int moved=ManaBottleItem.transferFromBase(l,base,player.getUUID(),bottle,5);
        h.assertTrue(moved==5&&forge.charge()==29&&ledger.amount(bottle)==5,"A base beside the furnace mouth sends and debits exactly five mana");
        l.setBlockAndUpdate(at.below(),Blocks.AIR.defaultBlockState());h.assertTrue(ManaBottleItem.transferFromBase(l,base,player.getUUID(),bottle,5)==0&&ledger.amount(bottle)==5,"Incomplete structures cannot accept or consume bottled mana");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void bottleCopiesShareOneSavedBalanceAndRejectStackedDeposit(GameTestHelper h){
        var ledger=new PastureBottleLedger();var bottle=new ItemStack(PastureRegistry.item("mana_bottle"));
        h.assertTrue(ledger.deposit(bottle,24,40)==24,"Capacity clamps an actual deposit");
        var copy=bottle.copy();h.assertTrue(ledger.withdraw(copy,15)==15&&ledger.amount(bottle)==9,"Copying NBT cannot duplicate authoritative mana");
        var restored=PastureBottleLedger.load(ledger.save(new CompoundTag()));h.assertTrue(restored.withdraw(bottle,30)==9&&restored.amount(copy)==0,"Save/load and another copied identity still share one balance");
        h.assertTrue(ledger.deposit(new ItemStack(PastureRegistry.item("mana_bottle"),2),24,10)==0,"A stack cannot represent several filled copies");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void normalBlockRightClickReallyTransfersBottledMana(GameTestHelper h){
        var l=h.getLevel();var p=AdventureGameTests.player(h);var at=h.absolutePos(new BlockPos(2,2,2));
        l.setBlock(at,EnergyContent.GENERATOR.defaultBlockState(),3);var node=(EnergyBlockEntity)l.getBlockEntity(at);node.claim(p.getUUID());
        var bottle=new ItemStack(PastureRegistry.item("mana_bottle"));var ledger=PastureBottleLedger.get(l);ledger.deposit(bottle,24,24);p.setItemInHand(InteractionHand.MAIN_HAND,bottle);
        var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false);
        l.getBlockState(at).use(l,p,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(node.mana()==24&&ledger.amount(bottle)==0,"Normal right-click reaches the item consumer before the block swallows it");
        l.getBlockState(at).use(l,p,InteractionHand.MAIN_HAND,hit);h.assertTrue(node.mana()==24,"Repeated clicks cannot inject twice");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void physicalRopeClimbsAndRecoveryKeepsReplacementBlocks(GameTestHelper h){
        var l=h.getLevel();var p=AdventureGameTests.player(h);var anchor=h.absolutePos(new BlockPos(3,12,3));l.setBlock(anchor,Blocks.STONE.defaultBlockState(),3);
        var rope=new ItemStack(WonderContent.SKY_ROPE);p.setItemInHand(InteractionHand.MAIN_HAND,rope);
        var ctx=new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.EAST,anchor,false));
        h.assertTrue(WonderRopes.use(ctx,8).consumesAction(),"A reusable cord lays physical rope nodes");var first=anchor.east();
        h.assertTrue(l.getBlockState(first).is(net.minecraft.tags.BlockTags.CLIMBABLE),"Vanilla climbable tag gives real climbing");
        var stranger=AdventureGameTests.player(h);stranger.setShiftKeyDown(true);stranger.setItemInHand(InteractionHand.MAIN_HAND,rope);
        h.assertTrue(!WonderRopes.use(new UseOnContext(stranger,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.EAST,anchor,false)),8).consumesAction()&&rope.getOrCreateTag().contains("RopeNodes")&&l.getBlockState(first).is(WonderContent.ROPE),"Trading a deployed cord cannot clear its binding or leave free permanent lines");
        var replaced=first.below(3);l.setBlock(replaced,Blocks.GOLD_BLOCK.defaultBlockState(),3);
        var saved=ItemStack.of(rope.save(new CompoundTag()));p.setItemInHand(InteractionHand.MAIN_HAND,saved);p.setShiftKeyDown(true);
        WonderRopes.use(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.EAST,anchor,false)),8);
        h.assertTrue(l.getBlockState(first).isAir()&&l.getBlockState(replaced).is(Blocks.GOLD_BLOCK)&&!saved.getOrCreateTag().contains("RopeNodes"),"Recovery follows saved owner/identity, preserves foreign replacement, and clears deployed state");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void removingVineRootCleansOnlyItsRecordedPlantBlocksAcrossSave(GameTestHelper h){
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(4,3,4));l.setBlock(at.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(at,WonderContent.SKY_VINE.defaultBlockState(),3);var be=(SkyVineRootEntity)l.getBlockEntity(at);var owner=UUID.randomUUID();
        var stem=at.above();var replaced=at.above(2);l.setBlock(stem,WonderContent.VINE_STEM.defaultBlockState(),3);l.setBlock(replaced,Blocks.GOLD_BLOCK.defaultBlockState(),3);
        var saved=new CompoundTag();saved.putUUID("Owner",owner);saved.putLongArray("Stem",new long[]{stem.asLong(),replaced.asLong()});be.load(saved);
        l.setBlock(at,Blocks.AIR.defaultBlockState(),3);var restored=VineCleanupData.load(VineCleanupData.get(l).save(new CompoundTag()));restored.tick(l);
        h.assertTrue(l.getBlockState(stem).isAir()&&l.getBlockState(replaced).is(Blocks.GOLD_BLOCK),"Root cleanup resumes after save and only removes recorded matching vine blocks");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void bowlRecipesAndFoodSchedulesReturnContainersAndAllTicks(GameTestHelper h){
        var p=AdventureGameTests.player(h);long now=p.level().getGameTime();p.setHealth(10);var stew=new ItemStack(PastureRegistry.item("honeydew_casserole"));
        h.assertTrue(stew.getCraftingRemainingItem().is(Items.BOWL),"Food gifting/crafting can identify the bowl container");
        var returned=stew.finishUsingItem(p.level(),p);h.assertTrue(returned.is(Items.BOWL),"Consuming a last bowl gives the container in the returned hand stack");
        for(int n=1;n<=4;n++)PastureEffectEvents.applyMeals(p,now+n*100);
        h.assertTrue(p.getHealth()==14&&p.getPersistentData().getInt("TncRestHeals")==0,"All four scheduled heals occur, including the exact deadline");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void vineQueueResumesItsOriginalStageAndNeverExceedsTheEnvelope(GameTestHelper h){
        var l=h.getLevel();var at=new BlockPos(24000,200,24000);var p=AdventureGameTests.player(h);
        // The five-block fixture cannot contain a nine-by-nine, 64-high tree.
        // Isolate this volume from neighbouring concurrently running fixtures.
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=1;y<=64;y++)l.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        l.setBlock(at.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(at,WonderContent.SKY_VINE.defaultBlockState(),3);var be=(SkyVineRootEntity)l.getBlockEntity(at);
        List<BlockPos> plan=SkyVineRootEntity.plan(at,0,12);var tag=new CompoundTag();tag.putUUID("Owner",p.getUUID());tag.putLong("Growth",48000);tag.putInt("QueueTarget",12);tag.putLongArray("Queue",plan.stream().mapToLong(BlockPos::asLong).toArray());be.load(tag);be.onLoad();
        SkyVineRootEntity.tick(l,at,l.getBlockState(at),be);var first=be.saveWithoutMetadata();
        h.assertTrue(first.getInt("Cursor")<=32&&first.getLongArray("Stem").length+first.getLongArray("Leaf").length<=32,"At most32 blocks are placed in one tick");
        be.load(first);be.onLoad();for(int n=0;n<40&&be.saveWithoutMetadata().getLongArray("Queue").length>0;n++)SkyVineRootEntity.tick(l,at,l.getBlockState(at),be);
        h.assertTrue(be.saveWithoutMetadata().getInt("Height")==12,"A resumed12-block queue cannot claim24-block height after the clock advances: "+be.status());
        h.assertTrue(SkyVineRootEntity.plan(at,0,64).stream().allMatch(q->q.getY()<=at.getY()+64&&Math.abs(q.getX()-at.getX())<=4&&Math.abs(q.getZ()-at.getZ())<=4),"Every mature canopy block fits the promised9x9x64 envelope");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=30)
    public static void vineObstaclePausesWithoutReplacingTheObstacle(GameTestHelper h){
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(4,3,4));l.setBlock(at.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(at,WonderContent.SKY_VINE.defaultBlockState(),3);var be=(SkyVineRootEntity)l.getBlockEntity(at);
        var t=new CompoundTag();t.putUUID("Owner",UUID.randomUUID());t.putLong("Growth",96000);be.load(t);
        var obstacle=at.above(16);l.setBlock(obstacle,Blocks.CHEST.defaultBlockState(),3);be.onLoad();for(int n=0;n<10;n++)SkyVineRootEntity.tick(l,at,l.getBlockState(at),be);
        h.assertTrue(l.getBlockState(obstacle).is(Blocks.CHEST)&&be.saveWithoutMetadata().getInt("Height")==0&&be.saveWithoutMetadata().getLongArray("Stem").length==0,"A planted vine pauses before any world edit when its future space contains a building");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=40)
    public static void temporaryLocalLightExpiresAcrossSavedNodeReload(GameTestHelper h){
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(3,3,3));l.setBlock(at,WonderContent.LIGHT_NODE.defaultBlockState(),3);var be=(LightNodeEntity)l.getBlockEntity(at);be.bind(UUID.randomUUID(),l.getGameTime()+4);var saved=be.saveWithoutMetadata();be.load(saved);be.onLoad();
        h.runAfterDelay(8,()->{h.assertTrue(l.getBlockState(at).isAir(),"An orphan/local light still expires when its in-memory owner session is lost");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=40)
    public static void cleanupNeverDeletesANewerReplacementBlock(GameTestHelper h){
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(3,3,3));l.setBlock(at,WonderContent.LIGHT_NODE.defaultBlockState(),3);var old=(LightNodeEntity)l.getBlockEntity(at);old.bind(UUID.randomUUID(),l.getGameTime());l.setBlock(at,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);old.expireIfDue();
        h.assertTrue(l.getBlockState(at).is(Blocks.DIAMOND_BLOCK),"Expired identity cannot delete a different block later occupying its position");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=40)
    public static void tunedRelayOutputsRealPulseAndRetainsConfiguration(GameTestHelper h){
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(2,2,2));var p=AdventureGameTests.player(h);l.setBlock(at,WonderContent.RELAY.defaultBlockState(),3);var be=(SoundRelayEntity)l.getBlockEntity(at);be.setOwner(p.getUUID());be.installBell();
        h.assertTrue(!be.hear(l,4)&&be.hear(l,6)&&l.getSignal(at,Direction.UP)==15,"Only the tuned pitch produces real redstone output");h.assertTrue(!be.hear(l,6),"Cooldown prevents a note feedback loop");
        var saved=be.saveWithoutMetadata();be.load(saved);be.onLoad();h.assertTrue(be.hasBell()&&be.pitch()==6,"Installed bell and tuning surviveNBT reload");
        h.runAfterDelay(14,()->{h.assertTrue(l.getSignal(at,Direction.UP)==0,"Pulse turns off after ten ticks");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="living_wonders",timeoutTicks=200)
    public static void farlightReallyIlluminatesFiftyBlocksWithoutCrossingWalls(GameTestHelper h){
        var l=h.getLevel();var player=AdventureGameTests.player(h);var center=new BlockPos(26000,200,26000);
        List<net.minecraft.world.level.ChunkPos> chunks=new ArrayList<>();
        for(int x=(center.getX()-56)>>4;x<=(center.getX()+56)>>4;x++)for(int z=(center.getZ()-56)>>4;z<=(center.getZ()+56)>>4;z++){chunks.add(new net.minecraft.world.level.ChunkPos(x,z));l.setChunkForced(x,z,true);l.getChunk(x,z);}
        for(int dx=-56;dx<=56;dx++)for(int dz=-56;dz<=56;dz++)l.setBlock(center.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
        var sites=LightBloomData.findSites(player,center);h.assertTrue(sites.size()>80&&sites.size()<=256,"Surface discovery creates a bounded light field");
        for(var at:sites){l.setBlock(at,WonderContent.LIGHT_NODE.defaultBlockState(),3);((LightNodeEntity)l.getBlockEntity(at)).bind(UUID.randomUUID(),l.getGameTime()+400);}
        h.startSequence().thenWaitUntil(()->{
            int lit=0,total=0;for(int dx=-50;dx<=50;dx+=2)for(int dz=-50;dz<=50;dz+=2)if(dx*dx+dz*dz<=2500){total++;if(l.getBrightness(LightLayer.BLOCK,center.offset(dx,0,dz))>=5)lit++;}
            h.assertTrue(lit>=total*.9,"Actual block light reaches at least90% of a flat50-block radius; got "+lit+"/"+total);
            }).thenExecute(()->{try{
            for(var at:sites)l.setBlock(at,Blocks.AIR.defaultBlockState(),2);
            for(int z=-56;z<=56;z++)for(int y=0;y<=9;y++)l.setBlock(center.offset(4,y,z),Blocks.STONE.defaultBlockState(),2);
            var sheltered=LightBloomData.findSites(player,center);h.assertTrue(sheltered.stream().noneMatch(at->at.getX()>center.getX()+4),"Opaque wall prevents planting lights through the wall");h.succeed();
        }finally{for(var chunk:chunks)l.setChunkForced(chunk.x,chunk.z,false);}});
    }
}
