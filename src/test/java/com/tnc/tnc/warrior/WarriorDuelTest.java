package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 对拼规则：振刀架进攻、破架破振刀、进攻 ↔ 破架 的先后手。
 *
 * <p>规则是纯函数（{@link WarriorDuel}），所以这里直接把每种组合摆出来验，
 * 不用起服务器、不用两个真玩家。
 */
class WarriorDuelTest {

    @Test
    void attackIntoStanceIsParriedForOneSecond() {
        assertEquals(WarriorDuel.Outcome.PARRIED,
                WarriorDuel.beforeHit(WarriorComboRole.OFFENSE, true));
        assertEquals(20, WarriorDuel.PARRY_STAGGER);
        assertEquals(WarriorDuel.PARRY_STAGGER, WarriorDuel.Outcome.PARRIED.staggerTicks());
    }

    @Test
    void stanceDoesNotParryBreakerOrFellowStance() {
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.beforeHit(WarriorComboRole.BREAKER, true), "破架不受振刀影响");
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.beforeHit(WarriorComboRole.STANCE, true), "振刀之间不互相架");
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.beforeHit(WarriorComboRole.OFFENSE, false), "没架刀就照常吃伤害");
    }

    @Test
    void breakerShattersTheStanceForTwoSeconds() {
        assertEquals(WarriorDuel.Outcome.STANCE_BROKEN,
                WarriorDuel.afterHit(WarriorComboRole.BREAKER, true, WarriorComboRole.NEUTRAL, Long.MAX_VALUE));
        assertEquals(40, WarriorDuel.BREAK_STAGGER);
        assertEquals(WarriorDuel.BREAK_STAGGER, WarriorDuel.Outcome.STANCE_BROKEN.staggerTicks());
        // 对方没在振刀，破架就只是普通一刀
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.afterHit(WarriorComboRole.BREAKER, false, WarriorComboRole.NEUTRAL, Long.MAX_VALUE));
    }

    @Test
    void offenseAndBreakerInterruptWhicheverLandedLater() {
        // 我先出手，但对方上一 tick 刚用逆鳞斩打到人 → 我慢半拍
        assertEquals(WarriorDuel.Outcome.CLASH_LOST,
                WarriorDuel.afterHit(WarriorComboRole.OFFENSE, false, WarriorComboRole.BREAKER, 1));
        // 反过来也一样：对方的攻击链先落地，我这记破架就慢了
        assertEquals(WarriorDuel.Outcome.CLASH_LOST,
                WarriorDuel.afterHit(WarriorComboRole.BREAKER, false, WarriorComboRole.OFFENSE, 1));
        assertEquals(5, WarriorDuel.CLASH_STAGGER);
        assertEquals(WarriorDuel.CLASH_STAGGER, WarriorDuel.Outcome.CLASH_LOST.staggerTicks());
    }

    @Test
    void clashWindowClosesAfterOneSecond() {
        assertEquals(WarriorDuel.Outcome.CLASH_LOST,
                WarriorDuel.afterHit(WarriorComboRole.OFFENSE, false, WarriorComboRole.BREAKER, WarriorDuel.CLASH_WINDOW));
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.afterHit(WarriorComboRole.OFFENSE, false, WarriorComboRole.BREAKER, WarriorDuel.CLASH_WINDOW + 1));
        // 对方那一下是另一种定位（例如振刀），不构成对拼
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.afterHit(WarriorComboRole.OFFENSE, false, WarriorComboRole.STANCE, 1));
        // 从没打过连招
        assertEquals(WarriorDuel.Outcome.NONE,
                WarriorDuel.afterHit(WarriorComboRole.OFFENSE, false, WarriorComboRole.BREAKER, Long.MAX_VALUE));
    }

    @Test
    void parryAndBreakAreMutuallyExclusive() {
        // 振刀同时只能被一种规则命中：进攻被架住时不结算破架，破架打散时也不吃 0.25 秒
        assertEquals(WarriorDuel.Outcome.STANCE_BROKEN,
                WarriorDuel.afterHit(WarriorComboRole.BREAKER, true, WarriorComboRole.OFFENSE, 1));
        assertEquals(WarriorDuel.Outcome.PARRIED,
                WarriorDuel.beforeHit(WarriorComboRole.OFFENSE, true));
    }
}
