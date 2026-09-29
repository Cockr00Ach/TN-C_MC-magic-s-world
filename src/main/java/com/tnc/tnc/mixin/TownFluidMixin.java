package com.tnc.tnc.mixin;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FlowingFluid.class,remap=false)
abstract class TownFluidMixin {
    @Inject(method={"spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V","m_6364_(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noTownFlood(LevelAccessor level,BlockPos pos,BlockState state,Direction direction,FluidState fluid,CallbackInfo ci){if(TownProtection.hazard(level,pos))ci.cancel();}
}
