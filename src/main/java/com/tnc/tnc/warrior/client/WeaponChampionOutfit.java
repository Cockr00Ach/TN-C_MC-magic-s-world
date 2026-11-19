package com.tnc.tnc.warrior.client;

import com.google.gson.Gson;
import com.tnc.tnc.warrior.WeaponChampion;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Bakes authored joint hierarchies, including independent animal skeletons and their weapon sockets. */
final class WeaponChampionOutfit {
    private static final Gson GSON = new Gson();
    private static final float RADIANS = (float) (Math.PI / 180);

    private record Outfit(List<Part> parts, Beast beast, int textureHeight, List<WeaponChampionHead.Surface> headSurfaces) {}
    enum Form { UPRIGHT, APE, BEAR, BIRD, MANTIS }
    record Beast(Form form, Map<String, float[]> anchors, String gripBone, float[] grip, float[] gripRotation) {}
    private record Part(String name, String bone, float[] pivot, float[] rotation, List<Cube> cubes, Motion motion) {}
    private record Cube(int[] uv, float[] from, float[] size) {}
    private record Motion(float[] idle, float[] walk, float[] wind, float[] hit, float[] open) {}
    record Baked(ModelPart root, List<Joint> joints, Beast beast, WeaponChampionHead headSurface) {}

    record Joint(ModelPart part, Motion motion) {
        void pose(float idle, float walk, float wind, float hit, float open) {
            part.resetPose();
            part.xRot += angle(0, idle, walk, wind, hit, open);
            part.yRot += angle(1, idle, walk, wind, hit, open);
            part.zRot += angle(2, idle, walk, wind, hit, open);
        }

        private float angle(int axis, float idle, float walk, float wind, float hit, float open) {
            return RADIANS * (motion.idle[axis] * idle + motion.walk[axis] * walk
                + motion.wind[axis] * wind + motion.hit[axis] * hit + motion.open[axis] * open);
        }
    }

    private WeaponChampionOutfit() {}

    static Baked bake(ResourceManager resources, WeaponChampion champion) {
        var model = ResourceLocation.fromNamespaceAndPath("tnc", "models/entity/champions/" + champion.id() + ".json");
        try (var reader = resources.getResourceOrThrow(model).openAsReader()) {
            return bake(reader);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load champion outfit " + model, error);
        }
    }

    static Baked bake(Reader reader) {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        Outfit outfit = GSON.fromJson(reader, Outfit.class);
        if (outfit.beast() != null) {
            // Keep the vanilla renderer's named joint API, replacing every humanoid cuboid.
            outfit.beast().anchors().forEach((name, at) -> mesh.getRoot().addOrReplaceChild(name,
                CubeListBuilder.create(), PartPose.offset(at[0], at[1], at[2])));
            mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        }
        var definitions = new HashMap<String, PartDefinition>();
        for (Part part : outfit.parts()) {
            var builder = CubeListBuilder.create();
            for (Cube cube : part.cubes()) {
                float[] start = cube.from();
                float[] size = cube.size();
                builder.texOffs(cube.uv()[0], cube.uv()[1])
                    .addBox(start[0], start[1], start[2], size[0], size[1], size[2]);
            }
            float[] pivot = part.pivot();
            float[] rotation = part.rotation();
            var pose = PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2],
                rotation[0] * RADIANS, rotation[1] * RADIANS, rotation[2] * RADIANS);
            var parent = definitions.get(part.bone());
            if (parent == null) parent = mesh.getRoot().getChild(part.bone());
            definitions.put(part.name(), parent.addOrReplaceChild(part.name(), builder, pose));
        }
        int atlasHeight = outfit.textureHeight() == 0 ? 128 : outfit.textureHeight();
        var root = LayerDefinition.create(mesh, 128, atlasHeight).bakeRoot();
        var nodes = new HashMap<String, ModelPart>();
        var joints = new ArrayList<Joint>();
        for (Part part : outfit.parts()) {
            var parent = nodes.get(part.bone());
            if (parent == null) parent = root.getChild(part.bone());
            var node = parent.getChild(part.name());
            nodes.put(part.name(), node);
            if (part.motion() != null) joints.add(new Joint(node, part.motion()));
        }
        var sculpt = outfit.headSurfaces() == null ? null : new WeaponChampionHead(outfit.headSurfaces(), atlasHeight);
        return new Baked(root, List.copyOf(joints), outfit.beast(), sculpt);
    }
}
