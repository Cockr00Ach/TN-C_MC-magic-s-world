package com.tnc.tnc.boss.eclipse.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.world.ink.InkDragonMeshData;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bake only on first use after resource reload. The bind-pose vertices never change. */
final class EclipseMesh {
    record Rig(Node root, Node head) {}

    record Node(String name, float x, float y, float z, List<Node> children,
                Batch[] body, Batch[] radiant) {}

    record Batch(float[] positions, float[] normals, int[] colors) {
        private static final float[] U = {.25f, .75f, .75f, .25f};
        private static final float[] V = {.25f, .25f, .75f, .75f};

        static Batch decode(byte[] data) {
            var buffer = ByteBuffer.wrap(data);
            int vertices = data.length / InkDragonMeshData.STRIDE;
            float[] positions = new float[vertices * 3];
            float[] normals = new float[vertices];
            int[] colors = new int[vertices];
            for (int i = 0; i < vertices; i++) {
                for (int axis = 0; axis < 3; axis++) positions[i * 3 + axis] = buffer.getFloat();
                colors[i] = buffer.getInt();
            }
            for (int i = 0; i < vertices; i += 3) {
                int a = i * 3;
                var ab = new Vector3f(positions[a + 3] - positions[a], positions[a + 4] - positions[a + 1],
                        positions[a + 5] - positions[a + 2]);
                var ac = new Vector3f(positions[a + 6] - positions[a], positions[a + 7] - positions[a + 1],
                        positions[a + 8] - positions[a + 2]);
                ab.cross(ac);
                if (ab.lengthSquared() > 1e-20f) ab.normalize();
                else ab.set(0, 1, 0);
                normals[i] = ab.x;
                normals[i + 1] = ab.y;
                normals[i + 2] = ab.z;
            }
            return new Batch(positions, normals, colors);
        }

        void draw(PoseStack.Pose pose, VertexConsumer out, int light, float alpha, float tint) {
            for (int i = 0; i < colors.length; i += 3) {
                for (int corner = 0; corner < 4; corner++) {
                    int v = i + Math.min(corner, 2), p = v * 3, c = colors[v];
                    out.vertex(pose.pose(), positions[p], positions[p + 1], positions[p + 2])
                            .color(channel(c >>> 24, tint), channel(c >>> 16, tint),
                                    channel(c >>> 8, tint), alpha)
                            .uv(U[corner], V[corner]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(pose.normal(), normals[i], normals[i + 1], normals[i + 2]).endVertex();
                }
            }
        }

        private static float channel(int shifted, float tint) {
            return Math.min(1, (shifted & 255) * tint / 255f);
        }
    }

    static Rig load(ResourceManager resources) throws IOException {
        var body = loadPass(resources, "body");
        var radiant = loadPass(resources, "radiant");
        var nodes = new LinkedHashMap<String, Node>();
        try (var reader = resources.getResourceOrThrow(location("rig.json")).openAsReader()) {
            for (var item : JsonParser.parseReader(reader).getAsJsonArray()) {
                var json = item.getAsJsonObject();
                String name = json.get("name").getAsString();
                var p = json.getAsJsonArray("pivot");
                var node = new Node(name, p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(),
                        new ArrayList<>(), body.remove(name), radiant.remove(name));
                if (!json.get("parent").isJsonNull()) {
                    String parent = json.get("parent").getAsString();
                    var parentNode = nodes.get(parent);
                    if (parentNode == null) throw new IOException("Missing Eclipse parent " + parent);
                    parentNode.children.add(node);
                } else if (!name.equals("root")) {
                    throw new IOException("Unexpected Eclipse root " + name);
                }
                if (nodes.putIfAbsent(name, node) != null) throw new IOException("Duplicate Eclipse bone " + name);
            }
        }
        if (!body.isEmpty() || !radiant.isEmpty() || !nodes.containsKey("root") || !nodes.containsKey("head")) {
            throw new IOException("Eclipse mesh and rig disagree");
        }
        return new Rig(nodes.get("root"), nodes.get("head"));
    }

    private static Map<String, Batch[]> loadPass(ResourceManager resources, String name) throws IOException {
        Map<String, Batch[]> batches = new HashMap<>();
        try (var input = resources.open(location(name + ".mesh"))) {
            for (var group : InkDragonMeshData.read(input).groups()) {
                batches.put(group.name(), group.levels().stream().map(Batch::decode).toArray(Batch[]::new));
            }
        }
        return batches;
    }

    private static ResourceLocation location(String file) {
        return ResourceLocation.fromNamespaceAndPath("tnc", "meshes/eclipse/" + file);
    }

    private EclipseMesh() {}
}
