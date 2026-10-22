package com.tnc.tnc.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class ArcanaWaterWalkMixin {
    @Inject(method="canStandOnFluid",at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$mealWaterWalk(FluidState fluid,CallbackInfoReturnable<Boolean> ci){var self=(LivingEntity)(Object)this;if(!self.isShiftKeyDown()&&self.hasEffect(com.tnc.tnc.arcana.ArcanaMealEffects.MEALS[4])&&fluid.is(net.minecraft.tags.FluidTags.WATER))ci.setReturnValue(true);}
}
