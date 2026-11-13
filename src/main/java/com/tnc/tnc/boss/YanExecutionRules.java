package com.tnc.tnc.boss;

/**
 * ★ <b>衍的处决演出</b>（2026-11-14 作者：整段分镜）✓
 *
 * <p>作者原话：「为他做一个处决动作，首先对衍特写，衍脚下展开魔法阵，然后玩家脚下出现法阵和雷网
 * 并且被禁锢不能动了，此时特写给到玩家，然后特写给到天空，暗示即将有一道极其高伤害的雷电要杀死玩家，
 * 然后雷电劈向玩家，此时我希望这个雷电从出现到劈中玩家是一个慢动作特写镜头，也就是镜头跟着雷电的末端
 * 然后大概耗时 3 秒才劈中玩家，然后玩家身上爆出无数雷电，秒杀技能」
 * 「这个技能只在衍的第二阶段，生命值掉落到 75% 的时候释放一次，只能释放那一次哦」
 *
 * <h2>分镜表（tick）</h2>
 * <table border="1">
 *   <tr><th>段</th><th>tick</th><th>画面</th></tr>
 *   <tr><td>0 · 衍特写</td><td>0~20</td><td>镜头贴到衍的脸；衍脚下展开暗系大法阵</td></tr>
 *   <tr><td>1 · 玩家特写</td><td>20~40</td><td>玩家脚下法阵 ＋ 雷网；玩家被定住</td></tr>
 *   <tr><td>2 · 抬头看天</td><td>40~55</td><td>镜头抬向天空，云里有雷在聚（暗示）</td></tr>
 *   <tr><td>3 · 慢动作落雷</td><td>55~115</td><td><b>3 秒</b>：镜头跟着雷的末端往下坠</td></tr>
 *   <tr><td>4 · 命中</td><td>115~140</td><td>玩家身上炸出无数雷电 ⇒ 秒杀</td></tr>
 *   <tr><td>结束</td><td>140</td><td>镜头交还玩家，衍恢复</td></tr>
 * </table>
 *
 * <p>纯数学（不碰 MC 类）⇒ 单元测试直接钉住"整段 140 tick、落雷正好 3 秒、只在 75% 触发一次"✓
 */
public final class YanExecutionRules {

    /** 二阶段血量掉到这个比例时触发一次 ✓（作者：75%）。 */
    public static final float TRIGGER_HEALTH_RATIO = 0.75F;

    /** 各段长度（tick）✓。 */
    public static final int BOSS_CLOSEUP = 20;
    public static final int PLAYER_CLOSEUP = 20;
    public static final int SKY_LOOK = 15;
    /** 落雷 60 tick ＝ <b>3 秒</b> ✓（作者："大概耗时 3 秒才劈中玩家"）。 */
    public static final int FALL = 60;
    public static final int IMPACT = 25;

    /** 段起点 ✓。 */
    public static final int AT_PLAYER = BOSS_CLOSEUP;
    public static final int AT_SKY = AT_PLAYER + PLAYER_CLOSEUP;
    public static final int AT_FALL = AT_SKY + SKY_LOOK;
    public static final int AT_IMPACT = AT_FALL + FALL;
    public static final int END = AT_IMPACT + IMPACT;

    /** 雷从多高落下来（格）✓。 */
    public static final double SKY_HEIGHT = 42.0D;

    /** 处决伤害（玩家 20 点血也必死；对高血量模组同理）✓。 */
    public static final float EXECUTION_DAMAGE = 10000.0F;

    public static final int STAGE_BOSS = 0;
    public static final int STAGE_PLAYER = 1;
    public static final int STAGE_SKY = 2;
    public static final int STAGE_FALL = 3;
    public static final int STAGE_IMPACT = 4;
    public static final int STAGE_DONE = 5;

    private YanExecutionRules() {
    }

    /** 现在演到哪一段 ✓。 */
    public static int stage(int tick) {
        if (tick < 0) {
            return STAGE_DONE;
        }
        if (tick < AT_PLAYER) {
            return STAGE_BOSS;
        }
        if (tick < AT_SKY) {
            return STAGE_PLAYER;
        }
        if (tick < AT_FALL) {
            return STAGE_SKY;
        }
        if (tick < AT_IMPACT) {
            return STAGE_FALL;
        }
        if (tick < END) {
            return STAGE_IMPACT;
        }
        return STAGE_DONE;
    }

    /** 落雷进度 0..1（0 = 刚出现，1 = 正好劈到）✓。 */
    public static double fallProgress(int tick) {
        if (tick <= AT_FALL) {
            return 0.0D;
        }
        double p = (tick - AT_FALL) / (double) FALL;
        return Math.max(0.0D, Math.min(1.0D, p));
    }

    /** 雷末端（镜头跟的那个点）的高度 ✓ —— 从天上匀速落到地面。 */
    public static double boltTipY(double skyY, double groundY, int tick) {
        double p = fallProgress(tick);
        return skyY + (groundY - skyY) * p;
    }

    /** 这一段镜头在谁身上/看哪（0 = 衍、1 = 玩家、2 = 天空、3 = 雷末端）✓。 */
    public static int cameraFocus(int tick) {
        return switch (stage(tick)) {
            case STAGE_BOSS -> 0;
            case STAGE_PLAYER, STAGE_IMPACT -> 1;
            case STAGE_SKY -> 2;
            case STAGE_FALL -> 3;
            default -> 1;
        };
    }

    /**
     * 要不要触发处决 ✓ —— 二阶段 ＋ 没用过 ＋ 血量 ≤ 75% ＋ 有活着的玩家目标。
     *
     * <p>⚠️ "只能释放那一次" 由 {@code used} 兜住：用过之后哪怕血量再掉、再回到二阶段也不再触发 ✓。
     */
    public static boolean shouldTrigger(int phase, float health, float maxHealth, boolean used,
                                        boolean hasTarget) {
        if (used || !hasTarget || maxHealth <= 0.0F) {
            return false;
        }
        return phase >= 2 && health <= maxHealth * TRIGGER_HEALTH_RATIO + 1.0E-3F;
    }

    /** 演出结束（应该把镜头还回去、解除禁锢）✓。 */
    public static boolean finished(int tick) {
        return tick >= END;
    }
}
