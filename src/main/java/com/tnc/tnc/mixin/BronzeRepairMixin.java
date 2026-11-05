package com.tnc.tnc.mixin;

import com.tnc.tnc.bronze.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla two-item crafting repair builds a fresh stack; carry the higher temper level forward. */
@Mixin(RepairItemRecipe.class)
public abstract class BronzeRepairMixin {
    @Inject(method={"matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z","m_5818_(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z"},at=@At("HEAD"),remap=false,cancellable=true,require=1)
    private void bronzeNoFlyingRepair(CraftingContainer grid,Level level,CallbackInfoReturnable<Boolean> result){
        for(int i=0;i<grid.getContainerSize();i++){
            var stack=grid.getItem(i);
            if(stack.getItem() instanceof BronzeWeaponItem&&stack.hasTag()&&stack.getTag().contains(BronzeWeaponItem.FLIGHT)){result.setReturnValue(false);return;}
        }
    }
    @Inject(method={"assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;","m_5874_(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"},at=@At("RETURN"),remap=false,cancellable=true,require=1)
    private void bronzeKeepTempering(CraftingContainer grid,RegistryAccess registries,CallbackInfoReturnable<ItemStack> result){
        var output=result.getReturnValue();if(output.isEmpty()||!(output.getItem() instanceof BronzeWeaponItem))return;
        int level=0;for(int i=0;i<grid.getContainerSize();i++)level=Math.max(level,BronzeWeaponItem.upgrade(grid.getItem(i)));
        if(level>0)output.getOrCreateTag().putInt(BronzeWeaponItem.UPGRADE,level);
    }
}
