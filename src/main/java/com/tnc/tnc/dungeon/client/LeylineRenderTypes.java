package com.tnc.tnc.dungeon.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** World depth is read, never overwritten by a transparent rune or an artery's halo. */
final class LeylineRenderTypes extends RenderType {
    private LeylineRenderTypes(){super("tnc_leyline",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    static final RenderType GLOW=create("tnc_leyline_radiance",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,262144,false,true,
        CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
            .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("tnc","textures/particle/mana_glow.png"),false,false))
            .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
    static final RenderType STONE=create("tnc_leyline_material",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,16384,false,true,
        CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/smooth_stone.png"),false,false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
}
