package com.tnc.tnc.mixin;

import com.tnc.tnc.magic.water.WaterBoreEditGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BlockBehaviour.BlockStateBase.class,remap=false)
abstract class WaterBoreBlockStateMixin {
    @Inject(method={"neighborChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            "m_60690_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noImmediateNeighbors(Level level,BlockPos pos,Block block,BlockPos from,boolean moving,CallbackInfo ci) {
        if(WaterBoreEditGuard.active())ci.cancel();
    }
}
