package com.tnc.tnc.magic.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.MagicStoneLearning;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.warrior.WarriorTrailAppearance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 领域法术（2026-11-08 作者：「放在独立法术那里，并且告知玩家只能选择一个，
 * 条件是有亲和力就行」）的闸门。
 *
 * <p>这里固定的是规则，不是手感：
 * <ol>
 *   <li>它们必须挂在「独立」页，且元素为 null（否则会进七元素 NBT 数组 ✗）；</li>
 *   <li>没亲和力学不了、有亲和力就能学、已经选过一个就学不了第二个；</li>
 *   <li>学习成功即「拥有该领域」（{@code specials} 打开亲和力加成的开关）；</li>
 *   <li>JSON 里的冷却 / 效果 id / 读条和 {@link DomainSpells} 的数值表一致；</li>
 *   <li>服务端确实做了「预警 → 落雷/出手」和「战士附伤」这几件事（源码扫描）。</li>
 * </ol>
 */
class DomainSpellTest {

    private static final Path JAR = Path.of("src/main/resources/data/tnc/spells");
    private static final Path DP = Path.of("modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/spells");

    private static SpellCatalog.Entry entry(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tnc", path);
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        throw new AssertionError("目录里没有 " + path + " ✗");
    }

    @Test
    void domainsSitOnTheIndependentPageWithNoElement() {
        for (String path : DomainSpells.PATHS) {
            SpellCatalog.Entry entry = entry(path);
            assertTrue(entry.independent(), path + " 必须是独立法术（不能混进七元素的链）✗");
            assertNull(entry.element(), path + " 的元素必须是 null ✗ —— 否则会进七元素 NBT 数组");
            assertEquals(1, entry.tier(), path + " 是一次性解锁，不该分档 ✗");
        }
        assertEquals(Element.LIGHTNING, DomainSpells.elementOf(
                ResourceLocation.fromNamespaceAndPath("tnc", "lightning_domain")));
        assertEquals(Element.DARK, DomainSpells.elementOf(
                ResourceLocation.fromNamespaceAndPath("tnc", "dark_domain")));
        assertEquals("lightning_domain", DomainSpells.pathOf(Element.LIGHTNING));
        assertEquals("dark_domain", DomainSpells.pathOf(Element.DARK));
        // 2026-11-08 第二轮：另外五系也补齐了（作者「可以尝试做做别的法术领域」）✓
        assertEquals("fire_domain", DomainSpells.pathOf(Element.FIRE));
        assertEquals("wind_domain", DomainSpells.pathOf(Element.WIND));
        assertEquals("water_domain", DomainSpells.pathOf(Element.WATER));
        assertEquals("earth_domain", DomainSpells.pathOf(Element.EARTH));
        assertEquals("light_domain", DomainSpells.pathOf(Element.LIGHT));
    }

    @Test
    void affinityIsTheOnlyRequirementAndYouMayOnlyPickOne() {
        MagicStoneData data = new MagicStoneData();
        // 测试环境没有 Config.pointThresholds（null），所以直接给「冒险点数」这条通路 ✓
        data.setAdventurePoints(60);
        SpellCatalog.Entry storm = entry("lightning_domain");
        SpellCatalog.Entry dark = entry("dark_domain");

        // ① 没亲和力 ⇒ 学不了（「条件是有亲和力就行」的另一面）
        assertEquals(MagicStoneLearning.Result.DOMAIN_NEEDS_AFFINITY,
                MagicStoneLearning.check(data, storm), "没亲和力却让学领域 ✗");

        // ② 有亲和力（哪怕只有 1 级）⇒ 可以学，而且不看链进度 / 等级
        data.setAffinity(Element.LIGHTNING, 1);
        assertEquals(MagicStoneLearning.Result.OK, MagicStoneLearning.check(data, storm),
                "有亲和力却不给学领域 ✗ —— 领域不看等级、不看链进度");
        assertEquals(MagicStoneLearning.Result.OK, MagicStoneLearning.unlock(data, storm));
        assertTrue(data.hasLearned(storm.id()), "学完没记进已学 ✗");
        assertTrue(data.hasSpecial(Element.LIGHTNING),
                "学会领域却没打开该系的 special ✗ —— 亲和力加成就不会生效");

        // ③ 已经选了雷领域 ⇒ 暗领域学不了（作者：「只能选择一个领域法术」）
        data.setAffinity(Element.DARK, 4);
        assertEquals(MagicStoneLearning.Result.DOMAIN_CONFLICT,
                MagicStoneLearning.check(data, dark), "已经有领域了还能再选一个 ✗");
        assertFalse(data.hasSpecial(Element.DARK), "被拒绝的那次不能偷偷把 special 加上 ✗");

        // ④ 想换：先遗忘旧的，就能选新的（不然玩家会被永久锁死 ✗）
        data.revokeSpecial(Element.LIGHTNING);
        assertEquals(MagicStoneLearning.Result.OK, MagicStoneLearning.check(data, dark),
                "遗忘之后应该允许换领域 ✗");
    }

    @Test
    void domainJsonMatchesTheRulesTable() throws Exception {
        // ⚠️ 领域法术只在 jar 里有一份。仓库的数据包镜像里**不能**再放一份：
        //    两个目录会被读成两条同 group / 同档的法术，
        //    SpellBalanceTest 的「同一档里有两个法术」当场就红 ✗（这是对的，重复定义本来就是 bug）。
        Map<String, String> effects = Map.of(
                "lightning_domain", "tnc:domain_lightning",
                "dark_domain", "tnc:domain_dark",
                "fire_domain", "tnc:domain_fire",
                "wind_domain", "tnc:domain_wind",
                "water_domain", "tnc:domain_water",
                "earth_domain", "tnc:domain_earth",
                "light_domain", "tnc:domain_light");
        assertEquals(Element.values().length, effects.size(), "七系必须各有一条领域 ✗");
        for (Map.Entry<String, String> expected : effects.entrySet()) {
            String file = expected.getKey() + ".json";
            assertFalse(Files.exists(DP.resolve(file)),
                    "数据包里重复定义了 " + file + " ✗ —— jar 里已经有一份");
            JsonObject json = JsonParser.parseString(
                    Files.readString(JAR.resolve(file))).getAsJsonObject();
            assertEquals(DomainSpells.COOLDOWN / 20,
                    json.getAsJsonObject("cost").get("cooldown_duration").getAsInt(),
                    file + " 的冷却和数值表不一致 ✗");
            assertEquals(0.0D, json.getAsJsonObject("cost").get("exhaust").getAsDouble(), 1e-9,
                    file + " 不该再吃引擎那套 exhaust ✗");
            assertTrue(json.getAsJsonObject("cast").get("duration").getAsDouble() > 0.0D,
                    file + " 是展开，不该瞬发 ✗");
            assertEquals(DomainSpells.DURATION / 20, effect(json).get("duration").getAsInt(),
                    file + " 的持续时间和数值表不一致 ✗");
            assertEquals(expected.getValue(), effect(json).get("effect_id").getAsString(),
                    file + " 挂错了效果（客户端就看不到状态、刀光也不会变色）✗");
            assertEquals(expected.getKey(), json.get("group").getAsString(),
                    file + " 的 group 必须和 path 一致（平衡表按 group 分链）✗");
        }
    }

    @Test
    void everyElementHasADomainAndNoElementIsLeftOut() {
        assertEquals(Element.values().length, DomainSpells.domains().size(),
                "领域数量必须等于元素数量（七系各一条）✗");
        for (Element element : Element.values()) {
            DomainSpells.Domain domain = DomainSpells.of(element);
            assertNotNull(domain, element.id() + " 还没有领域 ✗");
            assertEquals(element, DomainSpells.elementOf(domain.path()), "元素反查不一致 ✗");
            assertEquals(domain.path(), DomainSpells.pathOf(element), "path 反查不一致 ✗");
            assertNotNull(entry(domain.path()), "目录里没有 " + domain.path() + " ✗");
            // 作者原话「间隔要很久」：4 秒以下就不叫"很久"了 ✓
            assertTrue(domain.interval() >= 80,
                    domain.path() + " 每 " + domain.interval() + " tick 就出手一次，太密 ✗");
            assertTrue(domain.telegraph() > 0, domain.path() + " 没有落点预警 ✗");
            assertTrue(domain.telegraph() < domain.interval(),
                    domain.path() + " 的预警比出手间隔还长 ✗");
            assertTrue(domain.damage() > 0.0F || DomainSpells.KIND_GRIP.equals(domain.kind()),
                    domain.path() + " 既没有伤害也不是控制型 ✗");
        }
        // 七个领域的阵样式不该全都一样（火/水暂时共用通用阵，属于已知待办）✓
        long styles = DomainSpells.domains().stream().map(DomainSpells.Domain::style).distinct().count();
        assertTrue(styles >= 5, "领域的地面预警长得都一模一样，玩家分不出是哪一系 ✗");
    }

    @Test
    void everyDomainHasItsOwnStatusEffect() throws Exception {
        String effects = Files.readString(Path.of("src/main/java/com/tnc/tnc/magic/TNEffects.java"));
        for (String name : new String[]{"domain_lightning", "domain_dark", "domain_fire", "domain_wind",
                "domain_water", "domain_earth", "domain_light"}) {
            assertTrue(effects.contains("\"" + name + "\""), "没注册效果 " + name + " ✗");
        }
        assertTrue(effects.contains("DOMAIN_EFFECTS"),
                "元素 → 领域效果 的查表不见了 ✗（DomainSpells 靠它读状态）");
    }

    private static JsonObject effect(JsonObject spell) {
        return spell.getAsJsonArray("impact").get(0).getAsJsonObject()
                .getAsJsonObject("action").getAsJsonObject("status_effect");
    }

    @Test
    void darknessTurnsTheWarriorTrailBlackAndEveryDomainRepaintsIt() {
        int darkBase = WarriorTrailAppearance.of("dragon_sword", null, "dark").base();
        assertEquals(0x10101B, darkBase, "暗领域的刀光底色必须是近黑（作者：刀光剑影变成黑色）✗");
        int stormCore = WarriorTrailAppearance.of("dragon_sword", null, "lightning").core();
        assertEquals(0xE2C9FF, stormCore, "雷领域的刀光应该是紫电色 ✗");
        // 七系的刀光要能互相认出来（作者：每个领域都改战士的刀光）✓
        var colours = new java.util.HashSet<Integer>();
        for (Element element : Element.values()) {
            int core = WarriorTrailAppearance.of("dragon_sword", null, element.id()).core();
            colours.add(core);
        }
        assertEquals(Element.values().length, colours.size(),
                "有两系的刀光撞色了，玩家看不出领域换了 ✗");
    }

    @Test
    void serviceActuallyDoesWhatTheAuthorAskedFor() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tnc/tnc/magic/domain/DomainService.java"));
        String table = Files.readString(Path.of("src/main/java/com/tnc/tnc/magic/domain/DomainSpells.java"));
        // 雷：落点预警 + 真雷 + 只打「玩家和队友正在打的目标」
        assertTrue(table.contains("11, 14.0F"), "雷霆领域的预警样式/伤害不在数值表里 ✗");
        assertTrue(table.contains("13, 0.0F"), "暗黑领域的预警样式/伤害不在数值表里 ✗");
        assertTrue(source.contains("setVisualOnly(true)"), "雷的视觉没有走 visualOnly（会双重结算伤害）✗");
        assertTrue(source.contains("TARGET_MEMORY"), "没有「刚被打过」的时间窗 ⇒ 会乱劈 ✗");
        assertTrue(source.contains("isAlliedTo"), "没有排除友军 ⇒ 会把队友也劈了 ✗");
        assertTrue(source.contains("sceneryBolt"),
                "没人在打的时候天上就彻底安静了 ✗（作者：周围会时不时遭雷劈）");
        // 暗：黑雾 + 手 + 一直控制
        assertTrue(source.contains("ParticleTypes.SQUID_INK"), "暗领域脚下没有黑雾 ✗");
        assertTrue(source.contains("launchFrom("), "暗领域没有从脚下伸出手 ✗");
        assertTrue(source.contains("MOVEMENT_SLOWDOWN"), "暗领域抓住了却没控制 ✗");
        // 战士附伤
        assertTrue(source.contains("MELEE_BONUS"), "战士攻击没有附带元素伤害 ✗");
        assertTrue(source.contains("indirectMagic"), "附伤没有走法术伤害 ✗");
        // 状态必须挂在效果上（自动同步 + 自动到期）
        String effects = Files.readString(Path.of("src/main/java/com/tnc/tnc/magic/TNEffects.java"));
        assertTrue(effects.contains("DOMAIN_LIGHTNING") && effects.contains("DOMAIN_DARK"),
                "领域状态没有走状态效果 ✗ —— 那样客户端看不到，刀光也不会变色");
    }

    @Test
    void domainsHaveAnAreaNotJustSinglePointHits() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tnc/tnc/magic/domain/DomainService.java"));
        // 作者 2026-11-08：「领域我希望来点范围的那种效果」
        assertTrue(source.contains("AURA_RADIUS"), "领域没有常驻光环半径 ✗ —— 站在领域里跟站外面一样");
        assertTrue(source.contains("aura(player, domain)"), "光环根本没被每 tick 调用 ✗");
        assertTrue(source.contains("AURA_INTERVAL"), "光环没有固定节拍 ✗");
        assertTrue(source.contains("AURA_DAMAGE"), "光环不掉血 ⇒ 还是「点名」不是「范围」✗");
        assertTrue(source.contains("MAX_TARGETS"), "一次只点一个落点 ⇒ 打起来不够「一片」✗");
        assertTrue(source.contains("TARGET_SPACING"), "多个落点会叠成一个圈 ✗");
        // 数值上：光环必须比单次打击更大，单次打击本身也得是"一片"而不是一个点 ✓
        assertTrue(DomainSpells.AURA_RADIUS >= 6.0D, "光环半径太小，罩不住人 ✗");
        assertTrue(DomainSpells.AURA_DAMAGE > 0.0F, "光环伤害必须为正 ✗");
        assertTrue(DomainSpells.AURA_INTERVAL >= 10, "光环跳得太快会变成挂机神器 ✗");
        assertTrue(DomainSpells.MAX_TARGETS >= 2, "至少要点两个落点才算范围 ✗");
        assertTrue(DomainSpells.TARGET_SPACING > 0.0D, "落点间距必须为正 ✗");
        assertTrue(source.contains("invulnerableTime = 0"),
                "领域伤害没清无敌帧 ⇒ 每秒 1 点的光环会把玩家的一刀吞掉 ✗");
        for (DomainSpells.Domain domain : DomainSpells.domains()) {
            assertTrue(domain.radius() >= 3.0D,
                    domain.path() + " 的单次范围只有 " + domain.radius() + " 格，太小 ✗");
            assertTrue(domain.radius() < DomainSpells.AURA_RADIUS,
                    domain.path() + " 的单次范围比光环还大，光环就没意义了 ✗");
        }
    }

    /**
     * 作者 2026-11-08 实际报的 bug：「学领域就报错了」。
     *
     * <p>崩溃发生在<b>客户端渲染</b>时：鼠标划过领域法术 → 悬停提示调
     * {@code MagicStoneScreen.effectSummary} → 那里直接 {@code entry.element().cn()}，
     * 而领域法术的 element 是 null（它们只在「独立」页）⇒ NPE 崩游戏 ✗。
     *
     * <p>这条闸门把「每一句给玩家看的话都不能碰空元素」钉死：
     * 七个 Result 全都在领域法术上跑一遍，任何一句里出现 NPE 或字面 null 都算红 ✓。
     */
    @Test
    void everyLineShownForADomainIsNullSafe() throws Exception {
        MagicStoneData data = new MagicStoneData();
        data.setAdventurePoints(60);
        data.setAffinity(Element.FIRE, 3);
        SpellCatalog.Entry fire = entry("fire_domain");
        for (MagicStoneLearning.Result result : MagicStoneLearning.Result.values()) {
            String text = assertDoesNotThrow(
                    () -> MagicStoneLearning.describe(result, fire, data).getString(),
                    "describe(" + result + ") 在领域法术上崩了 ✗ —— 就是这次崩的那类空元素 NPE");
            assertFalse(text.contains("null"), "describe(" + result + ") 的文本里出现了 null ✗");
        }
        // 真正的崩点是悬停提示这一条
        String screen = Files.readString(Path.of("src/main/java/com/tnc/tnc/client/MagicStoneScreen.java"));
        assertTrue(screen.contains("entry.element() != null"),
                "effectSummary 没有「元素为 null」的分支 ⇒ 鼠标划过领域还会崩 ✗");
        assertTrue(screen.contains("DomainSpells.isDomain(entry.id())"),
                "effectSummary 没认出领域法术，会落进空指针分支 ✗");
        // 名字也不能再说"无元素"（领域是有元素的，只是不进七元素数组）✓
        String name = fire.fullName();
        assertTrue(name.contains("领域魔法"), "领域的名字应该是「领域魔法 · 火」这种 ✗，实际：" + name);
        assertFalse(name.contains("无元素"), "领域的名字还写着「无元素」，玩家会以为它不吃亲和力 ✗：" + name);
    }

    /** 领域法术的悬停说明必须有手写文案（不然玩家只看到「某系·某链」这种没用的兜底）✓。 */
    @Test
    void everyDomainHasTooltipText() throws Exception {
        for (String lang : new String[]{"zh_cn", "en_us"}) {
            String json = Files.readString(Path.of("src/main/resources/assets/tnc/lang/" + lang + ".json"));
            for (String path : DomainSpells.PATHS) {
                String key = "spell.tnc." + path + ".description";
                assertTrue(json.contains(key),
                        lang + " 缺少 " + key + " ✗ —— 玩家看不到这个领域是干什么的");
            }
        }
    }

    @Test
    void onlyOneDomainEffectCanBeOpenAtATime() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tnc/tnc/magic/domain/DomainService.java"));
        assertTrue(source.contains("player.removeEffect(stale)"),
                "开第二个领域时没把第一个关掉 ✗（兜底，正常玩法学不到第二个）");
        assertTrue(source.contains("for (DomainSpells.Domain other : DomainSpells.domains())"),
                "只关了一两个领域，七系会同时挂着 ✗");
    }
}
