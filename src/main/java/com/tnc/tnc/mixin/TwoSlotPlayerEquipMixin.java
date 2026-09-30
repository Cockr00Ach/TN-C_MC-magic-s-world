package com.tnc.tnc.mixin;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public abstract class TwoSlotPlayerEquipMixin {
    @Inject(method="canTakeItem",at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noLowerDispenser(ItemStack stack,CallbackInfoReturnable<Boolean> ci){
        if(stack.getItem() instanceof ArmorItem a&&(a.getEquipmentSlot()==EquipmentSlot.LEGS||a.getEquipmentSlot()==EquipmentSlot.FEET))ci.setReturnValue(false);
    }
}
