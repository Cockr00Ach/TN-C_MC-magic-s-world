package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNThunderOrbEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 环绕雷球的渲染器 —— 贴投射物那张贴图，<b>全亮度</b>画（它就是发光的球 ✓）。
 *
 * <p>全亮度对应法术 JSON 里投射物的 {@code "light_emission": "RADIATE"}：
 * 站在暗处也该看得见它，不然一颗黑球没法看 ✗。
 *
 * <p>{@link #SCALE} 是唯一的尺寸旋钮：投射物本体走法术 JSON 的 {@code scale}，
 * 而这里绕身的球是固定大小（作者要"我们的实体雷球"，先给个和 t1 雷球接近的尺寸 ✓）。
 * 想让它更大/更小只改这一个数。
 */
public class TNThunderOrbRenderer extends EntityRenderer<TNThunderOrbEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/lightingball.png");

    /** 1.0 = 模型原尺寸（6/16 格 ≈ 0.375 格）。 */
    private static final float SCALE = 1.5F;

    private final TNThunderOrbModel model;

    public TNThunderOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new TNThunderOrbModel(context.bakeLayer(TNThunderOrbModel.LAYER));
        this.shadowRadius = 0.0F;       // 悬浮的光球不投影子
    }

    @Override
    public ResourceLocation getTextureLocation(TNThunderOrbEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNThunderOrbEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // 自转：让"尖"转起来，静态看着像卡住了 ✓
        float spin = (entity.tickCount + partialTick) * 12.0F;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        poseStack.scale(SCALE, SCALE, SCALE);
        VertexConsumer consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
