package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.world.ink.InkCranePhantomEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class InkCranePhantomRenderer extends GeoEntityRenderer<InkCranePhantomEntity> {
    public InkCranePhantomRenderer(EntityRendererProvider.Context context) {
        super(context, new InkCranePhantomModel()); withScale(.42F); shadowRadius = 0;
    }

    @Override public RenderType getRenderType(InkCranePhantomEntity entity, ResourceLocation texture,
                                               MultiBufferSource buffers, float partial) {
        return RenderType.entityTranslucent(texture);
    }
    @Override public Color getRenderColor(InkCranePhantomEntity entity, float partial, int light) {
        float formed = entity.mode() == InkCranePhantomEntity.LONG_WINGS
                ? Mth.clamp((entity.age() + partial) / 12, 0, 1) : 1;
        return Color.ofRGBA(1F, 1F, 1F, (entity.finished() ? .10F : .42F) * formed);
    }

    @Override protected void applyRotations(InkCranePhantomEntity entity, PoseStack pose, float age,
                                             float ignoredBodyYaw, float partial) {
        // GeoEntityRenderer supplies a zero body yaw for non-LivingEntity models.
        // These phantoms must instead use their synchronized flight heading.
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
    }

    @Override public void render(InkCranePhantomEntity entity, float yaw, float partial, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        if (entity.mode() == InkCranePhantomEntity.RITUAL) {
            InkCraneRitualVisuals.ritual(entity, partial, pose, buffers); return;
        }
        InkCraneRitualVisuals.trail(entity, pose, buffers);
        InkCraneRitualVisuals.aura(entity, partial, pose, buffers);
        pose.pushPose();
        try {
            float scale = entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE ? 20F / .42F
                    : entity.mode() == InkCranePhantomEntity.GIANT_DIVE ? 20F
                    : entity.mode() == InkCranePhantomEntity.LONG_WINGS ? 10F : 2F;
            pose.scale(scale, scale, scale);
            pose.translate(0, entity.mode() == InkCranePhantomEntity.DRAKE_CHARGE ? -.63 : -3.23, 0);
            super.render(entity, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
        } finally { pose.popPose(); }
    }
}
