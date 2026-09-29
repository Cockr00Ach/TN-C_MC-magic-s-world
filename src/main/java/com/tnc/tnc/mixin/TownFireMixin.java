package com.tnc.tnc.mixin;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FireBlock.class,remap=false)
abstract class TownFireMixin {
    // Forge's six-argument extension is unmapped, unlike vanilla's five-argument checkBurnOut.
    @Inject(method="tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",at=@At("HEAD"),cancellable=true,require=1)
    private void tnc$keepStructure(Level level,BlockPos pos,int chance,RandomSource random,int age,Direction face,CallbackInfo ci){if(TownProtection.hazard(level,pos))ci.cancel();}
}
