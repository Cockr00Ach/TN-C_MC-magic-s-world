package com.tnc.tnc.production;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The permanent workshop manual: right click to read its four illustrated pages. */
public final class ForgeGuideItem extends Item {
    public ForgeGuideItem() { super(new Properties().stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.tnc.tnc.production.client.ForgeGuideScreen.open(null));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("右键阅读三层炉台的搭法、注魔和导能说明"));
    }
}
