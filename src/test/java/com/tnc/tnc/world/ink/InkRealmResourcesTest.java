package com.tnc.tnc.world.ink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InkRealmResourcesTest {
    private static JsonObject read(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources").resolve(relative)))
                .getAsJsonObject();
    }

    @Test
    void dimensionUsesAFeaturelessFlatGenerator() throws Exception {
        JsonObject dimension = read("data/tnc/dimension/ink_realm.json");
        assertEquals("tnc:ink_realm", dimension.get("type").getAsString());

        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:flat", generator.get("type").getAsString());
        JsonObject settings = generator.getAsJsonObject("settings");
        assertEquals("tnc:ink_wastes", settings.get("biome").getAsString());
        assertFalse(settings.get("features").getAsBoolean());
        assertFalse(settings.get("lakes").getAsBoolean());
        assertTrue(settings.getAsJsonArray("structure_overrides").isEmpty());
        assertEquals("minecraft:white_concrete",
                settings.getAsJsonArray("layers").get(2).getAsJsonObject().get("block").getAsString());
    }

    @Test
    void dimensionTypeHasValidOverworldHeightAndFixedDaylight() throws Exception {
        JsonObject type = read("data/tnc/dimension_type/ink_realm.json");
        assertEquals(-64, type.get("min_y").getAsInt());
        assertEquals(384, type.get("height").getAsInt());
        assertEquals(384, type.get("logical_height").getAsInt());
        assertEquals(6000, type.get("fixed_time").getAsInt());
        assertEquals("tnc:ink_realm", type.get("effects").getAsString());
    }

    @Test
    void paperBaseContainsNoLegacyCalligraphyAtAnyLandscapeCoordinate() {
        for (int x = -224; x < 224; x++) {
            for (int z = -224; z < 224; z++) {
                assertEquals(InkFloorPattern.Shade.WHITE,InkFloorPattern.shadeAt(x,z));
            }
        }
    }

    @Test
    void mountainCloudDecalsRemainPresentWithoutTheStrokeMask() {
        assertEquals(5,InkPaintingPattern.stamps(0,0).size());
        assertTrue(InkPaintingPattern.atlasWidth()>1000);
    }
}
