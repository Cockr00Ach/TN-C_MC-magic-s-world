package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.TNDarkGiantEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 暗系巨人领主的 GeckoLib 模型 ✓（照 {@code TNApolloGeoModel} 那套 ✓）。
 *
 * <p>动画文件是有意留空的（{@code {"animations":{}}} ✓）—— 模型是静态的，
 * 等作者要挥砍/咆哮再补，补法和阿波罗一样（{@code triggerableAnim} ✓）。
 * {@code crashIfBoneMissing()} 返回 false ✓：以后作者改模型骨骼名也不会把客户端搞崩 ✗。
 */
public class TNDarkGiantGeoModel extends GeoModel<TNDarkGiantEntity> {
    @Override
    public ResourceLocation getModelResource(TNDarkGiantEntity a) { return TNDarkGiantEntity.modelResource(); }
    @Override
    public ResourceLocation getTextureResource(TNDarkGiantEntity a) { return TNDarkGiantEntity.textureResource(); }
    @Override
    public ResourceLocation getAnimationResource(TNDarkGiantEntity a) { return TNDarkGiantEntity.animationResource(); }
    @Override
    public boolean crashIfBoneMissing() { return false; }
}
