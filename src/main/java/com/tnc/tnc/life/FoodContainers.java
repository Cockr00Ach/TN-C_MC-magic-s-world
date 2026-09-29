package com.tnc.tnc.life;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

/** Gift/delivery consumption must return vessels without eating or applying player buffs. */
public final class FoodContainers {
    private static final TagKey<Item> BOWLS=TagKey.create(Registries.ITEM,ResourceLocation.parse("tnc:bowl_foods"));
    public static ItemStack remainder(ItemStack food){
        if(food.hasCraftingRemainingItem())return food.getCraftingRemainingItem();
        if(food.getItem() instanceof BowlFoodItem||food.getItem() instanceof SuspiciousStewItem||food.getItem() instanceof LifeEvents.DelicacyItem||food.is(BOWLS))return new ItemStack(Items.BOWL);
        if(food.getItem() instanceof HoneyBottleItem)return new ItemStack(Items.GLASS_BOTTLE);
        return ItemStack.EMPTY;
    }
}
