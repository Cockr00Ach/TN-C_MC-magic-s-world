package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/** Completes position/colour meshes with all NEW_ENTITY attributes in format order. */
final class WarriorEffectVertexConsumer extends VertexConsumerWrapper {
    WarriorEffectVertexConsumer(VertexConsumer parent){super(parent);}
    @Override public void endVertex(){
        parent.uv(.5f,.5f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0,1,0);
        parent.endVertex();
    }
}
