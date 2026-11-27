package com.tnc.tnc.life.pasture.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Vector3f;

/** Sculpted surfaces use the original ModelPart pivots, including work, growth and shearing poses. */
public final class PastureSkin {
    private record Vertex(float x, float y, float z, float u, float v) {}
    private record Face(Vertex[] loop, Vector3f normal, boolean luminous) {}
    private static final class Joint {
        final ModelPart pose;
        final Face[] faces;
        final Face[] lightFaces;
        final ArrayList<Joint> children = new ArrayList<>();
        boolean glows;
        Joint(ModelPart pose, Face[] faces) {
            this.pose=pose; this.faces=faces;
            this.lightFaces=java.util.Arrays.stream(faces).filter(Face::luminous).toArray(Face[]::new);
        }
        boolean markLightBranches() {
            glows = lightFaces.length>0;
            for (var child : children) glows |= child.markLightBranches();
            return glows;
        }

        void draw(PoseStack stack, VertexConsumer out, int light, int overlay, float r, float g, float b, float a, boolean glow) {
            if (!pose.visible || (glow && !glows)) return;
            stack.pushPose();
            pose.translateAndRotate(stack);
            var transform = stack.last();
            for (var face : glow ? lightFaces : faces) {
                var n = face.normal;
                for (var v : face.loop) {
                    out.vertex(transform.pose(), v.x, v.y, v.z).color(r,g,b,a).uv(v.u,v.v)
                            .overlayCoords(overlay).uv2(light).normal(transform.normal(), n.x, n.y, n.z).endVertex();
                }
            }
            for (var child : children) child.draw(stack, out, light, overlay, r,g,b,a,glow);
            stack.popPose();
        }
    }
    private static final Face[] EMPTY = new Face[0];
    private final Joint root;
    private final Map<String,Joint> joints = new LinkedHashMap<>();

    /** Renderer construction/baking is repeated by Minecraft on each resource-pack reload. */
    public PastureSkin(String species, ModelPart part, String[][] parents) {
        var path = ResourceLocation.fromNamespaceAndPath("tnc", "pasture_meshes/" + species + ".json");
        Map<String,Face[]> geometry = new HashMap<>();
        Map<String,JsonArray> hinges = new LinkedHashMap<>();
        try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(path).openAsReader()) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : data.entrySet()) {
                var bone=entry.getValue().getAsJsonObject();
                geometry.put(entry.getKey(), readFaces(bone));
                if (bone.has("hinge")) hinges.put(entry.getKey(),bone.getAsJsonArray("hinge"));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Missing pasture sculpture " + path, error);
        }
        root = new Joint(part, EMPTY);
        joints.put("root", root);
        for (var pair : parents) {
            var parent = joints.get(pair[1]);
            var joint = new Joint(parent.pose.getChild(pair[0]), geometry.getOrDefault(pair[0], EMPTY));
            joints.put(pair[0], joint);
            parent.children.add(joint);
            geometry.remove(pair[0]);
        }
        for (var entry : hinges.entrySet()) {
            var bind=entry.getValue();
            var parent=joints.get(bind.get(0).getAsString());
            var jointPose=new ModelPart(java.util.List.of(),Map.of());
            var rest=net.minecraft.client.model.geom.PartPose.offset(bind.get(1).getAsFloat(),bind.get(2).getAsFloat(),bind.get(3).getAsFloat());
            jointPose.setInitialPose(rest); jointPose.loadPose(rest);
            var joint=new Joint(jointPose, geometry.remove(entry.getKey()));
            joints.put(entry.getKey(), joint); parent.children.add(joint);
        }
        root.markLightBranches();
        if (!geometry.isEmpty()) throw new IllegalStateException(path+" references unknown joints: "+geometry.keySet());
    }

    public void bindArticulatedParts(Map<String,ModelPart> poses) {
        joints.forEach((name,joint) -> poses.putIfAbsent(name,joint.pose));
    }

    private static Face[] readFaces(JsonObject bone) {
        var points = bone.getAsJsonArray("points");
        var faces = bone.getAsJsonArray("faces");
        Face[] result = new Face[faces.size()];
        for (int i=0;i<result.length;i++) {
            var f = faces.get(i).getAsJsonArray();
            var v = new Vertex[4];
            for (int j=0;j<4;j++) {
                JsonArray p=points.get(f.get(j).getAsInt()).getAsJsonArray();
                v[j]=new Vertex(p.get(0).getAsFloat()/16f,p.get(1).getAsFloat()/16f,p.get(2).getAsFloat()/16f,
                        f.get(4+j*2).getAsFloat()/128f,f.get(5+j*2).getAsFloat()/128f);
            }
            var a=v[0]; var b=v[1]; var c=v[2];
            var n=new Vector3f(b.x-a.x,b.y-a.y,b.z-a.z).cross(c.x-a.x,c.y-a.y,c.z-a.z).normalize();
            result[i]=new Face(v,n,f.get(12).getAsBoolean());
        }
        return result;
    }

    public void draw(PoseStack stack, VertexConsumer out, int light, int overlay, float r,float g,float b,float a, boolean glow) {
        root.draw(stack,out,light,overlay,r,g,b,a,glow);
    }

    public void drawSheepSection(boolean head, PoseStack stack, VertexConsumer out, int light, int overlay,float r,float g,float b,float a) {
        for (var child : root.children)
            if ((child==joints.get("head"))==head) child.draw(stack,out,light,overlay,r,g,b,a,false);
    }
}

