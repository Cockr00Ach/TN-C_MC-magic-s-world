package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Triangle clipping keeps authored UVs at the reveal edge. Used only for the two short close-ups. */
final class SanctumMeshCut {
    private record Plane(float x, float y, float z, float w) {
        float side(Vector3f p) { return x * p.x + y * p.y + z * p.z + w; }
    }
    private static final int STRIDE = 10;
    private static final int MAX_POLYGON = 12;
    private final Matrix4f local;
    private final Plane[] planes;
    private final boolean keepInside;

    private SanctumMeshCut(Matrix4f local, boolean keepInside, Plane... planes) {
        this.local = local;
        this.planes = planes;
        this.keepInside = keepInside;
    }

    static SanctumMeshCut tears(float length) {
        // The uppermost vertices are at both lower eyelids. Reveal along the existing cheek channels.
        return length >= 1 ? null : new SanctumMeshCut(new Matrix4f(), true, new Plane(0, 1, 0, -(.925F - 2.70F * length)));
    }

    static SanctumMeshCut aperture(Matrix4f local, float halfWidth, boolean limb) {
        float y = SanctumRupture.CENTER_Y, h = SanctumRupture.HALF_HEIGHT;
        float taper = .46F * halfWidth / h;
        return new SanctumMeshCut(local, limb,
                new Plane(0, 0, 1, limb ? -5.12F : -1.85F),
                new Plane(0, 1, 0, h - y), new Plane(0, -1, 0, h + y),
                new Plane(-1, taper, 0, halfWidth - taper * y), new Plane(1, taper, 0, halfWidth - taper * y),
                new Plane(-1, -taper, 0, halfWidth + taper * y), new Plane(1, -taper, 0, halfWidth + taper * y));
    }

    private static float[] point(float[] vertices, int index) {
        float[] p = new float[STRIDE];
        System.arraycopy(vertices, index * STRIDE, p, 0, STRIDE);
        return p;
    }

    private static void between(float[] a, float[] b, float t, float[] p) {
        for (int i = 0; i < STRIDE; i++) p[i] = a[i] + t * (b[i] - a[i]);
    }

    void draw(VertexConsumer out, SanctumMesh.Layer layer, Matrix4f pose, int light) {
        var normal = pose.normal(new Matrix3f());
        var position = new Vector3f();
        var direction = new Vector3f();
        float[][] inside = new float[MAX_POLYGON][], next = new float[MAX_POLYGON][], outside = new float[MAX_POLYGON][];
        float[][] edges = new float[2 * planes.length][STRIDE];
        if (layer.cutVertices == null) {
            layer.cutVertices = new float[layer.source.length / STRIDE][];
            for (int i = 0; i < layer.cutVertices.length; i++) layer.cutVertices[i] = point(layer.source, i);
        }
        var vertices = layer.cutVertices;
        for (int i = 0; i < layer.corners.length; i += 4) {
            float[] a = vertices[layer.corners[i]], b = vertices[layer.corners[i + 1]], c = vertices[layer.corners[i + 2]];
            inside[0] = a; inside[1] = b; inside[2] = c;
            clip(out, pose, normal, direction, position, layer.blend, light, inside, next, outside, edges);
            if (layer.corners[i + 2] != layer.corners[i + 3]) {
                inside[0] = a; inside[1] = c; inside[2] = vertices[layer.corners[i + 3]];
                clip(out, pose, normal, direction, position, layer.blend, light, inside, next, outside, edges);
            }
        }
    }

    private void clip(VertexConsumer out, Matrix4f pose, Matrix3f normal, Vector3f direction, Vector3f position,
                      boolean blended, int light, float[][] inside, float[][] next, float[][] outside, float[][] edges) {
        int count = 3, edgeIndex = 0;
        for (Plane plane : planes) {
            if (count < 3) return;
            int kept = 0, removed = 0;
            float[] previous = inside[count - 1];
            float a = side(plane, previous, position);
            for (int j = 0; j < count; j++) {
                float[] p = inside[j];
                float b = side(plane, p, position);
                if ((a >= 0) != (b >= 0)) {
                    float[] edge = edges[edgeIndex++];
                    between(previous, p, a / (a - b), edge);
                    next[kept++] = edge;
                    outside[removed++] = edge;
                }
                if (b >= 0) next[kept++] = p;
                else outside[removed++] = p;
                previous = p;
                a = b;
            }
            if (!keepInside) submit(out, pose, normal, direction, blended, light, outside, removed);
            float[][] swap = inside;
            inside = next;
            next = swap;
            count = kept;
        }
        if (keepInside) submit(out, pose, normal, direction, blended, light, inside, count);
    }

    private float side(Plane plane, float[] p, Vector3f scratch) {
        return plane.side(local.transformPosition(scratch.set(p[0], p[1], p[2])));
    }

    private static void submit(VertexConsumer out, Matrix4f pose, Matrix3f normal, Vector3f dir,
                               boolean blended, int light, float[][] poly, int count) {
        for (int i = 1; i + 1 < count; i++) {
            vertex(out, pose, normal, dir, blended, light, poly[0]);
            vertex(out, pose, normal, dir, blended, light, poly[i]);
            vertex(out, pose, normal, dir, blended, light, poly[i+1]);
            vertex(out, pose, normal, dir, blended, light, poly[i+1]);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Matrix3f normal, Vector3f dir,
                               boolean blended, int light, float[] p) {
        normal.transform(dir.set(p[6], p[7], p[8]));
        if (dir.lengthSquared() > 1e-8F) dir.normalize();
        int packed = LightTexture.pack(Math.max((int)p[9], LightTexture.block(light)), LightTexture.sky(light));
        out.vertex(pose, p[0], p[1], p[2]).color(p[5], p[5], p[5], blended ? .67F : 1)
                .uv(p[3], p[4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packed)
                .normal(dir.x, dir.y, dir.z).endVertex();
    }
}
