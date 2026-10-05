package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.fire.TNFireBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 火球的渲染器 —— <b>视觉完全由粒子承担</b>。
 *
 * <p>为什么先不做几何体：水系那颗 {@code TNWaterBoltRenderer} 靠一整套
 * {@code WaterGeometry} / {@code WaterRenderTypes} 画球体 + 拖尾，火系要同样效果得再写一套
 * {@code FireGeometry}。这一版先用<b>密集火焰粒子</b>把「火球在飞」表达出来
 * （{@link TNFireBoltEntity#tick} 的客户端分支每 2 tick 撒一圈 FLAME、每 6 tick 一颗 LAVA），
 * 先把机制跑通。**后面要换成自定义几何时，只改这一个文件**，实体与数值都不用动。
 */
public final class TNFireBoltRenderer extends EntityRenderer<TNFireBoltEntity> {

    /** 这里不会被真正贴图（render 是空的），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    public TNFireBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(TNFireBoltEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 160.0D * 160.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(TNFireBoltEntity entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(TNFireBoltEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        // 故意留空：火球的样子由粒子表达，见类注释。
    }
}
