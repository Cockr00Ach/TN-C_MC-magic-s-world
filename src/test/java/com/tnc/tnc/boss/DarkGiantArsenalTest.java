package com.tnc.tnc.boss;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DarkGiantArsenalTest {
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
