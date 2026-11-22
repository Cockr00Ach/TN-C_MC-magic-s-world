package com.tnc.tnc.mana.sight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/** Untinted translucent pigment. Share the entity vertex layout understood by Oculus and vanilla. */
final class SpiritVeil {
    static boolean skipOpaque;
    static final ParticleRenderType SHEET=new ParticleRenderType(){
        @Override public void begin(BufferBuilder buffer,TextureManager textures){
            skipOpaque=ManaParticleShaderCompat.opaquePass();
            RenderSystem.setShader(GameRenderer::getRendertypeEyesShader);
            RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
        }
        @Override public void end(Tesselator t){
            t.end();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(true);
        }
        @Override public String toString(){return "TN_C_TRANSLUCENT_SPIRIT_SKIN";}
    };
    private SpiritVeil(){}
}

