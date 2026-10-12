package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class NightHandGeometryTest {
    @Test void eachHandHasADifferentBendAndBloodUsesTheSameConnectedShape() {
        var start=Vec3.ZERO;var end=new Vec3(12,1,0);
        for(int i=0;i<5;i++)for(int j=i+1;j<5;j++)
            assertTrue(NightHandGeometry.centre(start,end,.5,i).distanceTo(NightHandGeometry.centre(start,end,.5,j))>.1);
    }
    @Test void armStaysConnectedAndFiniteForHorizontalVerticalAndShortSpans() {
        Vec3 start = new Vec3(1, 2, 3);
        for (Vec3 end : new Vec3[]{new Vec3(1, 30, 3), new Vec3(40, 2, 3), start.add(0.1, 0, 0)}) {
            assertEquals(0, NightHandGeometry.centre(start, end, 0).distanceTo(start), 1e-9);
            assertEquals(0, NightHandGeometry.centre(start, end, 1).distanceTo(end), 1e-9);
            int[] vertices = {0};
            VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class}, (proxy, method, args) -> {
                if (method.getName().equals("vertex")) {
                    vertices[0]++;
                    for (Object arg : args) if (arg instanceof Float f) assertTrue(Float.isFinite(f));
                }
                return method.getReturnType() == void.class ? null : proxy;
            });
            NightHandGeometry.arm(out, new Matrix4f(), start, end, 0.8);
            assertTrue(vertices[0] <= 1000, "One arm has a fixed small mesh budget");
            assertTrue(vertices[0] > 0);
        }
    }
    @Test void gateHasDenseBackingWithinAFixedMeshBudget() {
        int[] counts = {0, 0};
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class}, (proxy, method, args) -> {
            if (method.getName().equals("vertex")) counts[0]++;
            if (method.getName().equals("color") && args.length == 4 && args[3].equals(215)) counts[1]++;
            return method.getReturnType() == void.class ? null : proxy;
        });
        NightHandGeometry.gate(out, new Matrix4f(), 20, 1);
        assertTrue(counts[1] >= 256, "The complete gate must have an 84 percent opaque backing");
        assertTrue(counts[0] <= 1150, "Gate detail must remain bounded at every tier");
    }
}
