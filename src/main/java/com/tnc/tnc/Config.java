package com.tnc.tnc;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * TN-C 数值配置。
 *
 * <p>这些数字全部来自设计文档《TN-C-魔法系统设计.md》第十三节「待确认清单」，
 * 凡是标 ❓ 的都先按<b>建议值</b>写在这里，游戏里改 config 就能调，不用重新编译。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ---------------- 魔力值（设计文档 3.1 / A1 / A2） ----------------

    private static final ForgeConfigSpec.IntValue MANA_PER_AFFINITY = BUILDER
            .comment("初始魔力 = 亲和力总和 × 这个值。（A1，建议 10；主角七元素全 3 → 21 × 10 = 210）")
            .defineInRange("manaPerAffinity", 10, 0, 100000);

    private static final ForgeConfigSpec.IntValue MANA_PER_VANILLA_LEVEL = BUILDER
            .comment("每 1 级原版等级 +多少魔力上限。（A2，建议 10）")
            .defineInRange("manaPerVanillaLevel", 10, 0, 100000);

    private static final ForgeConfigSpec.IntValue FLAT_MANA_BONUS = BUILDER
            .comment("额外固定魔力上限加成，留给以后的任务/道具奖励用。")
            .defineInRange("flatManaBonus", 0, 0, 1000000);

    private static final ForgeConfigSpec.IntValue MANA_REGEN_PER_SECOND = BUILDER
            .comment("魔力值每秒恢复多少（固定值，0 = 不加这一项）。（A5）")
            .defineInRange("manaRegenPerSecond", 1, 0, 100000);

    private static final ForgeConfigSpec.DoubleValue MANA_REGEN_PERCENT_PER_SECOND = BUILDER
            .comment("魔力值每秒恢复「上限的百分之几」（0 = 不加这一项）。",
                    "为什么需要它：固定值 1/秒时，神级法术（160 魔力）要回 160 秒，等于放不出来。",
                    "按上限百分比恢复才能让高等级角色的续航跟得上高等级法术。")
            .defineInRange("manaRegenPercentPerSecond", 5.0, 0.0, 100.0);

    // ---------------- 亲和力（设计文档第二节） ----------------

    private static final ForgeConfigSpec.IntValue DEFAULT_AFFINITY = BUILDER
            .comment("新玩家七个元素统一给多少亲和力。主角（S/M）七元素全为 3，所以默认 3。")
            .defineInRange("defaultAffinity", 3, 1, 6);

    // ---------------- 魔法点数（设计文档 A3 / A4） ----------------

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> POINT_THRESHOLDS = BUILDER
            .comment("魔法点数档位：魔力上限每跨过一个档位给 1 点。（A3，原始清单只给了 100→1 / 300→2 / 500→3）")
            .defineListAllowEmpty("pointThresholds", List.of(100, 300, 500, 800, 1200),
                    o -> o instanceof Integer i && i > 0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LEARN_COST_PER_TIER = BUILDER
            .comment("解锁一个法术要投多少点数，按等级 1..5 递增。（A4，建议 1/2/3/4/5）")
            .defineListAllowEmpty("learnCostPerTier", List.of(1, 2, 3, 4, 5),
                    o -> o instanceof Integer i && i >= 0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> MANA_COST_PER_TIER = BUILDER
            .comment("施放一个法术要消耗多少魔力，按等级 1..5 递增。",
                    "设计文档给的基准是「火球耗 50 魔力」，所以一级定 20、神级 160（1/3 左右的血量感）。",
                    "⚠️ 这个表是「魔力上限 = manaCostBaselineMaxMana」时的消耗，",
                    "上限更高时会被 manaCostScalesWithMaxMana 等比放大。")
            .defineListAllowEmpty("manaCostPerTier", List.of(20, 40, 60, 100, 160),
                    o -> o instanceof Integer i && i >= 0);

    private static final ForgeConfigSpec.BooleanValue MANA_COST_SCALES_WITH_MAX = BUILDER
            .comment("法术消耗要不要随魔力上限等比放大（默认开）。",
                    "为什么必须开：上限 = 亲和力总和 x 10 + 原版等级 x 10，会一直涨（41 级时 620），",
                    "而消耗表是固定的 —— 不放大就会「等级越高法术越便宜」：",
                    "上限 210 时一级法术花 20（9.5%，HUD 上看得很清楚），",
                    "上限 620 时还是 20（3.2%，条子上只有 2 像素，而且一个回魔周期就回满了）。",
                    "关掉 = 退回固定消耗（对照/调试用）。")
            .define("manaCostScalesWithMaxMana", false);

    private static final ForgeConfigSpec.IntValue MANA_COST_BASELINE_MAX_MANA = BUILDER
            .comment("上面那张消耗表对应的魔力上限基准值。",
                    "210 = 亲和力总和 21 x manaPerAffinity 10（0 级时的上限），也就是设计时假定的那个上限。",
                    "把这个值调小 = 所有法术整体变贵；调大 = 整体变便宜。")
            .defineInRange("manaCostBaselineMaxMana", 210, 1, 1000000);

    private static final ForgeConfigSpec.BooleanValue REQUIRE_LEARNED_TO_CAST = BUILDER
            .comment("施法必须先在该元素的魔法石里解锁（true = 魔法石是唯一入口，卷轴/绑定台绑进来的法术也放不出来）。",
                    "这是「取代卷轴与法术注册台」的强制手段；调试时想放行就设 false。")
            .define("requireLearnedToCast", true);

    private static final ForgeConfigSpec.BooleanValue REQUIRE_WAND_TO_CAST = BUILDER
            .comment("施法必须手上拿着法杖（主手或副手都算）。",
                    "设计决定：抛弃法术书之后，「有法杖」就是施法的前提；魔法石负责决定能放哪些、放得起哪些。",
                    "调试时想空手放就设 false。")
            .define("requireWandToCast", true);

    private static final ForgeConfigSpec.BooleanValue RESTORE_WAND_ON_LOGIN = BUILDER
            .comment("登录时把法杖内容同步到魔法石的「已解锁」集合；如果身上没有法杖但已经解锁过法术，就补一根。",
                    "设计上：魔法石是唯一权威数据（隐藏数据、不会随物品丢失），法杖只是派生道具。",
                    "所以法杖丢了不该等于法术丢了。关掉的话就用 /tnc wand 手动补。")
            .define("restoreWandOnLogin", true);

    private static final ForgeConfigSpec.IntValue MANA_REGEN_INTERVAL_TICKS = BUILDER
            .comment("每多少 tick 回一次魔。20 tick = 1 秒。",
                    "回魔速度 = 每次回的量 ÷ 这个周期。所以把它从 20 改成 40，速度就正好减半。",
                    "为什么用周期而不是直接把量减半：魔力值是整数，11 减半成 5.5 会被取整成 5 或 6，",
                    "而周期翻倍是精确的（11 点 / 2 秒 = 5.5 点/秒）。")
            .defineInRange("manaRegenIntervalTicks", 40, 1, 1200);

    private static final ForgeConfigSpec.IntValue MANA_REGEN_DELAY_AFTER_CAST = BUILDER
            .comment("施法之后多少 tick 内不回魔（20 tick = 1 秒，默认 60 = 3 秒）。",
                    "为什么需要：回魔是「每次 1 + 上限的 5%」，上限 620 时每 2 秒回 32，",
                    "而一级法术只花 20 —— 扣完不到一个周期就回满了，条子上等于看不见。",
                    "设 0 = 施法后立刻开始回魔。")
            .defineInRange("manaRegenDelayAfterCastTicks", 60, 0, 12000);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ---------------- 运行时字段（读一次缓存到这里） ----------------

    public static int manaPerAffinity;
    public static int manaPerVanillaLevel;
    public static int flatManaBonus;
    public static int manaRegenPerSecond;
    public static double manaRegenPercentPerSecond;
    public static int defaultAffinity;
    public static List<? extends Integer> pointThresholds;
    public static List<? extends Integer> learnCostPerTier;
    public static List<? extends Integer> manaCostPerTier;
    public static boolean manaCostScalesWithMaxMana;
    public static int manaCostBaselineMaxMana;
    public static boolean requireLearnedToCast;
    public static boolean requireWandToCast;
    public static boolean restoreWandOnLogin;
    public static int manaRegenIntervalTicks;
    public static int manaRegenDelayAfterCastTicks;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        manaPerAffinity = MANA_PER_AFFINITY.get();
        manaPerVanillaLevel = MANA_PER_VANILLA_LEVEL.get();
        flatManaBonus = FLAT_MANA_BONUS.get();
        manaRegenPerSecond = MANA_REGEN_PER_SECOND.get();
        manaRegenPercentPerSecond = MANA_REGEN_PERCENT_PER_SECOND.get();
        defaultAffinity = DEFAULT_AFFINITY.get();
        pointThresholds = POINT_THRESHOLDS.get();
        learnCostPerTier = LEARN_COST_PER_TIER.get();
        manaCostPerTier = MANA_COST_PER_TIER.get();
        manaCostScalesWithMaxMana = MANA_COST_SCALES_WITH_MAX.get();
        manaCostBaselineMaxMana = MANA_COST_BASELINE_MAX_MANA.get();
        requireLearnedToCast = REQUIRE_LEARNED_TO_CAST.get();
        requireWandToCast = REQUIRE_WAND_TO_CAST.get();
        restoreWandOnLogin = RESTORE_WAND_ON_LOGIN.get();
        manaRegenIntervalTicks = MANA_REGEN_INTERVAL_TICKS.get();
        manaRegenDelayAfterCastTicks = MANA_REGEN_DELAY_AFTER_CAST.get();
    }

    /** 第 tier 级（1..5）法术需要投多少魔法点数。 */
    public static int learnCostForTier(int tier) {
        return valueForTier(learnCostPerTier, tier, tier);
    }

    /** 第 tier 级（1..5）法术施放一次消耗多少魔力（消耗表的原始值，基准上限下的消耗）。 */
    public static int manaCostForTier(int tier) {
        return valueForTier(manaCostPerTier, tier, tier * 20);
    }

    /**
     * 第 tier 级法术在<b>某个魔力上限</b>下实际要花多少魔力。
     *
     * <p>消耗表是固定值，而上限会随等级一直涨 —— 直接用手册值就会出现
     * "等级越高法术越便宜"（上限 620 时一级法术只花上限的 3%，HUD 上根本看不见）。
     * 所以按 {@link #manaCostBaselineMaxMana} 等比放大：
     * {@code 实际消耗 = 表里的值 × 上限 ÷ 基准上限}。
     *
     * <p>基准上限下结果和表里完全一致，所以自检/探针（用的是基准上限的假数据）不受影响。
     */
    public static int manaCostForTier(int tier, int maxMana) {
        int base = manaCostForTier(tier);
        if (!manaCostScalesWithMaxMana || base <= 0) {
            return base;
        }
        int baseline = Math.max(1, manaCostBaselineMaxMana);
        // 用 double 算再四舍五入，顺便防溢出；至少 1 点，别让高等级把消耗放大成 0
        long scaled = Math.round((double) base * Math.max(0, maxMana) / baseline);
        return (int) Math.max(1L, scaled);
    }

    /**
     * <b>每回一次</b>恢复多少魔力 = 固定值 + 上限的百分比。
     *
     * <p>实际速度还要除以周期 {@link #manaRegenIntervalTicks}。
     * 例：上限 210、固定 1、百分比 5 → 每次 1 + 10 = 11；
     * 周期 20 → 11/秒；周期 40（默认）→ 5.5/秒，回满约 38 秒。
     */
    public static int manaRegenFor(int maxMana) {
        int flat = Math.max(0, manaRegenPerSecond);
        int percent = (int) Math.floor(Math.max(0, maxMana) * Math.max(0.0D, manaRegenPercentPerSecond) / 100.0D);
        return flat + percent;
    }

    /** 实际每秒恢复多少（给自检/界面显示用，含周期换算）。 */
    public static double manaRegenPerSecondFor(int maxMana) {
        int interval = Math.max(1, manaRegenIntervalTicks);
        return manaRegenFor(maxMana) * 20.0D / interval;
    }

    private static int valueForTier(List<? extends Integer> table, int tier, int fallback) {
        if (table == null || table.isEmpty()) {
            return fallback;
        }
        int index = Math.max(0, Math.min(table.size() - 1, tier - 1));
        Integer value = table.get(index);
        return value == null ? fallback : value;
    }
}
