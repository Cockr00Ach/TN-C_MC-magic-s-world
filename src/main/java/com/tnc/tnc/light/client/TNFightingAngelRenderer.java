package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.light.TNFightingAngelEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 战斗天使渲染器（模型高 2.44 格；按档位放大：t1 0.7 → t5 1.4 ✓，只改这一处公式 ✓） */
public class TNFightingAngelRenderer extends GeoEntityRenderer<TNFightingAngelEntity> {
    public TNFightingAngelRenderer(EntityRendererProvider.Context context) {
        super(context, new TNFightingAngelGeoModel());
        this.shadowRadius = 0.5F;
    }

    /**
     * ★ 为什么要在这里转 180°（作者 2026-10-02："是你现在用背部攻击敌人啊"）
     *
     * <p><b>这个模型是反着做的</b> ✗：作者把眼睛画在头的 <b>+Z</b> 那一面，
     * 而 GeckoLib 和原版一样，把模型的 <b>−Z</b> 当成实体朝向
     * （证据：{@code GeoEntityRenderer.applyRotations} 里就是
     * {@code Axis.YP.rotationDegrees(180.0F - rotationYaw)} ✓，和原版
     * {@code LivingEntityRenderer.setupRotations} 一字不差 ✓）
     * ⇒ 不处理就是**拿背对着敌人** ✗，飞起来也是倒着飞 ✗。
     *
     * <p>作者其它模型都是 −Z 朝前（公孙衍：{@code hairBangs} 在 −Z、{@code hairBack} 在 +Z ✓），
     * 只有这对天使模型是反的 ✓。判据不是猜的：把头部贴图的 box-UV 四面裁出来看眼睛在哪一面
     * ——{@code tools/face_uv_check.py} ✓，输出 {@code docs/previews/face_sides_*.png} ✓。
     *
     * <p><b>作者以后若重新导出这个模型（脸改回 −Z），把这一小段删掉即可</b> ✓。
     */
    @Override
    public void preRender(PoseStack poseStack, TNFightingAngelEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
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