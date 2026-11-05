package com.tnc.tnc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * Guards the earth windup shape: the cracks must fan out over the whole circle at every tier,
 * and their tips must crawl inwards towards the caster as the cast progresses. Pure math, so it
 * runs in plain JUnit without a render context.
 */
class EarthCastFxTest {

    @Test void crackCountGrowsWithTierAndCoversTheWholeCircle() {
        int previous = 0;
        for (int tier = 1; tier <= 5; tier++) {
            int count = EarthCastFx.crackCountFor(tier);
            assertTrue(count > previous, "tier " + tier + " should add cracks");
            previous = count;

            double[] angles = EarthCastFx.crackAnglesFor(tier);
            assertEquals(count, angles.length);
            double[] sorted = angles.clone();
            Arrays.sort(sorted);
            for (int i = 0; i < sorted.length; i++) {
                assertTrue(sorted[i] >= 0 && sorted[i] < Math.PI * 2, "angle outside circle: " + sorted[i]);
                double next = i + 1 < sorted.length ? sorted[i + 1] : sorted[0] + Math.PI * 2;
                double gap = next - sorted[i];
                assertTrue(gap > .12, "tier " + tier + " cracks " + i + " and " + (i + 1) + " overlap");
                assertTrue(gap < Math.PI * 2 * .6, "tier " + tier + " leaves a " + gap + " rad hole");
            }
        }
    }

    @Test void cracksCrawlFromTheOuterRingToTheCaster() {
        double radius = 8.0;
        int count = EarthCastFx.crackCountFor(4);
        float[] steps = {0F, .2F, .4F, .6F, .8F, 1F};
        for (int i = 0; i < count; i++) {
            double previous = Double.MAX_VALUE;
            for (float p : steps) {
                double tip = EarthCastFx.crackTipFor(radius, p, i);
                assertTrue(tip < previous, "tip should shrink as the cast progresses (crack " + i + ")");
                previous = tip;
            }
            double end = EarthCastFx.crackTipFor(radius, 1F, i);
            assertTrue(end <= 1.2, "crack " + i + " should reach the caster's feet, got " + end);
            assertTrue(EarthCastFx.crackStartFor(radius, i) > end,
                    "crack " + i + " must start outside its final tip");
        }
    }

    @Test void reachEasesFromZeroToOne() {
        assertEquals(0F, EarthCastFx.reachFor(0F), 1e-6);
        assertEquals(1F, EarthCastFx.reachFor(.85F), 1e-6);
        float previous = -1;
        for (float p = 0; p <= 1F; p += .05F) {
            float reach = EarthCastFx.reachFor(p);
            assertTrue(reach >= previous, "reach must be monotonic");
            assertTrue(reach >= 0 && reach <= 1, "reach must stay in 0..1");
            previous = reach;
        }
    }

    /**
     * The crack must read as torn ground, not a smooth arc: radii shrink monotonically, the path
     * bends (direction changes between segments), and it narrows towards the tip.
     */
    @Test void cracksAreJaggedPolylinesThatTaper() {
        double radius = 8.0;
        int tier = 4;
        int count = EarthCastFx.crackCountFor(tier);
        for (int i = 0; i < count; i++) {
            double start = EarthCastFx.crackStartFor(radius, i);
            double tip = EarthCastFx.crackTipFor(radius, 1F, i);
            Vec3[] path = EarthCastFx.crackPath(i, count, start, tip, tier);
            assertEquals(EarthCastFx.CRACK_POINTS, path.length);

            double previousRadius = Double.MAX_VALUE;
            for (Vec3 point : path) {
                double r = Math.hypot(point.x, point.z);
                assertTrue(r <= previousRadius + 1e-6, "crack " + i + " should only crawl inward");
                previousRadius = r;
            }

            double[] deltas = new double[path.length - 2];
            for (int s = 0; s + 2 < path.length; s++) {
                Vec3 d0 = path[s + 1].subtract(path[s]);
                Vec3 d1 = path[s + 2].subtract(path[s + 1]);
                deltas[s] = Math.abs(Math.atan2(d0.z, d0.x) - Math.atan2(d1.z, d1.x));
            }
            double mean = Arrays.stream(deltas).average().orElse(0);
            assertTrue(mean > .01, "crack " + i + " bends like a smooth arc, mean turn " + mean);

            double firstWidth = EarthCastFx.crackWidth(i, 0, tier);
            assertTrue(firstWidth > 0, "crack must have width");
            assertTrue(EarthCastFx.crackWidth(i, EarthCastFx.CRACK_POINTS - 1, tier) < firstWidth,
                    "crack " + i + " must taper towards its tip");
        }
    }

    /**
     * The god-spear silhouette has to keep the original weapon's proportions: every size is
     * derived from one length, so growing it only scales it up (the old version stretched the
     * shaft faster than the head).
     */
    @Test void spearSilhouetteKeepsItsProportionsWhenScaledUp() {
        double previousLength = 0;
        for (float form : new float[]{0F, .25F, .5F, .75F, 1F}) {
            double[] dims = EarthCastFx.spearDims(form);
            double length = dims[0];
            assertTrue(length > previousLength, "spear should grow with the cast, form " + form);
            previousLength = length;
            assertEquals(EarthCastFx.SPEAR_HALF_WIDTH_RATIO, dims[1] / length, 1e-9);
            assertEquals(EarthCastFx.SPEAR_HEAD_LENGTH_RATIO, dims[2] / length, 1e-9);
            assertEquals(EarthCastFx.SPEAR_SHAFT_RADIUS_RATIO, dims[3] / length, 1e-9);
        }
    }

    /**
     * Regression for the 2026-11-06 03:41 client crash: the dark pass used the water helpers,
     * which only write position+color, but {@code LightningRenderTypes.DARK} is NEW_ENTITY
     * (position/color/uv/overlay/light/normal) and throws "Not filled all elements of the vertex".
     * Every vertex emitted by the dark helpers must therefore carry all six elements.
     */
    @Test void darkGeometryFillsEveryVertexElement() {
        List<String> calls = new ArrayList<>();
        VertexConsumer recorder = (VertexConsumer) Proxy.newProxyInstance(
                EarthCastFxTest.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        Matrix4f pose = new Matrix4f();
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(1, 0, 0), c = new Vec3(1, 0, 1), d = new Vec3(0, 0, 1);
        EarthCastFx.darkQuad(recorder, pose, a, b, c, d, 1, 1, 1, 1);
        EarthCastFx.darkStroke(recorder, pose, new Vec3(0, 1, 0), a, b, .1, 1, 1, 1, 1);
        EarthCastFx.darkDisk(recorder, pose, a, new Vec3(0, 1, 0), 1, 1, 1, 1, 1, 8);
        EarthCastFx.darkRing(recorder, pose, a, new Vec3(0, 1, 0), 1, .1, 1, 1, 1, 1, 8);
        EarthCastFx.darkCube(recorder, pose, a, .1, 1, 1, 1, 1);
        assertTrue(calls.size() > 0, "dark helpers should emit vertices");

        List<String> pending = new ArrayList<>();
        for (String call : calls) {
            if (call.equals("endVertex")) {
                assertTrue(pending.containsAll(List.of("vertex", "color", "uv", "overlayCoords", "uv2", "normal")),
                        "dark vertex missing elements, got only " + pending);
                pending.clear();
            } else {
                pending.add(call);
            }
        }
        assertTrue(pending.isEmpty(), "last dark vertex was never ended: " + pending);
    }
}
