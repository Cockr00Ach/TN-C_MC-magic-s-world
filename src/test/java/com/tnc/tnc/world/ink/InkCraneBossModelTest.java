package com.tnc.tnc.world.ink;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InkCraneBossModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/tnc");

    @Test
    void oversizedModelHasArticulatedWingsAndLayeredTail() throws Exception {
        var geometry = JsonParser.parseString(Files.readString(
                ASSETS.resolve("geo/entity/ink_crane_boss.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var bones = geometry.getAsJsonArray("bones");
        Set<String> names = new HashSet<>();
        int cubes = 0;
        for (var element : bones) {
            var bone = element.getAsJsonObject();
            names.add(bone.get("name").getAsString());
            if (bone.has("cubes")) cubes += bone.getAsJsonArray("cubes").size();
        }
        assertTrue(bones.size() >= 214, "refinement must at least double the original 107-bone rig");
        assertTrue(cubes >= 176, "refinement must at least double the original 88-cube geometry");
        for (String side : Set.of("l", "r")) {
            assertTrue(names.contains("wing_" + side));
            assertTrue(names.contains("wing_" + side + "_fore"));
            for (int i = 1; i <= 10; i++) assertTrue(names.contains("wing_" + side + "_finger_" + i));
            assertTrue(names.contains("hock_" + side));
            assertTrue(names.contains("emitter_wing_" + side));
            for (int i = 1; i <= 4; i++) assertTrue(names.contains("toe_" + side + "_" + i));
        }
        for (int i = 1; i <= 11; i++) {
            assertTrue(names.contains("tail_feather_" + i));
            assertTrue(names.contains("glow_outline_tail_feather_" + i));
        }
    }

    @Test
    void wingAttacksAnimateEveryFeatherFinger() throws Exception {
        var animations = JsonParser.parseString(Files.readString(
                ASSETS.resolve("animations/entity/ink_crane_boss.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        assertTrue(animations.has("idle"));
        assertTrue(animations.has("walk"));
        assertTrue(animations.has("wing_double"));
        for (String side : Set.of("l", "r")) {
            var slash = animations.getAsJsonObject("wing_slash_" + (side.equals("l") ? "left" : "right"))
                    .getAsJsonObject("bones");
            for (int i = 1; i <= 10; i++) assertTrue(slash.has("wing_" + side + "_finger_" + i));
        }
    }

    @Test
    void textureIsStrictBlackWhiteGray() throws Exception {
        var image = ImageIO.read(ASSETS.resolve("textures/entity/ink_crane_boss.png").toFile());
        assertNotNull(image);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 255;
                int green = (rgb >>> 8) & 255;
                int blue = rgb & 255;
                assertTrue(red == green && green == blue, "colored pixel at " + x + "," + y);
            }
        }
    }

    @Test
    void everyFaceHasAnInBoundsAtlasRegionAndEveryAnimationBoneExists() throws Exception {
        var geometry = JsonParser.parseString(Files.readString(
                ASSETS.resolve("geo/entity/ink_crane_boss.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        int width = geometry.getAsJsonObject("description").get("texture_width").getAsInt();
        int height = geometry.getAsJsonObject("description").get("texture_height").getAsInt();
        Set<String> names = new HashSet<>();
        for (var entry : geometry.getAsJsonArray("bones")) {
            var bone = entry.getAsJsonObject();
            assertTrue(names.add(bone.get("name").getAsString()), "duplicate bone name");
            if (bone.has("parent")) assertTrue(names.contains(bone.get("parent").getAsString()),
                    "parent must be declared before its child");
            if (!bone.has("cubes")) continue;
            for (var cube : bone.getAsJsonArray("cubes")) {
                var uv = cube.getAsJsonObject().getAsJsonObject("uv");
                for (String face : Set.of("north", "south", "east", "west", "up", "down")) {
                    var region = uv.getAsJsonObject(face);
                    var origin = region.getAsJsonArray("uv");
                    var size = region.getAsJsonArray("uv_size");
                    assertTrue(origin.get(0).getAsDouble() >= 0 && origin.get(1).getAsDouble() >= 0);
                    assertTrue(size.get(0).getAsDouble() > 0 && size.get(1).getAsDouble() > 0);
                    assertTrue(origin.get(0).getAsDouble() + size.get(0).getAsDouble() <= width);
                    assertTrue(origin.get(1).getAsDouble() + size.get(1).getAsDouble() <= height);
                }
            }
        }
        var animations = JsonParser.parseString(Files.readString(
                ASSETS.resolve("animations/entity/ink_crane_boss.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        for (var animation : animations.entrySet()) {
            for (String name : animation.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
                assertTrue(names.contains(name), "animation points at missing bone: " + name);
            }
        }
    }

    @Test
    void phantomCopiesKeepBossMeshButUseIndependentGeometryCacheKeys() throws Exception {
        for (String boss : Set.of("ink_crane_boss", "ink_abyss_drake")) {
            String phantom = boss.equals("ink_crane_boss") ? "ink_crane_phantom" : "ink_abyss_drake_phantom";
            var original = JsonParser.parseString(Files.readString(ASSETS.resolve("geo/entity/" + boss + ".geo.json")))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var copy = JsonParser.parseString(Files.readString(ASSETS.resolve("geo/entity/" + phantom + ".geo.json")))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            assertNotEquals(original.getAsJsonObject("description").get("identifier"),
                    copy.getAsJsonObject("description").get("identifier"));
            var a = original.getAsJsonArray("bones");
            var b = copy.getAsJsonArray("bones");
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).getAsJsonObject().get("name"), b.get(i).getAsJsonObject().get("name"));
                assertEquals(a.get(i).getAsJsonObject().get("cubes"), b.get(i).getAsJsonObject().get("cubes"));
            }
        }
    }

    @Test
    void authorExamplesArePreservedButLiveFlightUsesServerOwnedDisplacement() throws Exception {
        var examples = JsonParser.parseString(Files.readString(Path.of(
                "model-source/ink-crane/author-attacks-v4/attack-examples.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        var clips = JsonParser.parseString(Files.readString(ASSETS.resolve(
                "animations/entity/ink_crane_boss.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        assertEquals(examples.get("attack2"), clips.get("author_attack2"));
        assertEquals(examples.get("attack3"), clips.get("author_attack3"));
        assertTrue(clips.has("ink_roar") && clips.has("avatar_sweep") && clips.has("sky_ascent"));
        assertEquals(InkCraneAttackRules.ROAR_TICKS / 20.0,
                clips.getAsJsonObject("ink_roar").get("animation_length").getAsDouble(), .001);
        assertTrue(!clips.getAsJsonObject("sky_ascent").getAsJsonObject("bones").has("root"),
                "visual root translation must not double the real server ascent");
        assertTrue(!clips.getAsJsonObject("spectral_sweep").getAsJsonObject("bones")
                .getAsJsonObject("root").has("position"));
    }
}
