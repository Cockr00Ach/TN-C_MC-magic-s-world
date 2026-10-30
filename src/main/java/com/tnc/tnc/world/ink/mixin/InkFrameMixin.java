package com.tnc.tnc.world.ink.mixin;

import net.minecraft.client.renderer.GameRenderer;
import com.tnc.tnc.world.ink.client.InkScreenEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GameRenderer.class,remap=false)
public abstract class InkFrameMixin {
    @Inject(method={"render(FJZ)V","m_109093_(FJZ)V"},at=@At("TAIL"))
    private void ink$finalFrame(float partial,long nanos,boolean renderLevel,CallbackInfo ci){InkScreenEffects.finishFrame(partial);}
}
