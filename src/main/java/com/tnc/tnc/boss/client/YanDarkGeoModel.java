package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.YanDarkBossEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 公孙衍（迷失）的 GeckoLib 模型：只回答"模型/贴图/动画在哪" ✓ */
public class YanDarkGeoModel extends GeoModel<YanDarkBossEntity> {
    @Override
    public ResourceLocation getModelResource(YanDarkBossEntity a) { return YanDarkBossEntity.modelResource(); }
    @Override
    public ResourceLocation getTextureResource(YanDarkBossEntity a) { return YanDarkBossEntity.textureResource(); }
    @Override
    public ResourceLocation getAnimationResource(YanDarkBossEntity a) { return YanDarkBossEntity.animationResource(); }
    @Override
    public boolean crashIfBoneMissing() { return false; }
}