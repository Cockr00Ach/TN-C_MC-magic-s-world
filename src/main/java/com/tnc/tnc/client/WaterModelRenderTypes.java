package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Textured models with real alpha blending and normal light-map support. */
final class WaterModelRenderTypes extends RenderType {
    private WaterModelRenderTypes(){super("tnc_water_model_alpha",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static final RenderType ALPHA=create("tnc_water_model_alpha",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    static RenderType alpha(){return ALPHA;}
    private static final RenderType EMISSION=create("tnc_water_model_emission",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    static RenderType emission(){return EMISSION;}
}
