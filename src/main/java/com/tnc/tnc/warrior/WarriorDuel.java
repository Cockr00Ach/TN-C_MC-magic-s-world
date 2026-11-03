package com.tnc.tnc.warrior;

/**
 * 对拼判定：<b>纯规则，不带任何 Minecraft 类型</b>，所以每条克制关系都能在 JUnit 里验。
 *
 * <p>规则来自用户口述，落成三条：
 *
 * <ol>
 *   <li><b>振刀架住进攻</b>：攻击链/突击链打到正在振刀的人 → 这一下不掉血，
 *       出手的人整段连招作废并僵直 1 秒；</li>
 *   <li><b>破架破振刀</b>：破架招式（逆鳞斩 / 铁壁 / 断招）打到正在振刀的人 →
 *       打散架势、撤掉抗性，对方僵直 2 秒；</li>
 *   <li><b>进攻 ↔ 破架 互相中断</b>：谁的连招伤害先落地谁赢，后手那一方僵直 0.25 秒。</li>
 * </ol>
 *
 * <p>"先落地"用时间差判断：{@link #CLASH_WINDOW} tick 之内都算同一回合。
 */
public final class WarriorDuel {

    /** 被振刀架住：僵直 1 秒。 */
    public static final int PARRY_STAGGER = 20;
    /** 振刀被破架打散：僵直 2 秒。 */
    public static final int BREAK_STAGGER = 40;
    /** 对拼后手：僵直 0.25 秒。 */
    public static final int CLASH_STAGGER = 5;
    /** 对拼窗口：对方的连招伤害落在这个 tick 数内才算法"同一回合"。 */
    public static final int CLASH_WINDOW = 20;

    /** 一次命中可能触发的克制结果。 */
    public enum Outcome {
        NONE(0),
        /** 自己被打散：整段连招作废。 */
        PARRIED(PARRY_STAGGER),
        /** 对方的振刀被打散。 */
        STANCE_BROKEN(BREAK_STAGGER),
        /** 对拼慢半拍：自己被打断。 */
        CLASH_LOST(CLASH_STAGGER);

        private final int staggerTicks;

        Outcome(int staggerTicks) {
            this.staggerTicks = staggerTicks;
        }

        /** 要压多少 tick 僵直（施在谁身上由调用方决定）。 */
        public int staggerTicks() {
            return staggerTicks;
        }
    }

    private WarriorDuel() {
    }

    /**
     * 伤害结算<b>之前</b>先问一句：这一下会不会被振刀架住？
     *
     * @param role          自己这一招的定位
     * @param victimInStance 目标此刻是否处在振刀架势里
     */
    public static Outcome beforeHit(WarriorComboRole role, boolean victimInStance) {
        if (role != null && role.parriedByStance() && victimInStance) {
            return Outcome.PARRIED;
        }
        return Outcome.NONE;
    }

    /**
     * 伤害结算<b>之后</b>结算克制：破架打散振刀、进攻与破架的先后手。
     *
     * @param role              自己这一招的定位
     * @param victimInStance    目标命中时是否还在振刀架势里
     * @param victimLastRole    目标最近一次连招伤害的定位（没打过就是 {@link WarriorComboRole#NEUTRAL}）
     * @param sinceVictimCombo  距离目标那次连招伤害过了多少 tick（没打过传 {@link Long#MAX_VALUE}）
     */
    public static Outcome afterHit(WarriorComboRole role, boolean victimInStance,
                                   WarriorComboRole victimLastRole, long sinceVictimCombo) {
        if (role == null || role == WarriorComboRole.NEUTRAL) {
            return Outcome.NONE;
        }
        if (role.breaksStance() && victimInStance) {
            return Outcome.STANCE_BROKEN;
        }
        WarriorComboRole rival = role.rival();
        if (rival == WarriorComboRole.NEUTRAL || victimLastRole != rival) {
            return Outcome.NONE;
        }
        // 对方的连招先落地 → 自己这一下算慢了半拍
        return sinceVictimCombo >= 0 && sinceVictimCombo <= CLASH_WINDOW ? Outcome.CLASH_LOST : Outcome.NONE;
    }
}
