package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import com.tnc.tnc.boss.fire.TortoiseDeath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 模型的单位已经是 1/16 格（60×44×88 单位 ⇒ 3.75×2.75×5.5 格），所以这里不再放大。 */
public final class VolcanicTortoiseRenderer extends GeoEntityRenderer<VolcanicTortoiseEntity> {
    private float deathAge = -1;
    public VolcanicTortoiseRenderer(EntityRendererProvider.Context context) {
        super(context, new VolcanicTortoiseGeoModel());
        this.shadowRadius = 3.4F;
        this.addRenderLayer(new VolcanicTortoiseEmissiveLayer(this));
    }

    @Override
    public void render(VolcanicTortoiseEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        this.deathAge = entity.isDyingAnimation() ? entity.deathAge(partialTick) : -1;
        this.shadowRadius = 3.4F * TortoiseDeath.opacity(this.deathAge);
        try {
            super.render(entity, yaw, partialTick, poses, buffers, light);
        } finally {
            this.deathAge = -1;
        }
    }

    @Override
    public RenderType getRenderType(VolcanicTortoiseEntity entity, ResourceLocation texture,
                                    MultiBufferSource buffers, float partialTick) {
        return entity.isDyingAnimation() ? RenderType.entityTranslucent(texture)
                : super.getRenderType(entity, texture, buffers, partialTick);
    }

    @Override
    protected float getDeathMaxRotation(VolcanicTortoiseEntity entity) {
        return 0; // The head bows through bones; no vanilla corpse roll.
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer,
                                     int light, int overlay, float red, float green, float blue, float alpha) {
        if (this.deathAge >= 0) {
            float cool = TortoiseDeath.cooling(this.deathAge);
            red *= 1 - cool * 0.72F;
            green *= 1 - cool * 0.68F;
            blue *= 1 - cool * 0.62F;
            alpha *= TortoiseDeath.opacity(this.deathAge);
            overlay = OverlayTexture.NO_OVERLAY;
        }
        super.createVerticesOfQuad(quad, pose, normal, buffer, light, overlay, red, green, blue, alpha);
    }
}
