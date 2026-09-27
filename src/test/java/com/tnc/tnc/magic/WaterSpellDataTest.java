package com.tnc.tnc.magic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterSpellDataTest {
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
