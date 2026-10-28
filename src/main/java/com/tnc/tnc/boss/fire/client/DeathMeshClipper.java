package com.tnc.tnc.boss.fire.client;

import java.util.ArrayList;
import java.util.List;

/** A single horizontal clipping plane. Interpolates position and UV, never fades a whole model. */
public final class DeathMeshClipper {
    public record Vertex(float x, float y, float z, float u, float v, float height) {
        Vertex between(Vertex other, float t) {
            return new Vertex(x + (other.x - x) * t, y + (other.y - y) * t,
                    z + (other.z - z) * t, u + (other.u - u) * t, v + (other.v - v) * t,
                    height + (other.height - height) * t);
        }
    }

    private DeathMeshClipper() {}

    public static List<Vertex> below(List<Vertex> polygon, float cutoff) {
        List<Vertex> out = new ArrayList<>(6);
        if (polygon.isEmpty()) return out;
        Vertex previous = polygon.get(polygon.size() - 1);
        for (Vertex current : polygon) {
            boolean prevInside = previous.height <= cutoff, inside = current.height <= cutoff;
            if (inside != prevInside) add(out, previous.between(current,
                    (cutoff - previous.height) / (current.height - previous.height)));
            if (inside) add(out, current);
            previous = current;
        }
        if (out.size() > 1 && out.get(0).equals(out.get(out.size() - 1))) out.remove(out.size() - 1);
        return out;
    }

    private static void add(List<Vertex> out, Vertex vertex) {
        if (out.isEmpty() || !out.get(out.size() - 1).equals(vertex)) out.add(vertex);
    }
}
