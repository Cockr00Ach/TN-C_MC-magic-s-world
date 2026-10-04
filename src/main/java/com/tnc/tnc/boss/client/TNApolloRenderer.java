package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.TNApolloEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 阿波罗的渲染器（模型本身约 2.15 格，这里再放大 1.25 倍 => 约 2.7 格 ✓，只改这一个数 ✓） */
public class TNApolloRenderer extends GeoEntityRenderer<TNApolloEntity> {
    private static final float MODEL_SCALE = 1.25F;
    public TNApolloRenderer(EntityRendererProvider.Context context) {
        super(context, new TNApolloGeoModel());
        this.shadowRadius = 0.7F;
    }
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNApolloEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        if (MODEL_SCALE != 1.0F) {
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }
    }
}