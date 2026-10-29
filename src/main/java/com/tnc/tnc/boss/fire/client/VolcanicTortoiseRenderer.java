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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 模型的单位已经是 1/16 格（60×44×88 单位 ⇒ 3.75×2.75×5.5 格），所以这里不再放大。 */
public final class VolcanicTortoiseRenderer extends GeoEntityRenderer<VolcanicTortoiseEntity> {
    private float deathAge = -1;
    private VolcanicTortoiseEntity scatterEntity;
    private Matrix4f inverseRoot;
    private final List<Vec3> scatterSamples = new ArrayList<>();
    private int scatterSeen;
    private int scatterLimit;
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
        this.scatterEntity = entity.isDyingAnimation() && TortoiseDeathFx.beginSurfaceFrame(entity, this.deathAge) ? entity : null;
        this.inverseRoot = this.scatterEntity == null ? null : new Matrix4f(poses.last().pose()).invert();
        this.scatterSamples.clear();
        this.scatterSeen = 0;
        this.scatterLimit = TortoiseDeath.particleBudget(this.deathAge);
        try {
            // A zero-alpha corpse must not keep writing depth over the later rock pile.
            if (this.deathAge < TortoiseDeath.FADE_END) {
                super.render(entity, yaw, partialTick, poses, buffers, light);
            }
            if (this.scatterEntity != null) {
                for (Vec3 sample : this.scatterSamples) TortoiseDeathFx.scatter(entity, this.deathAge, sample);
            }
        } finally {
            this.deathAge = -1;
            this.scatterEntity = null;
            this.inverseRoot = null;
            this.scatterSamples.clear();
        }
    }

    @Override
    public RenderType getRenderType(VolcanicTortoiseEntity entity, ResourceLocation texture,
                                    MultiBufferSource buffers, float partialTick) {
        return entity.isDyingAnimation() ? RenderType.entityTranslucentCull(texture)
                : super.getRenderType(entity, texture, buffers, partialTick);
    }

    @Override
    protected float getDeathMaxRotation(VolcanicTortoiseEntity entity) {
        return 0; // The head bows through bones; no vanilla corpse roll.
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer,
                                     int light, int overlay, float red, float green, float blue, float alpha) {
        if (this.scatterEntity != null) {
            // Reservoir sampling distributes a bounded set across ALL bones, without privileging
            // whichever shell or leg happens to render first. No mesh is clipped or removed early.
            int seen = ++this.scatterSeen;
            int slot = seen <= this.scatterLimit ? seen - 1 : this.scatterEntity.level().random.nextInt(seen);
            if (slot < this.scatterLimit) {
                var vertices = quad.vertices();
                float u = this.scatterEntity.level().random.nextFloat();
                float v = this.scatterEntity.level().random.nextFloat();
                Vector3f point = new Vector3f(vertices[0].position()).lerp(vertices[1].position(), u);
                Vector3f opposite = new Vector3f(vertices[3].position()).lerp(vertices[2].position(), u);
                point.lerp(opposite, v);
                pose.transformPosition(point);
                this.inverseRoot.transformPosition(point);
                Vec3 at = this.scatterEntity.position().add(point.x, point.y, point.z);
                if (slot == this.scatterSamples.size()) this.scatterSamples.add(at);
                else this.scatterSamples.set(slot, at);
            }
        }
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
