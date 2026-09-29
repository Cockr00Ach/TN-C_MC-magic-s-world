package com.tnc.tnc.mixin;

import com.tnc.tnc.magic.water.WaterBoreEditGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit official/SRG aliases work in userdev and the reobfuscated production jar. */
@Mixin(value=Level.class,remap=false)
abstract class WaterBoreLevelMixin implements WaterBoreEditGuard.Installed {
    @Inject(method={"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$boundedWrite(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> ci) {
        if(state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock&&com.tnc.tnc.home.TownProtection.hazard((Level)(Object)this,pos)){ci.setReturnValue(false);return;}
        if(!WaterBoreEditGuard.allowSetBlock((Level)(Object)this,pos))ci.setReturnValue(false);
    }
    @Inject(method={"destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            "m_7740_(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$noCallbackDestruction(BlockPos pos,boolean drops,Entity entity,int depth,CallbackInfoReturnable<Boolean> ci) {
        if(WaterBoreEditGuard.active())ci.setReturnValue(false);
    }
}
