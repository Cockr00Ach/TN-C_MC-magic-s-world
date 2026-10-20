package com.tnc.tnc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 17 只地下城小怪的**离线自检**（不用进游戏 ✓）。
 *
 * <p>这些错进游戏才炸，而且报错信息很难对回"是哪只怪的哪个文件"：
 * <ul>
 *   <li>geoTick 里写了 geo 不存在的骨头 → GeckoLib **不报错**，只是那条肢体永远不动；</li>
 *   <li>贴图/动画文件缺一个 → 实体变成紫黑格，或者干脆隐形；</li>
 *   <li>刷怪表里 {@code tnc:xxx} 拼错 → 地城里那一档权重永远刷不出来，日志只有一行 warning。</li>
 * </ul>
 *
 * <p>所以这里把"枚举 → 三个资源文件 → 动画片段 → 刷怪表引用"这条链整根验一遍。
 * 纯读文件，不加载 Minecraft 注册表，所以跑得快、能在 CI 上跑 ✓。
 */
class DungeonMobAssetsTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "tnc");
    private static final Path MOB_LIST = Path.of("modpack", "元素觉醒1.4.3-魔改版-20260915",
            "config", "openloader", "data", "yuansujuexing", "data", "library_of_exile",
            "library_of_exile_mob_list");

    private static final Set<String> REQUIRED_CLIPS =
            Set.of("idle", "walk", "attack", "special", "death");

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void everySpeciesHasGeoAnimationAndTexture() {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            Path geo = ASSETS.resolve("geo/entity/" + species.id + ".geo.json");
            Path anim = ASSETS.resolve("animations/entity/" + species.id + ".animation.json");
            Path tex = ASSETS.resolve("textures/entity/" + species.id + "_bedrock.png");
            assertTrue(Files.isRegularFile(geo), "缺 geo: " + geo);
            assertTrue(Files.isRegularFile(anim), "缺动画: " + anim);
            assertTrue(Files.isRegularFile(tex), "缺贴图: " + tex);
        }
    }

    @Test
    void everyClipAnimatesBonesThatExistInTheGeo() throws IOException {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            JsonObject geo = readJson(ASSETS.resolve("geo/entity/" + species.id + ".geo.json"));
            Set<String> bones = new HashSet<>();
            JsonArray geoBones = geo.getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject().getAsJsonArray("bones");
            for (int i = 0; i < geoBones.size(); i++) {
                bones.add(geoBones.get(i).getAsJsonObject().get("name").getAsString());
            }

            JsonObject anim = readJson(ASSETS.resolve("animations/entity/" + species.id
                    + ".animation.json"));
            JsonObject clips = anim.getAsJsonObject("animations");
            for (String clipName : clips.keySet()) {
                JsonObject bonesOfClip = clips.getAsJsonObject(clipName).getAsJsonObject("bones");
                for (String bone : bonesOfClip.keySet()) {
                    assertTrue(bones.contains(bone),
                            species.id + " 的 " + clipName + " 动了不存在的骨头: " + bone);
                }
            }
        }
    }

    @Test
    void everySpeciesShipsTheFiveClips() throws IOException {
        for (DungeonSpecies species : DungeonSpecies.values()) {
            JsonObject anim = readJson(ASSETS.resolve("animations/entity/" + species.id
                    + ".animation.json"));
            Set<String> clips = anim.getAsJsonObject("animations").keySet();
            for (String required : REQUIRED_CLIPS) {
                assertTrue(clips.contains(required),
                        species.id + " 缺动画片段 " + required + "（有: " + clips + "）");
            }
        }
    }

    @Test
    void speciesIdsAreUniqueAndStatsAreSane() {
        Set<String> ids = new HashSet<>();
        for (DungeonSpecies species : DungeonSpecies.values()) {
            assertTrue(ids.add(species.id), "物种 id 重复: " + species.id);
            assertTrue(species.health > 0.0D, species.id + " 血量必须为正");
            assertTrue(species.attackDamage >= 0.0D, species.id + " 伤害不能为负");
            assertTrue(species.speed > 0.0D, species.id + " 速度必须为正");
            assertTrue(species.width > 0.0F && species.height > 0.0F, species.id + " 体型必须为正");
            assertNotNull(species.kind, species.id + " 必须有攻击方式");
            assertTrue(species.kind.reach > 0.0D, species.id + " 起手距离必须为正");
            // 前摇是"能被躲开"的前提：太短就等于没提示，太长就是白送反打
            assertTrue(species.kind.windup >= 8 && species.kind.windup <= 40,
                    species.id + " 前摇 " + species.kind.windup + " tick 不在 8~40 的合理区间");
        }
        assertEquals(17, DungeonSpecies.values().length, "地下城小怪应为 17 只");
    }

    @Test
    void everyAttackKindIsUsedByAtLeastOneSpecies() {
        Set<DungeonSpecies.Kind> used = new HashSet<>();
        for (DungeonSpecies species : DungeonSpecies.values()) {
            used.add(species.kind);
        }
        for (DungeonSpecies.Kind kind : DungeonSpecies.Kind.values()) {
            assertTrue(used.contains(kind), "攻击方式 " + kind + " 没有任何怪在用（是漏配了？）");
        }
    }

    @Test
    void dungeonSpawnListsOnlyReferenceSpeciesWeShip() throws IOException {
        if (!Files.isDirectory(MOB_LIST)) {
            return; // 只在本仓的整合包目录里跑；换台机器没有就静默跳过
        }
        List<String> unknown = new ArrayList<>();
        try (var stream = Files.list(MOB_LIST)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject list = readJson(file);
                JsonArray mobs = list.getAsJsonArray("mobs");
                if (mobs == null) {
                    continue;
                }
                for (int i = 0; i < mobs.size(); i++) {
                    String id = mobs.get(i).getAsJsonObject().get("mob_id").getAsString();
                    if (!id.startsWith("tnc:")) {
                        continue;
                    }
                    String bare = id.substring("tnc:".length());
                    if (DungeonSpecies.byId(bare) == null) {
                        unknown.add(file.getFileName() + " -> " + id);
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), "刷怪表引用了不存在的 tnc 实体: " + unknown);
    }
}
