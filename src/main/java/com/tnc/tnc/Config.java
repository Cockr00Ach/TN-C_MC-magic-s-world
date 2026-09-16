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
                    "设计文档给的基准是「火球耗 50 魔力」，所以一级定 20、神级 160（1/3 左右的血量感）。")
            .defineListAllowEmpty("manaCostPerTier", List.of(20, 40, 60, 100, 160),
                    o -> o instanceof Integer i && i >= 0);

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
    public static boolean requireLearnedToCast;
    public static boolean requireWandToCast;
    public static boolean restoreWandOnLogin;
    public static int manaRegenIntervalTicks;

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
        requireLearnedToCast = REQUIRE_LEARNED_TO_CAST.get();
        requireWandToCast = REQUIRE_WAND_TO_CAST.get();
        restoreWandOnLogin = RESTORE_WAND_ON_LOGIN.get();
        manaRegenIntervalTicks = MANA_REGEN_INTERVAL_TICKS.get();
    }

    /** 第 tier 级（1..5）法术需要投多少魔法点数。 */
    public static int learnCostForTier(int tier) {
        return valueForTier(learnCostPerTier, tier, tier);
    }

    /** 第 tier 级（1..5）法术施放一次消耗多少魔力。 */
    public static int manaCostForTier(int tier) {
        return valueForTier(manaCostPerTier, tier, tier * 20);
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
