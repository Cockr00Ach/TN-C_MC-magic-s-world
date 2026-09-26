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
        GALE("范围风", "大范围风场与风刃"),

        // ---- 水魔法（文档：全能 —— 控制 / 输出 / 防御 / 回血）----
        WATER_BALL("水球", "凝聚水元素射出的水球"),
        WATER_WAVE("水纹", "向前扩散的水波"),
        WATER_BIND("水缚", "用水流锁住目标"),
        WATER_RAIN("雨滴", "从天空落下的水滴"),

        // ---- 土魔法（文档：防御与控制为核心）----
        EARTH_SHOT("土弹", "泥土与岩石形成的弹丸"),
        EARTH_MOVE("土动", "操纵脚下土地"),
        EARTH_MUD("土泥", "把地面变成泥沼"),
        EARTH_RING("土环", "环形土墙保护自己"),

        // ---- 暗系（文档：以离奇手段压倒对手）----
        DARK_HAND("黑夜之手", "暗影之手抓向目标"),
        DARK_SACRIFICE("以伤换伤", "以生命力换力量"),
        DARK_SUMMON("召唤", "从黑暗中召唤暗属性生物"),
        DARK_FOG("黑雾", "弥漫的黑色雾气");

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
            // 2026-09-22 作者调序：爆炸雷球降到 t3、环绕雷球升到 t4（环绕的球就是爆炸雷球 ✓）
            entry("explosive_thunder_orb", Chain.ORB, 3, "爆炸雷球"),
            entry("orbiting_thunder_orb", Chain.ORB, 4, "环绕雷球"),
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
            windEntry("wind_god_gale", Chain.GALE, 5, "风神风暴"),

            // ================= 骨架（法术 JSON 待补，先占名字对齐文档）=================
            // 水魔法：水球 / 水纹 / 水缚 / 雨滴
            of(Element.WATER, "water_ball", Chain.WATER_BALL, 1, "水球"),
            of(Element.WATER, "water_cannon", Chain.WATER_BALL, 2, "水炮弹"),
            of(Element.WATER, "dragon_roar", Chain.WATER_BALL, 3, "龙王吼"),
            of(Element.WATER, "dragon_howl", Chain.WATER_BALL, 4, "龙啸"),
            of(Element.WATER, "dragon_ruin", Chain.WATER_BALL, 5, "龙滅"),
            of(Element.WATER, "water_ripple", Chain.WATER_WAVE, 1, "水纹"),
            of(Element.WATER, "water_wave", Chain.WATER_WAVE, 2, "水波"),
            of(Element.WATER, "wave_slash", Chain.WATER_WAVE, 3, "海浪斩击"),
            of(Element.WATER, "tsunami", Chain.WATER_WAVE, 4, "海啸"),
            of(Element.WATER, "world_ending_sea", Chain.WATER_WAVE, 5, "灭世之海"),
            of(Element.WATER, "water_bind", Chain.WATER_BIND, 1, "水缚"),
            of(Element.WATER, "water_prison", Chain.WATER_BIND, 2, "水牢"),
            of(Element.WATER, "water_burial", Chain.WATER_BIND, 3, "水葬"),
            of(Element.WATER, "abyss", Chain.WATER_BIND, 4, "深渊"),
            of(Element.WATER, "sea_god_crypt", Chain.WATER_BIND, 5, "水神的冥穴"),
            of(Element.WATER, "raindrop", Chain.WATER_RAIN, 1, "雨滴"),
            of(Element.WATER, "first_rain", Chain.WATER_RAIN, 2, "初雨"),
            of(Element.WATER, "rainfall", Chain.WATER_RAIN, 3, "雨落"),
            of(Element.WATER, "downpour", Chain.WATER_RAIN, 4, "暴雨落"),
            of(Element.WATER, "flood_of_heaven", Chain.WATER_RAIN, 5, "天洪"),

            // 土魔法：土弹 / 土动 / 土泥 / 土环
            of(Element.EARTH, "earth_shot", Chain.EARTH_SHOT, 1, "土弹"),
            of(Element.EARTH, "earth_ball", Chain.EARTH_SHOT, 2, "土球"),
            of(Element.EARTH, "rock_cannon", Chain.EARTH_SHOT, 3, "岩炮弹"),
            of(Element.EARTH, "ten_thousand_rocks", Chain.EARTH_SHOT, 4, "万岩穿"),
            of(Element.EARTH, "earth_god_spear", Chain.EARTH_SHOT, 5, "土神的枪"),
            of(Element.EARTH, "earth_stir", Chain.EARTH_MOVE, 1, "土动"),
            of(Element.EARTH, "earth_spike", Chain.EARTH_MOVE, 2, "土刺"),
            of(Element.EARTH, "rock_burst", Chain.EARTH_MOVE, 3, "岩突"),
            of(Element.EARTH, "earth_rift", Chain.EARTH_MOVE, 4, "大地之裂"),
            of(Element.EARTH, "star_quake", Chain.EARTH_MOVE, 5, "震星"),
            of(Element.EARTH, "mud", Chain.EARTH_MUD, 1, "土泥"),
            of(Element.EARTH, "pit", Chain.EARTH_MUD, 2, "土坑"),
            of(Element.EARTH, "mire", Chain.EARTH_MUD, 3, "泥沼"),
            of(Element.EARTH, "earth_flow", Chain.EARTH_MUD, 4, "土泷"),
            of(Element.EARTH, "earth_core_burst", Chain.EARTH_MUD, 5, "地爆天星"),
            of(Element.EARTH, "earth_ring", Chain.EARTH_RING, 1, "土环"),
            of(Element.EARTH, "earth_prison", Chain.EARTH_RING, 2, "土牢"),
            of(Element.EARTH, "rock_kingdom", Chain.EARTH_RING, 3, "岩国"),
            of(Element.EARTH, "all_things_grow", Chain.EARTH_RING, 4, "万物生"),
            of(Element.EARTH, "earth_god_blessing", Chain.EARTH_RING, 5, "大地神恩"),

            // 暗系：黑夜之手 / 以伤换伤 / 召唤 / 黑雾
            of(Element.DARK, "night_hand", Chain.DARK_HAND, 1, "黑夜之手"),
            of(Element.DARK, "night_raid", Chain.DARK_HAND, 2, "黑夜之袭"),
            of(Element.DARK, "night_embrace", Chain.DARK_HAND, 3, "黑夜之拥"),
            of(Element.DARK, "black_ruin", Chain.DARK_HAND, 4, "黑之破灭"),
            of(Element.DARK, "slay_light", Chain.DARK_HAND, 5, "戮光"),
            of(Element.DARK, "trade_wounds", Chain.DARK_SACRIFICE, 1, "以伤换伤"),
            of(Element.DARK, "blood_burn", Chain.DARK_SACRIFICE, 2, "燃血"),
            of(Element.DARK, "sacrifice", Chain.DARK_SACRIFICE, 3, "献祭"),
            of(Element.DARK, "possess", Chain.DARK_SACRIFICE, 4, "夺舍"),
            of(Element.DARK, "i_am_god", Chain.DARK_SACRIFICE, 5, "我为神"),
            of(Element.DARK, "summon_dark", Chain.DARK_SUMMON, 1, "召唤"),
            of(Element.DARK, "summon_elite", Chain.DARK_SUMMON, 2, "召唤精兵"),
            of(Element.DARK, "summon_lord", Chain.DARK_SUMMON, 3, "召唤统领"),
            of(Element.DARK, "dark_king", Chain.DARK_SUMMON, 4, "暗之国王"),
            of(Element.DARK, "evil_god", Chain.DARK_SUMMON, 5, "邪神"),
            of(Element.DARK, "black_mist", Chain.DARK_FOG, 1, "黑雾"),
            of(Element.DARK, "night_grace", Chain.DARK_FOG, 2, "黑夜眷顾"),
            of(Element.DARK, "dark_city", Chain.DARK_FOG, 3, "暗之都"),
            of(Element.DARK, "where_light_cannot_reach", Chain.DARK_FOG, 4, "光无法到达之地"),
            of(Element.DARK, "devour_light", Chain.DARK_FOG, 5, "吞光领域")
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

    // ------------------------------------------------------------------
    //  特殊魔法（领域魔法）—— 设计总纲 §12.F
    // ------------------------------------------------------------------

    /**
     * 一条领域魔法。
     *
     * <p>和 {@link Entry} 的关键区别（文档 §12.F 的三条硬规则）：
     * <ul>
     *   <li><b>无法升级</b>：没有 1~5 级，学会就是学会 —— 所以它**不是链**
     *       （塞进 {@link Chain} 会破坏自检里"每条链 5 级"的断言 ✗）。</li>
     *   <li><b>获得路线即获得</b>：一次性解锁，不逐级买点。</li>
     *   <li><b>强度只与亲和力有关</b>：不看等级、不看装备，只看该元素亲和力。</li>
     * </ul>
     *
     * @param name 占位名 —— 文档只定了 S/M 的光暗领域，七个元素各自的领域内容还没定，
     *             所以这里先按"<元素>领域"命名，等用户给了正式名字改这一行即可。
     */
    public record Special(ResourceLocation id, Element element, String name) {

        /** 界面/提示里的完整名字。 */
        public String fullName() {
            return name + "（" + element.cn() + " · 领域魔法）";
        }
    }

    /** 七个元素各一条领域魔法（暂时都是占位名，法术本体也还没做）。 */
    private static final List<Special> SPECIALS = List.of(
            special(Element.LIGHTNING, "lightning_domain", "雷霆领域"),
            special(Element.FIRE, "fire_domain", "焚天领域"),
            special(Element.WIND, "wind_domain", "风神领域"),
            special(Element.WATER, "water_domain", "深海领域"),
            special(Element.EARTH, "earth_domain", "大地领域"),
            special(Element.LIGHT, "light_domain", "圣光领域"),
            special(Element.DARK, "dark_domain", "暗黑领域"));

    private static Special special(Element element, String path, String name) {
        return new Special(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), element, name);
    }

    /** 全部领域魔法（每个元素一条）。 */
    public static List<Special> specials() {
        return SPECIALS;
    }

    /** 某个元素的领域魔法。 */
    public static Special specialOf(Element element) {
        for (Special s : SPECIALS) {
            if (s.element() == element) {
                return s;
            }
        }
        return null;
    }

    /** 水/土/暗三系（骨架先铺，法术 JSON 逐步补；名字全照设计文档）。 */
    private static Entry of(Element element, String path, Chain chain, int tier, String name) {
        return new Entry(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), element, chain, tier, name);
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
