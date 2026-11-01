package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.InstructorEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 一套 geo + 一套动画，四张皮肤按变体切换。 */
public final class InstructorGeoModel extends GeoModel<InstructorEntity> {
    private static final ResourceLocation GEO =
        ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "geo/entity/instructor.geo.json");
    private static final ResourceLocation ANIM =
        ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "animations/entity/instructor.animation.json");
    private static final ResourceLocation[] SKINS = new ResourceLocation[InstructorEntity.VARIANTS.length];

    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = ResourceLocation.fromNamespaceAndPath(TNMod.MODID,
                "textures/entity/instructor_" + InstructorEntity.VARIANTS[i] + ".png");
        }
    }

    @Override
    public ResourceLocation getModelResource(InstructorEntity animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(InstructorEntity animatable) {
        int variant = Math.max(0, Math.min(SKINS.length - 1, animatable.variant()));
        return SKINS[variant];
    }

    @Override
    public ResourceLocation getAnimationResource(InstructorEntity animatable) {
        return ANIM;
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
