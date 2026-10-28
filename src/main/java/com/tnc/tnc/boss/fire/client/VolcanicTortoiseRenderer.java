package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import com.tnc.tnc.boss.fire.TortoiseDeath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 模型的单位已经是 1/16 格（60×44×88 单位 ⇒ 3.75×2.75×5.5 格），所以这里不再放大。 */
public final class VolcanicTortoiseRenderer extends GeoEntityRenderer<VolcanicTortoiseEntity> {
    private VolcanicTortoiseEntity deathEntity;
    private Matrix4f inverseRoot;
    private float deathAge;
    private boolean emitFragments;
    public VolcanicTortoiseRenderer(EntityRendererProvider.Context context) {
        super(context, new VolcanicTortoiseGeoModel());
        this.shadowRadius = 3.4F;
        this.addRenderLayer(new VolcanicTortoiseEmissiveLayer(this));
    }

    @Override
    public void render(VolcanicTortoiseEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        this.deathEntity = entity.isDyingAnimation() ? entity : null;
        this.deathAge = entity.deathAge(partialTick);
        this.inverseRoot = this.deathEntity == null ? null : new Matrix4f(poses.last().pose()).invert();
        this.emitFragments = this.deathEntity != null && this.deathAge >= TortoiseDeath.DISSOLVE_START
                && TortoiseDeathFx.beginFragmentFrame(entity);
        this.shadowRadius = this.deathEntity == null ? 3.4F : 3.4F * (1 - TortoiseDeath.dissolved(this.deathAge));
        try {
            super.render(entity, yaw, partialTick, poses, buffers, light);
        } finally {
            this.deathEntity = null;
            this.inverseRoot = null;
            this.emitFragments = false;
        }
    }

    @Override
    protected float getDeathMaxRotation(VolcanicTortoiseEntity entity) {
        return 0; // Kneel and collapse through bones; never use the vanilla 90-degree corpse roll.
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer,
                                     int light, int overlay, float red, float green, float blue, float alpha) {
        if (this.deathEntity == null) {
            super.createVerticesOfQuad(quad, pose, normal, buffer, light, overlay, red, green, blue, alpha);
            return;
        }
        float cool = TortoiseDeath.cooling(this.deathAge);
        red *= 1 - cool * 0.58F;
        green *= 1 - cool * 0.52F;
        blue *= 1 - cool * 0.46F;
        if (this.deathAge < TortoiseDeath.DISSOLVE_START) {
            super.createVerticesOfQuad(quad, pose, normal, buffer, light, OverlayTexture.NO_OVERLAY, red, green, blue, alpha);
            return;
        }
        float cutoff = TortoiseDeath.cutoff(this.deathAge, this.deathEntity.usesPhaseTwoModel());
        List<DeathMeshClipper.Vertex> polygon = new ArrayList<>(4);
        for (var vertex : quad.vertices()) {
            Vector3f point = pose.transformPosition(new Vector3f(vertex.position()));
            Vector3f local = this.inverseRoot.transformPosition(new Vector3f(point));
            polygon.add(new DeathMeshClipper.Vertex(point.x, point.y, point.z, vertex.texU(), vertex.texV(), local.y));
        }
        List<DeathMeshClipper.Vertex> clipped = DeathMeshClipper.below(polygon, cutoff);
        if (this.emitFragments && clipped.size() >= 3 && polygon.stream().anyMatch(v -> v.height() > cutoff)) {
            List<DeathMeshClipper.Vertex> edge = clipped.stream().filter(v -> Math.abs(v.height() - cutoff) < 0.0001F).toList();
            if (edge.size() >= 2) {
                var a = edge.get(0);
                var b = edge.get(edge.size() - 1);
                float t = this.deathEntity.level().random.nextFloat();
                Vector3f point = this.inverseRoot.transformPosition(new Vector3f(a.x() + (b.x() - a.x()) * t,
                        a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t));
                TortoiseDeathFx.fragment(this.deathEntity, this.deathAge,
                        this.deathEntity.position().add(point.x, point.y, point.z));
            }
        }
        // The backing render type consumes quads; repeat each triangle's last vertex for its fourth corner.
        for (int i = 1; i + 1 < clipped.size(); i++) {
            for (var v : List.of(clipped.get(0), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1))) {
                buffer.vertex(v.x(), v.y(), v.z(), red, green, blue, alpha, v.u(), v.v(), OverlayTexture.NO_OVERLAY,
                        light, normal.x, normal.y, normal.z);
            }
        }
    }
}
