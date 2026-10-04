package com.tnc.tnc.home;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.adventure.AdventureSavedData;
import com.tnc.tnc.life.HomewardFlower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class PlantingBoundaryGameTests {
    @GameTest(template="building_test_empty",batch="planting_boundary",timeoutTicks=30)
    public static void seedChecksFinalPositionAcrossOwnedBoundary(GameTestHelper h){
        var l=h.getLevel();var owner=AdventureGameTests.player(h);var stranger=AdventureGameTests.player(h);
        var plot=PlotCatalog.find("sweet_cottage");var target=h.absolutePos(new BlockPos(3,3,3));var soil=target.below();
        var saved=AdventureSavedData.get(l.getServer());var before=saved.housing.copy();
        try{
            var home=new CompoundTag();home.putUUID("Owner",owner.getUUID());home.putLong("Origin",target.subtract(plot.min()).asLong());saved.housing.put(plot.id(),home);
            l.setBlockAndUpdate(soil,net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
            var hit=new BlockHitResult(Vec3.atCenterOf(soil).add(0,.5,0),Direction.UP,soil,false);
            stranger.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(HomewardFlower.SEED));
            var attempt=new PlayerInteractEvent.RightClickBlock(stranger,InteractionHand.MAIN_HAND,soil,hit);
            TownProtection.using(attempt);h.assertTrue(attempt.isCanceled(),"A seed cannot enter somebody else's land through an outside support block");
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(HomewardFlower.SEED));
            var allowed=new PlayerInteractEvent.RightClickBlock(owner,InteractionHand.MAIN_HAND,soil,hit);
            TownProtection.using(allowed);h.assertTrue(!allowed.isCanceled(),"The plot owner can plant through the same boundary");h.succeed();
        }finally{saved.housing=before;saved.setDirty();}
    }
}
