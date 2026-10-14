package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNWaterLeviathanEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Makes the eyes, crown and sacred scale seams readable in deep water and at night. */
public final class TNWaterLeviathanEmissiveLayer extends GeoRenderLayer<TNWaterLeviathanEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/water_leviathan_emissive.png");

    public TNWaterLeviathanEmissiveLayer(GeoRenderer<TNWaterLeviathanEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TNWaterLeviathanEntity entity, BakedGeoModel model,
                       RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
        this.getRenderer().reRender(model, poseStack, buffers, entity, glow, buffers.getBuffer(glow),
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, entity.isPhaseTwo() ? 1.0F : 0.78F);
    }
}
