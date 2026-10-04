package com.tnc.tnc.dark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.light.TNDragonEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 暗龙渲染器 ✓ —— 和光龙那个（{@code light/client/TNDragonRenderer}）**只差一张贴图** ✓：
 * 光龙是半透明自发光的"光明龙"✓，暗龙同样自发光，只是贴图换成玄黑紫 ✓
 * （自发光对暗龙反而更对：紫黑鳞里那些幽紫亮边在黑处也会亮 ✓）。
 *
 * <p>缩放同样由法术写进实体（{@code scale() ✓}），照抄光龙那一处 ✓。
 */
public class TNDarkDragonRenderer extends GeoEntityRenderer<TNDragonEntity> {

    public TNDarkDragonRenderer(EntityRendererProvider.Context context) {
        super(context, new TNDarkDragonGeoModel());
        this.shadowRadius = 0.0F;                 // 发光体不投影 ✓（和光龙/光天使一致 ✓）
    }

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
