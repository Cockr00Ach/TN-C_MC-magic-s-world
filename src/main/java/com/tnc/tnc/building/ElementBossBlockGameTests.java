package com.tnc.tnc.building;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncelementblocks")
@PrefixGameTestTemplate(false)
public final class ElementBossBlockGameTests {
    @GameTest(template = "element_blocks_empty", timeoutTicks = 30)
    public static void allShadesPlaceHaveCollisionAndDropTheirOwnItem(GameTestHelper helper) {
        int row = 0;
        for (String theme : ElementBossBlocks.THEMES) {
            for (int shade = 1; shade <= ElementBossBlocks.SHADES; shade++) {
                Block block = ElementBossBlocks.block(theme, shade);
                String name = ElementBossBlocks.name(theme, shade);
                helper.assertTrue(block != null && block.asItem() instanceof BlockItem, "Registered block item: " + name);
                BlockPos relative = new BlockPos(shade, 2, row + 1);
                helper.setBlock(relative, block);
                BlockPos pos = helper.absolutePos(relative);
                var state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.is(block) && state.isCollisionShapeFullBlock(helper.getLevel(), pos), "Full solid placement: " + name);
                helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), "Pickaxe harvest tag: " + name);
                helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "Stone-tier requirement: " + name);
                var drops = Block.getDrops(state, helper.getLevel(), pos, null, null, new ItemStack(Items.IRON_PICKAXE));
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1, "Self-drop loot: " + name);
                helper.assertTrue(!state.isRandomlyTicking() && !state.hasBlockEntity(), "Static low-cost building block: " + name);
            }
            row++;
        }
        helper.succeed();
    }
}
