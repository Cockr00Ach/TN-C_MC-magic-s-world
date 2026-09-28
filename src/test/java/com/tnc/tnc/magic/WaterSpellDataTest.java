package com.tnc.tnc.magic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterSpellDataTest {
    @Test void allWaterAndIndependentIconsExistAndAreDistinct() throws Exception {
        var folder=Path.of("src/main/resources/assets/tnc/textures/spell");
        var ids=List.of("water_ball","water_cannon","dragon_roar","dragon_howl","dragon_ruin","water_ripple","water_wave","wave_slash","tsunami","world_ending_sea","water_bind","water_prison","water_burial","abyss","sea_god_crypt","raindrop","first_rain","rainfall","downpour","flood_of_heaven","chaos_magic");
        var hashes=new HashSet<String>();
        for(String id:ids) {
            var file=folder.resolve(id+".png");assertTrue(Files.exists(file),id);
            var image=javax.imageio.ImageIO.read(file.toFile());assertNotNull(image);assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());
            hashes.add(Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }assertEquals(21,hashes.size(),"Distinct identities rather than a repeated generic icon");
    }
    @Test void chaosHasIndependentEngineDataAndCooldown() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/tnc/spells/chaos_magic.json"))).getAsJsonObject();
        assertEquals("ARCANE",json.get("school").getAsString());assertEquals(10,json.getAsJsonObject("cost").get("cooldown_duration").getAsInt());
        assertEquals("SELF",json.getAsJsonObject("release").getAsJsonObject("target").get("type").getAsString());
    }
    @Test void allTwentySpellsHaveOnlyOneJavaMechanicOwner() throws Exception {
        int count=0;
        for(Path folder:List.of(Path.of("src/main/resources/data/tnc/spells"),Path.of("modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/spells"))) {
            try(var paths=Files.list(folder)) {
                for(Path path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                    var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    if(!json.has("school")||!json.get("school").getAsString().equals("WATER"))continue;
                    count++;assertFalse(json.has("area_impact"),path.toString());
                    assertEquals("SELF",json.getAsJsonObject("release").getAsJsonObject("target").get("type").getAsString(),path.toString());
                    var impacts=json.getAsJsonArray("impact");assertEquals(1,impacts.size());
                    var action=impacts.get(0).getAsJsonObject().getAsJsonObject("action");
                    assertEquals("STATUS_EFFECT",action.get("type").getAsString());
                    assertEquals("tnc:water_cast",action.getAsJsonObject("status_effect").get("effect_id").getAsString());
                }
            }
        }assertEquals(20,count);
    }
}
