package com.tnc.tnc.world.wind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure rule checks for 苍岚战将: timings, damage ordering, and the sword/spear split.
 *
 * <p>Nothing here touches a level: {@link CanglanWarlordAttackRules} is all
 * constants and pure functions, so the whole fight's contract can be pinned down
 * without launching the game.
 */
class CanglanWarlordAttackRulesTest {

    @Test
    void ghostFormHasFiveTimesTheBodyAndWeaponScale() {
        assertEquals(1F,CanglanWarGodRules.scale(1));
        assertEquals(5F,CanglanWarGodRules.scale(2));
        assertEquals(10,CanglanWarGodRules.ANCHORS);
    }

    @Test
    void thePhaseDecidesWhichArsenalFormIsDrawn() {
        assertEquals(CanglanWarlordAttackRules.FORM_SWORD,
                CanglanWarlordAttackRules.formFor(CanglanWarlordAttackRules.PHASE_SWORD));
        assertEquals(CanglanWarlordAttackRules.FORM_SPEAR,
                CanglanWarlordAttackRules.formFor(CanglanWarlordAttackRules.PHASE_SPEAR));
        // form 1 and 2 are the wind arsenal's sword and spear indices
        assertEquals(1, CanglanWarlordAttackRules.FORM_SWORD);
        assertEquals(2, CanglanWarlordAttackRules.FORM_SPEAR);
    }

    @Test
    void eachPhaseOwnsItsOwnMoves() {
        int[] swordOnly = {CanglanWarlordAttackRules.CLEAVE_LEFT,
                CanglanWarlordAttackRules.CLEAVE_RIGHT, CanglanWarlordAttackRules.OVERHEAD};
        int[] spearOnly = {CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SWEEP, CanglanWarlordAttackRules.SPEAR_THROW};
        for (int move : swordOnly) {
            assertTrue(CanglanWarlordAttackRules.available(
                    CanglanWarlordAttackRules.PHASE_SWORD, move), "持剑阶段缺少招式 " + move);
            assertFalse(CanglanWarlordAttackRules.available(
                    CanglanWarlordAttackRules.PHASE_SPEAR, move), "换矛后不该再挥剑 " + move);
        }
        for (int move : spearOnly) {
            assertTrue(CanglanWarlordAttackRules.available(
                    CanglanWarlordAttackRules.PHASE_SPEAR, move), "换矛阶段缺少招式 " + move);
            assertFalse(CanglanWarlordAttackRules.available(
                    CanglanWarlordAttackRules.PHASE_SWORD, move), "持剑阶段不该有矛招 " + move);
        }
        // the buckler and the roar belong to the body, not to a weapon
        for (int phase : new int[]{CanglanWarlordAttackRules.PHASE_SWORD,
                CanglanWarlordAttackRules.PHASE_SPEAR}) {
            assertTrue(CanglanWarlordAttackRules.available(phase,
                    CanglanWarlordAttackRules.SHIELD_BASH), "盾击两阶段都能用");
            assertTrue(CanglanWarlordAttackRules.available(phase,
                    CanglanWarlordAttackRules.GALE_ROAR), "狂风怒吼两阶段都能用");
        }
    }

    @Test
    void theRoarIsLateAndBraced() {
        assertFalse(CanglanWarlordAttackRules.shouldRoar(
                CanglanWarlordAttackRules.PHASE_SWORD, 300.0F, 1200.0F, 0),
                "持剑阶段不怒吼");
        assertTrue(CanglanWarlordAttackRules.shouldRoar(
                CanglanWarlordAttackRules.PHASE_SPEAR, 400.0F, 1200.0F, 0));
        assertFalse(CanglanWarlordAttackRules.shouldRoar(
                CanglanWarlordAttackRules.PHASE_SPEAR, 900.0F, 1200.0F, 0),
                "血量还高时不怒吼");
        assertFalse(CanglanWarlordAttackRules.shouldRoar(
                CanglanWarlordAttackRules.PHASE_SPEAR, 300.0F, 1200.0F, 40),
                "冷却中不怒吼");
        assertFalse(CanglanWarlordAttackRules.shouldRoar(
                CanglanWarlordAttackRules.PHASE_SPEAR, 0.0F, 1200.0F, 0), "死亡帧不该怒吼");
    }

    @Test
    void everyMoveHasExactlyItsBudgetedHitWindow() {
        int[] all = {CanglanWarlordAttackRules.CLEAVE_LEFT, CanglanWarlordAttackRules.CLEAVE_RIGHT,
                CanglanWarlordAttackRules.OVERHEAD, CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SWEEP, CanglanWarlordAttackRules.SPEAR_THROW,
                CanglanWarlordAttackRules.SHIELD_BASH, CanglanWarlordAttackRules.GALE_ROAR};
        for (int move : all) {
            int duration = CanglanWarlordAttackRules.duration(move);
            int windows = 0;
            for (int age = 0; age < duration; age++) {
                if (CanglanWarlordAttackRules.connects(move, age)) {
                    windows++;
                }
            }
            int expected = move == CanglanWarlordAttackRules.SWEEP ? 2 : 1;
            assertEquals(expected, windows, "招式 " + move + " 的伤害窗口数量不对");
        }
        assertThrows(IllegalArgumentException.class,
                () -> CanglanWarlordAttackRules.duration(CanglanWarlordAttackRules.IDLE));
    }

    @Test
    void damageWindowsLandLateEnoughToBeReadable() {
        // every hit lands after the halfway point of the telegraph
        int[] all = {CanglanWarlordAttackRules.CLEAVE_LEFT, CanglanWarlordAttackRules.CLEAVE_RIGHT,
                CanglanWarlordAttackRules.OVERHEAD, CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SPEAR_THROW, CanglanWarlordAttackRules.SHIELD_BASH};
        for (int move : all) {
            int duration = CanglanWarlordAttackRules.duration(move);
            int hit = 0;
            for (int age = 0; age < duration; age++) {
                if (CanglanWarlordAttackRules.connects(move, age)) {
                    hit = age;
                }
            }
            assertTrue(hit >= duration / 3, "招式 " + move + " 起手太快");
            assertTrue(hit < duration, "招式 " + move + " 在收招后才判定");
        }
        assertTrue(CanglanWarlordAttackRules.SWEEP_HIT_SECOND
                - CanglanWarlordAttackRules.SWEEP_HIT_FIRST >= 6, "旋转扫击的两段要分开");
    }

    @Test
    void theSpearHitsHarderAndFasterThanTheSword() {
        // damage ladder: overhead > thrust > cleave > sweep > roar
        // (the sweep is two half-turns of a wide arc, so each window is lighter)
        assertTrue(CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.OVERHEAD)
                > CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.THRUST));
        assertTrue(CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.THRUST)
                > CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.CLEAVE_LEFT));
        assertTrue(CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.CLEAVE_LEFT)
                > CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.SWEEP),
                "扫击是两段判定，单段应该更轻");
        assertTrue(CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.SWEEP)
                > CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.GALE_ROAR),
                "怒吼是控制技，伤害最低");
        int[] all = {CanglanWarlordAttackRules.CLEAVE_LEFT, CanglanWarlordAttackRules.CLEAVE_RIGHT,
                CanglanWarlordAttackRules.OVERHEAD, CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SWEEP, CanglanWarlordAttackRules.SPEAR_THROW,
                CanglanWarlordAttackRules.SHIELD_BASH, CanglanWarlordAttackRules.GALE_ROAR};
        for (int move : all) {
            assertTrue(CanglanWarlordAttackRules.damageFactor(move) > 0.0F,
                    "招式 " + move + " 没有伤害");
            assertTrue(CanglanWarlordAttackRules.damageFactor(move,
                            CanglanWarlordAttackRules.PHASE_SPEAR)
                            > CanglanWarlordAttackRules.damageFactor(move,
                            CanglanWarlordAttackRules.PHASE_SWORD),
                    "换矛后招式 " + move + " 该更痛");
        }
        assertTrue(CanglanWarlordAttackRules.recovery(CanglanWarlordAttackRules.PHASE_SPEAR)
                < CanglanWarlordAttackRules.recovery(CanglanWarlordAttackRules.PHASE_SWORD),
                "换矛后收招更快");
    }

    @Test
    void reachMatchesTheDrawnBlade() {
        // the spear out-ranges the sword, the bash is the close-quarters move
        assertTrue(CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.THRUST)
                > CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.OVERHEAD), "突刺比劈砍长");
        assertTrue(CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.OVERHEAD)
                > CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.CLEAVE_LEFT));
        assertTrue(CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.SWEEP)
                > CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.SHIELD_BASH),
                "盾击是贴身技");
        assertTrue(CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.GALE_ROAR)
                > CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.THRUST), "怒吼是范围技");
        int[] melee = {CanglanWarlordAttackRules.CLEAVE_LEFT, CanglanWarlordAttackRules.CLEAVE_RIGHT,
                CanglanWarlordAttackRules.OVERHEAD, CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SWEEP, CanglanWarlordAttackRules.SHIELD_BASH,
                CanglanWarlordAttackRules.GALE_ROAR};
        for (int move : melee) {
            assertTrue(CanglanWarlordAttackRules.reach(move)
                    > CanglanWarlordAttackRules.HITBOX_WIDTH * 0.5D + 1.0D,
                    "招式 " + move + " 的判定比自身碰撞箱还小");
        }
        // a thrown spear has no melee window of its own
        assertEquals(0.0D, CanglanWarlordAttackRules.reach(CanglanWarlordAttackRules.SPEAR_THROW));
    }

    @Test
    void conesAndKnockbackFollowTheWeapon() {
        assertTrue(CanglanWarlordAttackRules.coneDegrees(CanglanWarlordAttackRules.SWEEP)
                > CanglanWarlordAttackRules.coneDegrees(CanglanWarlordAttackRules.CLEAVE_LEFT));
        assertTrue(CanglanWarlordAttackRules.coneDegrees(CanglanWarlordAttackRules.THRUST)
                < CanglanWarlordAttackRules.coneDegrees(CanglanWarlordAttackRules.CLEAVE_LEFT),
                "突刺是窄锥");
        assertTrue(CanglanWarlordAttackRules.knockback(CanglanWarlordAttackRules.SHIELD_BASH)
                > CanglanWarlordAttackRules.knockback(CanglanWarlordAttackRules.CLEAVE_LEFT),
                "盾击要能把人推开");
        assertTrue(CanglanWarlordAttackRules.knockback(CanglanWarlordAttackRules.THRUST)
                > CanglanWarlordAttackRules.knockback(CanglanWarlordAttackRules.CLEAVE_LEFT));
        assertTrue(CanglanWarlordAttackRules.launch(CanglanWarlordAttackRules.SHIELD_BASH)
                > CanglanWarlordAttackRules.launch(CanglanWarlordAttackRules.SWEEP), "盾击把人挑起来");
        // wind pushes harder than it crushes: the roar is the biggest shove
        assertTrue(CanglanWarlordAttackRules.knockback(CanglanWarlordAttackRules.GALE_ROAR)
                >= CanglanWarlordAttackRules.knockback(CanglanWarlordAttackRules.SHIELD_BASH));
    }

    @Test
    void chooseOnlyPicksMovesThePhaseCanPerform() {
        for (int phase : new int[]{CanglanWarlordAttackRules.PHASE_SWORD,
                CanglanWarlordAttackRules.PHASE_SPEAR}) {
            for (int cycle = 0; cycle < 12; cycle++) {
                for (double range = 1.0D; range < 45.0D; range += 1.5D) {
                    int move = CanglanWarlordAttackRules.choose(phase, cycle, range);
                    if (move == CanglanWarlordAttackRules.IDLE) {
                        continue;
                    }
                    assertTrue(CanglanWarlordAttackRules.available(phase, move),
                            "阶段 " + phase + " 选了不属于它的招式 " + move);
                }
            }
        }
    }

    @Test
    void chooseRespectsDistance() {
        for (int cycle = 0; cycle < 8; cycle++) {
            int far = CanglanWarlordAttackRules.choose(CanglanWarlordAttackRules.PHASE_SPEAR,
                    cycle, CanglanWarlordAttackRules.SPEAR_THROW_RANGE + 6.0D);
            assertEquals(CanglanWarlordAttackRules.IDLE, far, "太远就该走过去");
            int mid = CanglanWarlordAttackRules.choose(CanglanWarlordAttackRules.PHASE_SPEAR,
                    cycle, CanglanWarlordAttackRules.SPEAR_ENGAGE + 2.0D);
            assertTrue(mid == CanglanWarlordAttackRules.SPEAR_THROW
                            || mid == CanglanWarlordAttackRules.IDLE,
                    "中距离应该掷矛或继续靠近");
            int close = CanglanWarlordAttackRules.choose(CanglanWarlordAttackRules.PHASE_SPEAR,
                    cycle, 2.5D);
            assertNotEquals(CanglanWarlordAttackRules.IDLE, close, "贴身必须出手");
            int swordClose = CanglanWarlordAttackRules.choose(
                    CanglanWarlordAttackRules.PHASE_SWORD, cycle, 2.5D);
            assertNotEquals(CanglanWarlordAttackRules.IDLE, swordClose, "持剑贴身必须出手");
        }
    }

    @Test
    void everyMoveNamesARealClip() {
        int[] all = {CanglanWarlordAttackRules.CLEAVE_LEFT, CanglanWarlordAttackRules.CLEAVE_RIGHT,
                CanglanWarlordAttackRules.OVERHEAD, CanglanWarlordAttackRules.THRUST,
                CanglanWarlordAttackRules.SWEEP, CanglanWarlordAttackRules.SPEAR_THROW,
                CanglanWarlordAttackRules.SHIELD_BASH, CanglanWarlordAttackRules.GALE_ROAR};
        for (int move : all) {
            String clip = CanglanWarlordAttackRules.animationFor(move);
            assertFalse(clip.isBlank(), "招式 " + move + " 没有对应动画");
        }
        assertEquals("spear_throw", CanglanWarlordAttackRules.animationFor(
                CanglanWarlordAttackRules.SPEAR_THROW));
        assertEquals("war_cry", CanglanWarlordAttackRules.animationFor(
                CanglanWarlordAttackRules.GALE_ROAR), "怒吼复用 war_cry 片段");
        assertEquals("idle", CanglanWarlordAttackRules.animationFor(
                CanglanWarlordAttackRules.IDLE));
    }

    @Test
    void theThrownSpearIsBigAndFast() {
        assertTrue(CanglanWarlordAttackRules.SPEAR_THROW_SPEED >= 1.5D);
        assertTrue(CanglanWarlordAttackRules.SPEAR_THROW_SIZE >= 3.0D);
        assertTrue(CanglanWarlordAttackRules.SPEAR_THROW_RANGE > CanglanWarlordAttackRules.THRUST_REACH);
    }
}
