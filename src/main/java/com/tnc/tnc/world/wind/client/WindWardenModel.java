package com.tnc.tnc.world.wind.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.wind.WindWardenEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class WindWardenModel extends GeoModel<WindWardenEntity> {
    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(WindWardenEntity entity) {
        return asset("geo/entity/wind_warden.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WindWardenEntity entity) {
        return asset("textures/entity/wind_warden.png");
    }

    @Override
    public ResourceLocation getAnimationResource(WindWardenEntity entity) {
        return asset("animations/entity/wind_warden.animation.json");
    }
}
