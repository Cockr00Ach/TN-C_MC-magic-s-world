package com.tnc.tnc.bronze;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Copy the actual stack, including damage, enchantments, name and mod capabilities. */
public final class BronzeUpgradeRecipe extends CustomRecipe {
    public BronzeUpgradeRecipe(ResourceLocation id,CraftingBookCategory category){super(id,category);}
    private boolean valid(CraftingContainer grid){
        if(grid.getWidth()!=3||grid.getHeight()!=3)return false;
        ItemStack center=grid.getItem(4);
        if(!(center.getItem() instanceof BronzeWeaponItem)||center.getCount()!=1||BronzeWeaponItem.upgrade(center)>=10)return false;
        if(center.hasTag()&&center.getTag().contains(BronzeWeaponItem.FLIGHT))return false;
        for(int i=0;i<9;i++)if(i!=4&&!grid.getItem(i).is(Items.COPPER_BLOCK))return false;
        return true;
    }
    @Override public boolean matches(CraftingContainer grid,Level level){return valid(grid);}
    @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess registries){
        if(!valid(grid))return ItemStack.EMPTY;
        ItemStack output=grid.getItem(4).copyWithCount(1);
        output.setTag(upgradedTag(output.getOrCreateTag()));
        return output;
    }
    static net.minecraft.nbt.CompoundTag upgradedTag(net.minecraft.nbt.CompoundTag original){
        int level=Math.max(0,original.getInt(BronzeWeaponItem.UPGRADE));
        if(level>=10)throw new IllegalArgumentException("Bronze weapon already fully upgraded");
        var result=original.copy();result.putInt(BronzeWeaponItem.UPGRADE,level+1);return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return width==3&&height==3;}
    @Override public RecipeSerializer<?> getSerializer(){return BronzeWeapons.UPGRADE.get();}
}
