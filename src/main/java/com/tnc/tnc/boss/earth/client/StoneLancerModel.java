package com.tnc.tnc.boss.earth.client;

import com.tnc.tnc.boss.earth.StoneLancerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public final class StoneLancerModel extends GeoModel<StoneLancerEntity> {
    private static ResourceLocation asset(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    @Override public ResourceLocation getModelResource(StoneLancerEntity e){return asset("geo/entity/stone_lancer.geo.json");}
    @Override public ResourceLocation getTextureResource(StoneLancerEntity e){return asset("textures/entity/stone_lancer.png");}
    @Override public ResourceLocation getAnimationResource(StoneLancerEntity e){return asset("animations/entity/stone_lancer.animation.json");}
    @Override public BakedGeoModel getBakedModel(ResourceLocation r){var model=super.getBakedModel(r);StoneLancerMesh.apply(model);return model;}
    @Override public boolean crashIfBoneMissing(){return false;}
}
