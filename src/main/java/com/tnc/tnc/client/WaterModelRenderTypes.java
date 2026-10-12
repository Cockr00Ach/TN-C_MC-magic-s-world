package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Full-bright textured models with real alpha blending for transparent rain seals. */
final class WaterModelRenderTypes extends RenderType {
    private WaterModelRenderTypes(){super("tnc_water_model_alpha",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static final RenderType ALPHA=create("tnc_water_model_alpha",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    static RenderType alpha(){return ALPHA;}
}
