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
