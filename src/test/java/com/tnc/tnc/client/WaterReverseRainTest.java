package com.tnc.tnc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 逆雨的时间轴：水必须是**往上**走的，这一点用测试钉住。 */
class WaterReverseRainTest {

    @Test
    void dropletsTravelUpwardsInsteadOfFalling() {
        double height = WaterReverseRain.columnHeightFor(3);
        assertEquals(0D, WaterReverseRain.dropHeight(0D, height), 1e-9);
        assertEquals(height, WaterReverseRain.dropHeight(1D, height), 1e-9);
        // 严格单调上涨：中间任何一点都在起点之上
        double previous = -1;
        for (int i = 0; i <= 20; i++) {
            double y = WaterReverseRain.dropHeight(i / 20D, height);
            assertTrue(y > previous, "雨滴必须一直往上走，第 " + i + " 步掉下来了");
            previous = y;
        }
    }

    @Test
    void higherTierLiftsTheWaterHigher() {
        for (int tier = 2; tier <= 5; tier++) {
            assertTrue(WaterReverseRain.columnHeightFor(tier) > WaterReverseRain.columnHeightFor(tier - 1),
                    "档位越高水柱应该越高");
            assertTrue(WaterReverseRain.dropCountFor(tier) > WaterReverseRain.dropCountFor(tier - 1),
                    "档位越高水线应该越多");
        }
        // t5 的水柱要明显高过一栋房子（4 格），不然"回到天上"看不出来
        assertTrue(WaterReverseRain.columnHeightFor(5) > 8D);
        // 上限别离谱：t5 也就 11~12 格
        assertTrue(WaterReverseRain.columnHeightFor(5) < 14D);
    }

    @Test
    void streaksFadeAndConvergeTowardsTheCloud() {
        assertEquals(1F, WaterReverseRain.dropAlpha(0D), 1e-6F);
        assertTrue(WaterReverseRain.dropAlpha(.9D) < .5F, "接近云的时候应该已经淡掉大半");
        assertEquals(1D, WaterReverseRain.dropRadiusFactor(0D), 1e-9);
        assertTrue(WaterReverseRain.dropRadiusFactor(1D) < 1D, "顶部要往中间收拢");
        for (int i = 1; i <= 10; i++) {
            assertTrue(WaterReverseRain.dropAlpha(i / 10D) < WaterReverseRain.dropAlpha((i - 1) / 10D));
            assertTrue(WaterReverseRain.dropRadiusFactor(i / 10D)
                    < WaterReverseRain.dropRadiusFactor((i - 1) / 10D));
        }
    }

    @Test
    void windupRampsUpAsTheCastCompletes() {
        assertEquals(0F, WaterReverseRain.chargeGlow(0F), 1e-6F);
        assertEquals(1F, WaterReverseRain.chargeGlow(1F), 1e-6F);
        // 0.82 之前一直在涨；之后已经满格（封顶），只要求不再下降
        for (int i = 1; i <= 8; i++) {
            assertTrue(WaterReverseRain.chargeGlow(i / 10F) > WaterReverseRain.chargeGlow((i - 1) / 10F));
        }
        for (int i = 9; i <= 10; i++) {
            assertTrue(WaterReverseRain.chargeGlow(i / 10F)
                    >= WaterReverseRain.chargeGlow((i - 1) / 10F));
        }
    }
}
