package com.tnc.tnc.travel;

import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.Element;
import net.minecraft.world.item.ItemStack;

/**
 * 能承载「元素附身」的法杖。
 *
 * <p>只由 {@link ElementWands.Wand} 实现：通用 {@code magic_wand} 没有元素，
 * 不能附身。这样判断不需要读 NBT，也不会被改客户端伪造。
 */
public interface TravelStaff {

    Element travelElement();

    int travelTier();

    /** 主手物品是否是元素法杖；返回 {@code null} 表示不是。 */
    static TravelStaff of(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TravelStaff staff
                ? staff : null;
    }
}
