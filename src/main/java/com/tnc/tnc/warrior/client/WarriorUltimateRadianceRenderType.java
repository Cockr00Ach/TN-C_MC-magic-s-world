package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Small additive light accents, kept separate from the coloured blade and carved geometry passes. */
public final class WarriorUltimateRadianceRenderType extends RenderType {
    private WarriorUltimateRadianceRenderType(){
        super("tnc_ultimate_radiance",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,
                131072,false,false,()->{},()->{});
    }
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("tnc_ultimate_src_alpha_one",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    public static final RenderType TYPE=create("tnc_ultimate_radiance",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,131072,false,false,CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/beam_white.png"),false,false))
                    .setTransparencyState(ADDITIVE).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setOverlayState(OVERLAY).setOutputState(MAIN_TARGET).createCompositeState(false));
    /** Accepts position/colour/endVertex and supplies UV, overlay, fullbright light and normal attributes. */
    public static VertexConsumer buffer(MultiBufferSource buffers){
        return new WarriorEffectVertexConsumer(buffers.getBuffer(TYPE));
    }
}
