package com.tnc.tnc.world.ink.mixin;

import com.tnc.tnc.world.ink.InkDragonDread;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=LivingEntity.class,remap=false)
public abstract class InkLivingTimeMixin {
    @Inject(method={"getCurrentSwingDuration()I","m_21304_()I"},at=@At("RETURN"),cancellable=true)
    private void ink$slowSwing(CallbackInfoReturnable<Integer> callback){if(InkDragonDread.active((LivingEntity)(Object)this))callback.setReturnValue(callback.getReturnValue()*4);}
    @Inject(method={"shouldTriggerItemUseEffects()Z","m_21332_()Z"},at=@At("HEAD"),cancellable=true)
    private void ink$slowDrinkingSounds(CallbackInfoReturnable<Boolean> callback){var e=(LivingEntity)(Object)this;if(InkDragonDread.active(e)&&!InkDragonDread.advances(e.tickCount))callback.setReturnValue(false);}
}
