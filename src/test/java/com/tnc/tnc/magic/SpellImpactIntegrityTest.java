package com.tnc.tnc.magic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 法术 JSON 的"impact 必须带 action"守卫 ✓。
 *
 * <h2>为什么要钉这一条（2026-10-01 实机事故）</h2>
 * 作者报："天使的悲悯异常放不出来"、"光翼展开按了没反应" ✗。
 * 日志里是引擎的崩栈（作者那次两次 t5、三次 t3 全哑火）：
 * <pre>
 * java.lang.NullPointerException: Cannot read field "type" because "action" is null
 *     at net.spell_engine.internals.SpellHelper.intent(SpellHelper.java:956)
 *     at net.spell_engine.internals.SpellHelper.performImpacts(SpellHelper.java:599)
 *     at net.spell_engine.internals.SpellHelper.performSpell(SpellHelper.java:268)
 * </pre>
 * 根因：那两个 JSON 的 {@code impact} 数组里**多写了一个只有 {@code particles}、没有 {@code action} 的元素** ✗
 * （当时想的是"再来一层烟花粒子"✓）—— 引擎会对**每一个** impact 元素调用
 * {@code intent(action)} 去算目标类型 ✗，{@code action == null} 直接 NPE ⇒
 * 整个 {@code performSpell} 半路炸掉 ⇒ 法术既没生效、也没发 SPELL_CAST（表现为"按了没反应"✗）。
 *
 * <p>所以：<b>纯粒子要并进同一个 impact 的 {@code particles} 列表里</b> ✓（一个 impact 可以有多组粒子 ✓），
 * 不能单开一个没有 action 的元素 ✗。这条测试就替人记着这件事 ✓。
 */
class SpellImpactIntegrityTest {

    /** 目录里所有编了 JSON 的法术：逐个查 impact ✓。 */
    @Test
    void everyImpactCarriesAnAction() throws Exception {
        Gson gson = new Gson();
        List<String> checked = new ArrayList<>();
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            String resource = "/data/tnc/spells/" + entry.id().getPath() + ".json";
            try (var stream = getClass().getResourceAsStream(resource)) {
                if (stream == null) {
                    continue;                       // 还没写 JSON 的骨架法术：跳过 ✓
                }
                JsonObject root = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                        JsonObject.class);
                assertNotNull(root, resource + " 不是合法 JSON");
                JsonArray impacts = root.getAsJsonArray("impact");
                if (impacts == null) {
                    continue;                       // 纯增益/纯自施法可以没有 impact ✓
                }
                int index = 0;
                for (JsonElement element : impacts) {
                    JsonObject impact = element.getAsJsonObject();
                    assertTrue(impact.has("action") && !impact.get("action").isJsonNull(),
                            resource + " 的 impact[" + index + "] 没有 action ✗"
                                    + "（引擎 SpellHelper.intent() 会对它 NPE，法术直接放不出来 ✗）");
                    index++;
                }
                checked.add(entry.id().getPath());
            }
        }
        assertTrue(checked.size() >= 8,
                "只查到 " + checked.size() + " 个法术 JSON（至少该有光系两条链的 8 个）");
    }
}
