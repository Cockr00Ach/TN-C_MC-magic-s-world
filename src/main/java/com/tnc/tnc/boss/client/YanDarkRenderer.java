package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.YanDarkBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 公孙衍（迷失）的渲染器；模型本身 2.2125 格高，这里再放大 1.15 倍 ✓（只改这一个数） */
public class YanDarkRenderer extends GeoEntityRenderer<YanDarkBossEntity> {
    private static final float MODEL_SCALE = 1.15F;
    public YanDarkRenderer(EntityRendererProvider.Context context) {
        super(context, new YanDarkGeoModel());
        this.shadowRadius = 0.7F;
    }
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    YanDarkBossEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        if (MODEL_SCALE != 1.0F) {
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }
    }
}