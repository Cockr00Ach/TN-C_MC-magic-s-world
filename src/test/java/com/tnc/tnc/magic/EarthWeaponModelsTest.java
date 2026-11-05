package com.tnc.tnc.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the generated stone-weapon models against the failure that made them render as
 * purple missing-model cubes: vanilla rejects an element with zero faces, and every face
 * needs a uv plus a texture reference.
 */
class EarthWeaponModelsTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/tnc/models/projectile");
    private static final Path TEXTURES = Path.of("src/main/resources/assets/tnc/textures/block/earth_weapon");

    @Test void everyWeaponModelIsLoadableAndTextured() throws Exception {
        assertEquals(17, EarthWeaponModels.MODELS.size());
        for (String name : EarthWeaponModels.MODELS) {
            assertTrue(name.startsWith("earth_wpn_"), name);
            Path file = MODELS.resolve(name + ".json");
            assertTrue(Files.exists(file), file.toString());
            JsonObject model;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                model = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject textures = model.getAsJsonObject("textures");
            String texture = textures.get("0").getAsString();
            assertEquals("tnc:block/earth_weapon/" + name.substring("earth_wpn_".length()), texture);

            var elements = model.getAsJsonArray("elements");
            assertFalse(elements.isEmpty(), name);
            for (JsonElement element : elements) {
                JsonObject cube = element.getAsJsonObject();
                JsonObject faces = cube.getAsJsonObject("faces");
                int count = faces.size();
                assertTrue(count >= 1 && count <= 6, name + " element has " + count + " faces");
                for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    assertTrue(face.getValue().getAsJsonObject().has("uv"), name + " " + face.getKey());
                    assertEquals("#0", face.getValue().getAsJsonObject().get("texture").getAsString());
                }
                for (String bound : new String[] {"from", "to"}) {
                    var xyz = cube.getAsJsonArray(bound);
                    assertEquals(3, xyz.size());
                    for (JsonElement value : xyz) {
                        double v = value.getAsDouble();
                        assertTrue(v >= -16 && v <= 32, name + " " + bound + " out of range: " + v);
                    }
                }
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(cube.getAsJsonArray("from").get(axis).getAsDouble()
                                    <= cube.getAsJsonArray("to").get(axis).getAsDouble() + 1e-6,
                            name + " from > to");
                }
            }
            Path png = TEXTURES.resolve(name.substring("earth_wpn_".length()) + ".png");
            assertTrue(Files.exists(png), png.toString());
        }
    }
}
