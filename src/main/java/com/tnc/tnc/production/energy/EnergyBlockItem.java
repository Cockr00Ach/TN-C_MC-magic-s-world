package com.tnc.tnc.production.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A carried machine always shows its stored resources before the player places it. */
final class EnergyBlockItem extends BlockItem {
    private final EnergyBlockEntity.Kind kind;

    EnergyBlockItem(Block block, EnergyBlockEntity.Kind kind) {
        super(block, new Properties());
        this.kind = kind;
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("旧版 FE 器件已退役，请使用魔导工坊中的纯魔力装置").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tnc.energy." + kind.name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));
        var stored = stack.getTagElement("BlockEntityTag");
        if (stored == null) return;
        if (kind == EnergyBlockEntity.Kind.GENERATOR)
            tooltip.add(Component.translatable("tooltip.tnc.energy.mana", stored.getInt("Mana"), 200)
                    .withStyle(ChatFormatting.GREEN));
        if (kind != EnergyBlockEntity.Kind.CABLE)
            tooltip.add(Component.translatable("tooltip.tnc.energy.fe", stored.getInt("Energy"),
                    kind == EnergyBlockEntity.Kind.BATTERY ? 5000
                            : kind == EnergyBlockEntity.Kind.LAMP ? 200
                            : kind == EnergyBlockEntity.Kind.PRESS ? 1000 : 2000)
                    .withStyle(ChatFormatting.AQUA));
        if (kind == EnergyBlockEntity.Kind.PRESS)
            tooltip.add(Component.translatable("tooltip.tnc.energy.press_storage",
                    stored.getInt("Reeds"), stored.getInt("WaterUnits"), stored.getInt("Paper"))
                    .withStyle(ChatFormatting.GREEN));
    }
}
