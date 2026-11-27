package com.tnc.tnc.sanctum.client;

import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import static com.tnc.tnc.sanctum.SanctumTimings.*;
import static com.tnc.tnc.sanctum.client.SanctumPose.ease;

/** Both sides of the chest opening use the same aperture in sculpture coordinates. */
final class SanctumRupture {
    static final float CENTER_Y = 30.6F, HALF_HEIGHT = 7.8F;
    private final Matrix4f inverse;
    private final float halfWidth;

    private SanctumRupture(Matrix4f base, float halfWidth) {
        this.inverse = base.invert(new Matrix4f());
        this.halfWidth = halfWidth;
    }

    SanctumMeshCut cut(String bone, boolean limb, Matrix4f transform) {
        if (!limb && !bone.equals("torso") && !bone.equals("girdle")
                && !bone.equals("skirts") && !bone.equals("pectorale")) return null;
        return SanctumMeshCut.aperture(inverse.mul(transform, new Matrix4f()), halfWidth, limb);
    }

    static void draw(SanctumMesh shell, SanctumMesh demon, MultiBufferSource buffers,
                     Matrix4f base, float t, float age, int light) {
        float frame = t * RUPTURE;
        float strain = ease(frame, 0, FINGERS);
        float split = ease(frame, ARM_OUT, SHELL_SPLIT);
        float opening = .045F + .65F * strain + 5.95F * ease(frame, FINGERS, ARM_OUT) + 1.5F * split;
        var aperture = new SanctumRupture(base, opening);
        if (shell != null) shell.draw(base, buffers, new SanctumPose("bleeding", "rupture", t, age, 0, false),
                null, light, null, null, aperture);
        SanctumRelics.rupture(buffers, base, strain, split, opening);
        if (demon == null || frame < FINGERS) return;

        // The fingertips start inside the chest. Every triangle behind the lip or outside the opening stays inside.
        float emergence = ease(frame, FINGERS, ARM_OUT + 4);
        var arm = new Matrix4f(base).translate(.55F + 1.1F * split, 30.4F + 1.25F * split, .65F + 19.8F * emergence)
                .rotateY(.09F * (1 - emergence) - .24F * split).rotateZ(-.18F * split)
                .scale(.85F + .08F * emergence).translate(13.9F, -33.1F, -18.2F);
        demon.draw(arm, buffers, new SanctumPose("demon", "", t, age, 0, true),
                "forearm_-1", light, null, null, aperture);
    }
}
