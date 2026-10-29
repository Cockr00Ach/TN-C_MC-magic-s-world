package com.tnc.tnc.boss.fire;

import java.util.ArrayList;
import java.util.List;

/** Continuous annulus geometry. Ground is only a height query; no block model is involved. */
public final class TortoiseQuakeMesh {
    public static final int SEGMENTS = 160;
    public static final int RADIAL_STEPS = 12;
    public static final double WIDTH = 3.5D;
    public static final double HEIGHT = 0.6D;
    public static final double MAX_STEP = 1.05D;
    public static final double GROUND_OFFSET = 0.035D;
    private static final double[] COS = new double[SEGMENTS + 1];
    private static final double[] SIN = new double[SEGMENTS + 1];

    static {
        for (int i = 0; i < SEGMENTS; i++) {
            COS[i] = Math.cos(i * Math.PI * 2.0D / SEGMENTS);
            SIN[i] = Math.sin(i * Math.PI * 2.0D / SEGMENTS);
        }
        COS[SEGMENTS] = COS[0];
        SIN[SEGMENTS] = SIN[0];
    }

    private TortoiseQuakeMesh() {
    }

    @FunctionalInterface
    public interface Ground {
        /** NaN means unloaded, obstructed or outside the acceptable ground height. */
        double height(double x, double z);
    }

    public record Vertex(double x, double y, double z, float u, float v, double groundY) {
    }

    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {
    }

    public record Mesh(List<Quad> quads, float opacity) {
    }

    public static double profile(double across) {
        if (across <= 0.0D || across >= 1.0D) return 0.0D;
        return Math.sin(Math.PI * across) * HEIGHT;
    }

    public static Mesh build(double centerX, double centerZ, double age, Ground ground) {
        float opacity = TortoiseQuake.opacity(age);
        if (opacity <= 0.0F) return new Mesh(List.of(), 0.0F);
        double radius = TortoiseQuake.radius(age);
        double inner = Math.max(0.0D, radius - WIDTH * 0.5D);
        double outer = radius + WIDTH * 0.5D;
        Vertex[][] vertices = new Vertex[SEGMENTS + 1][RADIAL_STEPS + 1];
        for (int angle = 0; angle <= SEGMENTS; angle++) {
            for (int row = 0; row <= RADIAL_STEPS; row++) {
                double v = row / (double) RADIAL_STEPS;
                double r = v <= 0.5D ? inner + (radius - inner) * v * 2.0D
                        : radius + (outer - radius) * (v - 0.5D) * 2.0D;
                double x = centerX + COS[angle] * r, z = centerZ + SIN[angle] * r;
                double height = ground.height(x, z);
                if (!Double.isFinite(height)) continue;
                // U scrolls along the ring; V remains fixed so its soft edges cannot wrap into view.
                vertices[angle][row] = new Vertex(x, height + GROUND_OFFSET + profile(v), z,
                        (float) (angle * 12.0D / SEGMENTS - age * 0.0125D), (float) v, height);
            }
        }
        List<Quad> quads = new ArrayList<>(SEGMENTS * RADIAL_STEPS);
        for (int angle = 0; angle < SEGMENTS; angle++) {
            for (int row = 0; row < RADIAL_STEPS; row++) {
                Vertex a = vertices[angle][row], b = vertices[angle + 1][row];
                Vertex c = vertices[angle + 1][row + 1], d = vertices[angle][row + 1];
                if (a == null || b == null || c == null || d == null) continue;
                if (Math.hypot(d.x - a.x, d.z - a.z) < 1.0E-6D) continue;
                double middle = ground.height((a.x + b.x + c.x + d.x) * 0.25D,
                        (a.z + b.z + c.z + d.z) * 0.25D);
                if (!Double.isFinite(middle)) continue;
                double low = Math.min(middle, Math.min(Math.min(a.groundY, b.groundY), Math.min(c.groundY, d.groundY)));
                double high = Math.max(middle, Math.max(Math.max(a.groundY, b.groundY), Math.max(c.groundY, d.groundY)));
                if (high - low > MAX_STEP) continue;
                quads.add(new Quad(a, b, c, d));
            }
        }
        return new Mesh(quads, opacity);
    }
}
