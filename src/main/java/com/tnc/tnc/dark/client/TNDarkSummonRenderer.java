package com.tnc.tnc.dark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.dark.TNDarkSummonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 暗系召唤物渲染器 ✓ —— 五档共用（模型类自己按档位挑文件 ✓）。
 *
 * <p>★ 不走自发光 ✗（作者 2026-10-04 对暗系的要求："不要自发光" ✓）：暗影就该是暗的 ✓，
 * 正常吃场景光照、黑处就是一团黑 ✓。缩放由法术写进实体（默认 1.0 ✓）。
 */
public class TNDarkSummonRenderer extends GeoEntityRenderer<TNDarkSummonEntity> {

    public TNDarkSummonRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDarkSummonGeoModel());
        this.shadowRadius = 0.4F;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNDarkSummonEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) animatable.scale();
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
    }
}
