package com.tnc.tnc.world.ink;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InkAbyssDrakeModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/tnc");

    @Test
    void modelIsArticulatedAndNonHumanoid() throws Exception {
        var geometry = JsonParser.parseString(Files.readString(
                ASSETS.resolve("geo/entity/ink_abyss_drake.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var bones = geometry.getAsJsonArray("bones");
        Set<String> names = new HashSet<>();
        int cubes = 0;
        for (var element : bones) {
            var bone = element.getAsJsonObject();
            names.add(bone.get("name").getAsString());
            if (bone.has("cubes")) cubes += bone.getAsJsonArray("cubes").size();
        }
        assertTrue(bones.size() >= 35, "the boss needs enough bones for a flowing silhouette");
        assertTrue(cubes >= 70, "the boss needs layered geometry, not stretched boxes");
        for (String required : Set.of("head", "lower_jaw", "broken_enso", "tail_5",
                "tail_wisp_l", "tail_wisp_c", "tail_wisp_r", "fore_fin_l", "fore_fin_r")) {
            assertTrue(names.contains(required), "missing articulated part: " + required);
        }
        assertTrue(names.stream().noneMatch(name -> name.matches(".*(human|leg|boot|hand).*")),
                "the ink boss must stay non-humanoid");
    }

    @Test
    void textureIsStrictlyMonochromeAndUsesDryBrushTransparency() throws Exception {
        var image = ImageIO.read(ASSETS.resolve("textures/entity/ink_abyss_drake.png").toFile());
        assertNotNull(image);
        int transparent = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) { transparent++; continue; }
                int red = (argb >>> 16) & 255;
                int green = (argb >>> 8) & 255;
                int blue = argb & 255;
                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                assertTrue(max - min <= 4, "colored pixel at " + x + "," + y);
            }
        }
        assertTrue(transparent > 500, "dry-brush regions need visible flying-white gaps");
    }

    @Test
    void idleAnimationMovesEveryTailSection() throws Exception {
        var root = JsonParser.parseString(Files.readString(
                ASSETS.resolve("animations/entity/ink_abyss_drake.animation.json"))).getAsJsonObject();
        var animation = root.getAsJsonObject("animations").getAsJsonObject("idle");
        assertTrue(animation.get("loop").getAsBoolean());
        var bones = animation.getAsJsonObject("bones");
        for (int i = 1; i <= 5; i++) assertTrue(bones.has("tail_" + i));
        assertTrue(bones.has("lower_jaw"));
        assertTrue(bones.has("broken_enso"));
    }
}
