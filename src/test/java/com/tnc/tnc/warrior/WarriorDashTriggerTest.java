package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tnc.tnc.warrior.client.WarriorDash;
import org.junit.jupiter.api.Test;

/**
 * 冲刺残影的触发门槛：把"冲刺"和"跑得快"分开。
 *
 * <p>原版疾跑大约 0.28 格/tick，Shift 短按冲刺是一下 1.9 格（瞬间速度就超过 1.5），
 * 所以门槛必须在两者之间、且离疾跑够远 —— 否则平常跑动就满屏拉线。
 */
class WarriorDashTriggerTest {

    @Test
    void walkingAndSprintingDoNotLeaveStreaks() {
        assertFalse(WarriorDash.triggered(0.0), "站着不动不该有残影");
        assertFalse(WarriorDash.triggered(0.28), "疾跑（原版 0.28 格/tick）不该有残影");
        assertFalse(WarriorDash.triggered(0.54), "门槛以下都给 false");
    }

    @Test
    void theShiftDashLeavesStreaks() {
        assertTrue(WarriorDash.triggered(0.55), "刚好到门槛就算冲刺");
        assertTrue(WarriorDash.triggered(1.9), "短按 Shift 冲刺（1.9 格）必须有残影");
        assertTrue(WarriorDash.triggered(4.0), "被击飞/爆炸推走也算，画面统一");
    }

    @Test
    void thresholdSitsBetweenSprintAndDash() {
        assertTrue(WarriorDash.TRIGGER_SPEED > 0.28, "门槛必须高于疾跑速度");
        assertTrue(WarriorDash.TRIGGER_SPEED < 1.5, "门槛必须低于冲刺速度，否则冲刺就不亮了");
    }
}
