package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.fire.TNLavaFieldEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 熔岩地的渲染器 —— <b>视觉由粒子承担</b>（见 {@code TNLavaFieldEntity.tick} 的客户端分支）。
 *
 * <p>和 {@link TNFireBoltRenderer} 同一个取舍：真的要画一层贴地的火面，
 * 得先有火系那套 {@code FireGeometry}；这一版先用"地面上闷烧的火星"表达，
 * 要升级只改这一个文件。
 */
public final class TNLavaFieldRenderer extends EntityRenderer<TNLavaFieldEntity> {

    /** 不会被真正贴图（render 是空的），指向一张一定存在的原版贴图只为不触发缺图警告。 */
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");

    public TNLavaFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(TNLavaFieldEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 96.0D * 96.0D;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLavaFieldEntity entity) {
        return PLACEHOLDER;
    }

    @Override
    public void render(TNLavaFieldEntity entity, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        // 故意留空：熔岩地的样子由粒子表达，见类注释。
    }
}
