package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 同一个实体两套模型/贴图：按 {@code phase} 选，切阶段当场换外观。 */
public final class VolcanicTortoiseGeoModel extends GeoModel<VolcanicTortoiseEntity> {
    @Override
    public ResourceLocation getModelResource(VolcanicTortoiseEntity entity) {
        return VolcanicTortoiseEntity.modelResource(entity.isPhaseTwo());
    }

    @Override
    public ResourceLocation getTextureResource(VolcanicTortoiseEntity entity) {
        return VolcanicTortoiseEntity.textureResource(entity.isPhaseTwo());
    }

    @Override
    public ResourceLocation getAnimationResource(VolcanicTortoiseEntity entity) {
        return VolcanicTortoiseEntity.animationResource();
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
