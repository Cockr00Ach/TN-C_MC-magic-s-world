package com.tnc.tnc.dark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.light.TNDragonEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 暗龙渲染器 ✓ —— 和光龙那个（{@code light/client/TNDragonRenderer}）差别就是**不自发光** ✓。
 *
 * <p>★ 作者 2026-10-04：<b>"黑龙不要自发光了，让他周身弥漫着黑雾"</b> ✓
 * ⇒ 渲染类型从 {@code entityTranslucentEmissive}（发光 ✗）改成 {@code entityTranslucent}
 * （正常吃场景光照 ✓，黑鳞才真的是黑的 ✓）；"黑雾"由实体自己每 tick 沿身体撒
 * {@code LARGE_SMOKE} 来做 ✓（见 {@code light/TNDragonEntity.trail()} 的暗龙分支 ✓）。
 *
 * <p>缩放同样由法术写进实体（{@code scale() ✓}），照抄光龙那一处 ✓。
 */
public class TNDarkDragonRenderer extends GeoEntityRenderer<TNDragonEntity> {

    public TNDarkDragonRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDarkDragonGeoModel());
        this.shadowRadius = 0.0F;                 // 不投影（体形太大，投影会拖一大片 ✗）
    }

    /** ★ 不自发光 ✗ —— 半透明但正常受光 ✓（作者 2026-10-04 指定 ✓）。 */
    @Override
    public RenderType getRenderType(TNDragonEntity animatable, ResourceLocation texture,
                                    net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNDragonEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) animatable.scale();
        poseStack.scale(s, s, s);
    }
}
