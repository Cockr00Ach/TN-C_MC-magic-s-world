package com.tnc.tnc.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 衍的雷锲交互：只有"六枚全碎且没超时"才算成功（作者 2026-11-15 的规则）。
 *
 * <p>只钉纯逻辑（{@link YanNailPuzzle#succeeded} 与常量），真正的实体交互属于 gametest。
 */
class YanNailPuzzleTest {

    @Test
    void allNailsBrokenInTimeIsASuccess() {
        assertTrue(YanNailPuzzle.succeeded(0, false), "六枚都碎了、还没超时 → 成功");
    }

    @Test
    void survivingNailsMeanFailure() {
        assertFalse(YanNailPuzzle.succeeded(1, false), "还剩一枚 → 失败（他会把雷还回来）");
        assertFalse(YanNailPuzzle.succeeded(6, false), "一枚没打 → 失败");
    }

    @Test
    void timeoutIsAlwaysAFailureEvenIfNothingIsLeft() {
        assertFalse(YanNailPuzzle.succeeded(0, true), "超时那一刻就算全碎也不算成功");
    }

    @Test
    void windowAndPayoutMatchTheDesign() {
        assertEquals(6, YanNailPuzzle.NAILS, "六枚雷锲");
        assertEquals(300, YanNailPuzzle.WINDOW_TICKS, "15 秒 = 300 tick");
        assertEquals(0.10F, YanNailPuzzle.SUCCESS_PERCENT, 1e-6, "打碎成功扣 10% 最大生命");
        assertEquals(24.0D, YanNailPuzzle.FAIL_RADIUS, 1e-6, "失败落雷范围 24 格");
        assertTrue(YanNailPuzzle.FAIL_DAMAGE > 0, "失败落雷要有实际伤害");
    }

    @Test
    void nailHasEnoughHealthToNeedARealHit() {
        assertTrue(TNThunderNailEntity.NAIL_HEALTH >= 10.0F,
                "雷锲不是一碰就碎 —— 否则 15 秒的时限没有意义");
    }
}
