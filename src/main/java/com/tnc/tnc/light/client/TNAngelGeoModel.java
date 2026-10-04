package com.tnc.tnc.light.client;

import com.tnc.tnc.light.TNAngelEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 光天使的 GeckoLib 模型：只回答"模型 / 贴图 / 动画在哪"✓（照 {@code YanDarkGeoModel} 的写法 ✓）。 */
public class TNAngelGeoModel extends GeoModel<TNAngelEntity> {
    @Override
    public ResourceLocation getModelResource(TNAngelEntity a) { return TNAngelEntity.modelResource(); }

    @Override
    public ResourceLocation getTextureResource(TNAngelEntity a) { return TNAngelEntity.textureResource(); }

    @Override
    public ResourceLocation getAnimationResource(TNAngelEntity a) { return TNAngelEntity.animationResource(); }

    @Override
    public boolean crashIfBoneMissing() { return false; }
}
