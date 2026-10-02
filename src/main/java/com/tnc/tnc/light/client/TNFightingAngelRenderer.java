package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.light.TNFightingAngelEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 战斗天使渲染器（模型高 2.44 格；按档位放大：t1 0.7 → t5 1.4 ✓，只改这一处公式 ✓） */
public class TNFightingAngelRenderer extends GeoEntityRenderer<TNFightingAngelEntity> {
    public TNFightingAngelRenderer(EntityRendererProvider.Context context) {
        super(context, new TNFightingAngelGeoModel());
        this.shadowRadius = 0.5F;
    }
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNFightingAngelEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = 0.7F + 0.175F * (animatable.tier() - 1);       // t1 0.70 / t3 1.05 / t5 1.40 ✓
        poseStack.scale(s, s, s);
    }
}