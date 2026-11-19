package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import org.joml.Vector3f;

/** Textured contours in head-joint coordinates. Rings run from crown to chin. */
final class WeaponChampionHead {
    record Surface(float[] origin, float roll, int sides, float[][] rings, float[] uv, float expressionStride) {}
    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
    private record Patch(Vertex[] vertices, int sides, float expressionStride) {}
    private final List<Patch> patches;

    WeaponChampionHead(List<Surface> surfaces, int atlasHeight) {
        patches = surfaces.stream().map(s -> bake(s, atlasHeight)).toList();
    }

    private static Patch bake(Surface s, int atlasHeight) {
        float[][] rings = s.rings();
        int sides = s.sides();
        var vertices = new Vertex[rings.length * (sides + 1)];
        double roll = Math.toRadians(s.roll());
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        for (int row = 0; row < rings.length; row++) {
            float[] ring = rings[row];
            float[] above = rings[Math.max(0, row - 1)], below = rings[Math.min(rings.length - 1, row + 1)];
            for (int column = 0; column <= sides; column++) {
                double theta = (2.0 * column / sides - 1) * Math.PI;
                float sin = (float) Math.sin(theta), cos = (float) Math.cos(theta);
                float x = ring[1] * sin, y = ring[0], z = ring[3] - ring[2] * cos;
                var normal = new Vector3f((below[1] - above[1]) * sin, below[0] - above[0],
                    below[3] - above[3] - (below[2] - above[2]) * cos);
                if (ring[1] == 0 && ring[2] == 0) normal.set(0, row == 0 ? -1 : 1, 0);
                else normal.cross(ring[1] * cos, 0, ring[2] * sin).normalize();
                vertices[row * (sides + 1) + column] = new Vertex(
                    (s.origin()[0] + cr * x - sr * y) / 16,
                    (s.origin()[1] + sr * x + cr * y) / 16, (s.origin()[2] + z) / 16,
                    (s.uv()[0] + s.uv()[2] * column / sides) / 128,
                    (s.uv()[1] + s.uv()[3] * row / (rings.length - 1)) / atlasHeight,
                    cr * normal.x - sr * normal.y, sr * normal.x + cr * normal.y, normal.z);
            }
        }
        return new Patch(vertices, sides, s.expressionStride() / atlasHeight);
    }

    void render(PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                float red, float green, float blue, float alpha, int expression) {
        for (var patch : patches) {
            int width = patch.sides() + 1;
            var points = patch.vertices();
            float shift = expression * patch.expressionStride();
            for (int row = 0; row < points.length - width; row += width) {
                for (int j = row; j < row + width - 1; j++) {
                    vertex(points[j], pose, out, light, overlay, red, green, blue, alpha, shift);
                    vertex(points[j + width], pose, out, light, overlay, red, green, blue, alpha, shift);
                    vertex(points[j + width + 1], pose, out, light, overlay, red, green, blue, alpha, shift);
                    vertex(points[j + 1], pose, out, light, overlay, red, green, blue, alpha, shift);
                }
            }
        }
    }

    private static void vertex(Vertex p, PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                               float red, float green, float blue, float alpha, float shift) {
        out.vertex(pose.pose(), p.x(), p.y(), p.z()).color(red, green, blue, alpha).uv(p.u(), p.v() + shift)
            .overlayCoords(overlay).uv2(light).normal(pose.normal(), p.nx(), p.ny(), p.nz()).endVertex();
    }
}
