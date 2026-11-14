package com.tnc.tnc.boss.earth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 土系 Boss 交互：破柱且没超时才停手（作者 2026-11-15）。 */
class EarthPillarTrialTest {

    @Test
    void brokenInTimePacifiesTheBoss() {
        assertTrue(EarthPillarTrial.pacified(true, false), "15 秒内打碎 → 停手 10 秒");
    }

    @Test
    void timeoutMeansSkyfall() {
        assertFalse(EarthPillarTrial.pacified(true, true), "超时那一刻就算碎了也不算成功");
        assertFalse(EarthPillarTrial.pacified(false, false), "没打碎 → 地爆天星");
    }

    @Test
    void timingMatchesTheDesign() {
        assertEquals(300, EarthPillarTrial.WINDOW_TICKS, "15 秒窗口");
        assertEquals(200, EarthPillarTrial.PASSIVE_TICKS, "破柱后停手 10 秒");
        assertEquals(30.0D, EarthPillarTrial.SKY_HEIGHT, 1e-6, "失败后飞到 30 格高空");
        assertTrue(EarthPillarTrial.PULL_TICKS > 0 && EarthPillarTrial.PULL_RADIUS > 0,
                "地爆天星要真的能把人拽住");
    }

    @Test
    void pillarNeedsRealDamage() {
        assertTrue(TNEarthPillarEntity.PILLAR_HEALTH >= 100.0F,
                "土柱不能一碰就碎，否则 15 秒窗口没有意义");
    }
}
