package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 法术目录（{@link SpellCatalog}）的结构测试 —— 纯逻辑，不碰引擎。
 *
 * <h2>为什么值得单独测</h2>
 * 2026-10-01 发现的真事故：光系的 5 个法术（`light_radiance` … `angel_mercy`）**引擎侧全做好了**
 * —— 法术 JSON、法术池、法杖装配、Java 机制（法阵 / 治疗 / 减伤 / 天使）、图标，一样不缺 ✗
 * —— 但 {@link SpellCatalog} 里**没有它们的条目**，于是：
 * <ul>
 *   <li>魔法石界面是<b>按目录画</b>的 ⇒ "光"连元素按钮都不出现（界面只列"有链的元素"）✗</li>
 *   <li>目录与引擎不通报 ⇒ 一条"我做完了但玩家看不见"的静默失效路径 ✗</li>
 * </ul>
 * 所以这里钉住几条结构不变量，任何一条坏了当场变红 ✓。
 *
 * <p><b>注意</b>：本测试只管"目录结构"，不管"引擎认不认" ——
 * 后者只有装了 SpellEngine 才能问（{@code SpellEngineBridge.hasSpell}），
 * 在游戏里由 {@code MagicStoneLearning} 的防呆闸门 + {@code /tnc selftest} 覆盖 ✓。
 */
class SpellCatalogStructureTest {

    /**
     * "不满档"的链（作者规格就是这么多档）✓ —— 记在这里，免得后来人以为是漏做 ✗。
     *
     * <p>目前只有一条：**光系「光翼」链**（飞行 → 极速飞行 → 光翼展开，2026-10-01 作者指定，
     * 见 {@code docs/光系链_设计.md} 第一节："链结构（3 档，均为自身增益）"✓）。
     * 加进来的时候**必须写清依据** ✗ —— 这条表就是"故意不满档"的白名单 ✓。
     */
    private static final Map<String, Integer> EXPECTED_TIER_COUNT = Map.of(
            "光 · 光翼", 3
    );

    @Test
    void everyChainHasExactlyOneSpellPerTier() {
        int max = SpellCatalog.maxTier();
        for (Element element : Element.values()) {
            for (SpellCatalog.Chain chain : SpellCatalog.chainsOf(element)) {
                List<SpellCatalog.Entry> entries = SpellCatalog.of(element, chain);
                String key = element.cn() + " · " + chain.cn();
                int expected = EXPECTED_TIER_COUNT.getOrDefault(key, max);
                assertEquals(expected, entries.size(),
                        key + " 这条链不是 " + expected + " 档");
                for (int tier = 1; tier <= expected; tier++) {
                    int wanted = tier;
                    assertTrue(entries.stream().anyMatch(e -> e.tier() == wanted),
                            key + " 缺第 " + tier + " 档");
                }
                // 同一条链里不许有两个同档（会让"边学边进"的判定算错）
                assertEquals(entries.size(),
                        entries.stream().map(SpellCatalog.Entry::tier).distinct().count(),
                        key + " 有重复档位");
            }
        }
    }

    @Test
    void spellIdsAreUnique() {
        List<SpellCatalog.Entry> all = SpellCatalog.all();
        long distinct = all.stream().map(SpellCatalog.Entry::id).distinct().count();
        assertEquals(all.size(), distinct, "目录里有重复的法术 id（后一个会覆盖前一个）");
    }

    /**
     * 光系（作者 2026-10-01 要在魔法石里能看见）：
     * 光耀链 5 档，且中文名与 `light/TNLightChainMechanics` 的数值表逐字一致 ✓。
     */
    @Test
    void lightGraceChainIsPresentWithTheDocumentedNames() {
        List<SpellCatalog.Chain> chains = SpellCatalog.chainsOf(Element.LIGHT);
        assertTrue(chains.contains(SpellCatalog.Chain.LIGHT_GRACE),
                "光系必须有「光耀」链（否则魔法石界面不显示光元素）");

        Map<Integer, String> expected = new java.util.LinkedHashMap<>();
        expected.put(1, "光芒照耀");
        expected.put(2, "圣光");
        expected.put(3, "神光");
        expected.put(4, "天使降临");
        expected.put(5, "天使的悲悯");

        for (var want : expected.entrySet()) {
            SpellCatalog.Entry entry = SpellCatalog.of(Element.LIGHT, SpellCatalog.Chain.LIGHT_GRACE).stream()
                    .filter(e -> e.tier() == want.getKey()).findFirst().orElse(null);
            assertNotNull(entry, "光耀链缺第 " + want.getKey() + " 档");
            assertEquals(want.getValue(), entry.displayName(),
                    "第 " + want.getKey() + " 档的名字要和 TNLightChainMechanics 的数值表一致");
        }

        // 光耀链的 id 顺序（和 spell_pools/tnc_light.json、magic_wand.json 里的写法一致）
        List<String> ids = SpellCatalog.of(Element.LIGHT, SpellCatalog.Chain.LIGHT_GRACE).stream()
                .map(e -> e.id().getPath()).toList();
        assertEquals(List.of("light_radiance", "holy_light", "divine_light", "angel_descent", "angel_mercy"), ids);
    }

    /** 每个元素都有自己的链（除了还没做内容的）—— 光系必须不再是"零条链"。 */
    @Test
    void everyElementExceptNoneHasAtLeastOneChain() {
        Map<Element, Integer> counts = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            counts.put(element, SpellCatalog.chainsOf(element).size());
        }
        // 七个元素都应该有内容：这是"七系"这个设定的下限
        for (Element element : Element.values()) {
            assertTrue(counts.get(element) >= 1,
                    element.cn() + " 系一条链都没有 —— 魔法石界面会整个跳过它（光系刚踩过这个坑）");
        }
    }
}
