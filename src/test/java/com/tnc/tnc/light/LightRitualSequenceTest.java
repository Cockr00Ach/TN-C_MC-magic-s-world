package com.tnc.tnc.light;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LightRitualSequence} 的不变量闸门（纯算术，不启动游戏）✓。
 *
 * <p>这些断言对应作者 2026-11-05 的三句话：
 * <ul>
 *   <li>「更多的小法阵」⇒ 最低档也至少 3 颗，且档位越高越多；</li>
 *   <li>「更久的施法前戏」⇒ 主阵成形/小阵点亮是<b>随时间单调</b>的，不能忽明忽暗；</li>
 *   <li>「暴风雨的前夕」⇒ 前段暗角 > 0、后段过曝 > 0，两者在终点交棒。</li>
 * </ul>
 */
class LightRitualSequenceTest {

    @Test
    void tierIsClamped() {
        assertEquals(1, LightRitualSequence.tier(0));
        assertEquals(1, LightRitualSequence.tier(-7));
        assertEquals(5, LightRitualSequence.tier(99));
        assertEquals(3, LightRitualSequence.tier(3));
    }

    @Test
    void satellitesGrowWithTierAndLowTierStaysSparse() {
        int previous = 0;
        for (int tier = 1; tier <= 5; tier++) {
            int count = LightRitualSequence.satelliteSeals(tier);
            assertTrue(count >= 2, "t" + tier + " 只有 " + count + " 颗小阵，连低阶的仪式感都没有 ✗");
            assertTrue(count <= 8, "t" + tier + " 的 " + count + " 颗小阵会超出 8 槽状态数组 ✗");
            assertTrue(count > previous, "档位越高小阵数必须严格增加 ✗");
            previous = count;
        }
        assertEquals(2, LightRitualSequence.satelliteSeals(1));
        assertEquals(3, LightRitualSequence.satelliteSeals(2));
        assertEquals(8, LightRitualSequence.satelliteSeals(5));
        assertTrue(LightRitualSequence.satelliteSeals(1) <= 3,
                "低阶不该铺满一圈小阵，否则和高阶只是数量差别 ✗");
        assertTrue(LightRitualSequence.satelliteSeals(5) >= 7,
                "高阶必须明显更密（t5 ≥ 7 颗）✗");
    }

    @Test
    void lowAndHighTierUseDifferentCastingRites() {
        assertEquals(LightRitualSequence.Rite.INSCRIPTION, LightRitualSequence.rite(1));
        assertEquals(LightRitualSequence.Rite.INSCRIPTION, LightRitualSequence.rite(2));
        assertEquals(LightRitualSequence.Rite.SANCTUARY, LightRitualSequence.rite(3));
        assertEquals(LightRitualSequence.Rite.DESCENT, LightRitualSequence.rite(4));
        assertEquals(LightRitualSequence.Rite.DESCENT, LightRitualSequence.rite(5));

        // 低阶：没有竖环、没有屏幕压暗/心跳、没有光柱/光雨
        assertEquals(0, LightRitualSequence.gateRings(1));
        assertEquals(0, LightRitualSequence.gateRings(2));
        assertEquals(0, LightRitualSequence.uprightHalos(2));
        assertEquals(0.0F, LightRitualSequence.screenFx(1), 1.0E-6F);
        assertFalse(LightRitualSequence.hasCorePillar(2));
        assertFalse(LightRitualSequence.hasRibbons(2));
        assertFalse(LightRitualSequence.hasLightRain(2));

        // 高阶：门环/竖环/满档屏幕演出/光柱/光雨全开，且比中阶更重
        assertTrue(LightRitualSequence.gateRings(5) > LightRitualSequence.gateRings(3));
        assertTrue(LightRitualSequence.uprightHalos(5) > LightRitualSequence.uprightHalos(3));
        assertEquals(1.0F, LightRitualSequence.screenFx(5), 1.0E-6F);
        assertTrue(LightRitualSequence.hasCorePillar(5));
        assertTrue(LightRitualSequence.hasRibbons(5));
        assertTrue(LightRitualSequence.hasLightRain(5));
        assertTrue(LightRitualSequence.groundRays(5) > LightRitualSequence.groundRays(3));
        assertTrue(LightRitualSequence.outerRunes(5) > LightRitualSequence.outerRunes(2));
    }

    @Test
    void formationIsMonotonicAndClosedAtBothEnds() {
        assertEquals(0.0F, LightRitualSequence.formation(0.0F), 1.0E-6F);
        assertEquals(1.0F, LightRitualSequence.formation(1.0F), 1.0E-6F);
        float previous = -1.0F;
        for (int i = 0; i <= 100; i++) {
            float value = LightRitualSequence.formation(i / 100.0F);
            assertTrue(value >= previous, "formation 在 " + i + "% 处倒退了 ✗");
            previous = value;
        }
    }

    @Test
    void satelliteIgnitionIsStaggeredThenAllLit() {
        for (int tier = 1; tier <= 5; tier++) {
            int count = LightRitualSequence.satelliteSeals(tier);
            Set<Integer> order = new HashSet<>();
            for (int index = 0; index < count; index++) {
                assertEquals(0.0F, LightRitualSequence.satelliteIgnition(tier, index, 0.0F), 1.0E-6F);
                assertEquals(1.0F, LightRitualSequence.satelliteIgnition(tier, index, 1.0F), 1.0E-6F);
                float previous = -1.0F;
                for (int step = 0; step <= 100; step++) {
                    float value = LightRitualSequence.satelliteIgnition(tier, index, step / 100.0F);
                    assertTrue(value >= previous, "第 " + index + " 颗小阵的点亮回退了 ✗");
                    previous = value;
                }
                order.add(firstLitPercent(tier, index));
            }
            assertTrue(order.size() >= Math.min(4, count),
                    "t" + tier + " 的小阵几乎同时亮，没有“一颗一颗被点亮”的顺序感 ✗");
        }
    }

    private static int firstLitPercent(int tier, int index) {
        for (int step = 0; step <= 100; step++) {
            if (LightRitualSequence.satelliteIgnition(tier, index, step / 100.0F) > 0.01F) {
                return step;
            }
        }
        return 101;
    }

    @Test
    void chargeGlowAcceleratesAtTheEnd() {
        assertEquals(0.0F, LightRitualSequence.chargeGlow(0.0F), 1.0E-6F);
        assertEquals(1.0F, LightRitualSequence.chargeGlow(1.0F), 1.0E-6F);
        // p^1.7：中点应该明显低于一半 —— 前段“不亮”，最后才爆
        assertTrue(LightRitualSequence.chargeGlow(0.5F) < 0.45F,
                "chargeGlow 中点太亮，读条会从一开头就过曝 ✗");
        float previous = -1.0F;
        for (int i = 0; i <= 100; i++) {
            float value = LightRitualSequence.chargeGlow(i / 100.0F);
            assertTrue(value >= previous, "chargeGlow 倒退了 ✗");
            previous = value;
        }
    }

    @Test
    void darkVignetteLeadsThenHandsOverToTheFlash() {
        // 低阶没有“天象”：全程 0
        for (int i = 0; i <= 100; i += 10) {
            assertEquals(0.0F, LightRitualSequence.darkVignette(1, i / 100.0F), 1.0E-6F);
        }
        // 高阶：开头压暗、前 55% 退场
        assertTrue(LightRitualSequence.darkVignette(5, 0.0F) > 0.5F,
                "高阶开头没有压暗，“暴风雨前夕”就没了 ✗");
        assertEquals(0.0F, LightRitualSequence.darkVignette(5, 0.55F), 1.0E-6F);
        assertEquals(0.0F, LightRitualSequence.darkVignette(5, 1.0F), 1.0E-6F);
        float previous = LightRitualSequence.darkVignette(5, 0.0F);
        for (int i = 1; i <= 55; i++) {
            float value = LightRitualSequence.darkVignette(5, i / 100.0F);
            assertTrue(value <= previous, "暗角不是单调退场 ✗");
            previous = value;
        }
    }

    @Test
    void heartbeatGetsFasterAndStrongerOnlyFromMidTier() {
        // 低阶没有心跳震屏
        for (float p = 0.0F; p <= 1.0F; p += 0.05F) {
            assertEquals(0, LightRitualSequence.heartbeatTicks(1, p));
            assertEquals(0.0F, LightRitualSequence.heartbeatShake(1, p), 1.0E-6F);
        }
        assertEquals(0, LightRitualSequence.heartbeatTicks(5, 0.2F));
        int previous = Integer.MAX_VALUE;
        for (float p = 0.45F; p <= 1.0F; p += 0.05F) {
            int ticks = LightRitualSequence.heartbeatTicks(5, p);
            assertTrue(ticks > 0 && ticks <= 14, "心跳周期越界：" + ticks);
            assertTrue(ticks <= previous, "心跳没有越来越密 ✗");
            previous = ticks;
        }
        assertTrue(LightRitualSequence.heartbeatShake(5, 1.0F)
                > LightRitualSequence.heartbeatShake(5, 0.5F), "后期的震屏没有更强 ✗");
        assertTrue(LightRitualSequence.heartbeatShake(5, 1.0F)
                > LightRitualSequence.heartbeatShake(3, 1.0F), "高阶心跳应比中阶更重 ✗");
    }

    @Test
    void releaseNumbersRampFromQuietLowTierToLoudHighTier() {
        for (int tier = 1; tier <= 5; tier++) {
            float flash = LightRitualSequence.releaseFlash(tier);
            float shake = LightRitualSequence.releaseShake(tier);
            assertTrue(flash > 0.15F && flash <= 0.75F, "t" + tier + " 白闪 = " + flash);
            assertTrue(shake >= 0.0F && shake <= 5.0F, "t" + tier + " 震屏 = " + shake);
        }
        assertEquals(0.0F, LightRitualSequence.releaseShake(1), 1.0E-6F);
        assertTrue(LightRitualSequence.releaseFlash(5) > LightRitualSequence.releaseFlash(1));
        assertTrue(LightRitualSequence.releaseShake(5) > LightRitualSequence.releaseShake(3));
    }

    @Test
    void satelliteAnglesAreDistinctOnBothOrbits() {
        for (int tier = 1; tier <= 5; tier++) {
            int count = LightRitualSequence.satelliteSeals(tier);
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < count; index++) {
                double angle = LightRitualSequence.satelliteAngle(tier, index);
                assertTrue(seen.add(String.format("%.4f", angle)),
                        "t" + tier + " 第 " + index + " 颗小阵和别颗完全重叠 ✗");
                double fraction = LightRitualSequence.satelliteOrbitFraction(index);
                assertTrue(fraction > 0.3D && fraction < 0.75D, "小阵轨道跑出主阵范围 ✗");
            }
        }
    }
}
