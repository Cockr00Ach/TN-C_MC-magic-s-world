package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.TNDarkGiantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 暗系巨人领主的渲染器 ✓ —— 只做一件事：把模型缩放到 {@link TNDarkGiantEntity#MODEL_SCALE} ✓
 * （模型/贴图都在 {@code tools/gen_dark_giant.ps1} 生成的资源里 ✓，改大小只动那一个数 ✓）。
 *
 * <p>影子半径给得比阿波罗大（0.9 ✓）—— 4.5 格高的巨人配一个小影子会很怪 ✗。
 */
public class TNDarkGiantRenderer extends GeoEntityRenderer<TNDarkGiantEntity> {
    public TNDarkGiantRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDarkGiantGeoModel());
        this.shadowRadius = 0.9F;
        // ★ 二阶段的背后虚影 ✓（作者 2026-10-03）；一阶段这一层什么都不画 ✓
        this.addRenderLayer(new TNDarkGiantPhantomLayer(this));
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNDarkGiantEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) TNDarkGiantEntity.MODEL_SCALE;
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
    }
}
