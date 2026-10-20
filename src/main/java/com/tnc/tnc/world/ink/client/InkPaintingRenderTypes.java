package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Paint is color-only: overlapping translucent washes must not write competing depth values. */
final class InkPaintingRenderTypes extends RenderType {
    private InkPaintingRenderTypes(){super("tnc_ink_painting",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    private static final RenderType PAINT=create("tnc_ink_painting",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,262144,false,false,CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("tnc","dynamic/ink_shanshui_neutral"),true,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setLayeringState(POLYGON_OFFSET_LAYERING).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET).createCompositeState(false));
    static RenderType paint(){return PAINT;}
}
