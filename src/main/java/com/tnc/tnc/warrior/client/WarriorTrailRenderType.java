package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Vanilla emissive entity routing: translucent, two-sided and colour-only depth mask. */
final class WarriorTrailRenderType {
    static final RenderType TYPE=RenderType.entityTranslucentEmissive(
            new ResourceLocation("tnc","textures/entity/beam_white.png"));
    private WarriorTrailRenderType() {}
    static VertexConsumer buffer(MultiBufferSource buffers){
        return new WarriorEffectVertexConsumer(buffers.getBuffer(TYPE));
    }
}
