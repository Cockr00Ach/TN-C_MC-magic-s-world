package com.tnc.tnc.boss;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 衍的处决演出：分镜/时长/触发条件的闸门（2026-11-14 作者整段分镜）✓。
 *
 * <p>守：
 * <ul>
 *   <li>落雷<b>正好 3 秒</b>（60 tick），而且镜头跟的是"雷的末端"（末端从 SKY_HEIGHT 匀速落到地面）✓</li>
 *   <li>分镜顺序：衍特写 → 玩家特写 → 天空 → 落雷 → 命中 ✓</li>
 *   <li><b>只在二阶段 75% 触发，而且只触发一次</b> ✓</li>
 *   <li>秒杀伤害足够大（普通玩家 20 点血必死）✓</li>
 * </ul>
 */
class YanExecutionRulesTest {

    @Test
    void theLightningFallsForExactlyThreeSeconds() {
        assertEquals(60, YanExecutionRules.FALL, "落雷不是 3 秒（60 tick）✗");
        assertEquals(20 * 3, YanExecutionRules.FALL, "60 tick 才等于 3 秒 ✗");
        assertEquals(YanExecutionRules.AT_FALL + 60, YanExecutionRules.AT_IMPACT,
                "落雷段和命中点对不上 ✗");
    }

    @Test
    void theBoltTipFallsFromTheSkyToTheGround() {
        double sky = 40.0D;
        double ground = -2.0D;
        assertEquals(sky, YanExecutionRules.boltTipY(sky, ground, YanExecutionRules.AT_FALL), 1.0E-9D,
                "刚出现时雷末端应该在天空 ✗");
        assertEquals(ground, YanExecutionRules.boltTipY(sky, ground, YanExecutionRules.AT_IMPACT), 1.0E-9D,
                "劈中的那一刻末端应该在地面 ✗");
        double mid = YanExecutionRules.boltTipY(sky, ground, YanExecutionRules.AT_FALL + 30);
        assertEquals((sky + ground) / 2.0D, mid, 1.0E-6D, "落雷不是匀速的（慢动作节奏会乱）✗");
        // 落到一半的时间点必须还在空中（作者要"跟着雷的末端慢慢下来"）✓
        assertTrue(YanExecutionRules.fallProgress(YanExecutionRules.AT_FALL + 45) > 0.5D);
    }

    @Test
    void theShotListRunsInTheAuthoredOrder() {
        assertEquals(YanExecutionRules.STAGE_BOSS, YanExecutionRules.stage(0), "开场应该是衍特写 ✗");
        assertEquals(YanExecutionRules.STAGE_BOSS, YanExecutionRules.stage(YanExecutionRules.AT_PLAYER - 1));
        assertEquals(YanExecutionRules.STAGE_PLAYER, YanExecutionRules.stage(YanExecutionRules.AT_PLAYER),
                "第二段应该是玩家特写 ✗");
        assertEquals(YanExecutionRules.STAGE_SKY, YanExecutionRules.stage(YanExecutionRules.AT_SKY),
                "第三段应该是抬头看天 ✗");
        assertEquals(YanExecutionRules.STAGE_FALL, YanExecutionRules.stage(YanExecutionRules.AT_FALL),
                "第四段应该是落雷 ✗");
        assertEquals(YanExecutionRules.STAGE_IMPACT, YanExecutionRules.stage(YanExecutionRules.AT_IMPACT),
                "第五段应该是命中 ✗");
        assertEquals(YanExecutionRules.STAGE_DONE, YanExecutionRules.stage(YanExecutionRules.END),
                "演完应该收镜头 ✗");
        // 镜头聚焦顺序：衍 → 玩家 → 天空 → 雷末端 → 玩家 ✓
        assertEquals(0, YanExecutionRules.cameraFocus(5));
        assertEquals(1, YanExecutionRules.cameraFocus(YanExecutionRules.AT_PLAYER + 2));
        assertEquals(2, YanExecutionRules.cameraFocus(YanExecutionRules.AT_SKY + 2));
        assertEquals(3, YanExecutionRules.cameraFocus(YanExecutionRules.AT_FALL + 2));
        assertEquals(1, YanExecutionRules.cameraFocus(YanExecutionRules.AT_IMPACT + 2));
    }

    @Test
    void itTriggersOnceAtSeventyFivePercentInPhaseTwo() {
        assertTrue(YanExecutionRules.shouldTrigger(2, 2250.0F, 3000.0F, false, true),
                "二阶段 75% 应该触发 ✗");
        assertTrue(YanExecutionRules.shouldTrigger(2, 2249.0F, 3000.0F, false, true), "低于 75% 也该触发 ✗");
        assertFalse(YanExecutionRules.shouldTrigger(2, 2300.0F, 3000.0F, false, true), "76% 不该触发 ✗");
        assertFalse(YanExecutionRules.shouldTrigger(1, 100.0F, 3000.0F, false, true), "一阶段不该触发 ✗");
        assertFalse(YanExecutionRules.shouldTrigger(2, 100.0F, 3000.0F, true, true),
                "放过一次之后绝不能再放（作者要「只能释放那一次」）✗");
        assertFalse(YanExecutionRules.shouldTrigger(2, 100.0F, 3000.0F, false, false), "没有目标不该触发 ✗");
        assertEquals(0.75F, YanExecutionRules.TRIGGER_HEALTH_RATIO, 1.0E-6F, "触发血线不是 75% ✗");
    }

    @Test
    void theKillDamageIsActuallyLethal() {
        assertTrue(YanExecutionRules.EXECUTION_DAMAGE > 1000.0F,
                "处决伤害 " + YanExecutionRules.EXECUTION_DAMAGE + " 不够秒杀 ✗");
    }

    /** 源码扫描：服务端/客户端真的接上了（不是只写了规则）✓。 */
    @Test
    void bothSidesAreActuallyWired() throws Exception {
        String boss = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/boss/YanDarkBossEntity.java"));
        assertTrue(boss.contains("DATA_EXEC_TICK"), "Boss 没有同步处决时钟（客户端没法运镜）✗");
        assertTrue(boss.contains("execUsed"), "Boss 没有「一辈子一次」的标记 ✗");
        assertTrue(boss.contains("YanExecutionRules.AT_IMPACT"), "命中时机没走规则 ✗");
        assertTrue(boss.contains("YanExecutionRules.AT_PLAYER"), "禁锢/玩家法阵时机没走规则 ✗");
        assertTrue(boss.contains("MOVEMENT_SLOWDOWN") && boss.contains("JUMP"),
                "玩家没有被真正定住（速度和跳跃都得压）✗");

        String camera = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/boss/client/YanExecutionCamera.java"));
        assertTrue(camera.contains("setCameraEntity("), "没有接管镜头 ✗");
        assertTrue(camera.contains("boltTipY("), "镜头没有跟着雷的末端 ✗");
        assertTrue(camera.contains("groundWeb("), "玩家脚下没有雷网 ✗");
        assertTrue(camera.contains("STAGE_FALL"), "没有落雷那一段的运镜 ✗");
        assertTrue(camera.contains("keyShift.isDown()"), "没有「潜行跳过」的退出口 ✗");
    }
}
