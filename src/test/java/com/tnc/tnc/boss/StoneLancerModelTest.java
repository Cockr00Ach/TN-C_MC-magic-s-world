package com.tnc.tnc.boss;

import com.tnc.tnc.boss.earth.client.StoneLancerMesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoneLancerModelTest {
    @Test void wideStoneFacesKeepTheirAspectRatioAndEmergenceJoinsTheCorePose() throws Exception {
        var gson=new com.google.gson.Gson();var root=java.nio.file.Path.of("src/main/resources/assets/tnc");
        var geo=gson.fromJson(java.nio.file.Files.readString(root.resolve("geo/entity/stone_lancer.geo.json")),com.google.gson.JsonObject.class);
        for(var entry:geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
            var bone=entry.getAsJsonObject();if(!bone.get("name").getAsString().equals("torso"))continue;
            var cube=bone.getAsJsonArray("cubes").get(0).getAsJsonObject();var size=cube.getAsJsonArray("size");var uv=cube.getAsJsonObject("uv").getAsJsonObject("north").getAsJsonArray("uv_size");
            assertEquals(size.get(0).getAsDouble()/size.get(1).getAsDouble(),uv.get(0).getAsDouble()/uv.get(1).getAsDouble(),.001,"Wide rock faces must not stretch a square texture sample");
        }
        var clips=gson.fromJson(java.nio.file.Files.readString(root.resolve("animations/entity/stone_lancer.animation.json")),com.google.gson.JsonObject.class).getAsJsonObject("animations");
        for(String name:new String[]{"torso","arm_-1","arm_1","fore_-1","fore_1"}){
            var last=clips.getAsJsonObject("emerge_core").getAsJsonObject("bones").getAsJsonObject(name).getAsJsonObject("rotation").getAsJsonArray("1.4");
            var first=clips.getAsJsonObject("core_followup").getAsJsonObject("bones").getAsJsonObject(name).getAsJsonObject("rotation").getAsJsonArray("0");
            assertEquals(last,first,"Emergence and the follow-up must share a pose: "+name);
        }
    }
    @Test void actualRockRigBakesAndBothArmsEndInRealPyramidalLances() throws Exception {
        var path=java.nio.file.Path.of("src/main/resources/assets/tnc/geo/entity/stone_lancer.geo.json");
        var raw=software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(java.nio.file.Files.readString(path),software.bernie.geckolib.loading.json.raw.Model.class);
        var model=new software.bernie.geckolib.loading.object.BakedModelFactory.Builtin().constructGeoModel(software.bernie.geckolib.loading.object.GeometryTree.fromModel(raw));
        StoneLancerMesh.apply(model);
        for(String bone:new String[]{"lance_point_down_-1","lance_point_down_1","crown_point_up_1"}){
            var cube=model.getBone(bone).orElseThrow().getCubes().get(0);assertEquals(5,cube.quads().length);
            for(var face:cube.quads()){
                assertTrue(face.normal().isFinite());assertEquals(1,face.normal().length(),.001);
                for(var v:face.vertices()){assertTrue(v.position().isFinite());assertTrue(v.texU()>=0&&v.texU()<=1&&v.texV()>=0&&v.texV()<=1);}
            }
        }
        assertEquals(20,model.getBone("torso").orElseThrow().getCubes().get(0).quads().length);
        var arm=model.getBone("fore_1").orElseThrow().getCubes().get(0);
        var side=java.util.Arrays.stream(arm.quads()).filter(q->q.normal().x>.99F).findFirst().orElseThrow();
        var edgeA=side.vertices()[0];var edgeB=side.vertices()[1];
        assertEquals(edgeA.position().z,edgeB.position().z,.00001);
        assertEquals(edgeA.texU(),edgeB.texU(),.00001,"A vertical side edge must retain constant U instead of stretching the depth texture up the arm");
        assertTrue(Math.abs(edgeA.texV()-edgeB.texV())>.001);
        var tip=model.getBone("lance_point_down_1").orElseThrow().getCubes().get(0);StoneLancerMesh.apply(model);
        assertSame(tip,model.getBone("lance_point_down_1").orElseThrow().getCubes().get(0),"Resource retrieval must not repeatedly rebuild spear meshes");
    }
    @Test void animationTracksOnlyAddressBonesInTheProductionRig() throws Exception {
        var parser=new com.google.gson.Gson();var root=java.nio.file.Path.of("src/main/resources/assets/tnc");
        var geo=parser.fromJson(java.nio.file.Files.readString(root.resolve("geo/entity/stone_lancer.geo.json")),com.google.gson.JsonObject.class);
        var names=new java.util.HashSet<String>();for(var bone:geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))names.add(bone.getAsJsonObject().get("name").getAsString());
        var anim=parser.fromJson(java.nio.file.Files.readString(root.resolve("animations/entity/stone_lancer.animation.json")),com.google.gson.JsonObject.class).getAsJsonObject("animations");
        assertEquals(12,anim.size());for(var clip:anim.entrySet())for(String name:clip.getValue().getAsJsonObject().getAsJsonObject("bones").keySet())assertTrue(names.contains(name),name);
    }
    @Test void castingRaisesTheLancesForwardAndRigIsSquat() throws Exception {
        var parser=new com.google.gson.Gson();var root=java.nio.file.Path.of("src/main/resources/assets/tnc");
        var geo=parser.fromJson(java.nio.file.Files.readString(root.resolve("geo/entity/stone_lancer.geo.json")),com.google.gson.JsonObject.class);
        var bones=geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        for(var entry:bones){var b=entry.getAsJsonObject();
            if(b.get("name").getAsString().equals("torso")){var size=b.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("size");assertTrue(size.get(0).getAsDouble()>size.get(1).getAsDouble()*2);}
            if(b.get("name").getAsString().equals("fore_1")){var size=b.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("size");assertTrue(size.get(0).getAsDouble()>40);}
        }
        var anim=parser.fromJson(java.nio.file.Files.readString(root.resolve("animations/entity/stone_lancer.animation.json")),com.google.gson.JsonObject.class).getAsJsonObject("animations");
        for(String clip:new String[]{"volley","fissure","sky","core","melee"})for(int sign:new int[]{-1,1}){
            var track=anim.getAsJsonObject(clip).getAsJsonObject("bones").getAsJsonObject("arm_"+sign).getAsJsonObject("rotation");
            assertTrue(track.entrySet().stream().anyMatch(e->e.getValue().getAsJsonArray().get(0).getAsDouble()<-60));
            assertTrue(track.entrySet().stream().allMatch(e->e.getValue().getAsJsonArray().get(0).getAsDouble()<=0));
        }
    }

}
