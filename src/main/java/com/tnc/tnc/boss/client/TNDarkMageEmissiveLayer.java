package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNDarkMageEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 黑魔法师的自发光层 ✓ —— 把 {@code dark_mage_emissive.png} 用**全亮**再画一遍 ✓。
 *
 * <p>贴图里只有三块有内容（两道眼缝 / 胸前符文 / 杖心）✓，其余 alpha=0 ✓ ⇒
 * 这一层看起来"只点亮了那三处"✓，不会把整件袍子提亮 ✗。
 *
 * <p>★ 用 {@code entityTranslucentEmissive} 而不是 {@code entityCutoutNoCull} ✗：
 * 前者才吃 alpha ✓（透明处真的不画 ✓），后者会把透明像素画成黑的 ✗（整件袍子会糊一层黑 ✗）。
 */
public final class TNDarkMageEmissiveLayer extends GeoRenderLayer<TNDarkMageEntity> {

    public TNDarkMageEmissiveLayer(GeoRenderer<TNDarkMageEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TNDarkMageEntity entity, BakedGeoModel model,
                       RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glow = RenderType.entityTranslucentEmissive(TNDarkMageEntity.emissiveTextureResource());
        this.getRenderer().reRender(model, poseStack, buffers, entity, glow, buffers.getBuffer(glow),
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
    }
}
