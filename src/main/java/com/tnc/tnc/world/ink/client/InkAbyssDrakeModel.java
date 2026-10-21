package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.ink.InkAbyssDrakeEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class InkAbyssDrakeModel extends GeoModel<InkAbyssDrakeEntity> {
    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(InkAbyssDrakeEntity entity) {
        return asset("geo/entity/ink_abyss_drake.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(InkAbyssDrakeEntity entity) {
        return asset("textures/entity/ink_abyss_drake.png");
    }

    @Override
    public ResourceLocation getAnimationResource(InkAbyssDrakeEntity entity) {
        return asset("animations/entity/ink_abyss_drake.animation.json");
    }
}
