package com.tnc.tnc.boss;

import com.tnc.tnc.boss.wind.WindShadowTrial;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 水渊兽 / 风将交互的纯逻辑（2026-11-15 作者设计）。 */
class BossTrialsTest {

    @Test
    void waterTrialLandsOnlyWhenNothingHit() {
        assertTrue(WaterOrbTrial.allMissed(0), "十颗全空 → 落地停手 15 秒");
        assertFalse(WaterOrbTrial.allMissed(1), "哪怕只中一发 → 走水牢分支");
    }

    @Test
    void waterTrialNumbersMatchTheDesign() {
        assertEquals(10, WaterOrbTrial.ORBS, "十颗水球");
        assertEquals(20, WaterOrbTrial.SPAWN_INTERVAL, "一秒一颗");
        assertEquals(300, WaterOrbTrial.LANDED_TICKS, "落地停手 15 秒");
        assertEquals("sea_god_crypt", WaterOrbTrial.PRISON_SPELL, "水牢 t5");
    }

    @Test
    void windTrialBranchesOnWhetherTheRealOneWasBroken() {
        assertTrue(WindShadowTrial.isReal(true), "打碎真身 → 眩晕 10 秒 + 风长枪 10%");
        assertFalse(WindShadowTrial.isReal(false), "打碎假的 → 全体龙卷");
    }

    @Test
    void windTrialNumbersMatchTheDesign() {
        assertEquals(5, WindShadowTrial.SHADOWS, "五个虚影");
        assertEquals(200, WindShadowTrial.WINDOW_TICKS, "10 秒判定");
        assertEquals(200, WindShadowTrial.STUN_TICKS, "眩晕 10 秒");
        assertEquals(0.10F, WindShadowTrial.REAL_DAMAGE_PERCENT, 1e-6, "真身掉 10% 血");
        assertTrue(WindShadowTrial.Shadow.MAX_HEALTH > 0, "虚影要有血量");
    }
}
