package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.ink.InkCraneBossEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import net.minecraft.util.Mth;

public final class InkCraneBossModel extends GeoModel<InkCraneBossEntity> {
    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @Override public ResourceLocation getModelResource(InkCraneBossEntity entity) {
        return asset("geo/entity/ink_crane_boss.geo.json");
    }
    @Override public ResourceLocation getTextureResource(InkCraneBossEntity entity) {
        return asset("textures/entity/ink_crane_boss.png");
    }
    @Override public ResourceLocation getAnimationResource(InkCraneBossEntity entity) {
        return asset("animations/entity/ink_crane_boss.animation.json");
    }

    @Override
    public void setCustomAnimations(InkCraneBossEntity entity, long instanceId,
                                    AnimationState<InkCraneBossEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        if (entity.inkMove() != 0) return;
        EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (look == null) return;
        float yaw = Mth.clamp(look.netHeadYaw(), -65.0F, 65.0F) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(look.headPitch(), -35.0F, 35.0F) * Mth.DEG_TO_RAD;
        // Share the turn over the S-neck and skull instead of swivelling a rigid neck.
        String[] names = {"neck_low", "neck_mid", "neck_high", "head"};
        float[] weights = {0.12F, 0.22F, 0.26F, 0.40F};
        for (int i = 0; i < names.length; i++) {
            var part = getAnimationProcessor().getBone(names[i]);
            if (part != null) {
                part.setRotY(yaw * weights[i]);
                part.setRotX(pitch * weights[i]);
            }
        }
    }
}
