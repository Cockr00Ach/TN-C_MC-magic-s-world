package com.tnc.tnc.bronze;

import java.util.EnumSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exactly one of each original weapon, all +10. Shapeless, no repair or duplicated-type shortcuts. */
public final class BronzeFusionRecipe extends CustomRecipe {
    public BronzeFusionRecipe(ResourceLocation id,CraftingBookCategory category){super(id,category);}
    private boolean valid(CraftingContainer grid){
        if(grid.getWidth()!=3||grid.getHeight()!=3)return false;
        var kinds=new java.util.ArrayList<BronzeProfile>();var levels=new java.util.ArrayList<Integer>();
        for(int i=0;i<grid.getContainerSize();i++){
            var stack=grid.getItem(i);if(stack.isEmpty())continue;
            if(!(stack.getItem() instanceof BronzeWeaponItem item)||stack.getCount()!=1||stack.hasTag()&&stack.getTag().contains(BronzeWeaponItem.FLIGHT))return false;
            kinds.add(item.profile);levels.add(BronzeWeaponItem.upgrade(stack));
        }
        return ready(kinds,levels);
    }
    static boolean ready(java.util.List<BronzeProfile> kinds,java.util.List<Integer> levels){return kinds.size()==4&&levels.size()==4&&!kinds.contains(BronzeProfile.HALBERD)&&new java.util.HashSet<>(kinds).size()==4&&levels.stream().allMatch(n->n==10);}
    @Override public boolean matches(CraftingContainer grid,Level level){return valid(grid);}
    @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){if(!valid(grid))return ItemStack.EMPTY;var result=new ItemStack(BronzeWeapons.WEAPONS.get(BronzeProfile.HALBERD).get());result.getOrCreateTag().putInt(BronzeWeaponItem.UPGRADE,10);return result;}
    @Override public boolean canCraftInDimensions(int width,int height){return width==3&&height==3;}
    @Override public RecipeSerializer<?> getSerializer(){return BronzeWeapons.FUSION.get();}
}
