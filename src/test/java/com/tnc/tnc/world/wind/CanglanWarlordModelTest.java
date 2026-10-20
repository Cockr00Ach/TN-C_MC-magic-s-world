package com.tnc.tnc.world.wind;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline checks on the generated 苍岚战将 assets.
 *
 * <p>These run without Minecraft: they read the same geo, animation and texture
 * files the game loads and assert the parts that would otherwise only be caught
 * by spawning the boss and watching it swing a sword it is not holding.
 */
class CanglanWarlordModelTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/tnc");
    private static final Gson GSON = new Gson();

    private static JsonObject geo() throws Exception {
        return GSON.fromJson(Files.readString(ROOT.resolve("geo/entity/canglan_warlord.geo.json")),
                JsonObject.class).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static JsonObject clips() throws Exception {
        return GSON.fromJson(Files.readString(
                        ROOT.resolve("animations/entity/canglan_warlord.animation.json")),
                JsonObject.class).getAsJsonObject("animations");
    }

    private static Map<String, JsonObject> bones() throws Exception {
        Map<String, JsonObject> out = new HashMap<>();
        for (var entry : geo().getAsJsonArray("bones")) {
            JsonObject bone = entry.getAsJsonObject();
            out.put(bone.get("name").getAsString(), bone);
        }
        return out;
    }

    @Test
    void everyClipLastsExactlyAsLongAsItsRule() throws Exception {
        JsonObject clips = clips();
        int[] moves = {CanglanWarlordAttackRules.CLEAVE_LEFT, CanglanWarlordAttackRules.CLEAVE_RIGHT,
                CanglanWarlordAttackRules.OVERHEAD, CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SWEEP, CanglanWarlordAttackRules.SPEAR_THROW,
                CanglanWarlordAttackRules.SHIELD_BASH, CanglanWarlordAttackRules.GALE_ROAR};
        for (int move : moves) {
            String clip = CanglanWarlordAttackRules.animationFor(move);
            assertTrue(clips.has(clip), "缺少动画片段 " + clip);
            double seconds = clips.getAsJsonObject(clip).get("animation_length").getAsDouble();
            assertEquals(CanglanWarlordAttackRules.duration(move), Math.round(seconds * 20.0D),
                    clip + " 的时长与 CanglanWarlordAttackRules 不一致");
        }
        for (Map.Entry<String, Integer> special : Map.of(
                "phase_shift", CanglanWarlordAttackRules.PHASE_SHIFT_LENGTH,
                "spawn", CanglanWarlordAttackRules.SPAWN_LENGTH,
                "death", CanglanWarlordAttackRules.DEATH_LENGTH,
                "idle", 80, "walk", 40).entrySet()) {
            double seconds = clips.getAsJsonObject(special.getKey())
                    .get("animation_length").getAsDouble();
            assertEquals(special.getValue().intValue(), Math.round(seconds * 20.0D),
                    special.getKey() + " 的时长不对");
        }
    }

    @Test
    void onlyIdleAndWalkLoop() throws Exception {
        JsonObject clips = clips();
        for (var entry : clips.entrySet()) {
            JsonObject animation = entry.getValue().getAsJsonObject();
            boolean loop = animation.has("loop") && animation.get("loop").getAsBoolean();
            boolean shouldLoop = Set.of("idle", "walk").contains(entry.getKey());
            assertEquals(shouldLoop, loop, entry.getKey() + " 的循环标记不对");
        }
    }

    @Test
    void animationTracksOnlyAddressBonesThatExist() throws Exception {
        Set<String> names = bones().keySet();
        for (var clip : clips().entrySet()) {
            var tracks = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (tracks == null) {
                continue;
            }
            for (String bone : tracks.keySet()) {
                assertTrue(names.contains(bone),
                        clip.getKey() + " 里的轨道指向不存在的骨骼 " + bone);
            }
        }
    }

    @Test
    void theWeaponIsDrawnOnAnEmptyAnchorInsteadOfModelled() throws Exception {
        Map<String, JsonObject> bones = bones();
        JsonObject anchor = bones.get("weapon_1");
        assertNotNull(anchor, "缺少 weapon_1 锚点骨骼");
        assertTrue(!anchor.has("cubes") || anchor.getAsJsonArray("cubes").isEmpty(),
                "weapon_1 必须是空锚点：剑与矛由渲染器绘制");
        assertEquals("hand_1", anchor.get("parent").getAsString(), "锚点必须挂在右手上");
        assertEquals("weapon_1",
                bones.get("weapon_tip_1").get("parent").getAsString(), "矛尖标记必须是锚点的子级");

        var hand = bones.get("hand_1").getAsJsonArray("pivot");
        var seat = anchor.getAsJsonArray("pivot");
        double gap = 0.0D;
        for (int i = 0; i < 3; i++) {
            gap += Math.pow(hand.get(i).getAsDouble() - seat.get(i).getAsDouble(), 2.0D);
        }
        assertTrue(Math.sqrt(gap) <= 4.0D, "锚点离拳头太远，剑会飘在手上方");

        // the old dagger-axe geometry must be gone, or the renderer would double-draw
        for (String retired : new String[]{"haft_a", "haft_b", "blade", "spur", "tassel_a"}) {
            assertFalse(bones.containsKey(retired), "退役的近战兵器骨骼还在：" + retired);
        }
    }

    @Test
    void theWindArsenalFormIndicesMatchTheSharedModels() throws Exception {
        // the renderer indexes WindConstructModels.NAMES with these numbers
        assertTrue(Files.exists(ROOT.resolve("models/wind/arsenal/sword.json")),
                "风系兵器库里没有剑");
        assertTrue(Files.exists(ROOT.resolve("models/wind/arsenal/spear.json")),
                "风系兵器库里没有长矛");
        assertEquals(1, CanglanWarlordAttackRules.FORM_SWORD);
        assertEquals(2, CanglanWarlordAttackRules.FORM_SPEAR);
    }

    @Test
    void theAtlasIsAQuarteredSheet() throws Exception {
        Path texture = ROOT.resolve("textures/entity/canglan_warlord.png");
        assertTrue(Files.exists(texture), "缺少实体贴图");
        try (InputStream in = Files.newInputStream(texture)) {
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, "贴图读不出来");
            assertEquals(256, image.getWidth(), "贴图必须是 256 宽的图集");
            assertEquals(256, image.getHeight(), "贴图必须是 256 高的图集");
        }
    }

    @Test
    void theRigKeepsItsWarriorProportions() throws Exception {
        Map<String, JsonObject> bones = bones();
        assertEquals(50, bones.size(), "骨骼数量变了，检查生成器");
        int cubes = 0;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (JsonObject bone : bones.values()) {
            if (!bone.has("cubes")) {
                continue;
            }
            for (var entry : bone.getAsJsonArray("cubes")) {
                JsonObject cube = entry.getAsJsonObject();
                cubes++;
                var origin = cube.getAsJsonArray("origin");
                var size = cube.getAsJsonArray("size");
                minY = Math.min(minY, origin.get(1).getAsDouble());
                maxY = Math.max(maxY, origin.get(1).getAsDouble() + size.get(1).getAsDouble());
                minX = Math.min(minX, origin.get(0).getAsDouble());
                maxX = Math.max(maxX, origin.get(0).getAsDouble() + size.get(0).getAsDouble());
            }
        }
        assertTrue(cubes >= 400, "方块预算过低：" + cubes);
        // 108 units tall = 6.75 blocks; a little slack for rotated crest tips
        double height = maxY - minY;
        assertTrue(height >= 100.0D && height <= 118.0D,
                "身高 " + (height / 16.0D) + " 格，与设定的 6.75 格不符");
        assertTrue(maxX - minX <= 66.0D, "肩宽超了：" + ((maxX - minX) / 16.0D) + " 格");
        assertTrue(bones.containsKey("eyes"), "面具要有眼睛骨骼才能发光");
        int mirrored = 0;
        for (String name : bones.keySet()) {
            if (name.endsWith("_1") && bones.containsKey(name.substring(0, name.length() - 2) + "_-1")) {
                mirrored++;
            }
        }
        assertTrue(mirrored >= 12, "镜像骨骼太少：" + mirrored);
    }

    @Test
    void bothLanguageFilesNameTheBoss() throws Exception {
        for (String file : new String[]{"zh_cn.json", "en_us.json"}) {
            JsonObject lang = GSON.fromJson(Files.readString(ROOT.resolve("lang/" + file)),
                    JsonObject.class);
            assertTrue(lang.has("entity.tnc.canglan_warlord"), file + " 缺少实体名");
            assertTrue(lang.has("item.tnc.canglan_warlord_spawn_egg"), file + " 缺少刷怪蛋名");
            assertTrue(lang.get("entity.tnc.canglan_warlord").getAsString().length() > 1);
            assertFalse(lang.has("entity.tnc.war_god"), file + " 还留着旧名字");
        }
        JsonObject zh = GSON.fromJson(Files.readString(ROOT.resolve("lang/zh_cn.json")),
                JsonObject.class);
        assertEquals("苍岚战将", zh.get("entity.tnc.canglan_warlord").getAsString());
    }

    @Test
    void theSpearPhaseClipThrowsForward() throws Exception {
        JsonObject clips = clips();
        var bones = clips.getAsJsonObject("spear_throw").getAsJsonObject("bones");
        var wrist = bones.getAsJsonObject("weapon_1").getAsJsonObject("rotation");
        assertTrue(wrist.size() >= 3, "掷矛需要足够的腕部关键帧");
        // the release key sits on the same tick the rules fire the projectile
        double release = CanglanWarlordAttackRules.SPEAR_THROW_HIT / 20.0D;
        assertTrue(wrist.keySet().stream()
                        .anyMatch(key -> Math.abs(Double.parseDouble(key) - release) < 1.0E-6D),
                "掷矛释放帧必须落在 " + release + "s");
    }
}
