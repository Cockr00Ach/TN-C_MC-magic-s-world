package com.tnc.tnc.mixin;

import com.tnc.tnc.world.eartharena.EarthArena;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes mod spells that remove blocks directly, not just player mining events. */
@Mixin(value=Level.class,remap=false)
abstract class EarthArenaLevelMixin {
    @Inject(method={"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$immutableArena(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> result) {
        Level level = (Level)(Object)this;
        if (!level.isClientSide && level.dimension().equals(EarthArena.KEY)) result.setReturnValue(false);
    }
}
