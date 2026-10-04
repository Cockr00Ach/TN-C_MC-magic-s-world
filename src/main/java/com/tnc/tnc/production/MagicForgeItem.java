package com.tnc.tnc.production;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Explain the control block before the player commits to building all 26 parts. */
public final class MagicForgeItem extends BlockItem {
    public MagicForgeItem(Block block) {
        super(block, new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("三层 3×3×3 空膛炉台的前方炉芯"));
        lines.add(Component.literal("另需 25 个结构部件；详见《炉台工匠札记》"));
        lines.add(Component.literal("右键打开四格工艺面板；注能口在背面"));
    }
}
