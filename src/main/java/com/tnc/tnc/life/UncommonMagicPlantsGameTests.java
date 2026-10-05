package com.tnc.tnc.life;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class UncommonMagicPlantsGameTests {
    private UncommonMagicPlantsGameTests() {}

    @GameTest(template = "building_test_empty", batch = "uncommon_plants", timeoutTicks = 30)
    public static void hushcapAcceptsCultivatedDirtAndRetainsQuietBehaviourAndRenewal(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        h.assertTrue(UncommonMagicPlants.HUSHCAP.defaultBlockState().canSurvive(level, pos),
                "Cultivated hushcap accepts ordinary soil");
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, UncommonMagicPlants.HUSHCAP.defaultBlockState());
        h.assertTrue(level.getBlockState(pos).canSurvive(level, pos), "Cave stone supports hushcap");
        for (int i = 0; i < 3; i++)
            UncommonMagicPlants.HUSHCAP.tick(level.getBlockState(pos), level, pos, level.random);
        h.assertTrue(level.getBlockState(pos).getValue(UncommonMagicPlants.HushcapBlock.QUIET) == 3,
                "Quiet surroundings unfold the mushroom cap");
        // Build an actual sealed cave instead of relying on stale underground skylight.
        for (int dx=-2; dx<=2; dx++) for (int dy=-2; dy<=2; dy++) for (int dz=-2; dz<=2; dz++)
            if (Math.abs(dx)==2 || Math.abs(dy)==2 || Math.abs(dz)==2)
                level.setBlockAndUpdate(pos.offset(dx,dy,dz), Blocks.STONE.defaultBlockState());
        h.runAfterDelay(5, () -> {
        h.assertTrue(level.getRawBrightness(pos, 0) < 8, "Sealed cave fixture is dark enough");
        var random = net.minecraft.util.RandomSource.create(311);
        for (int i = 0; i < 160; i++)
            UncommonMagicPlants.HUSHCAP.randomTick(level.getBlockState(pos), level, pos, random);
        var ripe = level.getBlockState(pos);
        h.assertTrue(ripe.getValue(UncommonMagicPlants.HushcapBlock.AGE) == 3,
                "A quiet dark cave eventually grows a full hushcap");
        var drops = Block.getDrops(ripe, level, pos, null);
        h.assertTrue(count(drops, UncommonMagicPlants.HUSHCAP_SPORE) == 1
                        && count(drops, UncommonMagicPlants.HUSHCAP_SLICE) == 2,
                "A full plant yields a replantable spore and useful food");
        h.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "uncommon_plants", timeoutTicks = 30)
    public static void rainletterAcceptsHandWaterAndRenewsItsSeed(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(pos, UncommonMagicPlants.RAINLETTER.defaultBlockState());
        var player = AdventureGameTests.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        UncommonMagicPlants.RAINLETTER.use(level.getBlockState(pos), level, pos, player,
                InteractionHand.MAIN_HAND, hit);
        h.assertTrue(level.getBlockState(pos).getValue(UncommonMagicPlants.RainletterBlock.WET),
                "A water bottle replaces waiting for random rain");
        h.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE),
                "Watering returns the empty bottle");
        var ripe = level.getBlockState(pos)
                .setValue(UncommonMagicPlants.RainletterBlock.AGE, 3);
        var drops = Block.getDrops(ripe, level, pos, null);
        h.assertTrue(count(drops, UncommonMagicPlants.RAINLETTER_SEED) == 1
                        && count(drops, UncommonMagicPlants.RAINLETTER_BERRY) == 2,
                "Harvest keeps a seed and berries for food and trade");
        h.succeed();
    }

    private static int count(java.util.List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
