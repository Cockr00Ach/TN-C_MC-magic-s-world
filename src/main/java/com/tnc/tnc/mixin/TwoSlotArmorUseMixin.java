package com.tnc.tnc.mixin;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ArmorItem.class)
public abstract class TwoSlotArmorUseMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$onlyTwo(Level l,Player p,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci){
        var slot=((ArmorItem)(Object)this).getEquipmentSlot();
        if(slot==EquipmentSlot.LEGS||slot==EquipmentSlot.FEET)ci.setReturnValue(InteractionResultHolder.fail(p.getItemInHand(hand)));
    }
}
