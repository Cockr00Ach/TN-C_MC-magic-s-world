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

    @Override public void setCustomAnimations(CanglanWarlordEntity entity,long instanceId,software.bernie.geckolib.core.animation.AnimationState<CanglanWarlordEntity> state){
        super.setCustomAnimations(entity,instanceId,state);
        var execution=CanglanExecutionClient.state(entity.getId());if(!CanglanExecutionClient.driving(execution,state.getPartialTick()))return;
        double age=CanglanExecutionClient.age(execution,state.getPartialTick());
        for(String name:com.tnc.tnc.world.wind.CanglanExecutionPose.bones()){
            var bone=getAnimationProcessor().getBone(name);if(bone==null)continue;
            double[] r=com.tnc.tnc.world.wind.CanglanExecutionPose.rotation(name,age);
            bone.setRotX((float)Math.toRadians(-r[0]));bone.setRotY((float)Math.toRadians(-r[1]));bone.setRotZ((float)Math.toRadians(r[2]));
            bone.setPosX(0);bone.setPosY(0);bone.setPosZ(0);
        }
    }
}
