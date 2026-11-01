package com.tnc.tnc.world.ink.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.Minecraft;
import com.tnc.tnc.world.ink.InkDragonDread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=ItemInHandRenderer.class,remap=false)
public abstract class InkHandTimeMixin {
    @ModifyVariable(method={"renderArmWithItem","m_109371_"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float ink$quarterPartial(float partial){var p=Minecraft.getInstance().player;return InkDragonDread.active(p)?((p.tickCount&3)+partial)*.25F:partial;}
}
