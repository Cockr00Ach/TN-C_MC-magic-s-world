package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 只做一件事：在 GeckoLib 渲染到 {@code shell}/{@code whorl} 骨骼时，把当前骨骼矩阵
 * 抄给 {@link VolcanicTortoiseAmbientFx}。
 *
 * <p>放在骨骼渲染层而不是实体坐标上算，是为了让烟和火星自动跟随：
 * 阶段模型（壳顶高度不同）、朝向、缩放、待机/行走/攻击/转阶段的骨骼动画。
 * 本层不改任何骨骼、不提交任何顶点，所以不会和正式动画抢控制权。
 */
public final class VolcanicTortoiseAnchorLayer extends GeoRenderLayer<VolcanicTortoiseEntity> {
    public VolcanicTortoiseAnchorLayer(GeoRenderer<VolcanicTortoiseEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, VolcanicTortoiseEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        String name = bone.getName();
        if ("whorl".equals(name) || "shell".equals(name)) {
            VolcanicTortoiseAmbientFx.captureBone(animatable, name, poseStack);
        }
    }
}
