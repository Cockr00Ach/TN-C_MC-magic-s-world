package com.tnc.tnc.boss.client;

import com.tnc.tnc.boss.TNWaterLeviathanEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class TNWaterLeviathanGeoModel extends GeoModel<TNWaterLeviathanEntity> {
    @Override public ResourceLocation getModelResource(TNWaterLeviathanEntity entity) {
        return TNWaterLeviathanEntity.modelResource();
    }
    @Override public ResourceLocation getTextureResource(TNWaterLeviathanEntity entity) {
        return TNWaterLeviathanEntity.textureResource();
    }
    @Override public ResourceLocation getAnimationResource(TNWaterLeviathanEntity entity) {
        return TNWaterLeviathanEntity.animationResource();
    }
    @Override public boolean crashIfBoneMissing() { return false; }
}
