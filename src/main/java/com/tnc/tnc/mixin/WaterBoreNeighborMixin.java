package com.tnc.tnc.mixin;

import com.tnc.tnc.magic.water.WaterBoreEditGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent removal callbacks from enqueuing neighbor work outside the checked bore slice. */
@Mixin(value=ServerLevel.class,remap=false)
abstract class WaterBoreNeighborMixin {
    @Inject(method={"updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
            "m_46672_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noQueuedNeighbors(BlockPos pos,Block block,CallbackInfo ci) {
        if(WaterBoreEditGuard.active())ci.cancel();
    }
    @Inject(method={"updateNeighborsAtExceptFromFacing(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/Direction;)V",
            "m_46590_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/Direction;)V"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noQueuedDirectionalNeighbors(BlockPos pos,Block block,Direction direction,CallbackInfo ci) {
        if(WaterBoreEditGuard.active())ci.cancel();
    }
    @Inject(method={"neighborChanged(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;)V",
            "m_46586_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;)V"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noQueuedSingleNeighbor(BlockPos pos,Block block,BlockPos from,CallbackInfo ci) {
        if(WaterBoreEditGuard.active())ci.cancel();
    }
    @Inject(method={"neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            "m_213960_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noQueuedStateNeighbor(BlockState state,BlockPos pos,Block block,BlockPos from,boolean moving,CallbackInfo ci) {
        if(WaterBoreEditGuard.active())ci.cancel();
    }
}
