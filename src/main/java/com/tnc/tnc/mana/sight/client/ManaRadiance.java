package com.tnc.tnc.mana.sight.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/** Same emissive shader family as the suit's crystal eyes, with the correct entity vertex layout.
 * Ordinary shader-pack particles can be classified as smoke and relit. Luminous cores must not be.
 */
final class ManaRadiance {
    static boolean skipOpaque;
    // Two bounded native queues: Minecraft otherwise evicts after 16384 particles in one queue.
    static final ParticleRenderType[] TYPES={type("A"),type("B")};
    private static ParticleRenderType type(String name){return new ParticleRenderType(){
        public void begin(BufferBuilder b,TextureManager textures){
            skipOpaque=ManaParticleShaderCompat.opaquePass();
            RenderSystem.setShader(GameRenderer::getRendertypeEyesShader);
            RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableDepthTest();RenderSystem.enableBlend();RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
            b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
        }
        public void end(Tesselator t){t.end();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(true);}
        public String toString(){return "TN_C_MANA_EMISSIVE_CORES_"+name;}
    };}
}
