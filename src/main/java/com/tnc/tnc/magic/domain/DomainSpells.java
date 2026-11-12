package com.tnc.tnc.magic.domain;

import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域法术的<b>唯一数值表</b>（作者 2026-11-08 定的设计，第二轮补齐七系）。
 *
 * <h2>三条硬规则（作者原话）</h2>
 * <ol>
 *   <li><b>「放在独立法术那里」</b> —— 它们在 {@code SpellCatalog} 里是
 *       {@code Chain.INDEPENDENT}，元素写 {@code null}（只出现在魔法石的「独立」页）✓；</li>
 *   <li><b>「只能选择一个领域法术」</b> —— 见 {@code MagicStoneLearning.check}：已经有一个就别想学第二个，
 *       想换要先遗忘 ✓；</li>
 *   <li><b>「条件是有亲和力就行」</b> —— 不看等级、不看点数门槛之外的进度，只要该元素亲和力 ≥ 1 ✓。</li>
 * </ol>
 *
 * <h2>七系各一句（作者 2026-11-08：「可以尝试做做别的法术领域」）</h2>
 * <pre>
 *   雷  雷霆领域  展开雷领域 → 加雷伤 → 战士附雷伤、刀上带小闪电；隔很久劈一次，
 *                主要劈玩家和队友打的目标，劈之前地面有明显落点
 *   暗  暗黑领域  展开暗领域 → 加暗伤 → 战士附暗伤、刀光剑影变黑；脚下起黑雾，
 *                伸手抓住目标一直控制，间隔也久
 *   火  焚天领域  加火伤 → 战士附火伤、刀光转橙红；目标脚下预警后砸下火柱，点燃
 *   风  风神领域  加风伤 → 战士附风伤、刀光转青绿；目标被风刃割开并推出去
 *   水  深海领域  加水伤 → 战士附水伤、刀光转青蓝；目标脚下涌起水浪，泡住减速
 *   土  大地领域  加土伤 → 战士附土伤、刀光转土黄；目标脚下岩刺顶起，定在原地
 *   光  圣光领域  加光伤 → 战士附光伤、刀光转白金；圣光灼烧敌人，同时给队友回血
 * </pre>
 *
 * <p>「加 X 伤」全部由 {@link com.tnc.tnc.magic.TNSpecialMagic} 的
 * 「亲和力 × 5% → {@code spell_power:<school>}」负责（学会领域即打开开关 ✓），
 * 这里只管<b>周期出手</b>和<b>战士附伤</b>。
 */
public final class DomainSpells {

    /** 雷霆领域（{@code tnc:lightning_domain}）。 */
    public static final String LIGHTNING = "lightning_domain";
    /** 暗黑领域（{@code tnc:dark_domain}）。 */
    public static final String DARK = "dark_domain";
    /** 焚天领域。 */
    public static final String FIRE = "fire_domain";
    /** 风神领域。 */
    public static final String WIND = "wind_domain";
    /** 深海领域。 */
    public static final String WATER = "water_domain";
    /** 大地领域。 */
    public static final String EARTH = "earth_domain";
    /** 圣光领域。 */
    public static final String LIGHT = "light_domain";

    /** 出手方式的代号（{@link Domain#kind()} 用）。 */
    public static final String KIND_BOLT = "bolt";     // 落雷
    public static final String KIND_GRIP = "grip";     // 黑雾 + 手
    public static final String KIND_BURN = "burn";     // 火柱
    public static final String KIND_CUT = "cut";       // 风刃
    public static final String KIND_WAVE = "wave";     // 水浪
    public static final String KIND_QUAKE = "quake";   // 岩刺
    public static final String KIND_SMITE = "smite";   // 圣光

    // ------------------------------------------------------------------
    //  通用数值（七个领域共用）
    // ------------------------------------------------------------------

    /** 领域持续多久（tick）：30 秒 —— 一波怪 / 一个 boss 阶段够用 ✓。 */
    public static final int DURATION = 600;

    /** 冷却（tick）：90 秒（和 JSON 的 {@code cooldown_duration} 一致 ✓）。 */
    public static final int COOLDOWN = 1800;

    /** 领域半径（格）：16 —— 比常见法术射程 12~14 略大，队友站身边也能吃到 ✓。 */
    public static final double RADIUS = 16.0D;

    /** 「刚被打过」的记忆多久（tick）：8 秒 ✓。 */
    public static final int TARGET_MEMORY = 160;

    /** 开领域后第一次出手的延迟（tick）：2 秒 —— 先让玩家看见领域开起来 ✓。 */
    public static final int FIRST_DELAY = 40;

    /** 战士攻击附带的元素伤害（占这一刀的百分比）：+30% ✓。 */
    public static final float MELEE_BONUS = 0.30F;

    /** 战士附伤一跳最多加多少（点）：防一刀斩出天文数字 ✓。 */
    public static final float MELEE_BONUS_CAP = 12.0F;

    /** 暗领域抓住后按住多久（tick）：3 秒 —— 作者「一直控制」✓。 */
    public static final int GRIP_TICKS = 60;

    /** 火领域点着几秒 ✓。 */
    public static final int BURN_SECONDS = 4;

    /** 土领域岩刺把人定住多久（tick）：1.2 秒 ✓。 */
    public static final int ROOT_TICKS = 24;

    /** 水领域泡住减速多久（tick）：2.5 秒 ✓。 */
    public static final int SOAK_TICKS = 50;

    /** 圣光领域每次出手给范围内的玩家/队友回多少血（点）✓。 */
    public static final float LIGHT_HEAL = 4.0F;

    // ------------------------------------------------------------------
    //  范围（2026-11-08 作者：「领域我希望来点范围的那种效果」）
    // ------------------------------------------------------------------

    /** 常驻光环半径（格）：7 —— 站在玩家身边的敌人一直在掉血/被染上本系状态 ✓。 */
    public static final double AURA_RADIUS = 7.0D;

    /** 光环多久脉冲一次（tick）：20 = 每秒一下 ✓。 */
    public static final int AURA_INTERVAL = 20;

    /** 光环一下多少伤害（点）：1 —— 一秒一跳，不抢主动法术的戏 ✓。 */
    public static final float AURA_DAMAGE = 1.0F;

    /** 光领域光环每次给范围内的玩家回多少血（点）：0.5/秒 ✓。 */
    public static final float AURA_HEAL = 0.5F;

    /** 一次出手最多同时点几个落点（格）：3 —— 一片劈下来，而不是只砸一个 ✓。 */
    public static final int MAX_TARGETS = 3;

    /** 两个落点至少隔多远（格）：2 —— 免得三个圈叠成一个 ✓。 */
    public static final double TARGET_SPACING = 2.0D;

    // ------------------------------------------------------------------
    //  一条领域
    // ------------------------------------------------------------------

    /**
     * 一条领域法术的全部数值。
     *
     * @param element   所属元素（决定「加什么伤」「战士附什么伤」「刀光什么色」）
     * @param path      法术 id 的 path（和 {@code SpellCatalog} / {@code spells()} 一一对应）
     * @param name      显示名
     * @param kind      {@link #KIND_BOLT} 等出手方式
     * @param interval  多久出手一次（tick）
     * @param telegraph 地面预警多久（tick）
     * @param style     {@code TNMagicCircleEntity} 的阵样式（没有专属样式的用 0 号通用阵）
     * @param damage    一次出手的伤害
     * @param radius    一次出手的命中半径（格）
     */
    public record Domain(Element element, String path, String name, String kind,
                         int interval, int telegraph, int style, float damage, double radius) {
    }

    /**
     * 七条领域（作者：先做雷，然后「可以尝试做做别的法术领域」，再「来点范围的那种效果」）。
     *
     * <p>阵样式：雷 11 / 暗 13 / 土 4 / 风 5 / 光 9 都有专属；火、水暂时用 0 号通用阵
     * （画的是线条型法阵，靠落点周围的火/水粒子区分 —— 专属样式下一轮再加 ✓）。
     *
     * <p>半径这一列是<b>一次出手的范围</b>，现在都在 3 格以上（原来只有 2~3 格，
     * 打起来像"点名"而不是"范围" ✗）；再加上 7 格常驻光环和一次最多三个落点，
     * 领域才有一片的感觉 ✓。
     */
    private static final List<Domain> DOMAINS = List.of(
            new Domain(Element.LIGHTNING, LIGHTNING, "雷霆领域", KIND_BOLT, 120, 22, 11, 14.0F, 3.2D),
            new Domain(Element.DARK, DARK, "暗黑领域", KIND_GRIP, 160, 22, 13, 0.0F, 3.0D),
            new Domain(Element.FIRE, FIRE, "焚天领域", KIND_BURN, 110, 20, 0, 12.0F, 4.0D),
            new Domain(Element.WIND, WIND, "风神领域", KIND_CUT, 100, 14, 5, 9.0F, 3.6D),
            new Domain(Element.WATER, WATER, "深海领域", KIND_WAVE, 130, 18, 0, 11.0F, 4.0D),
            new Domain(Element.EARTH, EARTH, "大地领域", KIND_QUAKE, 140, 24, 4, 13.0F, 3.6D),
            new Domain(Element.LIGHT, LIGHT, "圣光领域", KIND_SMITE, 150, 20, 9, 10.0F, 4.0D));

    /** 全部领域法术的 path（顺序 = 魔法石「独立」页的显示顺序 ✓）。 */
    public static final List<String> PATHS;

    static {
        List<String> paths = new ArrayList<>();
        for (Domain domain : DOMAINS) {
            paths.add(domain.path());
        }
        PATHS = List.copyOf(paths);
    }

    private DomainSpells() {
    }

    // ------------------------------------------------------------------
    //  查询
    // ------------------------------------------------------------------

    /** 七条领域。 */
    public static List<Domain> domains() {
        return DOMAINS;
    }

    /** 这个 id 是不是领域法术。 */
    public static boolean isDomain(ResourceLocation id) {
        return id != null && byPath(id.getPath()) != null;
    }

    /** 这个 path 是不是领域法术。 */
    public static boolean isDomain(String path) {
        return path != null && byPath(path) != null;
    }

    /** 按 path 找一条领域（找不到返回 {@code null}）。 */
    public static Domain byPath(String path) {
        if (path == null) {
            return null;
        }
        for (Domain domain : DOMAINS) {
            if (domain.path().equals(path)) {
                return domain;
            }
        }
        return null;
    }

    /** 按元素找一条领域（找不到返回 {@code null}）。 */
    public static Domain of(Element element) {
        if (element == null) {
            return null;
        }
        for (Domain domain : DOMAINS) {
            if (domain.element() == element) {
                return domain;
            }
        }
        return null;
    }

    /** 领域法术 → 它属于哪一系（不认识的返回 {@code null}）。 */
    public static Element elementOf(ResourceLocation id) {
        return id == null ? null : elementOf(id.getPath());
    }

    /** 同上，按 path 查。 */
    public static Element elementOf(String path) {
        Domain domain = byPath(path);
        return domain == null ? null : domain.element();
    }

    /** 元素 → 领域法术的 path（不认识的返回 {@code null}）。 */
    public static String pathOf(Element element) {
        Domain domain = of(element);
        return domain == null ? null : domain.path();
    }

    /** 界面上给玩家看的名字。 */
    public static String nameOf(Element element) {
        Domain domain = of(element);
        return domain == null ? "领域" : domain.name();
    }

    // ------------------------------------------------------------------
    //  状态（= 效果）
    // ------------------------------------------------------------------

    /** 玩家现在开着哪个领域：元素 id（{@code "lightning"}…）/ {@code null}（没开）。 */
    public static String activeElementId(Entity entity) {
        for (Domain domain : DOMAINS) {
            MobEffect effect = TNEffects.domainEffect(domain.element());
            if (effect != null && entity instanceof net.minecraft.world.entity.LivingEntity living
                    && living.hasEffect(effect)) {
                return domain.element().id();
            }
        }
        return null;
    }

    /** 同上，但返回领域本体。 */
    public static Domain active(Entity entity) {
        for (Domain domain : DOMAINS) {
            MobEffect effect = TNEffects.domainEffect(domain.element());
            if (effect != null && entity instanceof net.minecraft.world.entity.LivingEntity living
                    && living.hasEffect(effect)) {
                return domain;
            }
        }
        return null;
    }

    /** 同上，但返回元素本体。 */
    public static Element activeElement(Entity entity) {
        Domain domain = active(entity);
        return domain == null ? null : domain.element();
    }
}
