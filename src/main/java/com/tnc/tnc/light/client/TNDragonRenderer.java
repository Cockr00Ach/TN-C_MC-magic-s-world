package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.light.TNDragonEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 光明龙渲染器 ✓ —— 和光天使同一套"半透明 + 自发光"（作者那条龙是"光明龙"✓，
 * 贴图里的亮金/白热像素就是要它发光 ✓）。
 *
 * <p>缩放由法术写进实体（{@code scale() ✓}：t3 0.30 / t4 0.40 / t5 0.45 ✓），
 * 放在 {@code scaleModelForRender} 里 —— 和天使/修罗那两个同一个钩子 ✓。
 *
 * <p>★ 这条龙长 23 格 ✗ ⇒ 剔除盒必须在**实体**里按体长撑大
 * （{@link TNDragonEntity#getBoundingBoxForCulling} ✓），不然抬头只看一段时整条消失 ✓。
 */
public class TNDragonRenderer extends GeoEntityRenderer<TNDragonEntity> {

    public TNDragonRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDragonGeoModel());
        this.shadowRadius = 0.0F;                 // 发光体不投影 ✓（和光天使一致 ✓）
    }

    /** 自发光 + 半透明 ✓（和光天使/光翼同一套写法 ✓）。 */
    @Override
    public RenderType getRenderType(TNDragonEntity animatable, ResourceLocation texture,
                                    net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
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
