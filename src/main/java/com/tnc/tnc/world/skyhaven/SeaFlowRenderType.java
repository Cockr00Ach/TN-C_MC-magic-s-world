package com.tnc.tnc.world.skyhaven;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
/** Transparent feathering reads terrain depth but never writes an invisible depth mask. */
final class SeaFlowRenderType extends RenderType {
 private SeaFlowRenderType(){super("tnc_sea_undertow",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
 static final RenderType FLOW=create("tnc_sea_undertow",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,262144,false,false,
  CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
   .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.parse("tnc:textures/particle/mana_glow.png"),true,false))
   .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
   .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
}
