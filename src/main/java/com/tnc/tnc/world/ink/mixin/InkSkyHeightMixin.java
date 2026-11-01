package com.tnc.tnc.world.ink.mixin;

import com.tnc.tnc.world.ink.InkRealmTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom flat dimensions do not inherit the Overworld's client isFlat flag. */
@Mixin(value=ClientLevel.ClientLevelData.class,remap=false)
public abstract class InkSkyHeightMixin {
    @Inject(method={"getHorizonHeight(Lnet/minecraft/world/level/LevelHeightAccessor;)D","m_171687_(Lnet/minecraft/world/level/LevelHeightAccessor;)D"},at=@At("HEAD"),cancellable=true)
    private void ink$flatHorizon(LevelHeightAccessor height,CallbackInfoReturnable<Double> ci) {
        if(height instanceof ClientLevel level&&level.dimension().equals(InkRealmTravel.INK_REALM))ci.setReturnValue((double)height.getMinBuildHeight());
    }
    @Inject(method={"getClearColorScale()F","m_205519_()F"},at=@At("HEAD"),cancellable=true)
    private void ink$flatVoidColor(CallbackInfoReturnable<Float> ci) {
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.dimension().equals(InkRealmTravel.INK_REALM))ci.setReturnValue(1F);
    }
}
