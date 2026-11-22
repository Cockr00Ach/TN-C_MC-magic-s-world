package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Use the same shader-compatible emissive entity path as the authored water surfaces. */
final class WaterEffectPass extends RenderType {
    private WaterEffectPass(){super("tnc_water_ink",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,8192,false,true,()->{},()->{});}
    static RenderType create(ResourceLocation texture){
        return create("tnc_water_ink_"+texture,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,8192,false,true,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(texture,true,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
    }
}
