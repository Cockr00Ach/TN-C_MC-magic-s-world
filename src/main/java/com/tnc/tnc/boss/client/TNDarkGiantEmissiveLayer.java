package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNDarkGiantEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Draws only the abyss-rift mask at full brightness. */
public final class TNDarkGiantEmissiveLayer extends GeoRenderLayer<TNDarkGiantEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/dark_giant_emissive.png");

    public TNDarkGiantEmissiveLayer(GeoRenderer<TNDarkGiantEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TNDarkGiantEntity entity, BakedGeoModel model,
                       RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
        this.getRenderer().reRender(model, poseStack, buffers, entity, glow, buffers.getBuffer(glow),
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
    }
}
