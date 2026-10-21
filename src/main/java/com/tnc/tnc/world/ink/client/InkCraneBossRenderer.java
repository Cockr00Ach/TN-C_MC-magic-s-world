package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.world.ink.InkCraneBossEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class InkCraneBossRenderer extends GeoEntityRenderer<InkCraneBossEntity> {
    private static final DustParticleOptions INK = new DustParticleOptions(new Vector3f(0.015F), 0.65F);
    private static final DustParticleOptions PAPER = new DustParticleOptions(new Vector3f(0.94F), 0.40F);
    private final Map<InkCraneBossEntity, Map<String, TrailSample>> trails = new WeakHashMap<>();
    private final Map<InkCraneBossEntity, Map<String, Deque<Vec3>>> brushTrails = new WeakHashMap<>();

    private record TrailSample(int tick, Vec3 point) {}
    public InkCraneBossRenderer(EntityRendererProvider.Context context) {
        super(context, new InkCraneBossModel());
        withScale(com.tnc.tnc.world.ink.InkCraneAttackRules.BODY_SCALE);
        shadowRadius = 1.7F;
        addRenderLayer(new InkCraneSkyShroudLayer(this));
    }

    @Override public void render(InkCraneBossEntity entity, float yaw, float partial, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partial, pose, buffers, light);
        pose.pushPose();
        pose.scale(2, 2, 2);
        InkCraneRitualVisuals.caster(entity.inkMove(), entity.tickCount + partial, pose, buffers);
        pose.popPose();
        Map<String, Deque<Vec3>> paths = brushTrails.get(entity);
        if (paths != null) for (var entry : paths.entrySet()) {
            boolean wing = entry.getKey().startsWith("emitter_wing_");
            if (wing || entity.inkMove() != 0) InkCraneRitualVisuals.brushTrail(entry.getValue(),
                    entity.position(), entity.tickCount + partial, wing ? entity.inkMove() == 0 ? .25 : .65 : .18,
                    pose, buffers);
        }
    }

    @Override
    public void renderRecursively(PoseStack pose, InkCraneBossEntity entity, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer out,
                                  boolean rerender, float partial, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        boolean emitter = name.startsWith("emitter_");
        if (emitter) bone.setTrackingMatrices(true);
        if (name.startsWith("outline_") || name.startsWith("glow_") || name.equals("eyes")) {
            light = LightTexture.FULL_BRIGHT;
        }
        super.renderRecursively(pose, entity, bone, type, buffers, out, rerender,
                partial, light, overlay, red, green, blue, alpha);
        if (emitter && !rerender) emitBoneTrail(entity, bone);
    }

    private void emitBoneTrail(InkCraneBossEntity entity, GeoBone bone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.player == null || !entity.isAlive()
                || minecraft.player.distanceToSqr(entity) > 48.0 * 48.0) return;
        String name = bone.getName();
        var world = bone.getWorldPosition();
        Vec3 point = new Vec3(world.x, world.y, world.z);
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || point.distanceToSqr(entity.position()) > 576.0) return;
        Map<String, TrailSample> samples = trails.computeIfAbsent(entity, ignored -> new HashMap<>());
        TrailSample last = samples.get(name);
        // Rendering can run many times each game tick; emission stays tick-budgeted.
        if (last != null && entity.tickCount - last.tick() < 2) return;
        samples.put(name, new TrailSample(entity.tickCount, point));
        Deque<Vec3> path = brushTrails.computeIfAbsent(entity, ignored -> new HashMap<>())
                .computeIfAbsent(name, ignored -> new ArrayDeque<>());
        path.addFirst(point); while (path.size() > 24) path.removeLast();
        double distance = last == null ? 0.0 : point.distanceTo(last.point());
        boolean moved = last != null && distance > 0.015 && distance < 3.0
                && entity.tickCount - last.tick() <= 6;
        boolean wing = name.startsWith("emitter_wing_");
        boolean tail = name.equals("emitter_tail");
        int steps = moved ? Math.min(4, Math.max(1, (int) Math.ceil(distance / 0.12)))
                : (wing || tail ? 1 : 0);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = moved ? last.point().lerp(point, i / (double) steps) : point;
            entity.level().addParticle((i + entity.tickCount) % 3 == 0 ? PAPER : INK,
                    p.x, p.y, p.z, 0.0, 0.008, 0.0);
        }
        if (tail && entity.tickCount % 6 == 0) {
            entity.level().addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z,
                    0.0, 0.004, 0.0);
        }
    }

    @Override protected float getDeathMaxRotation(InkCraneBossEntity entity) { return 0.0F; }
}
