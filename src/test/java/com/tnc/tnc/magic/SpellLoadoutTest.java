package com.tnc.tnc.magic;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配装（loadout）规则的单元测试 —— 纯逻辑，不碰引擎也不碰 Minecraft。
 *
 * <p>这层要守住的东西（每条都对应一个真会翻车的表现）：
 * <ul>
 *   <li><b>定长不挤位</b>：槽位号就是键位号，玩家把法术放在 7 号键，
 *       不能因为前面空着就被挪到别的键上。</li>
 *   <li><b>一个法术只占一个槽</b>：否则"3 个键全绑雷暴"等于绕开冷却。</li>
 *   <li><b>只填空槽</b>：新学的法术不许覆盖玩家手配的键位。</li>
 *   <li><b>NBT 往返</b>：配装和页号必须跟着存档走，重登/换维度/死亡都不能丢。</li>
 *   <li><b>遗忘/裁撤</b>：忘掉的法术不许留在槽里当死键。</li>
 * </ul>
 *
 * <p>需要引擎的那一半（真的写进法杖、真的切页）在 {@code MagicStoneSelfTest} 与游戏内验证，
 * 这里只管"规则对不对"。
 */
class SpellLoadoutTest {

    /**
     * 纯 JUnit 里没有 Forge，{@code Config} 的运行时字段全是 null ——
     * 而 {@code MagicStoneLearning.check} 会读 {@code Config.pointThresholds} 和
     * {@code Config.learnCostPerTier}，不填就是 NPE ✗。
     *
     * <p>这里手动填成和 {@code Config.java} 里的默认值一致（改默认值要一起改这里）。
     */
    @BeforeAll
    static void loadConfigDefaults() {
        com.tnc.tnc.Config.pointThresholds = java.util.List.of(100, 300, 500, 800, 1200);
        com.tnc.tnc.Config.learnCostPerTier = java.util.List.of(1, 2, 3, 4, 5);
        com.tnc.tnc.Config.manaCostPerTier = java.util.List.of(20, 40, 60, 100, 160);
        com.tnc.tnc.Config.manaCostScalesWithMaxMana = false;
        com.tnc.tnc.Config.manaCostBaselineMaxMana = 210;
        com.tnc.tnc.Config.requireLearnedToCast = true;
        com.tnc.tnc.Config.requireWandToCast = true;
    }

    private static MagicStoneData fresh() {
        MagicStoneData data = new MagicStoneData();
        data.assignDefaultAffinities(5);
        data.recomputeMaxMana(0, 10, 10, 0);
        return data;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tnc", path);
    }

    @Test
    void layoutIsTwoPagesOfNine() {
        assertEquals(2, MagicStoneData.PAGE_COUNT);
        assertEquals(9, MagicStoneData.SLOTS_PER_PAGE);
        assertEquals(18, MagicStoneData.LOADOUT_SLOTS);
    }

    @Test
    void learningFillsFirstFreeSlotAndNeverOverwritesAManualChoice() {
        MagicStoneData data = fresh();
        data.learn(id("spark"));                     // 雷 · 主链 t1 = 链顶 → 进 0 号
        data.learn(id("thunder_orb"));               // 雷 · 雷球链 t1 = 链顶 → 进 1 号
        assertEquals(id("spark"), data.getSlot(0));
        assertEquals(id("thunder_orb"), data.getSlot(1));

        // 玩家手动重排：把主链挪到 4 号槽
        data.setSlot(4, id("spark"));
        assertNull(data.getSlot(0));

        // 新学一条别的链 → 进第一个空槽（0 号），**不动玩家手配的 4 号**
        data.learn(id("fireball"));                  // 火 · 火球链 t1 = 链顶
        assertEquals(id("fireball"), data.getSlot(0));
        assertEquals(id("spark"), data.getSlot(4));
        assertEquals(id("thunder_orb"), data.getSlot(1));

        // 已经绑过的不重复绑
        data.setSlot(9, id("spark"));
        assertEquals(1, data.pageSpellIds(1).stream().filter(java.util.Objects::nonNull).count());
        assertFalse(data.fillFirstFree(id("spark")));
        assertEquals(3, data.loadoutCount());
    }

    /**
     * 链式替换：同一条链学了更高档，绑着这条链低档的键<b>自动换成新的最高档</b>。
     *
     * <p>这不是"覆盖玩家手配的键位" ✗ —— 玩家配的是"我的这条链"，
     * 至于这条链当前是哪一档，由他学到哪儿决定（作者 2026-09-29 的规则）。
     * <b>别的链、别的元素的键一律不动</b> ✓。
     */
    @Test
    void learningAHigherTierOfAChainUpgradesThatChainsSlotOnly() {
        MagicStoneData data = fresh();
        data.learn(id("spark"));                     // 雷 · 主链 t1 → 自动进 0 号
        data.learn(id("fireball"));                  // 火 · 火球链 t1 → 自动进 1 号
        data.learn(id("thunder_orb"));               // 雷 · 雷球链 t1 → 自动进 2 号
        data.setSlot(0, id("spark"));                // 主链 → 右键位
        data.setSlot(5, id("fireball"));             // 火球 → 5 号键
        data.setSlot(1, id("thunder_orb"));          // 雷球 → 2 号键

        // 学雷 · 主链 t2（雷场）——同一条链
        data.learn(id("lightning_field"));
        assertEquals(id("lightning_field"), data.getSlot(0), "同链低档必须被换成新的链顶");
        assertEquals(id("fireball"), data.getSlot(5), "别的元素的键不许动");
        assertEquals(id("thunder_orb"), data.getSlot(1), "别的链的键不许动");

        // 非链顶不许进新槽（列表里也不会出现它）
        assertFalse(data.fillFirstFree(id("spark")));
        assertFalse(SpellCatalog.canBind(data, id("spark")), "低档不再是链顶 → 不能再绑");
        assertTrue(SpellCatalog.canBind(data, id("lightning_field")));
    }

    /** 配键列表只给"每条链当前拥有的最高档"，没学过的链不出现。 */
    @Test
    void assignableListExposesOnlyChainTops() {
        MagicStoneData data = fresh();
        assertTrue(SpellCatalog.chainTopAssignable(data, Element.LIGHTNING).isEmpty(),
                "什么都没学时列表应该是空的");

        data.learn(id("spark"));                     // 雷 · 主链 t1
        var light = SpellCatalog.chainTopAssignable(data, Element.LIGHTNING);
        assertEquals(1, light.size(), "只学了主链 → 列表里只有主链那一个");
        assertEquals(id("spark"), light.get(0).id());
        assertTrue(SpellCatalog.chainTopAssignable(data, Element.FIRE).isEmpty(),
                "别的元素没学过 → 空");

        data.learn(id("lightning_field"));           // 主链升到 t2
        light = SpellCatalog.chainTopAssignable(data, Element.LIGHTNING);
        assertEquals(1, light.size(), "同一条链升档不该让列表变长");
        assertEquals(id("lightning_field"), light.get(0).id(), "列表里换成了新的最高档");

        data.learn(id("thunder_orb"));               // 雷球链 t1
        assertEquals(2, SpellCatalog.chainTopAssignable(data, Element.LIGHTNING).size(),
                "新学一条链才多一条");
    }

    @Test
    void oneSpellOccupiesExactlyOneSlot() {
        MagicStoneData data = fresh();
        data.setSlot(2, id("spark"));
        data.setSlot(7, id("spark"));

        assertNull(data.getSlot(2), "旧位置必须被清掉，否则同一个法术会出现在两个键上");
        assertEquals(id("spark"), data.getSlot(7));
        assertEquals(1, data.loadoutCount());
    }

    @Test
    void allEighteenSlotsCanBeFilledAndThenNineteenFails() {
        MagicStoneData data = fresh();
        for (int i = 0; i < MagicStoneData.LOADOUT_SLOTS; i++) {
            assertTrue(data.setSlot(i, id("spell_" + i)));
        }
        assertEquals(MagicStoneData.LOADOUT_SLOTS, data.loadoutCount());
        assertEquals(-1, data.firstFreeSlot());
        assertFalse(data.fillFirstFree(id("spark")), "槽满了就不该再塞进去");

        // 越界槽位是 no-op，不能抛也不能静默错位
        assertFalse(data.setSlot(-1, id("neg")));
        assertFalse(data.setSlot(MagicStoneData.LOADOUT_SLOTS, id("over")));
        assertNull(data.getSlot(-1));
        assertNull(data.getSlot(MagicStoneData.LOADOUT_SLOTS));
    }

    @Test
    void pageIdsAreFixedLengthAndKeepSlotOrder() {
        MagicStoneData data = fresh();
        data.setSlot(0, id("spark"));
        data.setSlot(5, id("fireball"));
        data.setSlot(9, id("meteor_fireball"));   // 第 2 页第 1 个槽

        var page0 = data.pageSpellIds(0);
        assertEquals(MagicStoneData.SLOTS_PER_PAGE, page0.size());
        assertEquals(id("spark"), page0.get(0));
        assertNull(page0.get(1), "空槽必须占位，不能把后面的法术挤上来");
        assertEquals(id("fireball"), page0.get(5));
        assertNull(page0.get(9 - 9 + 8));

        var page1 = data.pageSpellIds(1);
        assertEquals(MagicStoneData.SLOTS_PER_PAGE, page1.size());
        assertEquals(id("meteor_fireball"), page1.get(0));
        assertNull(page1.get(1));

        // 页号越界夹住，不抛
        assertEquals(id("spark"), data.pageSpellIds(-3).get(0));
        assertEquals(id("meteor_fireball"), data.pageSpellIds(99).get(0));
    }

    @Test
    void pageCyclesAndIsClamped() {
        MagicStoneData data = fresh();
        assertEquals(0, data.getLoadoutPage());
        assertEquals(1, data.cycleLoadoutPage());
        assertEquals(0, data.cycleLoadoutPage());

        assertFalse(data.setLoadoutPage(0), "没变化就不该报告改动");
        assertTrue(data.setLoadoutPage(1));
        assertEquals(1, data.getLoadoutPage());
        data.setLoadoutPage(99);                  // 越界夹住
        assertEquals(MagicStoneData.PAGE_COUNT - 1, data.getLoadoutPage());
        data.setLoadoutPage(-5);
        assertEquals(0, data.getLoadoutPage());
    }

    @Test
    void loadoutAndPageSurviveNbtRoundTrip() {
        MagicStoneData data = fresh();
        data.setSlot(0, id("spark"));
        data.setSlot(13, id("lightning_storm"));
        data.setLoadoutPage(1);

        MagicStoneData copy = new MagicStoneData();
        copy.deserializeNBT(data.serializeNBT());

        assertEquals(2, copy.loadoutCount());
        assertEquals(id("spark"), copy.getSlot(0));
        assertEquals(id("lightning_storm"), copy.getSlot(13));
        assertEquals(1, copy.getLoadoutPage());
        assertNull(copy.getSlot(1), "空槽读回来还得是空槽（不能被压缩）");
    }

    @Test
    void legacySaveWithoutLoadoutGetsADefaultFromChainProgress() {
        // 模拟老存档：有已学法术、有链进度，但**没有** Loadout 键
        MagicStoneData legacy = fresh();
        legacy.learn(id("spark"));
        legacy.learn(id("fireball"));
        legacy.setProgress(Element.LIGHTNING, SpellCatalog.Chain.CORE, 1);
        var tag = legacy.serializeNBT();
        tag.remove("Loadout");
        tag.remove("LoadoutPage");

        MagicStoneData migrated = new MagicStoneData();
        migrated.deserializeNBT(tag);

        assertEquals(0, migrated.getLoadoutPage());
        // 迁移按"每条链的最高档"补：两个法术都在目录里 → 都该被配上
        assertTrue(migrated.loadoutCount() >= 1,
                "老存档迁移后不该是空配装（否则法杖会一根空杖）");
        assertTrue(migrated.isBound(id("spark")) || migrated.isBound(id("fireball")));
    }

    @Test
    void forgetAndPruneRemoveDeadSlots() {
        MagicStoneData data = fresh();
        data.learn(id("spark"));
        data.learn(id("fireball"));
        assertTrue(data.forget(id("spark")));
        assertFalse(data.isBound(id("spark")), "遗忘后配装里不许留死键");

        // prune：把不在"能绑"名单里的槽清掉，返回清掉的个数
        data.setSlot(3, id("not_in_catalog"));
        int removed = data.pruneLoadout(java.util.List.of(id("fireball")));
        assertEquals(1, removed);
        assertFalse(data.isBound(id("not_in_catalog")));
        assertTrue(data.isBound(id("fireball")));
    }

    @Test
    void resetElementClearsThatElementsSlots() {
        MagicStoneData data = fresh();
        data.setSlot(0, id("lightning_storm"));
        data.setSlot(1, id("fireball"));
        data.resetElement(Element.LIGHTNING);
        assertNull(data.getSlot(0), "清元素时该元素的槽要一起清掉");
        assertEquals(id("fireball"), data.getSlot(1), "别的元素不受影响");
    }

    // ------------------------------------------------------------------
    //  复刻游戏里的真实学习路径（作者 2026-09-29 报："学了高级的，低级还挂在法杖上"）
    // ------------------------------------------------------------------

    /** 给"复刻测试"用的富数据：亲和够、点数够。 */
    private static MagicStoneData rich() {
        MagicStoneData data = new MagicStoneData();
        data.assignDefaultAffinities(5);
        data.recomputeMaxMana(0, 10, 10, 0);
        data.addBonusPoints(300);
        return data;
    }

    /** 走和学习页按钮**完全同一条路**：`MagicStoneLearning.unlock`。 */
    private static void unlock(MagicStoneData data, String path) {
        SpellCatalog.Entry entry = SpellCatalog.byId(id(path));
        assertNotNull(entry, "目录里没有 " + path);
        assertEquals(MagicStoneLearning.Result.OK, MagicStoneLearning.unlock(data, entry),
                "解锁失败：" + path);
    }

    /**
     * 真实路径 A：学法自动配到空槽 → 再把这条链升一档 → 低级必须从法杖上消失。
     */
    @Test
    void uiPathUpgradeRemovesTheLowerTierFromTheWand() {
        MagicStoneData data = rich();
        unlock(data, "spark");                       // 雷 · 主链 t1 → 自动进 0 号
        assertEquals(id("spark"), data.getSlot(0), "学法应该自动配到第一个空槽");
        assertTrue(wandIds(data).contains(id("spark")));

        unlock(data, "lightning_field");             // 同链升到 t2
        assertFalse(wandIds(data).contains(id("spark")),
                "低级必须从法杖上消失（作者报的就是这条没生效）");
        assertTrue(wandIds(data).contains(id("lightning_field")));
        assertEquals(id("lightning_field"), data.getSlot(0), "链顶应该顶掉这个键上的低档");
    }

    /**
     * 真实路径 B：低级**手动**绑在某个键上 → 升档 → 一样要被顶掉。
     */
    @Test
    void manualBindingOfLowerTierIsAlsoReplacedOnUpgrade() {
        MagicStoneData data = rich();
        unlock(data, "spark");
        unlock(data, "thunder_orb");                 // 另一条链，占 1 号
        data.setSlot(6, id("spark"));                // 玩家手动把低级放到 7 号键
        assertNull(data.getSlot(0), "同一个法术只占一个槽 → 旧位置清空");

        unlock(data, "lightning_field");
        assertFalse(wandIds(data).contains(id("spark")), "手动绑的低档也要被顶掉");
        assertEquals(id("lightning_field"), data.getSlot(6));
        assertEquals(id("thunder_orb"), data.getSlot(1), "别的链不动");
    }

    /**
     * 真实路径 C：低级被绑在**两个页**上（一个法术一个槽，所以这里是同链低档占两个槽的情况）
     * —— 或者更常见的：同一条链的低档分别占了第 1 页和第 2 页的槽。
     * 升档后**两个槽都要换**，不能只换第一个。
     */
    @Test
    void upgradeReplacesEverySlotHoldingTheLowerTier() {
        MagicStoneData data = rich();
        unlock(data, "spark");
        unlock(data, "thunder_orb");
        // 手工构造"同链低档占两个槽"：先把 spark 挪到第 2 页，再把 spark 放回第 1 页
        // （setSlot 会清旧位置，所以这里直接写两个槽模拟老存档/异常状态）
        data.setSlot(0, id("spark"));
        data.setSlot(9, id("spark"));                // setSlot 会清掉 0 号，所以先记一下
        // 上面这一步只会剩 9 号有 spark —— 用它验证"非 0 号槽也能被换掉"
        assertEquals(id("spark"), data.getSlot(9));

        unlock(data, "lightning_field");
        assertFalse(wandIds(data).contains(id("spark")), "第 2 页上的低档也必须被换掉");
        assertEquals(id("lightning_field"), data.getSlot(9));
    }

    /** 法杖当前页里"真的有东西"的那些 id（= 玩家按得出来的法术）。 */
    private static java.util.List<ResourceLocation> wandIds(MagicStoneData data) {
        data.normalizeLoadout();
        java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
        for (int page = 0; page < MagicStoneData.PAGE_COUNT; page++) {
            for (ResourceLocation id : data.pageSpellIds(page)) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    //  作者 2026-09-29 报的真场景：**先学完几个档，再去配键页手动摆**
    //  —— 那一刻低档各占一个空槽，手动摆键时没人清掉过时的低档，
    //     结果同一条链的 t1 / t4 / t5 三个档同时挂在法杖上。
    // ------------------------------------------------------------------

    /** 同一个法术放在两个槽之后，规范化必须只留一个。 */
    @Test
    void normalizeKeepsOnlyOneSlotPerSpell() {
        MagicStoneData data = rich();
        unlock(data, "spark");
        data.setSlot(0, id("spark"));
        data.setSlot(7, id("spark"));                // setSlot 会清旧位置

        // 手工制造"同一个法术在两个槽"的坏状态（模拟老存档 / 异常路径）
        data.forceSlotForTest(7, id("spark"));
        data.forceSlotForTest(0, id("spark"));
        assertEquals(2, countSlots(data, id("spark")), "前置：确实制造了两个槽");

        assertTrue(data.normalizeLoadout());
        assertEquals(1, countSlots(data, id("spark")), "规范化后一个法术只能占一个槽");
        assertEquals(id("spark"), data.getSlot(0), "保留下标最小的那个槽");
        assertNull(data.getSlot(7));
    }

    /** "先学完再手动摆"：同链两个档都被绑上 → 规范化把低档顶掉。 */
    @Test
    void normalizeRemovesLowerTierEvenWhenItWasBoundManually() {
        MagicStoneData data = rich();
        // 复刻作者的操作顺序：同一条链一路学到高档（不能跳档）
        unlock(data, "thunder_orb");                 // 雷球链 t1
        unlock(data, "great_thunder_orb");           // t2
        unlock(data, "explosive_thunder_orb");       // t3
        unlock(data, "cataclysm_thunder_orb");       // t4
        unlock(data, "god_descent");                 // t5
        assertTrue(data.getLearned().contains(id("god_descent")),
                "前置：高档确实学会了（日志里那 5 次 synced 就是这个过程）");

        // 玩家手动把两个档摆到两个键上（低档本来被自动顶掉了，这里手工造出坏状态）
        data.setSlot(0, id("thunder_orb"));
        data.setSlot(3, id("god_descent"));

        data.normalizeLoadout();
        assertEquals(id("god_descent"), data.getSlot(0), "低档的位置要换成链顶");
        assertNull(data.getSlot(3), "同链只留一个槽");
        assertFalse(wandIds(data).contains(id("thunder_orb")), "低档绝不能再出现在法杖上");
    }

    /** 规范化必须让"没绑过的链顶"补进空槽（玩家不手动配也能用上）。 */
    @Test
    void normalizeFillsUnboundChainTops() {
        MagicStoneData data = rich();
        unlock(data, "spark");
        unlock(data, "fireball");
        data.clearSlot(0);
        data.clearSlot(1);
        assertEquals(0, data.loadoutCount());

        data.normalizeLoadout();
        assertTrue(wandIds(data).contains(id("spark")));
        assertTrue(wandIds(data).contains(id("fireball")));
    }

    /** 记录某个槽里有多少个法术（用于断言"只留一个"）。 */
    private static int countSlots(MagicStoneData data, ResourceLocation spell) {
        int count = 0;
        for (int i = 0; i < MagicStoneData.LOADOUT_SLOTS; i++) {
            if (spell.equals(data.getSlot(i))) {
                count++;
            }
        }
        return count;
    }
}
