package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** A textured sword silhouette may tint the scene, but must never become an invisible depth wall. */
final class WarriorPhantomRenderType extends RenderType {
    private WarriorPhantomRenderType() {
        super("tnc_weapon_phantom",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,
                65536,false,true,()->{},()->{});
    }
    static final RenderType TYPE=create("tnc_weapon_phantom",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,65536,false,true,CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setOutputState(MAIN_TARGET).createCompositeState(false));
}
