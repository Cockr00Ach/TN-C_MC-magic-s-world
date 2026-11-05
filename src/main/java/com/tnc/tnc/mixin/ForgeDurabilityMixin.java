package com.tnc.tnc.mixin;

import com.tnc.tnc.warrior.forge.ForgeWeaponData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only tagged hand-forged weapons change; wands, guns and every untagged item are untouched. */
@Mixin(ItemStack.class)
public abstract class ForgeDurabilityMixin {
    @Inject(method={"getMaxDamage()I","m_41776_()I"},at=@At("RETURN"),remap=false,cancellable=true,require=1)
    private void tncForgeDurability(CallbackInfoReturnable<Integer> result){
        var q=ForgeWeaponData.quality((ItemStack)(Object)this);
        if(q!=null&&result.getReturnValueI()>0)result.setReturnValue(Math.max(1,(int)Math.round(result.getReturnValueI()*q.durability)));
    }
}
