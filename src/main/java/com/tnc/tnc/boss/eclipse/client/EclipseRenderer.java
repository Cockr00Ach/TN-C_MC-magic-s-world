package com.tnc.tnc.boss.eclipse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.eclipse.EclipseLeviathanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class EclipseRenderer extends EntityRenderer<EclipseLeviathanEntity> {
    private static final ResourceLocation WHITE = texture("white");
    private static final ResourceLocation MOTE = texture("mote");
    private static final RenderType SOLID = RenderType.entityCutoutNoCull(WHITE);
    private static final RenderType FADE = RenderType.entityTranslucent(WHITE);
    private static final RenderType SPARK = RenderType.entityTranslucentEmissive(MOTE);
    private static EclipseMesh.Rig cachedRig;

    EclipseRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    static void clearMesh() {
        cachedRig = null;
    }

    private static EclipseMesh.Rig mesh() {
        if (cachedRig == null) {
            try {
                cachedRig = EclipseMesh.load(Minecraft.getInstance().getResourceManager());
            } catch (IOException error) {
                throw new UncheckedIOException("Unable to load Eclipse model", error);
            }
        }
        return cachedRig;
    }

    @Override
    public boolean shouldRender(EclipseLeviathanEntity entity, Frustum frustum, double x, double y, double z) {
        // Include the swept tail and fins even when the small server hit box is outside view.
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBoxForCulling().inflate(1.5));
    }

    @Override
    public void render(EclipseLeviathanEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        float elapsed = Math.max(0, entity.phaseAge(partial));
        float progress = Mth.clamp(elapsed / Math.max(1, entity.phaseDuration()), 0, 1);
        var phase = entity.phase();
        float charge = phase == EclipseLeviathanEntity.Phase.WINDUP ? smooth(progress) : 0;
        float fold = 0;
        float alpha = 1;
        float sink = 0;
        switch (phase) {
            case EMERGE -> {
                alpha = smooth(progress);
                sink = (1 - alpha) * 1.75f;
            }
            case LUNGE -> {
                fold = smooth(elapsed / 4);
                charge = 1 - smooth((elapsed - 10) / 4);
            }
            case RECOVER -> fold = 1 - smooth(progress);
            case RETREAT -> {
                float fade = smooth((progress - .25f) / .75f);
                alpha = 1 - fade;
                fold = smooth(progress * 2);
                sink = fade * 2.25f;
            }
            default -> {}
        }
        if (alpha < .015f) return;
        double distance = entityRenderDispatcher.distanceToSqr(entity);
        int lod = distance < 64 * 64 ? 0 : distance < 128 * 128 ? 1 : 2;
        var motion = new Motion(age, elapsed, charge, fold, alpha, lod);
        pose.pushPose();
        pose.translate(0, -sink, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(EclipseLeviathanEntity.MODEL_SCALE,EclipseLeviathanEntity.MODEL_SCALE,EclipseLeviathanEntity.MODEL_SCALE);
        var out = buffers.getBuffer(alpha >= .999f ? SOLID : FADE);
        var rig = mesh();
        draw(rig.root(), pose, out, light, motion);
        if (charge > .04f && lod == 0 && phase == EclipseLeviathanEntity.Phase.WINDUP) {
            var spark = buffers.getBuffer(SPARK);
            pose.pushPose();
            apply(rig.head(), pose, motion);
            drawIntake(pose, spark, motion);
            pose.popPose();
        }
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    private static void draw(EclipseMesh.Node node, PoseStack pose, VertexConsumer out, int light, Motion motion) {
        pose.pushPose();
        apply(node, pose, motion);
        if (node.body() != null) {
            node.body()[motion.lod].draw(pose.last(), out, light, motion.alpha, 1);
        }
        if (node.radiant() != null) {
            float glow = .91f + .09f * Mth.sin(motion.age * .17f) + motion.charge * .46f;
            node.radiant()[motion.lod].draw(pose.last(), out, LightTexture.FULL_BRIGHT, motion.alpha, glow);
        }
        for (var child : node.children()) draw(child, pose, out, light, motion);
        pose.popPose();
    }

    private static void apply(EclipseMesh.Node node, PoseStack pose, Motion motion) {
        float x = 0, y = 0, z = 0, drift = 0;
        switch (node.name()) {
            case "head" -> x = 12 * motion.charge;
            case "jaw_upper" -> x = 15 * motion.charge;
            case "jaw_lower" -> x = -22 * motion.charge;
            case "ring_fin_l", "ring_fin_r" -> {
                int sign = node.name().endsWith("_l") ? -1 : 1;
                y = -sign * (42 * motion.fold + 4 * Mth.sin(motion.age * .13f));
                z = sign * 3 * (1 - motion.fold) * Mth.cos(motion.age * .13f);
            }
            case "crown_arc" -> z = 4 * Mth.sin(motion.age * .11f);
            case "tail_fan" -> x = 7 * (1 - .65f * motion.fold) * Mth.sin(motion.age * .14f - 3.8f);
            default -> {
                if (node.name().startsWith("tail_")) {
                    int segment = node.name().charAt(6) - '0';
                    float wave = motion.age * .13f - segment * .58f;
                    y = (1.2f + .33f * segment) * (1 - .78f * motion.fold) * Mth.sin(wave);
                    x = .65f * Mth.cos(wave);
                } else if (node.name().startsWith("brow_")) {
                    drift = .09f * motion.charge + .035f * Mth.sin(motion.age * .10f + node.x());
                }
            }
        }
        if (x == 0 && y == 0 && z == 0 && drift == 0) return;
        pose.translate(node.x() + Math.signum(node.x()) * drift, node.y() + drift * .6f, node.z());
        if (y != 0) pose.mulPose(Axis.YP.rotationDegrees(y));
        if (x != 0) pose.mulPose(Axis.XP.rotationDegrees(x));
        if (z != 0) pose.mulPose(Axis.ZP.rotationDegrees(z));
        pose.translate(-node.x(), -node.y(), -node.z());
    }

    private static void drawIntake(PoseStack pose, VertexConsumer out, Motion motion) {
        for (int i = 0; i < 24; i++) {
            float t = (motion.elapsed * .056f + i / 24f) % 1;
            float angle = i * 2.39996f + t * 4.2f + motion.age * .04f;
            float radius = (1 - t) * (2.55f + .22f * Mth.sin(i * 1.72f)) + .21f;
            float x = Mth.cos(angle) * radius;
            float y = Mth.sin(angle) * radius * .86f;
            float z = Mth.lerp(t, -3.15f, .54f);
            float alpha = motion.charge * Mth.sin(t * Mth.PI) * (.55f + .35f * Mth.sin(i + motion.age * .49f));
            float size = .045f + .037f * (1 - t);
            mote(pose.last(), out, x, y, z, size, alpha, i % 4 == 0);
        }
    }

    private static void mote(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
                             float size, float alpha, boolean cyan) {
        for (int i = 0; i < 4; i++) {
            float u = i == 0 || i == 3 ? 0 : 1, v = i < 2 ? 0 : 1;
            out.vertex(pose.pose(), x + (u * 2 - 1) * size, y + (1 - v * 2) * size, z)
                    .color(cyan ? .46f : .75f, cyan ? .97f : .56f, 1, alpha).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.normal(), 0, 0, -1).endVertex();
        }
    }

    private static float smooth(float amount) {
        float t = Mth.clamp(amount, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/eclipse/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(EclipseLeviathanEntity entity) {
        return WHITE;
    }

    private record Motion(float age, float elapsed, float charge, float fold, float alpha, int lod) {}
}
