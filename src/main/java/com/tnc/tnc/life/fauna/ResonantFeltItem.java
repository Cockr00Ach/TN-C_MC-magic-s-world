package com.tnc.tnc.life.fauna;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A real use for two living fleeces: a durable offhand wrap against freezing. */
public final class ResonantFeltItem extends Item {
    public ResonantFeltItem() { super(new Properties().durability(120)); }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("放在副手，清除细雪冻结；在寒地保护时缓慢磨损。"));
        lines.add(Component.literal("两团响绒、线和皮革编成；无需杀死响铃羊。"));
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)
                || player.getOffhandItem() != stack || player.getTicksFrozen() <= 0) return;
        player.setTicksFrozen(0);
        if (player.tickCount % 20 == 0)
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.OFF_HAND));
    }
}
