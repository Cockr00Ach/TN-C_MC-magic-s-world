package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.molang.MolangParser;

/** 同一个实体两套模型/贴图：按 {@code phase} 选，切阶段当场换外观。 */
public final class VolcanicTortoiseGeoModel extends GeoModel<VolcanicTortoiseEntity> {
    static {
        MolangParser.INSTANCE.setValue("query.tnc_phase_two", () -> 0.0D);
    }

    @Override
    public void applyMolangQueries(VolcanicTortoiseEntity entity, double animTime) {
        super.applyMolangQueries(entity, animTime);
        MolangParser.INSTANCE.setValue("query.tnc_phase_two", () -> entity.usesPhaseTwoModel() ? 1.0D : 0.0D);
    }
    @Override
    public ResourceLocation getModelResource(VolcanicTortoiseEntity entity) {
        return VolcanicTortoiseEntity.modelResource(entity.usesPhaseTwoModel());
    }

    @Override
    public ResourceLocation getTextureResource(VolcanicTortoiseEntity entity) {
        return VolcanicTortoiseEntity.textureResource(entity.usesPhaseTwoModel());
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
