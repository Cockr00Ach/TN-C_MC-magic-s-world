package com.tnc.tnc.world.wind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线闸门 for 翠岚武尊 / Wind Warden timing.
 *
 * <p>Boss fights are expensive to verify in game, so everything that decides
 * <em>when</em> a hit lands is a pure function and is checked here: the windows,
 * the phase threshold, and the move chooser. A regression here would otherwise
 * only show up as "the boss sometimes never damages you".
 */
class WindWardenAttackRulesTest {

    @Test
    void phaseSwapsExactlyAtHalfHealth() {
        assertEquals(WindWardenAttackRules.PHASE_SWORD,
                WindWardenAttackRules.phaseFor(801.0F, 1600.0F), "过半血时不该换阶段");
        assertEquals(WindWardenAttackRules.PHASE_SPEAR,
                WindWardenAttackRules.phaseFor(800.0F, 1600.0F), "正好半血必须进入长矛阶段");
        assertEquals(WindWardenAttackRules.PHASE_SPEAR,
                WindWardenAttackRules.phaseFor(1.0F, 1600.0F));
        assertTrue(WindWardenAttackRules.shouldShift(
                WindWardenAttackRules.PHASE_SWORD, 700.0F, 1600.0F), "剑阶段半血要触发换装");
        assertFalse(WindWardenAttackRules.shouldShift(
                WindWardenAttackRules.PHASE_SPEAR, 700.0F, 1600.0F), "已经换过就不该再触发");
        assertFalse(WindWardenAttackRules.shouldShift(
                WindWardenAttackRules.PHASE_SWORD, 0.0F, 1600.0F), "死亡帧不该触发换装");
    }

    @Test
    void everyMoveHasABoundedDurationAndFitsInsideIt() {
        int[] moves = {WindWardenAttackRules.SLASH_LEFT, WindWardenAttackRules.SLASH_RIGHT,
                WindWardenAttackRules.OVERHEAD, WindWardenAttackRules.BLADE_WAVE,
                WindWardenAttackRules.LUNGE, WindWardenAttackRules.SPEAR_THRUST,
                WindWardenAttackRules.SPEAR_SWEEP, WindWardenAttackRules.SPEAR_THROW,
                WindWardenAttackRules.CYCLONE};
        for (int move : moves) {
            int duration = WindWardenAttackRules.duration(move);
            assertTrue(duration > 0 && duration <= 60,
                    "招式 " + move + " 的时长不合理: " + duration);
            // The clip must have finished before the state machine releases the boss.
            boolean anyWindow = false;
            for (int age = 0; age < duration; age++) {
                anyWindow |= WindWardenAttackRules.connects(move, age);
            }
            assertTrue(anyWindow, "招式 " + move + " 在整段时长里一次都不造成伤害");
        }
    }

    @Test
    void singleHitMovesHitExactlyOnce() {
        int[] single = {WindWardenAttackRules.SLASH_LEFT, WindWardenAttackRules.SLASH_RIGHT,
                WindWardenAttackRules.OVERHEAD, WindWardenAttackRules.LUNGE,
                WindWardenAttackRules.SPEAR_THRUST, WindWardenAttackRules.SPEAR_SWEEP,
                WindWardenAttackRules.SPEAR_THROW};
        for (int move : single) {
            int hits = 0;
            for (int age = 0; age < WindWardenAttackRules.duration(move); age++) {
                if (WindWardenAttackRules.connects(move, age)) {
                    hits++;
                }
            }
            assertEquals(1, hits, "招式 " + move + " 应该只判定一次，实际 " + hits + " 次");
        }
    }

    @Test
    void multiHitMovesLandTheDesignedNumberOfWindows() {
        int waveHits = 0;
        for (int age = 0; age < WindWardenAttackRules.duration(WindWardenAttackRules.BLADE_WAVE); age++) {
            if (WindWardenAttackRules.connects(WindWardenAttackRules.BLADE_WAVE, age)) {
                waveHits++;
            }
        }
        assertEquals(WindWardenAttackRules.BLADE_WAVE_COUNT, waveHits, "风刃必须是三连");
        assertTrue(WindWardenAttackRules.waveAt(WindWardenAttackRules.BLADE_WAVE_FIRST));
        assertFalse(WindWardenAttackRules.waveAt(WindWardenAttackRules.BLADE_WAVE_FIRST + 1),
                "两片风刃之间必须有间隔");

        int cycloneHits = 0;
        for (int age = 0; age < WindWardenAttackRules.duration(WindWardenAttackRules.CYCLONE); age++) {
            if (WindWardenAttackRules.connects(WindWardenAttackRules.CYCLONE, age)) {
                cycloneHits++;
            }
        }
        assertEquals(WindWardenAttackRules.CYCLONE_HITS, cycloneHits, "旋风必须是三段判定");
    }

    @Test
    void windowsAreSeparatedSoOneTargetCannotBeDoubleDippedInOneWindow() {
        // Each connecting tick is its own window key in WindWardenCombat, so a
        // single-target can at most be hit once per window. Assert the spacing.
        int previous = Integer.MIN_VALUE;
        for (int age = 0; age < WindWardenAttackRules.duration(WindWardenAttackRules.CYCLONE); age++) {
            if (WindWardenAttackRules.connects(WindWardenAttackRules.CYCLONE, age)) {
                assertTrue(previous == Integer.MIN_VALUE
                                || age - previous >= WindWardenAttackRules.CYCLONE_HIT_INTERVAL,
                        "旋风的两段判定挨得太近: " + previous + " -> " + age);
                previous = age;
            }
        }
    }

    @Test
    void spearReachesFurtherThanTheSword() {
        assertTrue(WindWardenAttackRules.reach(WindWardenAttackRules.SPEAR_THRUST)
                        > WindWardenAttackRules.reach(WindWardenAttackRules.SLASH_LEFT),
                "长矛必须比剑长");
        assertTrue(WindWardenAttackRules.reach(WindWardenAttackRules.CYCLONE)
                        > WindWardenAttackRules.reach(WindWardenAttackRules.SPEAR_SWEEP) * 0.5D,
                "旋风的范围不该比单体横扫还小");
        assertEquals(WindWardenAttackRules.SPEAR_REACH,
                WindWardenAttackRules.reach(WindWardenAttackRules.SPEAR_THROW),
                "掷矛的射程应与长矛一致");
    }

    @Test
    void heavyMovesHitHarderThanLightOnes() {
        assertTrue(WindWardenAttackRules.damageFactor(WindWardenAttackRules.SPEAR_THRUST)
                > WindWardenAttackRules.damageFactor(WindWardenAttackRules.SLASH_LEFT));
        assertTrue(WindWardenAttackRules.damageFactor(WindWardenAttackRules.OVERHEAD)
                > WindWardenAttackRules.damageFactor(WindWardenAttackRules.SLASH_RIGHT));
        assertTrue(WindWardenAttackRules.damageFactor(WindWardenAttackRules.BLADE_WAVE)
                < WindWardenAttackRules.damageFactor(WindWardenAttackRules.SPEAR_SWEEP),
                "远程风刃单发不该比近身横扫还疼");
        // Long-range options are explicitly cheaper per hit to compensate for safety.
        for (int move = 0; move <= WindWardenAttackRules.CYCLONE; move++) {
            if (move == WindWardenAttackRules.IDLE) {
                continue;
            }
            assertTrue(WindWardenAttackRules.damageFactor(move) > 0.0F, "招式 " + move + " 没有伤害");
        }
    }

    @Test
    void phaseOneStaysOnTheSwordAndPhaseTwoOnTheSpear() {
        int[] swordMoves = {WindWardenAttackRules.SLASH_LEFT, WindWardenAttackRules.SLASH_RIGHT,
                WindWardenAttackRules.OVERHEAD, WindWardenAttackRules.BLADE_WAVE,
                WindWardenAttackRules.LUNGE};
        int[] spearMoves = {WindWardenAttackRules.SPEAR_THRUST, WindWardenAttackRules.SPEAR_SWEEP,
                WindWardenAttackRules.SPEAR_THROW, WindWardenAttackRules.CYCLONE};
        for (int cycle = 0; cycle < 16; cycle++) {
            for (double range : new double[]{3.0, 8.0, 18.0, 33.0}) {
                int sword = WindWardenAttackRules.choose(WindWardenAttackRules.PHASE_SWORD, cycle, range);
                assertTrue(contains(swordMoves, sword),
                        "剑阶段选出了长矛招式 " + sword + "（cycle=" + cycle + ", range=" + range + "）");
                int spear = WindWardenAttackRules.choose(WindWardenAttackRules.PHASE_SPEAR, cycle, range);
                assertTrue(contains(spearMoves, spear),
                        "长矛阶段选出了剑招 " + spear + "（cycle=" + cycle + ", range=" + range + "）");
            }
        }
    }

    @Test
    void distanceDecidesWhetherTheBossClosesOrShoots() {
        int far = WindWardenAttackRules.choose(WindWardenAttackRules.PHASE_SWORD, 0,
                WindWardenAttackRules.BLADE_WAVE_RANGE + 5.0);
        assertEquals(WindWardenAttackRules.BLADE_WAVE, far, "太远时应该先丢风刃");
        int farSpear = WindWardenAttackRules.choose(WindWardenAttackRules.PHASE_SPEAR, 0,
                WindWardenAttackRules.BLADE_WAVE_RANGE + 5.0);
        assertEquals(WindWardenAttackRules.SPEAR_THROW, farSpear, "长矛阶段太远应该掷矛");
        // Every cycle step must be a legal move even at point-blank.
        for (int cycle = 0; cycle < 8; cycle++) {
            int close = WindWardenAttackRules.choose(WindWardenAttackRules.PHASE_SWORD, cycle, 2.0);
            assertTrue(close >= WindWardenAttackRules.SLASH_LEFT
                    && close <= WindWardenAttackRules.OVERHEAD, "贴脸时选出了远程招式 " + close);
        }
    }

    @Test
    void animationNamesExistForEveryMove() {
        // The renderer/entity look these up by string; a typo would silently
        // leave the boss T-posing through an attack.
        java.util.Set<String> clips = java.util.Set.of("slash_left", "slash_right", "overhead",
                "spin_sweep", "spear_thrust", "spear_rip", "spear_throw", "phase_shift");
        int[] moves = {WindWardenAttackRules.SLASH_LEFT, WindWardenAttackRules.SLASH_RIGHT,
                WindWardenAttackRules.OVERHEAD, WindWardenAttackRules.BLADE_WAVE,
                WindWardenAttackRules.LUNGE, WindWardenAttackRules.SPEAR_THRUST,
                WindWardenAttackRules.SPEAR_SWEEP, WindWardenAttackRules.SPEAR_THROW,
                WindWardenAttackRules.CYCLONE};
        for (int move : moves) {
            String clip = WindWardenCombat.animationFor(move);
            assertTrue(clips.contains(clip), "招式 " + move + " 指向了不存在的动画 " + clip);
        }
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }
}
