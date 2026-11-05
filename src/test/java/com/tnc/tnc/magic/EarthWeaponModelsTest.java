package com.tnc.tnc.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.block.model.BlockModel;
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
            // Parse with vanilla's own deserializer: this is what rejected the first batch
            // ("Expected between 1 and 6 unique faces, got 0") and produced missing-model cubes.
            BlockModel parsed;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                parsed = BlockModel.fromStream(reader);
            }
            var elements = parsed.getElements();
            assertFalse(elements.isEmpty(), name);
            for (var element : elements) {
                int count = element.faces.size();
                assertTrue(count >= 1 && count <= 6, name + " element has " + count + " faces");
            }
            JsonObject raw;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                raw = JsonParser.parseReader(reader).getAsJsonObject();
            }
            assertEquals("tnc:block/earth_weapon/" + name.substring("earth_wpn_".length()),
                    raw.getAsJsonObject("textures").get("0").getAsString());
            Path png = TEXTURES.resolve(name.substring("earth_wpn_".length()) + ".png");
            assertTrue(Files.exists(png), png.toString());
        }
    }

    @Test void everyBossWeaponModelIsLoadableAndUsesTheBlackGoldTexture() throws Exception {
        var bossModels = EarthWeaponModels.bossModels();
        assertEquals(17, bossModels.size());
        for (String model : bossModels) {
            assertTrue(EarthWeaponModels.isBossWeapon(model), model);
            Path file = MODELS.resolve(model + ".json");
            assertTrue(Files.exists(file), file.toString());
            BlockModel parsed;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                parsed = BlockModel.fromStream(reader);
            }
            assertFalse(parsed.getElements().isEmpty(), model);
            JsonObject raw;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                raw = JsonParser.parseReader(reader).getAsJsonObject();
            }
            String name = model.substring("stone_lancer_earth_wpn_dark_".length());
            assertEquals("tnc:block/earth_weapon_dark/" + name,
                    raw.getAsJsonObject("textures").get("0").getAsString());
            assertTrue(Files.exists(Path.of("src/main/resources/assets/tnc/textures/block/earth_weapon_dark")
                    .resolve(name + ".png")), model);
        }
    }
}
