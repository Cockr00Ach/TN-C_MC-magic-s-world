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
        RAY("射线", "穿透的火焰射线"),

        /** 火球线（2026-10-05 重做）：火球术 → 大火球术 → 熔岩火球 → 熔岳天倾 → 炎葬。 */
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
        DARK_FOG("黑雾", "弥漫的黑色雾气"),
        INDEPENDENT("独立魔法", "不属于任何元素，无亲和力要求"),
        /**
         * ★ 光系<b>第二条链</b>「光耀」（治疗 + 减伤 + <b>飞行</b>，作者 2026-10-01 定）：
         * 光芒照耀 → 圣光 → 神光 → 天使降临 → 天使的悲悯 ✓。
         *
         * <p>★ 2026-10-02 作者：<b>"把光魔法的飞行链删去，加入到光耀里，释放光耀法术就获得飞行"</b> ✓
         * ⇒ 原来那条「光翼」链（light_flight / light_swift_flight / light_wingspan）**整条删除** ✗，
         * 飞行改成"身上有光耀 buff 就能飞" ✓（时长 = 该档 buff 时长 12/14/16/18/20 秒 ✓，
         * 背后的光翼也一起点亮 ✓）。所以这条链现在同时管：治疗 / 减伤 / 飞行 ✓。
         */
        LIGHT_GRACE("光耀", "治疗、减伤与飞行（光系第二条链）"),
        /**
         * ★ 光系<b>第三条链</b>「光线」（作者 2026-10-01 指定）：
         * 光线（向前数道细彩色光线）→ 大光线（粗）→ 巨大光线（极粗）
         * → 圣光天降（天上开阵、垂直落下极粗光柱）→ 五光十射（天上五个阵，各自圣光天降）✓。
         *
         * <p>法阵用的是**雷法那种线条型**（{@code TNMagicCircleEntity.STORM} ✓，作者要求 ✓）；
         * 机制全在 {@code light/TNLightBeamMechanics} ✓（JSON 只负责表演 ✓）。
         *
         * <p>⚠️ 和其它新链一样：**必须追加在枚举末尾** ✗（存档按链的序数存进度 ✗）。
         */
        LIGHT_BEAM("光线", "向前/天降的彩色光线（光系第三条链）"),
        /**
         * ★ 光系<b>第四条链</b>「召唤天使」（作者 2026-10-02 指定 ✓）：
         * 召唤天使 → 天使双卫 → 天使军团 → 炽天使降临 → 大天使长 ✓。
         *
         * <p>召唤物是 {@code light/TNFightingAngelEntity}（作者的 fightingangel 模型 ✓）：
         * <b>会飞</b> ✓（无重力 + 飞行移动控制 + 飞行寻路 ✓）、跟着主人 ✓、替你打敌对生物 ✓、
         * 到点自己消散 ✓。档位越高：个头越大（渲染缩放 0.70 → 1.40 ✓）、血越厚、打得越疼 ✓。
         *
         * <p>⚠️ 和其它新链一样：**必须追加在枚举末尾** ✗（存档按链的序数存进度 ✗）。
         */
        LIGHT_SUMMON("召唤天使", "召唤会飞的战斗天使（光系第四条链）"),
        /**
         * ★ 光系<b>第五条链</b>「光龙」（作者 2026-10-02 指定 ✓）：
         * 光龙吐息 → 光龙鳞甲 → 光龙出击 → 光龙俯冲 → 光龙降世 ✓。
         *
         * <p>★ 作者同日第二版："<b>不要做成召唤物啊，我要释放出一条巨龙往前冲，触碰造成伤害</b>" ✓
         * ⇒ 龙不是宠物 ✗：放出去就**直线往前冲**，路上碰到谁伤谁，撞墙/到点爆开消失 ✓
         * （行为在 {@code light/TNDragonEntity}，档位数值在 {@code light/TNLightDragonChain} ✓）。
         *
         * <p>⚠️ 和其它新链一样：**必须追加在枚举末尾** ✗（存档按链的序数存进度 ✗）。
         */
        LIGHT_DRAGON("光龙", "放出一条巨龙向前冲（光系第五条链）"),
        /**
         * ★ 暗系<b>第五条链</b>「暗龙」（作者 2026-10-02：<b>"复制一下光龙，生成一个暗龙"</b> ✓）：
         * 暗龙吐息 → 暗龙鳞甲 → 暗龙出击 → 暗龙俯冲 → 暗龙降世 ✓。
         *
         * <p>**逐字照搬光龙那张表** ✓（同一套行为：t1 一条小龙冲出去、t2 三条小龙绕着自己转 +
         * 鳞甲 buff、t3/t4/t5 一条/两条/三条大龙往前冲 ✓），只换皮：
         * 龙的实体类型 + 贴图（玄黑紫 ✓）、粒子（灵魂火/黑烟 ✓）、
         * 鳞甲 buff 的伤害加成从"光"换成"暗"（{@code spell_power:soul} ✓）。
         *
         * <p>⚠️ 和其它新链一样：**必须追加在枚举末尾** ✗（存档按链的序数存进度 ✗）。
         */
        DARK_DRAGON("暗龙", "放出一条暗龙向前冲（暗系第五条链）");

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

        public boolean independent() { return chain == Chain.INDEPENDENT; }

        /** 界面/提示里显示的完整名字，例如「雷击（雷 · 主链 · 专家级）」。 */
        public String fullName() {
            return independent() ? displayName + "（独立魔法 · 无元素）"
                    : displayName + "（" + element.cn() + " · " + chain.cn() + " · " + Element.tierName(tier) + "）";
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
            entry("cataclysm_thunder_orb", Chain.ORB, 4, "超级无敌大雷球"),
            entry("god_descent", Chain.ORB, 5, "神在投篮"),

            // ---- 雷速线：加速、位移、以及"以雷的速度"带来的强化 ----
            entry("lightning_haste", Chain.SPEED, 1, "雷速"),
            entry("lightning_blink", Chain.SPEED, 2, "闪电移位"),
            entry("lightning_wind", Chain.SPEED, 3, "极速雷风"),
            entry("lightning_recharge", Chain.SPEED, 4, "闪电降低冷却"),
            entry("lightning_ascension", Chain.SPEED, 5, "闪电登神"),

            // ---- 火射线线：1 穿透 → 2 更粗 → 3 三向齐发 → 4 命中爆炸 → 5 巨大爆炸 ----
            fireEntry("sun_ray", Chain.RAY, 1, "烈阳射线"),
            fireEntry("blast_ray", Chain.RAY, 2, "爆炸射线"),
            fireEntry("fire_dragon", Chain.RAY, 3, "火龙术"),
            fireEntry("flame_demon_wrath", Chain.RAY, 4, "炎魔龙之怒"),
            fireEntry("cataclysm_fire_ray", Chain.RAY, 5, "巨大爆炸射线"),

            // ---- 火球线（作者 2026-10-05 定稿）----
            //   ★ 1/2 两档已改成**自有实体**（magic/fire/TNFireBoltEntity）：
            //     焚身要按"这一发实际打了多少"算，而引擎的 PROJECTILE 在伤害事件里
            //     不带法术 id —— 见 FireSpellRules 的类注释。
            //   ⚠️ 曾经想给 t2 加第二个选项「连珠火球术」，但
            //     SpellCatalogStructureTest 的「每条链每档恰好一个」是**有依据的**不变量
            //     （同档两个会让 isChainTop 的"一边学一边进"判定算错），
            //     所以按作者决定**只保留大火球术** ✓
            //   3/4/5 仍是引擎驱动，待换成 熔岩火球 / 熔岳天倾 / 炎葬（下一个分支）----
            fireEntry("fireball", Chain.BALL, 1, "火球术"),
            fireEntry("great_fireball", Chain.BALL, 2, "大火球术"),
            fireEntry("lava_fireball", Chain.BALL, 3, "熔岩火球"),
            fireEntry("molten_skyfall", Chain.BALL, 4, "熔岳天倾"),
            fireEntry("meteor_fall", Chain.BALL, 5, "陨星坠"),

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

            // ---- 光耀线（光系第二条链，作者 2026-10-01 定）----
            //   1 光芒照耀：范围 8，队友 25% 减伤 + 立刻回 20 血，地面铺同半径法阵
            //   2 圣光：范围 10，50% 减伤 + 回 30 血 + 法阵
            //   3 神光：范围 12，50% 减伤 + 回 30 血 + 法阵中心召唤 4 格高天使（白/淡黄粒子）+ 转晴
            //   4 天使降临：范围 14，天使 6 格高，减伤 70%
            //   5 天使的悲悯：范围 16，天使 10 格高，70% 减伤，范围内怪物停手 5 秒
            //   （天使高度 = 作者 2026-10-01："变成现在的两倍" ⇒ 4/6/10 ✓）
            //   （数值都在 light/TNLightChainMechanics 的一张表里 ✓，改那里就行 ✓）
            of(Element.LIGHT, "light_radiance", Chain.LIGHT_GRACE, 1, "光芒照耀"),
            of(Element.LIGHT, "holy_light", Chain.LIGHT_GRACE, 2, "圣光"),
            of(Element.LIGHT, "divine_light", Chain.LIGHT_GRACE, 3, "神光"),
            of(Element.LIGHT, "angel_descent", Chain.LIGHT_GRACE, 4, "天使降临"),
            of(Element.LIGHT, "angel_mercy", Chain.LIGHT_GRACE, 5, "天使的悲悯"),

            // ---- 光翼线：★ 2026-10-02 作者要求**整条删掉** ✗（"把光魔法的飞行链删去，
            //      加入到光耀里，释放光耀法术就获得飞行" ✓）—— light_flight /
            //      light_swift_flight / light_wingspan 三个法术 + 法术 JSON + 图标都删了 ✓，
            //      飞行改成"光耀 buff 在身就能飞" ✓（见 Chain.LIGHT_GRACE ✓）

            // ---- 光线线（光系第三条链，作者 2026-10-01 指定）----
            //   1 光线：向前 3 道细的彩色光线（扇形散开）
            //   2 大光线：一道粗的彩色光线
            //   3 巨大光线：一道极粗的彩色光线
            //   4 圣光天降：天上开阵（雷法那种线条型），从阵里垂直向下射出极粗光柱
            //   5 五光十射：天上开 5 个阵，每个阵各放一次圣光天降（错开落下）
            //   （数值都在 light/TNLightBeamMechanics 的一张表里 ✓，改那里就行 ✓）
            of(Element.LIGHT, "light_beam", Chain.LIGHT_BEAM, 1, "光线"),
            of(Element.LIGHT, "great_light_beam", Chain.LIGHT_BEAM, 2, "大光线"),
            of(Element.LIGHT, "giant_light_beam", Chain.LIGHT_BEAM, 3, "巨大光线"),
            of(Element.LIGHT, "holy_light_descent", Chain.LIGHT_BEAM, 4, "圣光天降"),
            of(Element.LIGHT, "radiant_barrage", Chain.LIGHT_BEAM, 5, "五光十射"),

            // ---- 召唤天使线（光系第四条链，作者 2026-10-02 指定；只数/个头是作者第二版要的）----
            //   1 召唤天使  ：1 只，个头 0.45（小小）
            //   2 天使卫队  ：3 只，0.70
            //   3 天使军团  ：5 只，1.05（= "现在这样的"）
            //   4 炽天使降临：15 只，0.75（一群小的）
            //   5 大天使长  ：3 只，1.80（大只的）
            //   ★ 天使**会飞**（跟着你上天 ✓）、会替你打敌对生物 ✓、
            //     **轮流放光线链的 t1（光线）和 t4（圣光天降）** ✓ ——
            //     其中 t4 的法阵按作者要求**缩小五倍** ✓（见 TNFightingAngelEntity.DESCENT_CIRCLE_SCALE）
            //   （只数/个头/血伤/时长都在 light/TNLightChainMechanics 的 SUMMONS 一张表里 ✓）
            of(Element.LIGHT, "summon_angel", Chain.LIGHT_SUMMON, 1, "召唤天使"),
            of(Element.LIGHT, "angel_twins", Chain.LIGHT_SUMMON, 2, "天使卫队"),
            of(Element.LIGHT, "angel_legion", Chain.LIGHT_SUMMON, 3, "天使军团"),
            of(Element.LIGHT, "seraph_descent", Chain.LIGHT_SUMMON, 4, "炽天使降临"),
            of(Element.LIGHT, "archangel", Chain.LIGHT_SUMMON, 5, "大天使长"),

            // ★★ 2026-10-04 作者："把龙法术都删了吧，包括光龙和暗龙" ✗
            //   ⇒ 光龙 5 招（light_dragon_breath / light_dragon_scales / summon_light_dragon /
            //      light_dragon_dive / light_dragon_descend）**从这里删掉了** ✓：
            //   - 法术 json 已删 ✓（data/tnc/spells/ 里没有了 ✓）
            //   - 法杖池里的条目也删了 ✓（data/tnc/spell_pools/tnc_light.json ✓）
            //   - 出招的口子也关了 ✓（light/TNLightChainMechanics 里那条分支 ✓）
            //   链的 enum 常量**故意留着** ✗：存档里的"每条链练到第几档"是**按序号**存的 ✗，
            //   删 enum 常量会让后面那些链的序号整体前移 ✗ ⇒ 老存档的进度会串位 ✗。
            //   常量留着不影响 UI ✓ —— 目录里没有这一条链的法术，它就不会出现在任何列表里 ✓
            //   （见 SpellCatalog.chainsOf：链是从"有没有法术"推出来的 ✓，不是枚举硬列的 ✓）。

            // ★★ 2026-10-04 作者："把龙法术都删了吧，包括光龙和暗龙" ✗
            //   ⇒ 暗龙 5 招（dark_dragon_breath / scales / charge / dive / descend）也**删掉了** ✓
            //   （同样的四件事：json 删 ✓ / 池子删 ✓ / 出招口子关 ✓ / enum 常量留着保序号 ✓ —— 理由见上面光龙那段 ✓）

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
            // Null element is deliberate: independent skills never enter the seven-element NBT arrays.
            new Entry(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "chaos_magic"), null, Chain.INDEPENDENT, 1, "乱魔"),

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
     * <b>配键列表用</b>：某元素下"每条链当前拥有的最高档"，按链的顺序返回。
     *
     * <p>为什么列表只给链顶而不是"全部已学"（作者 2026-09-29 要求）：
     * 玩家心里的一条链就是<b>一个法术</b> —— "我的雷系主链是雷击"。
     * 把 t1~t4 全列出来会让人以为四张牌都能带，实际上带哪张都得占一个键，
     * 而链内低档在数值上就是纯退化 ✗。
     *
     * <p>没学过的链直接<b>不出现</b>（不是灰条目）—— 列表里只有你真拥有的东西 ✓。
     * 找不到能绑的（引擎没实装）也跳过，免得出现按不动的条目。
     */
    public static List<Entry> chainTopAssignable(MagicStoneData data, Element element) {
        List<Entry> result = new ArrayList<>();
        for (Chain chain : chainsOf(element)) {
            Entry top = topLearned(data, element, chain);
            if (top != null && canBind(data, top.id())) {
                result.add(top);
            }
        }
        return result;
    }

    /**
     * 这个法术是不是它那条链的<b>链顶</b>。
     *
     * <p>独立魔法（乱魔）没有链概念，直接放行 ✓；不在目录里的（第三方法术）也放行 ✓。
     */
    public static boolean isChainTop(MagicStoneData data, ResourceLocation spell) {
        if (data == null || spell == null) {
            return false;
        }
        Entry entry = byId(spell);
        if (entry == null || entry.independent() || entry.element() == null) {
            return true;
        }
        Entry top = topLearned(data, entry.element(), entry.chain());
        return top != null && top.id().equals(spell);
    }

    /**
     * 老口径：每条链只取已解锁的最高级。
     *
     * <p>⚠️ <b>它已经不再决定法杖内容了</b>（那是 {@link #wandSpellIds}）。
     * 现在只剩两个用途：老存档迁移时生成第一版默认配装、以及自检里那条"链式替换"的断言。
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
        for (Entry independent : of(null)) {
            if (data.hasLearned(independent.id())) result.add(independent);
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

    // ------------------------------------------------------------------
    //  配装（loadout）—— "哪个键放哪个法术"
    // ------------------------------------------------------------------

    /**
     * 现在<b>还能被配到键位上</b>的法术。
     *
     * <p>为什么不是 {@link #effective}：那条路每条链只留最高档（那是老热键栏容量不够时的妥协 ✗）。
     * 有了 18 个槽 + 玩家自己排键位之后，<b>低档法术同样可以绑</b> ——
     * "右键小闪电清杂兵、2 号键雷暴打 Boss"是玩家自己的选择 ✓。
     *
     * <p>所以判据就一条：<b>学过 = 可以绑</b>。忘掉的（{@code forget} 会把它从 learned 移除）
     * 自动就不在这个名单里 ✓。
     */
    public static List<Entry> assignable(MagicStoneData data) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all()) {
            if (data.hasLearned(entry.id())) {
                result.add(entry);
            }
        }
        return result;
    }

    /** {@link #assignable(MagicStoneData)} 的 id 版本。 */
    public static List<ResourceLocation> assignableIds(MagicStoneData data) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Entry entry : assignable(data)) {
            ids.add(entry.id());
        }
        return ids;
    }

    /**
     * 这个法术<b>能不能被玩家配到键位上</b>。
     *
     * <p>三条判据（缺一不可）：
     * <ol>
     *   <li><b>学过</b> —— 魔法石是权威数据</li>
     *   <li><b>是它那条链的最高档</b> —— 作者 2026-09-29 定的规则：
     *       一条链就是一个法术，学了高阶就用高阶（低档在数值上纯退化 ✗）。
     *       独立魔法与目录外的第三方法术不受这条限制 ✓</li>
     *   <li><b>引擎认识它</b> —— 目录里有、JSON 还没写的法术绑上去也放不出来</li>
     * </ol>
     */
    public static boolean canBind(MagicStoneData data, ResourceLocation spell) {
        if (data == null || spell == null || !data.hasLearned(spell)) {
            return false;
        }
        if (!isChainTop(data, spell)) {
            return false;
        }
        Entry entry = byId(spell);
        return entry == null || com.tnc.tnc.magic.compat.SpellEngineBridge.hasSpell(entry.id());
    }

    /**
     * <b>法杖当前这一页该写什么</b> —— 定长 {@link MagicStoneData#SLOTS_PER_PAGE} 项，
     * 空槽是 {@code null}（空槽必须占位，不能挤位）。
     *
     * <p>这是"法杖内容"的<b>唯一口径</b>：登录补杖、学法后同步、切页、{@code /tnc wand}
     * 全都要走它 —— 几处各算一套的话，页号和配装会互相冲掉 ✗。
     *
     * <p><b>写杖之前先规范化配装</b>（{@link MagicStoneData#normalizeLoadout}）——
     * 这一步是"同一条链的低档不许留在法杖上"的强制执行点 ✗。
     * 作者 2026-09-29 实测的顺序是"先学完几个档、再去配键页手动摆"，
     * 那种顺序下低档会各占一个空槽，只有在这里统一收口才治得住 ✓。
     *
     * <p>顺带做两层过滤（只影响这次写进杖里的内容，<b>不改</b>玩家的配装）：
     * <ol>
     *   <li>没学过 / 目录里查不到 → 空槽（老存档迁移过来的配装对不上目录时兜底）</li>
     *   <li>引擎不认识的 → 空槽（防呆闸门，免得法杖上出现按不动的空格）</li>
     * </ol>
     */
    public static List<ResourceLocation> wandSpellIds(MagicStoneData data) {
        // ★ 唯一出口上的不变量：一条链一个法术、且只能是当前链顶
        data.normalizeLoadout();
        List<ResourceLocation> page = data.pageSpellIds(data.getLoadoutPage());
        List<ResourceLocation> result = new ArrayList<>(page.size());
        for (ResourceLocation id : page) {
            result.add(canBind(data, id) ? id : null);
        }
        return result;
    }

    /**
     * 现在值不值得给玩家写/补法杖 —— 当前这一页<b>真的至少有一个能放的法术</b>。
     *
     * <p>为什么不写成 {@code !wandSpellIds(data).isEmpty()}：那份列表是<b>定长</b>的，
     * 永远非空 ✗ —— 配装空着的时候会补出一根空杖。
     */
    public static boolean wandSpellIdsNonEmpty(MagicStoneData data) {
        for (ResourceLocation id : wandSpellIds(data)) {
            if (id != null) {
                return true;
            }
        }
        return false;
    }
}
