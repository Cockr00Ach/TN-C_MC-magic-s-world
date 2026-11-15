package com.tnc.tnc.boss;

/**
 * ★ <b>Boss 在专属维度里的"脱战距离"</b>（2026-11-15 作者：每个 boss 在自己专属的维度里，
 * 玩家离开多少格才会脱战「应该非常的远」）✓
 *
 * <h2>什么是"脱战距离"</h2>
 * 原版 Mob 靠 {@code Attributes.FOLLOW_RANGE} 决定"能咬住多远的玩家"：
 * 目标一旦跑出这个范围，{@code TargetGoal} 就不再续用 ⇒ {@code getTarget()} 被清空 ⇒ 脱战。
 * 所以"玩家跑多远才脱战" ≈ 这个属性 ✓
 *
 * <h2>为什么 Boss 要特别远</h2>
 * 这些 Boss 都住在<b>自己的维度</b>里（风原、火山、海渊、石原、墨境、衍的暗域…）——
 * 玩家进维度就是来打它的，跑到地图另一头再跑回来会非常烦 ✗。
 * 原值 40~400 不等（阿波罗只有 40 ✗），玩家绕个圈就脱战。
 *
 * <p>现在统一成 <b>{@link #ARENA_FOLLOW_RANGE} = 512 格</b>：
 * 专属维度一般就几百格见方 ⇒ 实际上等于"<b>只要还在这个维度里，就不会脱战</b>"✓。
 */
public final class BossLeash {

    /** 所有专属维度 Boss 的跟随/脱战距离（格）✓ —— 比任何专属维度都大 ⇒ 出了维度才真脱战。 */
    public static final double ARENA_FOLLOW_RANGE = 512.0D;

    /**
     * 追击上限（格）✓ —— Boss 会"追到这么远"就停下等，但<b>不脱战</b>（目标不清）✓。
     * 留一点余量比 FOLLOW_RANGE 小，免得它跨半个维度撞墙卡住 ✗。
     */
    public static final double ARENA_CHASE_RANGE = 448.0D;

    /** 之前最远的 Boss 是石枪的 400 格 ✓ —— 新的统一值必须比它更远。 */
    public static final double PREVIOUS_MAX = 400.0D;

    private BossLeash() {
    }

    /** 这个距离还算"在战斗里"吗 ✓（玩家离 Boss 多远之内不会脱战）。 */
    public static boolean stillEngaged(double distance) {
        return distance <= ARENA_FOLLOW_RANGE;
    }

    /** 该不该继续追（追到 {@link #ARENA_CHASE_RANGE} 就守在原地看着）✓。 */
    public static boolean shouldChase(double distance) {
        return distance <= ARENA_CHASE_RANGE;
    }
}
