package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Solid moving jewellery must own its depth, whichever deferred item batch flushes first. */
public final class WarriorWeaponOrnamentRenderType extends RenderType {
    private WarriorWeaponOrnamentRenderType(){
        super("tnc_weapon_ornament",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,
                65536,false,false,()->{},()->{});
    }
    public static final RenderType TYPE=create("tnc_weapon_ornament",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,65536,false,false,CompositeState.builder()
                    // Full-bright UVs keep the jewellery lit; the opaque entity shader preserves normal item fog.
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER).setLightmapState(LIGHTMAP)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/beam_white.png"),false,false))
                    .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
    public static VertexConsumer buffer(MultiBufferSource buffers){
        return new WarriorEffectVertexConsumer(buffers.getBuffer(TYPE));
    }
}
