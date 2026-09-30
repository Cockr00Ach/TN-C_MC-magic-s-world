package com.tnc.tnc.mixin;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InventoryMenu.class)
public abstract class TwoSlotInventoryMixin {
    @Inject(method="<init>",at=@At("RETURN"),require=1)
    private void tnc$twoSlots(Inventory inventory,boolean active,Player player,CallbackInfo ci){
        var menu=(InventoryMenu)(Object)this;
        for(int i:new int[]{5,6}){
            var old=menu.slots.get(i);
            var replacement=new com.tnc.tnc.equipment.GearSlot(old,i==5?17:53,player,i==5?net.minecraft.world.entity.EquipmentSlot.HEAD:net.minecraft.world.entity.EquipmentSlot.CHEST);
            replacement.index=i;menu.slots.set(i,replacement);
        }
        for(int i:new int[]{7,8}){
            var old=menu.slots.get(i);
            var disabled=new com.tnc.tnc.equipment.InactiveGearSlot(old.container,old.getContainerSlot());
            disabled.index=i;menu.slots.set(i,disabled);
        }
    }
}
