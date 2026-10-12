package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A continuous tapered shadow limb, with no particle network traffic. */
final class NightHandGeometry {
    static final int SEGMENTS = 28;
    static final int SIDES = 8;
    private NightHandGeometry() {}

    static Vec3 centre(Vec3 start, Vec3 end, double t) {
        return centre(start,end,t,0);
    }
    static Vec3 centre(Vec3 start, Vec3 end, double t,int slot) {
        Vec3 delta = end.subtract(start);
        Vec3 side = delta.cross(new Vec3(0, 1, 0)).normalize();
        double bow = Math.min(2.6D, delta.length() * 0.12D);
        // A fixed curved silhouette avoids noisy tick-based waves and flickering bends.
        double sign=slot%2==0?1:-1;
        return start.lerp(end,t).add(side.scale(Math.sin(Math.PI*t)*bow*sign*(.55+(slot%3)*.45)))
                .add(0,Math.sin(Math.PI*t)*bow*((slot%3)-1)*.65,0);
    }

    static void arm(VertexConsumer out, Matrix4f pose, Vec3 start, Vec3 end, double wristRadius) {
        tube(out,pose,start,end,wristRadius,0,false,0);
    }
    static void tube(VertexConsumer out,Matrix4f pose,Vec3 start,Vec3 end,double wristRadius,int slot,boolean blood,double age) {
        if (start.distanceToSqr(end) < 0.0025D) return;
        Vec3 axis = end.subtract(start).normalize();
        Vec3 u = axis.cross(Math.abs(axis.y) > 0.95D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = axis.cross(u).normalize();
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double t0 = segment / (double) SEGMENTS, t1 = (segment + 1.0D) / SEGMENTS;
            Vec3 a = centre(start, end, t0,slot), b = centre(start, end, t1,slot);
            double r0 = wristRadius * (0.48D + 0.52D * t0);
            double r1 = wristRadius * (0.48D + 0.52D * t1);
            for (int side = 0; side < SIDES; side++) {
                double angle0 = side * Math.PI * 2 / SIDES, angle1 = (side + 1) * Math.PI * 2 / SIDES;
                Vec3 n0 = u.scale(Math.cos(angle0)).add(v.scale(Math.sin(angle0)));
                Vec3 n1 = u.scale(Math.cos(angle1)).add(v.scale(Math.sin(angle1)));
                // Restrained edge shading gives the black limb volume, rather than a flat ribbon.
                int shade = side == 1 || side == 2 ? 30 : 8;
                int red=blood?80+(int)(40*(.5+.5*Math.cos(t0*32-age*.45))):shade;
                int green=blood?7:shade-3,blue=blood?13:shade+8;
                vertex(out,pose,a.add(n0.scale(r0)),red,green,blue,250);
                vertex(out,pose,b.add(n0.scale(r1)),red,green,blue,250);
                vertex(out,pose,b.add(n1.scale(r1)),red,green,blue,250);
                vertex(out,pose,a.add(n1.scale(r0)),red,green,blue,250);
            }
        }
    }

    static void gate(VertexConsumer out, Matrix4f pose, double radius, float alpha) {
        ring(out, pose, 0, radius * 0.91D, 9, 6, 14, Math.round(alpha * 215), 0.004D);
        ring(out, pose, radius * 0.88D, radius * 0.94D, 74, 49, 86, Math.round(alpha * 250), 0.006D);
        ring(out, pose, radius * 0.67D, radius * 0.69D, 58, 32, 67, Math.round(alpha * 240), 0.008D);
        ring(out, pose, radius * 0.43D, radius * 0.445D, 58, 32, 67, Math.round(alpha * 220), 0.008D);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            Vec3 a = new Vec3(Math.cos(angle) * radius * 0.73D, Math.sin(angle) * radius * 0.73D, 0.009D);
            Vec3 b = new Vec3(Math.cos(angle + 0.04D) * radius * 0.85D, Math.sin(angle + 0.04D) * radius * 0.85D, 0.009D);
            Vec3 width = new Vec3(-Math.sin(angle), Math.cos(angle), 0).scale(radius * 0.012D);
            vertex(out, pose, a.subtract(width), 83, 52, 87, Math.round(alpha * 245));
            vertex(out, pose, b.subtract(width), 83, 52, 87, Math.round(alpha * 245));
            vertex(out, pose, b.add(width), 83, 52, 87, Math.round(alpha * 245));
            vertex(out, pose, a.add(width), 83, 52, 87, Math.round(alpha * 245));
        }
    }

    private static void ring(VertexConsumer out, Matrix4f pose, double inner, double outer,
                             int r, int g, int b, int alpha, double z) {
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, next = (i + 1) * Math.PI / 32;
            vertex(out, pose, new Vec3(Math.cos(a) * inner, Math.sin(a) * inner, z), r,g,b,alpha);
            vertex(out, pose, new Vec3(Math.cos(a) * outer, Math.sin(a) * outer, z), r,g,b,alpha);
            vertex(out, pose, new Vec3(Math.cos(next) * outer, Math.sin(next) * outer, z), r,g,b,alpha);
            vertex(out, pose, new Vec3(Math.cos(next) * inner, Math.sin(next) * inner, z), r,g,b,alpha);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vec3 point, int r, int g, int b, int alpha) {
        out.vertex(pose, (float) point.x, (float) point.y, (float) point.z).color(r,g,b,alpha).endVertex();
    }
}
