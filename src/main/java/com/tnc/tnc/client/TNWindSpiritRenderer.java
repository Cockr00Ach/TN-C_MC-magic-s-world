package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.TNWindMechanics;
import com.tnc.tnc.magic.TNWindSpiritEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 风灵渲染器 ✓ —— 五档共用一个（大小由档位决定 ✓）。
 *
 * <p>★ 尺寸<b>不写在实体里</b> ✗：档位表在 {@link TNWindMechanics} 那张唯一的表上
 * （{@code scaleForTier}）✓，渲染器只负责照它缩放 ⇒ 想改大小只改那一处 ✓。
 */
public class TNWindSpiritRenderer extends GeoEntityRenderer<TNWindSpiritEntity> {

    public TNWindSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new TNWindSpiritGeoModel());
        this.shadowRadius = 0.0F;                 // 飘着的东西不该有影子 ✓
    }

    @Override
    public void render(TNWindSpiritEntity entity, float yaw, float partial, PoseStack poses,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int light) {
        if(entity.tier()>=3)super.render(entity,yaw,partial,poses,buffer,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        else WindEarthVisualBuffers.draw(buffer,(body,glow)->WindEarthGeometry.spirit(body,glow,poses.last().pose(),TNWindMechanics.scaleForTier(entity.tier()),entity.tier(),entity.tickCount+partial));
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    TNWindSpiritEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        float s = (float) (TNWindMechanics.scaleForTier(animatable.tier())
                * com.tnc.tnc.magic.WindEarthVisualRules.spiritScale(animatable.tier(),animatable.slot()));
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
    }
}
