package com.tnc.tnc.world.stonecrest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StonecrestResourceIdentityTest {
    @Test
    void manifestTargetsTheSouthernHeroskandMainFortress() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
                "/data/tnc/stonecrest/manifest.json")) {
            assertNotNull(stream);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                JsonObject bounds = root.getAsJsonObject("source_bounds");

                assertArrayEquals(new int[]{2114, 2385}, ints(bounds.getAsJsonArray("x")));
                assertArrayEquals(new int[]{128, 319}, ints(bounds.getAsJsonArray("y")));
                assertArrayEquals(new int[]{888, 1159}, ints(bounds.getAsJsonArray("z")));
                assertArrayEquals(new int[]{272, 192, 272}, ints(root.getAsJsonArray("dimensions")));
                assertArrayEquals(new int[]{150, 38, 262}, ints(root.getAsJsonArray("anchor_local")));

                JsonArray maskRows = root.getAsJsonArray("mask_rows");
                assertTrue(contains(maskRows, 150, 262), "the locator must be in the level terrain core");

                for (int sourceZ = 888; sourceZ < 908; sourceZ++) {
                    for (int sourceX = 2114; sourceX <= 2385; sourceX++) {
                        assertFalse(contains(maskRows, sourceX - 2114, sourceZ - 888),
                                "the northern garden connector must not be exported");
                    }
                }
                for (int sourceZ = 1000; sourceZ <= 1112; sourceZ++) {
                    for (int sourceX = 2320; sourceX <= 2385; sourceX++) {
                        assertFalse(contains(maskRows, sourceX - 2114, sourceZ - 888),
                                "the detached eastern circular garden must not be exported");
                    }
                }
            }
        }
    }

    private static boolean contains(JsonArray rows, int x, int z) {
        for (var runElement : rows.get(z).getAsJsonArray()) {
            JsonArray run = runElement.getAsJsonArray();
            if (x >= run.get(0).getAsInt() && x <= run.get(1).getAsInt()) {
                return true;
            }
        }
        return false;
    }

    private static int[] ints(JsonArray values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).getAsInt();
        }
        return result;
    }
}
