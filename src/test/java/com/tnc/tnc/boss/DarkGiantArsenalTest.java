package com.tnc.tnc.boss;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DarkGiantArsenalTest {
    @Test void newLegRigHasIndependentKneesAndParallelFeet() throws Exception {
        var geo=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/geo/entity/dark_giant.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var bones=new java.util.HashMap<String,com.google.gson.JsonObject>();
        geo.getAsJsonArray("bones").forEach(b->bones.put(b.getAsJsonObject().get("name").getAsString(),b.getAsJsonObject()));
        var animations=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/animations/entity/dark_giant.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        assertTrue(animations.has("idle"));
        for(String side:new String[]{"right","left"}){
            assertEquals("leg"+side,bones.get("shin"+side).get("parent").getAsString());
            assertEquals("shin"+side,bones.get("foot"+side).get("parent").getAsString());
            for(String part:new String[]{"leg","shin","foot"}){
                var track=animations.getAsJsonObject("walk").getAsJsonObject("bones").getAsJsonObject(part+side).getAsJsonObject("rotation");
                for(var frame:track.entrySet()){
                    assertEquals(0,frame.getValue().getAsJsonArray().get(1).getAsDouble(),.001);
                    assertEquals(0,frame.getValue().getAsJsonArray().get(2).getAsDouble(),.001);
                }
            }
        }
    }
    @Test void everyThreeCastsIncludeAnAlternatingTierThreeAndAvoidBackToBackRepeats(){
        for(int seed=0;seed<100;seed++){
            var random=net.minecraft.util.RandomSource.create(seed);
            int previous=-1;
            for(int cast=0;cast<60;cast++){
                int selected=TNDarkGiantSpells.pick(random,cast,previous);
                var move=TNDarkGiantSpells.move(selected);
                if(selected!=3)assertNotEquals(previous,selected);
                if(cast%3==0){
                    assertEquals(3,move.tier());
                    assertEquals((cast/3)%2==0?"sacrifice":"night_embrace",move.path());
                }else if(cast%3==1)assertEquals("black_ruin",move.path());
                else assertTrue(move.tier()>=4);
                previous=selected;
            }
        }
    }
    @Test void requestedTiersMatchTheActualPackAndEveryMoveHasAnAnimatedWindup() throws Exception {
        var expected=java.util.Map.of("black_ruin",4,"slay_light",5,"devour_light",5,"sacrifice",3,"evil_god",5);
        Path pack;
        try(var dirs=Files.list(Path.of("modpack"))){
            pack=dirs.filter(p->Files.isDirectory(p.resolve("kubejs/data/tnc/spells"))).findFirst().orElseThrow();
        }
        for(var entry:expected.entrySet()){
            assertEquals(entry.getValue().intValue(),TNDarkGiantSpells.tierOf(entry.getKey()));
            Path json=pack.resolve("kubejs/data/tnc/spells/"+entry.getKey()+".json");
            if(!Files.exists(json))json=Path.of("src/main/resources/data/tnc/spells/"+entry.getKey()+".json");
            var spell=JsonParser.parseString(Files.readString(json)
                    .replace("\uFEFF","")).getAsJsonObject();
            assertEquals(entry.getValue().intValue(),spell.getAsJsonObject("learn").get("tier").getAsInt());
        }
        var animations=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/animations/entity/dark_giant.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        var rig=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/geo/entity/dark_giant.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        var names=new java.util.HashSet<String>();rig.forEach(b->names.add(b.getAsJsonObject().get("name").getAsString()));
        var distinct=new java.util.HashSet<String>();
        for(int i=0;i<TNDarkGiantSpells.count();i++){
            var move=TNDarkGiantSpells.move(i);String name=TNDarkGiantSpells.animation(move);distinct.add(name);
            assertTrue(animations.has(name));var clip=animations.getAsJsonObject(name);
            assertEquals(move.castTicks()/20D+.6,clip.get("animation_length").getAsDouble(),.001);
            for(String bone:clip.getAsJsonObject("bones").keySet())assertTrue(names.contains(bone),bone);
        }
        assertEquals(5,distinct.size());
    }
}
