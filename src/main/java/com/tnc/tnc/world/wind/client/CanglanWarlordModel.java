package com.tnc.tnc.world.wind.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.wind.CanglanWarlordEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Points GeckoLib at the generated rig, atlas and clip set. */
public final class CanglanWarlordModel extends GeoModel<CanglanWarlordEntity> {
    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(CanglanWarlordEntity entity) {
        return asset("geo/entity/canglan_warlord.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CanglanWarlordEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID,
                "textures/entity/canglan_warlord.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CanglanWarlordEntity entity) {
        return asset("animations/entity/canglan_warlord.animation.json");
    }
}
