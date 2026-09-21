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
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StonecrestResourceIdentityTest {
    @Test
    void manifestTargetsTheSouthernHeroskandMainFortress() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
                "/data/tnc/stonecrest/manifest.json")) {
            assertNotNull(stream);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                JsonObject bounds = root.getAsJsonObject("source_bounds");

                assertArrayEquals(new int[]{2128, 2399}, ints(bounds.getAsJsonArray("x")));
                assertArrayEquals(new int[]{128, 319}, ints(bounds.getAsJsonArray("y")));
                assertArrayEquals(new int[]{888, 1159}, ints(bounds.getAsJsonArray("z")));
                assertArrayEquals(new int[]{272, 192, 272}, ints(root.getAsJsonArray("dimensions")));
                assertArrayEquals(new int[]{136, 38, 262}, ints(root.getAsJsonArray("anchor_local")));
            }
        }
    }

    private static int[] ints(JsonArray values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).getAsInt();
        }
        return result;
    }
}
