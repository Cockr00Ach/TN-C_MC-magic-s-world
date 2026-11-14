package com.tnc.tnc.magic.wind;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风灵出手方式的闸门（2026-11-11 作者：「风灵再改改」）✓。
 *
 * <p>守三件事：
 * <ul>
 *   <li>档位越高<b>打得越多</b>（1 / 1 / 2 / 2 / 3 发），t5 还有「风环齐射」✓</li>
 *   <li>多发不等于多倍伤害：单发按 <b>1/√n</b> 分摊 ⇒ 三发总伤 = 单发 ×1.73（不是 ×3 ✗）✓</li>
 *   <li>命中要<b>炸开一小圈</b>（有半径、有比例），不是"只掉一格血" ✓</li>
 * </ul>
 */
class WindArsenalRulesTest {

    @Test
    void higherTierFiresMoreWeaponsAtOnce() {
        assertEquals(1, WindArsenalRules.shots(1), "t1 应该单发 ✗");
        assertEquals(1, WindArsenalRules.shots(2), "t2 应该单发 ✗");
        assertEquals(2, WindArsenalRules.shots(3), "t3 应该双发 ✗");
        assertEquals(2, WindArsenalRules.shots(4), "t4 应该双发 ✗");
        assertEquals(3, WindArsenalRules.shots(5), "t5 应该三发 ✗");
        for (int tier = 2; tier <= 5; tier++) {
            assertTrue(WindArsenalRules.shots(tier) >= WindArsenalRules.shots(tier - 1),
                    "档位越高不应该打得越少 ✗");
        }
        // 越界要夹住（法术 JSON 可能给 0 或 6）✓
        assertEquals(WindArsenalRules.shots(1), WindArsenalRules.shots(0));
        assertEquals(WindArsenalRules.shots(5), WindArsenalRules.shots(9));
    }

    @Test
    void onlyTheTopTierGetsTheWindRingVolley() {
        assertEquals(0, WindArsenalRules.volleyEvery(4), "t4 不该有齐射 ✗");
        assertEquals(0, WindArsenalRules.volleyShots(4), "t4 不该有齐射 ✗");
        assertEquals(5, WindArsenalRules.volleyEvery(5), "t5 应该每 5 次出手齐射一轮 ✗");
        assertEquals(4, WindArsenalRules.volleyShots(5), "t5 齐射应该 4 连发 ✗");
        assertTrue(WindArsenalRules.volleyShots(5) > WindArsenalRules.shots(5),
                "齐射要比平时更多发才有意义 ✗");
    }

    @Test
    void moreShotsDoesNotMeanProportionalDamage() {
        for (int tier = 1; tier <= 5; tier++) {
            int shots = WindArsenalRules.shots(tier);
            float factor = WindArsenalRules.shotDamageFactor(tier);
            assertEquals(1.0F / Math.sqrt(shots), factor, 1.0E-5F,
                    "单发伤害系数不是 1/√n ✗");
        }
        // 三发总伤 = √3 ≈ 1.73 倍（比单发强，但绝不是三倍 ✗）
        float perShot = WindArsenalRules.shotDamageFactor(5);
        float total = perShot * WindArsenalRules.shots(5);
        assertTrue(total > 1.5F && total < 1.9F, "t5 三发的总伤害倍率 " + total + " 不在 1.5~1.9 ✗");
    }

    @Test
    void everyHitBurstsIntoASmallRing() {
        for (int tier = 1; tier <= 5; tier++) {
            double radius = WindArsenalRules.burstRadius(tier);
            assertTrue(radius >= 1.0D && radius <= 2.0D,
                    "t" + tier + " 的命中炸开半径 " + radius + " 不在 1~2 格 ✗");
        }
        assertTrue(WindArsenalRules.burstRadius(5) > WindArsenalRules.burstRadius(1),
                "档越高炸开应该稍大 ✗");
        float share = WindArsenalRules.burstDamageShare();
        assertTrue(share > 0.2F && share < 0.5F,
                "炸开伤害占比 " + share + " 不合适（太小没反馈 / 太大抢主伤害）✗");
    }

    @Test
    void firingRateStaysAtTenTicks() {
        assertEquals(10, WindArsenalRules.zapInterval(), "出手间隔被改了（风灵每 10 tick 打一次）✗");
    }

    /** 源码扫描：这一轮的三件事真的接上了（不是只写在规则里）✓。 */
    @Test
    void theBehaviourIsActuallyWired() throws Exception {
        String arsenal = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/wind/WindArsenal.java"));
        assertTrue(arsenal.contains("WindArsenalRules.shots("), "出手数量没接上 ✗");
        assertTrue(arsenal.contains("pickTargets("), "没有「挑多个目标」的实现 ✗");
        assertTrue(arsenal.contains("shotDamageFactor("), "多发伤害没分摊 ✗");
        assertTrue(arsenal.contains("inwardStreak("), "吸力没有画面 ✗");
        assertTrue(arsenal.contains("volleyShots("), "齐射没接上 ✗");
        assertTrue(arsenal.contains("SWEEP_ATTACK"), "齐射那一圈风环没画 ✗");

        String weapon = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/magic/wind/WindWeaponEntity.java"));
        assertTrue(weapon.contains("private void burst("), "命中炸开没实现 ✗");
        assertTrue(weapon.contains("burstRadius("), "命中炸开的半径没接上 ✗");
        assertTrue(weapon.contains("burst(victim)"), "命中时没调用炸开 ✗");
    }
}
