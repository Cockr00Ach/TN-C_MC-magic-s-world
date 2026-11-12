package com.tnc.tnc.fish;

import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** SwordItem deliberately reuses the existing warrior longsword attacks and synchronization. */
public final class FishSwordItem extends SwordItem {
    private final boolean metal;
    public FishSwordItem(boolean metal) {
        super(Tiers.IRON, (metal ? FishSwordRules.METAL_DAMAGE : FishSwordRules.LIVING_DAMAGE) - 3,
                (metal ? FishSwordRules.METAL_SPEED : FishSwordRules.LIVING_SPEED) - 4,
                new Item.Properties().durability(metal ? FishSwordRules.METAL_DURABILITY : FishSwordRules.LIVING_DURABILITY));
        this.metal = metal;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if (hand!=InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        return new InteractionResultHolder<>(FishSchoolSpell.cast((ServerPlayer)player,stack,metal),stack);
    }
    @Override public boolean isValidRepairItem(ItemStack sword, ItemStack material) {
        return metal ? material.is(Items.IRON_INGOT) : material.is(Items.COD) || material.is(Items.SALMON);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(metal ? "tooltip.tnc.fish_metal" : "tooltip.tnc.fish_living").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.tnc.fish_school",
                Component.translatable(metal ? "skill.tnc.fish_school_metal" : "skill.tnc.fish_school_living"),
                (int)FishSchoolGeometry.damage(metal),FishSchoolGeometry.mana(metal),FishSchoolGeometry.cooldown(metal)/20)
                .withStyle(ChatFormatting.DARK_AQUA));
        if (stack.hasTag() && stack.getTag().contains(FishSwordEvents.PENDING)) {
            int chance = stack.getTag().getCompound(FishSwordEvents.PENDING).getInt("Chance");
            lines.add(Component.translatable("tooltip.tnc.fish_pending", chance / 100).withStyle(ChatFormatting.GOLD));
        }
    }
}
