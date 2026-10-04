package com.tnc.tnc.boss;

/**
 * <b>巨兽人领主·二阶段</b>的规则与数值 ✓ —— 纯计算，不碰 Minecraft 类型 ✓
 * （所以离线测试 {@code DarkGiantPhaseTest} 能直接验 ✓）。
 *
 * <h2>作者 2026-10-03 的要求</h2>
 * "我希望他有<b>二阶段</b>，二阶段的时候他的<b>背后会出现他自己模型的虚影</b>
 * （他自己的<b>两倍高</b>），<b>虚影跟他做一样的动作</b>" ✓。
 *
 * <p>三件事分别落在：
 * <ol>
 *   <li><b>什么时候进二阶段</b> → {@link #shouldEnterPhaseTwo}（血量掉到 {@link #PHASE_TWO_AT} 以下 ✓，
 *       而且**只会进一次** ✗ 不会来回横跳 ✓）；</li>
 *   <li><b>虚影多大、站哪</b> → {@link #PHANTOM_SCALE}（2.0 = 本体两倍高 ✓）、
 *       {@link #PHANTOM_BACK}（往**背后**推多少格 ✓）；</li>
 *   <li><b>"跟他做一样的动作"</b> → 这是**渲染**上的事 ✓：虚影不是另一个实体 ✗，
 *       而是把**同一个已经摆好动作的模型**再画一遍 ✓
 *       （{@code TNDarkGiantPhantomLayer} 里调 GeckoLib 的 {@code reRender} ✓）——
 *       所以动作永远和本体一模一样 ✓，一个字节都不用同步 ✓。</li>
 * </ol>
 */
public final class TNDarkGiantPhase {

    /** 血量掉到这个比例（含）以下就进二阶段 ✓（3000 血 ⇒ 1500 ✓）。 */
    public static final float PHASE_TWO_AT = 0.5F;

    /** 一阶段 ✓。 */
    public static final int PHASE_ONE = 0;
    /** 二阶段 ✓（背后站着虚影 ✓）。 */
    public static final int PHASE_TWO = 1;

    /** 虚影的缩放：<b>2.0 = 本体两倍高</b> ✓（作者原话 ✓）。 */
    public static final double PHANTOM_SCALE = 2.0D;

    /** 虚影站在**背后**多少格 ✓（模型 +Z 是背 ✓；1.6 格 ⇒ 刚好从本体身后长出来 ✓）。 */
    public static final double PHANTOM_BACK = 1.6D;

    /** 虚影的颜色（暗紫）+ 透明度 ✓（半透明才叫"虚影" ✓）。 */
    public static final float PHANTOM_RED = 0.58F;
    public static final float PHANTOM_GREEN = 0.30F;
    public static final float PHANTOM_BLUE = 0.95F;
    /** 基础透明度 ✓（再叠一个 0.1 的呼吸 ✓）。 */
    public static final float PHANTOM_ALPHA = 0.34F;

    /** 二阶段本体的加成：移速 ×1.15 / 攻击 ×1.25 ✓（进了二阶段就该更凶 ✓）。 */
    public static final double PHASE_TWO_SPEED_MULTIPLIER = 1.15D;
    public static final double PHASE_TWO_DAMAGE_MULTIPLIER = 1.25D;

    private TNDarkGiantPhase() {
    }

    /**
     * 这一刻该不该切进二阶段 ✓。
     *
     * @param health 当前血量
     * @param maxHealth 最大血量
     * @param phase 现在已经处在的阶段（{@link #PHASE_ONE} / {@link #PHASE_TWO} ✓）
     * @return 只在"还没进过二阶段"且"血量掉到线以下"时为 true ✓（⇒ 一辈子只会触发一次 ✓）
     */
    public static boolean shouldEnterPhaseTwo(float health, float maxHealth, int phase) {
        if (phase >= PHASE_TWO || maxHealth <= 0.0F) {
            return false;
        }
        return health > 0.0F && health <= maxHealth * PHASE_TWO_AT;
    }

    /** 客户端该不该画那个虚影 ✓（只有二阶段画 ✓）。 */
    public static boolean phantomVisible(int phase) {
        return phase >= PHASE_TWO;
    }

    /** 虚影的呼吸透明度 ✓（让虚影"活"一点 ✗ 别像块塑料 ✓）。 */
    public static float phantomAlpha(int tickCount) {
        return PHANTOM_ALPHA + 0.10F * (float) Math.sin(tickCount * 0.15D);
    }

    /** 供日志/自检看的一行摘要 ✓。 */
    public static String describe() {
        return String.format(java.util.Locale.ROOT,
                "二阶段: 血量<=%.0f%% | 虚影 %.1f倍高 · 背后 %.1f 格 · alpha=%.2f",
                PHASE_TWO_AT * 100.0F, PHANTOM_SCALE, PHANTOM_BACK, PHANTOM_ALPHA);
    }
}
