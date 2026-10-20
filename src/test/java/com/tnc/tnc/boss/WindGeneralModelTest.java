package com.tnc.tnc.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindGeneralModelTest {
    @Test void humanRigAndRealSpearBakeWithFiniteIndependentUvs() throws Exception {
        var root=java.nio.file.Path.of("src/main/resources/assets/tnc");
        var raw=software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(java.nio.file.Files.readString(root.resolve("geo/entity/wind_general.geo.json")),software.bernie.geckolib.loading.json.raw.Model.class);
        var model=new software.bernie.geckolib.loading.object.BakedModelFactory.Builtin().constructGeoModel(software.bernie.geckolib.loading.object.GeometryTree.fromModel(raw));
        for(String name:new String[]{"head","helmet","hand_right","cape_-1_2","ribbon_1_2","plume_3","spear_point_up"})assertTrue(model.getBone(name).isPresent(),name);
        assertTrue(model.getBone("spear_point_up").orElseThrow().getCubes().size()>=4,"The blade is built from tapered layers of cuboids");
        assertTrue(model.getBone("head").orElseThrow().getCubes().size()>=40,"Facial features and hair are individually built blocks");
        var pending=new java.util.ArrayDeque<>(model.topLevelBones());int pieces=0,vertices=0;
        while(!pending.isEmpty()){
            var bone=pending.remove();pending.addAll(bone.getChildBones());
            for(var cube:bone.getCubes()){pieces++;assertEquals(6,cube.quads().length,"Keep original cuboids, with no sculpt/chamfer pass");for(var face:cube.quads())for(var v:face.vertices()){
                vertices++;assertTrue(v.position().isFinite());assertTrue(v.texU()>=0&&v.texU()<=1&&v.texV()>=0&&v.texV()<=1);
            }}
        }
        assertTrue(pieces>=180&&pieces<=360);assertTrue(vertices<12000);
        var image=javax.imageio.ImageIO.read(root.resolve("textures/entity/wind_general.png").toFile());assertEquals(2048,image.getWidth());
    }
    @Test void clothChainsAndLocomotionOnlyAnimateExistingBones() throws Exception {
        var root=java.nio.file.Path.of("src/main/resources/assets/tnc");var gson=new com.google.gson.Gson();
        var geo=gson.fromJson(java.nio.file.Files.readString(root.resolve("geo/entity/wind_general.geo.json")),com.google.gson.JsonObject.class).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var names=new java.util.HashSet<String>();for(var b:geo.getAsJsonArray("bones"))names.add(b.getAsJsonObject().get("name").getAsString());
        var animations=gson.fromJson(java.nio.file.Files.readString(root.resolve("animations/entity/wind_general.animation.json")),com.google.gson.JsonObject.class).getAsJsonObject("animations");
        for(String clip:new String[]{"idle","walk"}){
            var tracks=animations.getAsJsonObject(clip).getAsJsonObject("bones");assertTrue(tracks.has("cape_1_2"));assertTrue(tracks.has("plume_3"));
            for(String bone:tracks.keySet())assertTrue(names.contains(bone),bone);
        }
    }
}
