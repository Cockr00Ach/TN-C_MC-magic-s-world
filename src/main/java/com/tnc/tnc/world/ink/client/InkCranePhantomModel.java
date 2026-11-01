package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.InkCranePhantomEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** Uses the actual boss geometry; transparency belongs to the renderer. */
public final class InkCranePhantomModel extends GeoModel<InkCranePhantomEntity> {
    private ResourceLocation resource(InkCranePhantomEntity entity, String folder, String extension) {
        String name = entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE ? "ink_abyss_drake" : "ink_crane_boss";
        return ResourceLocation.fromNamespaceAndPath("tnc", folder + "/entity/" + name + extension);
    }
    @Override public ResourceLocation getModelResource(InkCranePhantomEntity entity) {
        String name = entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE ? "ink_abyss_drake_phantom" : "ink_crane_phantom";
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/" + name + ".geo.json");
    }
    @Override public ResourceLocation getTextureResource(InkCranePhantomEntity entity) { return resource(entity, "textures", ".png"); }
    @Override public ResourceLocation getAnimationResource(InkCranePhantomEntity entity) { return resource(entity, "animations", ".animation.json"); }

    @Override public void setCustomAnimations(InkCranePhantomEntity entity, long id,
                                              AnimationState<InkCranePhantomEntity> state) {
        super.setCustomAnimations(entity, id, state);
        boolean wings = entity.mode() == InkCranePhantomEntity.LONG_WINGS;
        for (var raw : getAnimationProcessor().getRegisteredBones()) {
            GeoBone b = (GeoBone) raw;
            b.setHidden(false);
            // V4 uses the complete avatar, including torso, legs and crown.
            b.setChildrenHidden(false);
        }
        GeoBone root = (GeoBone) getAnimationProcessor().getBone("root");
        if (root != null) {
            boolean drake = entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE;
            root.setPivotY(drake ? 24 : 123);
            if (!wings) {
                root.setRotX((entity.mode() == InkCranePhantomEntity.GIANT_DIVE ? -110 : drake ? -entity.getXRot() : -70) * Mth.DEG_TO_RAD);
                root.setRotY(0);
            }
        }
        for (String side : new String[]{"l", "r"}) {
            GeoBone b = (GeoBone) getAnimationProcessor().getBone("wing_" + side);
            if (b == null) continue;
            b.setScaleX(1.5F); b.setScaleY(1); b.setScaleZ(1);
        }
    }
}
