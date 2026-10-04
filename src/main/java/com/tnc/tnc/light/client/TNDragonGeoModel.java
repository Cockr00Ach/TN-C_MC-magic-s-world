package com.tnc.tnc.light.client;

import com.tnc.tnc.light.TNDragonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 光明龙的 GeckoLib 模型（作者手改的东方龙 geo + 我画的"光明龙"贴图 + idle/dash 动画 ✓）。 */
public class TNDragonGeoModel extends GeoModel<TNDragonEntity> {

    @Override
    public ResourceLocation getModelResource(TNDragonEntity animatable) {
        return TNDragonEntity.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(TNDragonEntity animatable) {
        return TNDragonEntity.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(TNDragonEntity animatable) {
        return TNDragonEntity.animationResource();
    }

    /** 动画里出现的骨头在 geo 里都有 ✓ —— 少一根也别崩 ✗（沿用天使那套 ✓）。 */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
