package com.tnc.tnc.world.stonecrest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BuildingResourceTest {
    @Test void everyReferencedPieceIsBundled() throws Exception {
        for (String name:new String[]{"roadside_ruin","fantasy_tavern","gothic_castle","dark_fantasy_castle","abyss_citadel",
                "end_pvp_island","heroskand_complex","heroskand_estate_v2","gothic_cathedral","elden_coastal_castle"}) {
            try (var stream=getClass().getResourceAsStream("/data/tnc/buildings/"+name+".json")) {
                assertNotNull(stream,name);
                var root=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),JsonObject.class);
                assertEquals(root.get("piece_count").getAsInt(),root.getAsJsonArray("pieces").size());
                long blocks=0;
                for (var element:root.getAsJsonArray("pieces")) {
                    var piece=element.getAsJsonObject();
                    String path="/data/tnc/structures/"+piece.get("resource").getAsString().substring(4)+".nbt";
                    assertNotNull(getClass().getResource(path),path);
                    blocks+=piece.get("blocks").getAsLong();
                }
                if (name.equals("abyss_citadel")) assertEquals(200395,blocks);
            }
            String id=name.startsWith("heroskand_")?"stonecrest_fortress":name;
            assertNotNull(getClass().getResource("/data/tnc/worldgen/structure/"+id+".json"));
            assertNotNull(getClass().getResource("/data/tnc/worldgen/structure_set/"+id+".json"));
        }
    }
}
