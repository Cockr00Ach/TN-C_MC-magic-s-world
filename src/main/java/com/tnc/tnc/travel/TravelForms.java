package com.tnc.tnc.travel;

import com.tnc.tnc.magic.Element;

/**
 * 元素附身的纯数值表与规则。
 *
 * <p>这里<b>不碰 Minecraft 类</b>，方便单元测试。服务端只从这张表取速度/时长/消耗，
 * 客户端 HUD 也只从这里取显示名与颜色，避免两边各写一份数字。
 *
 * <p>M1 只开放<b>雷 / 暗</b>两种形态；其余元素的数值先放在表里，等 M2/M3
 * 再接 {@link #supported(Element)}。这样做法杖 tooltip 和命令都能提前看到完整设计，
 * 但玩家不会误进一个还没做完的形态。
 */
public final class TravelForms {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;

    /** 进入消耗：8% 最大魔力（至少 1 点）。 */
    public static final double ENTRY_MANA_FRACTION = 0.08D;
    private static final int[] DURATION_TICKS = {200, 280, 360, 480, 600};
    private static final int[] COOLDOWN_TICKS = {160, 200, 240, 300, 400};
    private static final double[] DRAIN_PERCENT_PER_SECOND = {1.2D, 1.5D, 1.8D, 2.1D, 2.4D};

    private static final double[] LIGHTNING_SPEED = {14D, 16D, 18D, 20D, 22D};
    private static final double[] DARK_SPEED = {6D, 7D, 7.5D, 8.5D, 9D};
    private static final double[] FIRE_SPEED = {12D, 13D, 15D, 17D, 19D};
    private static final double[] WATER_SPEED = {8D, 9D, 10D, 11D, 12D};
    private static final double[] WIND_SPEED = {11D, 12.5D, 14D, 15.5D, 17D};
    /**
     * 土/光原本设计成“目标式”（一次位移），2026-11-08 先按飞行形态放给玩家，
     * 好让七系都能直接试外观；速度表是这一版新加的，等目标式位移做好再替换。
     */
    private static final double[] EARTH_SPEED = {9D, 10.5D, 12D, 13.5D, 15D};
    private static final double[] LIGHT_SPEED = {16D, 18D, 20D, 22D, 24D};

    private static final int[] DARK_PHASE = {2, 3, 4, 5, 6};
    private static final int[] EARTH_DISTANCE = {24, 32, 40, 52, 64};
    private static final int[] LIGHT_DISTANCE = {16, 22, 28, 36, 45};

    private TravelForms() {
    }

    /** 当前已开放附身的元素。2026-11-08 起七系全开。 */
    public static boolean supported(Element element) {
        return element != null;
    }

    /** 七系都是飞行形态（土/光的目标式位移留到打磨阶段再替换）。 */
    public static boolean airForm(Element element) {
        return element != null;
    }

    /** 法杖阶位与亲和力共同决定实际能发挥的阶位；亲和力 0 = 不能附身。 */
    public static int effectiveTier(int staffTier, int affinity) {
        if (affinity <= 0 || staffTier <= 0) {
            return 0;
        }
        return Math.min(Math.min(staffTier, affinity), MAX_TIER);
    }

    public static int index(int tier) {
        return Math.max(0, Math.min(MAX_TIER - 1, tier - 1));
    }

    /** 形态持续时长（tick）。风/暗 +20%，火 -10%。 */
    public static int durationTicks(Element element, int tier) {
        double value = DURATION_TICKS[index(tier)];
        if (element == Element.WIND || element == Element.DARK) {
            value *= 1.2D;
        } else if (element == Element.FIRE) {
            value *= 0.9D;
        }
        return Math.max(1, (int) Math.round(value));
    }

    /** 退出后的冷却（tick），从退出瞬间开始。 */
    public static int cooldownTicks(int tier) {
        return COOLDOWN_TICKS[index(tier)];
    }

    /** 每秒消耗最大魔力的百分比。土/光把消耗压到 25%，主要花在单次技能上。 */
    public static double drainPercentPerSecond(Element element, int tier) {
        double value = DRAIN_PERCENT_PER_SECOND[index(tier)];
        return value * switch (element) {
            case WIND -> 0.8D;
            case FIRE -> 1.15D;
            case DARK -> 1.0D;
            case WATER -> 0.9D;
            case EARTH, LIGHT -> 0.25D;
            default -> 1.0D;
        };
    }

    /** 空中形态的目标速度（格/秒）。 */
    public static double speed(Element element, int tier) {
        return switch (element) {
            case LIGHTNING -> LIGHTNING_SPEED[index(tier)];
            case DARK -> DARK_SPEED[index(tier)];
            case FIRE -> FIRE_SPEED[index(tier)];
            case WATER -> WATER_SPEED[index(tier)];
            case WIND -> WIND_SPEED[index(tier)];
            case EARTH -> EARTH_SPEED[index(tier)];
            case LIGHT -> LIGHT_SPEED[index(tier)];
        };
    }

    /**
     * 把“格/秒”换算成 {@code Abilities.flyingSpeed}。
     *
     * <p>原版 0.05 约等于 10.9 格/秒；下限必须是 0.02，不能沿用原版 0.05，
     * 否则暗/水的低速会被抬到 10.9 格/秒。
     */
    public static float flyingSpeed(double blocksPerSecond) {
        double value = 0.05D * Math.max(0D, blocksPerSecond) / 10.9D;
        return (float) Math.max(0.02D, Math.min(0.14D, value));
    }

    /** 暗形态单次相位最多穿过几格。 */
    public static int phaseBlocks(int tier) {
        return DARK_PHASE[index(tier)];
    }

    /** 土遁单次距离（M3 用）。 */
    public static int earthDistance(int tier) {
        return EARTH_DISTANCE[index(tier)];
    }

    /** 光行单次距离（M2 用）。 */
    public static int lightDistance(int tier) {
        return LIGHT_DISTANCE[index(tier)];
    }

    public static int entryMana(int maxMana) {
        return Math.max(1, (int) Math.ceil(Math.max(0, maxMana) * ENTRY_MANA_FRACTION));
    }

    /**
     * 形态技的速度倍率。土/光的目标式位移不吃倍率（它们是一次瞬移，不是加速）。
     */
    public static double skillSpeedMultiplier(Element element) {
        return switch (element) {
            case LIGHTNING -> 1.5D;
            case FIRE -> 1.6D;
            case WATER -> 1.4D;
            case WIND -> 1.3D;
            case DARK, EARTH, LIGHT -> 1.0D;
        };
    }

    /** 形态技的加速持续 tick；土/光没有加速段。 */
    public static int skillBoostTicks(Element element) {
        return switch (element) {
            case LIGHTNING, FIRE, WIND -> 30;
            case WATER -> 40;
            case DARK, EARTH, LIGHT -> 0;
        };
    }

    /** 形态技冷却（tick）。影返每次附身只能用一次，不靠冷却，所以是 0。 */
    public static int skillCooldownTicks(Element element) {
        return switch (element) {
            case LIGHTNING -> 120;
            case FIRE -> 140;
            case WATER -> 120;
            case WIND -> 100;
            case EARTH -> 200;
            case LIGHT -> 160;
            case DARK -> 0;
        };
    }

    public static int skillMana(Element element, int maxMana) {
        double fraction = element == Element.DARK ? 0.10D : 0.08D;
        return Math.max(1, (int) Math.ceil(Math.max(0, maxMana) * fraction));
    }

    /** 元素显示名。{@link Element#DARK} 的 {@code cn()} 是 "dark"，这里按项目习惯写“暗”。 */
    public static String elementName(Element element) {
        return element == Element.DARK ? "暗" : element.cn();
    }

    /** 形态名（玩家看到的名字）。 */
    public static String formName(Element element) {
        return switch (element) {
            case LIGHTNING -> "雷驰";
            case DARK -> "幽行";
            case FIRE -> "焰行";
            case WATER -> "雾行";
            case WIND -> "御风";
            case EARTH -> "土遁";
            case LIGHT -> "光行";
        };
    }

    /** 形态技名（tooltip / HUD 用；和 {@code TravelFormService.skill} 里的提示同字）。 */
    public static String skillName(Element element) {
        return switch (element) {
            case LIGHTNING -> "雷冲";
            case DARK -> "影返";
            case FIRE -> "爆燃";
            case WATER -> "顺流";
            case WIND -> "乘风";
            case EARTH -> "潜地";
            case LIGHT -> "光跃";
        };
    }

    // ------------------------------------------------------------------
    //  环境特征（作者 2026-11-09 口径）
    // ------------------------------------------------------------------

    /** 风泡在水里多久会被"打散"强制退出（tick）。 */
    public static final int WIND_WATER_DISSOLVE_TICKS = 60;

    /**
     * 环境速度倍率：水/雷入水更快，水在岸上更慢，其余不受影响。
     *
     * <p>这是"环境特征"的第一条：赶路能力得和地形有关，否则七系只是七种颜色 ✗。
     *
     * @param wet 身上是湿的（在水里或在下雨）
     */
    public static double environmentSpeed(Element element, boolean inWater, boolean wet) {
        return switch (element) {
            case WATER -> inWater ? 1.45D : (wet ? 1.00D : 0.82D);
            case LIGHTNING -> inWater ? 1.35D : 1.00D;
            default -> 1.00D;
        };
    }

    /**
     * 当前环境能不能进入这个形态。
     *
     * @return {@code null} = 可以；否则是给玩家看的拒绝原因
     */
    public static String entryBlocked(Element element, boolean inWater, boolean wet,
                                      boolean night, boolean underground, boolean nether) {
        if (nether) {
            return "下界元素不稳定，无法附身（但跳得更高）";
        }
        if (element == Element.FIRE && (inWater || wet)) {
            return "火遇水会灭，进不了焰行";
        }
        if (element == Element.DARK && !night && !underground) {
            return "幽行只能在夜里或地下附身";
        }
        return null;
    }

    /** HUD/拖尾用的 ARGB 元素色。 */
    public static int color(Element element) {
        return switch (element) {
            case LIGHTNING -> 0xFF8BE9FD;
            case DARK -> 0xFF7B4FBF;
            case FIRE -> 0xFFFF7A3D;
            case WATER -> 0xFF4FA3E8;
            case WIND -> 0xFFB7F0E8;
            case EARTH -> 0xFFC9A06A;
            case LIGHT -> 0xFFFFE9A8;
        };
    }
}
