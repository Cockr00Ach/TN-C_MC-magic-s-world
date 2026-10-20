package com.tnc.tnc.boss.wind.client;

import com.tnc.tnc.boss.wind.WindGeneralEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class WindGeneralModel extends GeoModel<WindGeneralEntity> {
    private static ResourceLocation asset(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    @Override public ResourceLocation getModelResource(WindGeneralEntity e){return asset("geo/entity/wind_general.geo.json");}
    @Override public ResourceLocation getTextureResource(WindGeneralEntity e){return asset("textures/entity/wind_general.png");}
    @Override public ResourceLocation getAnimationResource(WindGeneralEntity e){return asset("animations/entity/wind_general.animation.json");}
}
