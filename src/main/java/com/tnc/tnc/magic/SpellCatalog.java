package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * TN-C 法术目录（魔法石界面里"可解锁的法术"从哪来）。
 *
 * <p>现在是<b>硬编码</b>的一张表，和 `kubejs/data/tnc/spells/*.json` 里的法术一一对应。
 * 等界面稳定了再改成数据驱动（读 SpellEngine 的法术注册表）。
 *
 * <h2>链（Chain）</h2>
 * 同一个元素下可以有<b>多条互相独立的链</b>，每条链 5 级。规则：
 * <ul>
 *   <li>每条链各自算进度：想学某条链的 2 级，必须先学<b>同一条链</b>的 1 级
 *       （三条链互不影响，不是"整个元素一条进度"）。</li>
 *   <li><b>高阶替换低阶</b>：法杖上每条链只挂"已经解锁的最高那一级"。
 *       学了「大雷球」以后，法杖上就只有大雷球，不再有「雷球」——
 *       见 {@link #effective(MagicStoneData)}。</li>
 * </ul>
 * 这个替换规则顺带解决了热键栏容量问题：三条链满级时法杖上只有 3 个法术。
 */
public final class SpellCatalog {

    /** 一条链。同一元素下的链彼此独立（各自的 1..5 级）。 */
    public enum Chain {

        /** 基础雷法（最初那五个：小闪电 → 天打五雷轰）。 */
        CORE("主链", "基础雷法"),

        /** 雷球线：把雷电做成会飞的球，一级比一级大。 */
        ORB("雷球", "会飞出去的雷球"),

        /** 雷速线：加速、位移、以及"以雷的速度"带来的各种强化。 */
        SPEED("雷速", "加速与位移"),

        /** 火射线线：从一条穿透射线，到三条齐发，再到命中就炸。 */
        RAY("火射线", "穿透的火焰射线"),

        /** 火球线：从一颗火球，到砸地范围伤害，到自爆与陨石。 */
        BALL("火球", "会飞的火球"),

        /** 燃烧线：拿血量换伤害，越烧越强，烧到尽头能原地复活。 */
        BURN("燃烧", "以血换伤"),

        /** 风速线：从短时起飞，到 4 级起解锁即永久飞行。 */
        FLIGHT("风速", "起飞与加速"),

        /** 风球线：召唤会自己打人的风球，再召唤能召唤风球的风灵。 */
        SUMMON("风球", "召唤物"),

        /** 范围风线：大范围减速敌人/加速队友，并甩出风刃。 */
        GALE("范围风", "大范围风场与风刃");

        private final String cn;
        private final String desc;

        Chain(String cn, String desc) {
            this.cn = cn;
            this.desc = desc;
        }

        /** 中文短名（界面里当分组标题）。 */
        public String cn() {
            return cn;
        }

        /** 一句话说明（提示/文档用）。 */
        public String desc() {
            return desc;
        }
    }

    /** 一个可解锁法术。 */
    public record Entry(ResourceLocation id, Element element, Chain chain, int tier, String displayName) {

        /** 界面/提示里显示的完整名字，例如「雷击（雷 · 主链 · 专家级）」。 */
        public String fullName() {
            return displayName + "（" + element.cn() + " · " + chain.cn() + " · " + Element.tierName(tier) + "）";
        }

        /** 施放一次消耗多少魔力（消耗表里的原始值，= 基准上限下的消耗）。 */
        public int manaCost() {
            return com.tnc.tnc.Config.manaCostForTier(tier);
        }

        /**
         * 这一级法术在<b>某个魔力上限</b>下实际要花多少魔力。
         *
         * <p>消耗随上限等比放大（见 {@link com.tnc.tnc.Config#manaCostForTier(int, int)}）——
         * 判定、扣费、界面显示都必须走这一个口径，不然会出现
         * "提示说 20、实际扣 59" 这种对不上的情况。
         */
        public int manaCostFor(int maxMana) {
            return com.tnc.tnc.Config.manaCostForTier(tier, maxMana);
        }

        /** 解锁要投多少魔法点数。 */
        public int learnCost() {
            return com.tnc.tnc.Config.learnCostForTier(tier);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            // ---- 主链：最初那五个 ----
            entry("spark", Chain.CORE, 1, "小闪电"),
            entry("lightning_field", Chain.CORE, 2, "雷场"),
            entry("lightning_strike", Chain.CORE, 3, "雷击"),
            entry("lightning_storm", Chain.CORE, 4, "雷暴"),
            entry("heavenly_thunder", Chain.CORE, 5, "天打五雷轰"),

            // ---- 雷球线：把雷电做成会飞的球，一级比一级大 ----
            entry("thunder_orb", Chain.ORB, 1, "雷球"),
            entry("great_thunder_orb", Chain.ORB, 2, "大雷球"),
            entry("orbiting_thunder_orb", Chain.ORB, 3, "环绕雷球"),
            entry("explosive_thunder_orb", Chain.ORB, 4, "爆炸雷球"),
            entry("cataclysm_thunder_orb", Chain.ORB, 5, "天降超级无敌大雷球"),

            // ---- 雷速线：加速、位移、以及"以雷的速度"带来的强化 ----
            entry("lightning_haste", Chain.SPEED, 1, "雷速"),
            entry("lightning_blink", Chain.SPEED, 2, "闪电移位"),
            entry("lightning_wind", Chain.SPEED, 3, "极速雷风"),
            entry("lightning_recharge", Chain.SPEED, 4, "闪电降低冷却"),
            entry("lightning_ascension", Chain.SPEED, 5, "闪电登神"),

            // ---- 火射线线：1 穿透 → 2 更粗 → 3 三向齐发 → 4 命中爆炸 → 5 巨大爆炸 ----
            fireEntry("fire_ray", Chain.RAY, 1, "火射线"),
            fireEntry("thick_fire_ray", Chain.RAY, 2, "粗火射线"),
            fireEntry("triple_fire_ray", Chain.RAY, 3, "三条火射线"),
            fireEntry("explosive_fire_ray", Chain.RAY, 4, "爆炸射线"),
            fireEntry("cataclysm_fire_ray", Chain.RAY, 5, "巨大爆炸射线"),

            // ---- 火球线：1 火球 → 2 大火球 → 3 巨大火球(砸地) → 4 自爆 → 5 天降陨石 ----
            fireEntry("fireball", Chain.BALL, 1, "火球"),
            fireEntry("great_fireball", Chain.BALL, 2, "大火球"),
            fireEntry("giant_fireball", Chain.BALL, 3, "巨大火球"),
            fireEntry("self_destruct", Chain.BALL, 4, "自爆"),
            fireEntry("meteor_fireball", Chain.BALL, 5, "天降陨石火球"),

            // ---- 燃烧线：1 附着(+10%) → 2 初级(+25%) → 3 中级(+75%,15s复活)
            //              → 4 高级(+150%,20s复活) → 5 完全燃烧(血1+无敌15s+200%) ----
            fireEntry("fire_aspect", Chain.BURN, 1, "火附着"),
            fireEntry("ember_burn", Chain.BURN, 2, "初级燃烧"),
            fireEntry("blaze_burn", Chain.BURN, 3, "中级燃烧"),
            fireEntry("inferno_burn", Chain.BURN, 4, "高级燃烧"),
            fireEntry("total_burn", Chain.BURN, 5, "完全燃烧"),

            // ---- 风速线：1 起飞5s → 2 起飞5s+速100% → 3 起飞15s+速150%+风伤50%
            //              → 4 永久起飞+速300%30s+风伤100% → 5 再加无冷却+蓝耗减半 ----
            windEntry("wind_field", Chain.FLIGHT, 1, "风场"),
            windEntry("wind_speed", Chain.FLIGHT, 2, "风速"),
            windEntry("greater_wind_speed", Chain.FLIGHT, 3, "顶级风速"),
            windEntry("super_wind_speed", Chain.FLIGHT, 4, "超级风速"),
            windEntry("wind_god_descent", Chain.FLIGHT, 5, "风神降临"),

            // ---- 风球线：1 三个风球 → 2 五个风球 → 3 风灵 → 4 三个风灵
            //              → 5 用户还没定，先占位（名字/效果待确认） ----
            windEntry("wind_orb", Chain.SUMMON, 1, "召唤风球"),
            windEntry("wind_orb_swarm", Chain.SUMMON, 2, "召唤五个风球"),
            windEntry("wind_spirit", Chain.SUMMON, 3, "召唤风灵"),
            windEntry("triple_wind_spirit", Chain.SUMMON, 4, "召唤三个风灵"),
            windEntry("wind_spirit_lord", Chain.SUMMON, 5, "风灵之主"),

            // ---- 范围风线：1 范围风+小风刃 → 2 大范围20s+中风刃 → 3 超大30s+大风刃
            //              → 4 超大30s+巨型风刃 → 5 三个巨型风刃+无视防御 ----
            windEntry("gale", Chain.GALE, 1, "范围风"),
            windEntry("great_gale", Chain.GALE, 2, "大范围风"),
            windEntry("vast_gale", Chain.GALE, 3, "超大范围风"),
            windEntry("giant_gale", Chain.GALE, 4, "巨型风刃风暴"),
            windEntry("wind_god_gale", Chain.GALE, 5, "风神风暴")
    );

    private SpellCatalog() {
    }

    private static Entry entry(String path, Chain chain, int tier, String name) {
        return new Entry(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), Element.LIGHTNING, chain, tier, name);
    }

    /** 火系记录（同一个 Entry，只是元素不同）。 */
    private static Entry fireEntry(String path, Chain chain, int tier, String name) {
        return new Entry(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), Element.FIRE, chain, tier, name);
    }

    /** 风系记录。 */
    private static Entry windEntry(String path, Chain chain, int tier, String name) {
        return new Entry(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), Element.WIND, chain, tier, name);
    }

    /** 目录里的全部法术。 */
    public static List<Entry> all() {
        return ENTRIES;
    }

    /** 按元素筛选。 */
    public static List<Entry> of(Element element) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.element() == element) {
                result.add(entry);
            }
        }
        return result;
    }

    /** 某条链上的全部法术，按等级升序。 */
    public static List<Entry> of(Element element, Chain chain) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.element() == element && entry.chain() == chain) {
                result.add(entry);
            }
        }
        return result;
    }

    /** 某个元素下有哪些链（有法术的才算）。 */
    public static List<Chain> chainsOf(Element element) {
        List<Chain> result = new ArrayList<>();
        for (Chain chain : Chain.values()) {
            if (!of(element, chain).isEmpty()) {
                result.add(chain);
            }
        }
        return result;
    }

    /** 目录里的最高等级（界面/自检用）。 */
    public static int maxTier() {
        int max = 0;
        for (Entry entry : ENTRIES) {
            max = Math.max(max, entry.tier());
        }
        return max;
    }

    public static Entry byId(ResourceLocation id) {
        for (Entry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  "高阶替换低阶"
    // ------------------------------------------------------------------

    /**
     * 某条链里<b>当前该挂在法杖上</b>的那一个 = 这条链里已经解锁的最高级。
     *
     * @return 一个都没学就返回 {@code null}
     */
    public static Entry topLearned(MagicStoneData data, Element element, Chain chain) {
        Entry top = null;
        for (Entry entry : of(element, chain)) {
            if (data.hasLearned(entry.id()) && (top == null || entry.tier() > top.tier())) {
                top = entry;
            }
        }
        return top;
    }

    /**
     * 法杖的<b>实际内容</b>：每条链只取已解锁的最高级（高阶替换低阶）。
     *
     * <p>这就是为什么法杖不需要装下 15 个法术 —— 三条链满级时它上面只有 3 个。
     * 学过的低级法术仍然记在魔法石里（换链/回退都能用），只是不再占法杖的格子。
     */
    public static List<Entry> effective(MagicStoneData data) {
        List<Entry> result = new ArrayList<>();
        // 遍历"元素 x 链"：写死 LIGHTNING 的话，加了火系以后法杖永远拿不到火法术
        for (Element element : Element.values()) {
            for (Chain chain : chainsOf(element)) {
                Entry top = topLearned(data, element, chain);
                if (top != null) {
                    result.add(top);
                }
            }
        }
        return result;
    }

    /** {@link #effective(MagicStoneData)} 的 id 版本（喂给法杖同步用）。 */
    public static List<ResourceLocation> effectiveIds(MagicStoneData data) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Entry entry : effective(data)) {
            ids.add(entry.id());
        }
        return ids;
    }
}
