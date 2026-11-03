package com.tnc.tnc.warrior;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 新武器的"接线"回归测试：id、武器线标签、配方、中英文名必须四件套齐全。
 *
 * <p>为什么用资源文件而不是跑注册表：这几样东西出错时的表现是<b>静默</b>的 ——
 * 少一条标签，武器会被当成默认长剑（连招照打，但动作不对）；少一条 lang，物品名显示成
 * {@code item.tnc.xxx}；少一份配方，创造模式里看着有、生存里永远合不出来。
 * 这三种错误在游戏里都不会报错，只会"看起来怪"，所以钉在单元测试里。
 *
 * <p>注册本身（{@code TNMod}）不在这里查：注册需要 Forge 的注册表，单元测试起不来；
 * 注册漏了的表现是物品根本不存在，比这些更容易发现。
 */
class WarriorWeaponRegistryTest {

    /** 新增武器 → 它必须出现的武器线标签。长剑线多一个原版剑，这里只查我们自己这几把。 */
    private static final Map<String, String> TAGS = new LinkedHashMap<>();

    static {
        TAGS.put("dragon_sword", "longswords");
        TAGS.put("frost_katana", "longswords");
        TAGS.put("star_rapier", "longswords");
        TAGS.put("crimson_warblade", "greatswords");
        TAGS.put("doom_scythe", "greatswords");
        TAGS.put("earthshaker_hammer", "hammers");
        TAGS.put("sky_halberd", "spears");
    }

    /**
     * 有工作台配方的武器。`tnc:spear`（风遗物长枪）故意不在里面：它是风系遗物，
     * 只能从创造模式/事件拿到，配方一栏留着空 —— 长枪这条线由 `tnc:sky_halberd` 供生存玩家。
     */
    private static final String[] CRAFTABLE = {
            "greatsword", "hammer", "bow",
            "dragon_sword", "frost_katana", "star_rapier",
            "crimson_warblade", "doom_scythe", "earthshaker_hammer", "sky_halberd"};

    private JsonObject json(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "资源缺失: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private JsonArray values(String path) throws Exception {
        return json(path).getAsJsonArray("values");
    }

    @Test
    void everyWeaponSitsInItsFamilyTag() throws Exception {
        for (var entry : TAGS.entrySet()) {
            String id = entry.getKey();
            String tag = entry.getValue();
            JsonArray values = values("/data/tnc/tags/items/" + tag + ".json");
            boolean found = false;
            for (var value : values) {
                if (("tnc:" + id).equals(value.getAsString())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "tnc:" + id + " 不在 tnc:" + tag + " 里 —— 它会被当成默认长剑");
        }
    }

    @Test
    void everyWeaponIsInfusible() throws Exception {
        JsonArray values = values("/data/tnc/tags/items/infusible_weapons.json");
        for (String id : TAGS.keySet()) {
            boolean found = false;
            for (var value : values) {
                if (("tnc:" + id).equals(value.getAsString())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "tnc:" + id + " 不能灌注风元素");
        }
    }

    @Test
    void everyWeaponHasACraftingRecipe() throws Exception {
        for (String id : TAGS.keySet()) {
            assertTrue(java.util.Arrays.asList(CRAFTABLE).contains(id), id + " 没有进 CRAFTABLE 清单");
        }
        for (String id : CRAFTABLE) {
            JsonObject recipe = json("/data/tnc/recipes/" + id + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), id);
            assertEquals("tnc:" + id, recipe.getAsJsonObject("result").get("item").getAsString(), id);
            assertTrue(recipe.getAsJsonObject("result").get("count").getAsInt() >= 1, id);
            assertEquals("tnc_weapons", recipe.get("group").getAsString(), id + " 的配方面板分组");
            // 图案必须 1~3 行、每行等宽，否则 1.20.1 的配方解析器会静默丢弃这份配方
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            assertEquals(3, pattern.size(), id + " 的图案行数");
            int width = pattern.get(0).getAsString().length();
            for (var row : pattern) {
                assertEquals(width, row.getAsString().length(), id + " 的图案必须等宽");
            }
            assertFalse(recipe.getAsJsonObject("key").entrySet().isEmpty(), id);
        }
    }

    @Test
    void everyWeaponHasNamesInBothLanguages() throws Exception {
        JsonObject zh = json("/assets/tnc/lang/zh_cn.json");
        JsonObject en = json("/assets/tnc/lang/en_us.json");
        for (String id : TAGS.keySet()) {
            String key = "item.tnc." + id;
            assertTrue(zh.has(key), "中文名缺失: " + key);
            assertTrue(en.has(key), "英文名缺失: " + key);
            assertFalse(zh.get(key).getAsString().isBlank(), key);
            assertFalse(en.get(key).getAsString().isBlank(), key);
            // 没翻译的占位符（直接抄 id）也算缺失
            assertNotEquals(id, zh.get(key).getAsString(), key);
        }
    }

    /**
     * 提示框的文案：缺一条，玩家看到的就是 {@code tooltip.tnc.weapon.greatsword} 这种原始键名。
     * 家族名 + 单双手 + 键位，一共九条，中英都要有。
     */
    @Test
    void weaponTooltipTextsExistInBothLanguages() throws Exception {
        JsonObject zh = json("/assets/tnc/lang/zh_cn.json");
        JsonObject en = json("/assets/tnc/lang/en_us.json");
        String[] keys = {
                "longsword", "greatsword", "hammer", "spear", "bow",
                "one_handed", "two_handed", "ranged", "keys"};
        for (String name : keys) {
            String key = "tooltip.tnc.weapon." + name;
            assertTrue(zh.has(key), "中文提示缺失: " + key);
            assertTrue(en.has(key), "英文提示缺失: " + key);
            assertFalse(zh.get(key).getAsString().isBlank(), key);
            assertFalse(en.get(key).getAsString().isBlank(), key);
        }
    }
}
