package com.tnc.tnc.life;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.adventure.AdventureSavedData;
import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.PlotCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class MagicHomePlantsGameTests {
    private MagicHomePlantsGameTests() {}

    @GameTest(template="building_test_empty",batch="magic_home_plants",timeoutTicks=30)
    public static void noHousePlayerCanPlantBindAndBloomAtWildernessCamp(GameTestHelper h){
        var l=h.getLevel();var p=AdventureGameTests.player(h);var soil=h.absolutePos(new BlockPos(2,2,2));
        l.setBlockAndUpdate(soil,Blocks.DIRT.defaultBlockState());
        var seeds=new ItemStack(HomewardFlower.SEED,2);p.setItemInHand(InteractionHand.MAIN_HAND,seeds);
        var hit=new BlockHitResult(Vec3.atCenterOf(soil).add(0,0.5,0),Direction.UP,soil,false);
        var context=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,seeds,hit);
        h.assertTrue(((HomewardFlower.HomeSeedItem)HomewardFlower.SEED).place(context).consumesAction(),"A no-house survival player can actually plant a seed outdoors");
        var flower=soil.above();h.assertTrue(l.getBlockState(flower).is(HomewardFlower.FLOWER),"Placement created a real flower");
        h.assertTrue(HomewardFlower.bindCampFlower(p,flower,true),"A personal wilderness flower can mark a camp");
        h.assertTrue(HomewardFlower.bloomCamp(p)==1&&l.getBlockState(flower).getValue(HomewardFlower.FlowerBlock.BLOOMING),"A qualified return can bloom without buying property");
        h.assertTrue(HomewardFlower.bloomCamp(p)==0,"An already blooming flower does not award twice");
        var saved=LifeSavedData.get(p.server);var copy=LifeSavedData.load(saved.save(new CompoundTag()));
        var account=copy.account(p.getUUID());h.assertTrue(!account.campDimension.isEmpty()&&account.campPosition==flower.asLong(),"Camp and flower binding survive reload");h.succeed();
    }

    @GameTest(template = "building_test_empty", batch = "magic_home_plants", timeoutTicks = 30)
    public static void flowerRequiresActiveTravelAndHonorsOwnershipAndDailyCap(GameTestHelper h) {
        var journey = new HomewardFlower.Journey();
        BlockPos homePos = new BlockPos(0, 90, 0);
        h.assertTrue(!journey.sample(true, false, 20, homePos, Level.OVERWORLD),
                "Starting at home is not a return trip");
        for (int i = 0; i < 59; i++)
            journey.sample(false, true, 20, new BlockPos(520 + 3 * i, 90, 0), Level.OVERWORLD);
        h.assertTrue(!journey.sample(true, false, 20, homePos, Level.OVERWORLD),
                "A short outing cannot bloom the plant");
        for (int i = 0; i < 60; i++)
            journey.sample(false, true, 20, new BlockPos(520 + 3 * i, 90, 0), Level.OVERWORLD);
        h.assertTrue(journey.sample(true, false, 20, homePos, Level.OVERWORLD),
                "A full minute with real movement beyond the travel radius qualifies");
        h.assertTrue(!journey.sample(true, false, 20, homePos, Level.OVERWORLD),
                "Remaining in the house cannot award twice");
        for (int i = 0; i < 100; i++)
            journey.sample(false, true, 20, new BlockPos(530, 90, 0), Level.OVERWORLD);
        h.assertTrue(!journey.sample(true, false, 20, homePos, Level.OVERWORLD),
                "Standing still beyond the boundary is not a genuine outing");

        var level = h.getLevel();
        var owner = AdventureGameTests.player(h);
        var stranger = AdventureGameTests.player(h);
        PlotCatalog.Plot plot = PlotCatalog.find("sweet_cottage");
        BlockPos first = h.absolutePos(new BlockPos(1, 3, 2));
        BlockPos origin = first.subtract(plot.min());
        var saved = AdventureSavedData.get(level.getServer());
        boolean hadHome = saved.housing.contains(plot.id(), 10);
        CompoundTag oldHome = saved.housing.getCompound(plot.id()).copy();
        try {
            CompoundTag home = new CompoundTag();
            home.putUUID("Owner", owner.getUUID());
            home.putLong("Origin", origin.asLong());
            saved.housing.put(plot.id(), home);
            h.assertTrue(HousingService.mayDecorate(owner, first) && !HousingService.mayDecorate(stranger, first),
                    "The flower's purchased house has one owner; outsiders cannot harvest or plant there");
            for (int i = 0; i < 4; i++) {
                BlockPos pos = first.east(i);
                level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(pos, HomewardFlower.FLOWER.defaultBlockState());
            }
            PlotCatalog.Plot fixturePlot = new PlotCatalog.Plot(plot.id(), plot.number(), plot.name(),
                    plot.kind(), plot.price(), plot.min(), plot.min().east(3), plot.entry(), plot.detail());
            int bloomed = HomewardFlower.bloomOnReturn(level, fixturePlot, origin, home, owner);
            h.assertTrue(bloomed == 3 && home.getInt("HomewardBloomCount") == 3,
                    "One qualified arrival blooms at most three planted buds");
            h.assertTrue(HomewardFlower.bloomOnReturn(level, fixturePlot, origin, home, owner) == 0,
                    "The same home cannot earn more blooms by walking through its doorway again");
            BlockState mature = HomewardFlower.FLOWER.defaultBlockState()
                    .setValue(HomewardFlower.FlowerBlock.BLOOMING, true);
            var drops = Block.getDrops(mature, level, first, null);
            h.assertTrue(count(drops, HomewardFlower.SEED) == 1 && count(drops, HomewardFlower.PETAL) == 1,
                    "Breaking a blooming flower returns a renewable seed and usable petal");
            h.succeed();
        } finally {
            if (hadHome) saved.housing.put(plot.id(), oldHome);
            else saved.housing.remove(plot.id());
            saved.setDirty();
        }
    }

    @GameTest(template = "building_test_empty", batch = "magic_home_plants", timeoutTicks = 30)
    public static void warningMossAndDoorLanternRespondToLoadedHostiles(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos mossPos = h.absolutePos(new BlockPos(1, 3, 2));
        level.setBlockAndUpdate(mossPos.south(), Blocks.STONE_BRICKS.defaultBlockState());
        BlockState moss = WarningMoss.MOSS.defaultBlockState()
                .setValue(WarningMoss.MossBlock.FACING, Direction.NORTH)
                .setValue(WarningMoss.MossBlock.AGE, 3);
        level.setBlockAndUpdate(mossPos, moss);
        h.assertTrue(moss.canSurvive(level, mossPos), "Moss anchors to a real stone wall");
        h.assertTrue(!moss.canSurvive(level, mossPos.north(3)), "Moss cannot float away from its wall");
        BlockPos lampPos = mossPos.east(2);
        level.setBlockAndUpdate(lampPos, WarningMoss.LANTERN.defaultBlockState());
        var zombie = EntityType.ZOMBIE.create(level);
        h.assertTrue(zombie != null, "Zombie fixture exists");
        try {
            zombie.setNoAi(true);
            zombie.setPos(mossPos.getX() + 1.5, mossPos.getY(), mossPos.getZ() + 0.5);
            level.addFreshEntity(zombie);
            WarningMoss.MOSS.tick(level.getBlockState(mossPos), level, mossPos, level.random);
            WarningMoss.LANTERN.tick(level.getBlockState(lampPos), level, lampPos, level.random);
            h.assertTrue(level.getBlockState(mossPos).getValue(WarningMoss.MossBlock.LIT)
                            && level.getBlockState(lampPos).getValue(WarningMoss.LanternBlock.LIT),
                    "Both living moss and a house lantern visibly warn when a hostile is nearby");
            zombie.discard();
            WarningMoss.MOSS.tick(level.getBlockState(mossPos), level, mossPos, level.random);
            WarningMoss.LANTERN.tick(level.getBlockState(lampPos), level, lampPos, level.random);
            h.assertTrue(!level.getBlockState(mossPos).getValue(WarningMoss.MossBlock.LIT)
                            && !level.getBlockState(lampPos).getValue(WarningMoss.LanternBlock.LIT),
                    "Both alarms return to a dark safe state after the hostile leaves");
            var ripeDrops = Block.getDrops(moss, level, mossPos, null);
            h.assertTrue(count(ripeDrops, WarningMoss.SPORE) == 1
                            && count(ripeDrops, WarningMoss.FLAKE) == 2,
                    "Mature moss can replant and supplies real lantern material");
            var youngDrops = Block.getDrops(moss.setValue(WarningMoss.MossBlock.AGE, 0), level, mossPos, null);
            h.assertTrue(count(youngDrops, WarningMoss.SPORE) == 1
                            && count(youngDrops, WarningMoss.FLAKE) == 0,
                    "Replanting an immature mat cannot instantly duplicate flakes");
            h.succeed();
        } finally {
            zombie.discard();
        }
    }

    private static int count(java.util.List<net.minecraft.world.item.ItemStack> drops,
                             net.minecraft.world.item.Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
    }
}
