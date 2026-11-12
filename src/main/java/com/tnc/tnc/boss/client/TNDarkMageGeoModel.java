package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.TNDarkMageEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 黑魔法师的 GeckoLib 模型 ✓（照 {@code TNDarkGiantGeoModel} 那套 ✓）。
 *
 * <p>{@code crashIfBoneMissing()} 返回 false ✓：以后改模型/换骨骼名也不会把客户端搞崩 ✗
 * （缺骨的动画 GeckoLib 会跳过那根骨 ✓，不会 NPE ✓）。
 */
public class TNDarkMageGeoModel extends GeoModel<TNDarkMageEntity> {
    @Override
    public ResourceLocation getModelResource(TNDarkMageEntity a) {
        return TNDarkMageEntity.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(TNDarkMageEntity a) {
        return TNDarkMageEntity.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(TNDarkMageEntity a) {
        return TNDarkMageEntity.animationResource();
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
