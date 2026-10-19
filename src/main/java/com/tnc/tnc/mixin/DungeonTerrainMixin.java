package com.tnc.tnc.mixin;
import com.tnc.tnc.dungeon.DungeonRealm;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All direct spell edits use Level.setBlock too. Only the dungeon builder may edit its realm. */
@Mixin(value=Level.class,remap=false)
abstract class DungeonTerrainMixin {
    @Inject(method={"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z","m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$dungeonShell(BlockPos p,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> ci){if(DungeonRealm.locked((Level)(Object)this))ci.setReturnValue(false);}
}
