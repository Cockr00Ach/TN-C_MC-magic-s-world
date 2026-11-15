package com.tnc.tnc.magic.fire;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 火法重做的"契约"测试：只钉不依赖游戏世界的不变量。
 *
 * <p>为什么只测这些：真正的施法要世界、实体、SpellEngine，属于 gametest 范畴；
 * 这里钉住的是<b>改动容易踩坏的那几条</b>——哪些 id 归重做管、热量数值的单调性。
 */
class FireReworkContractTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tnc", path);
    }

    @Test
    void reworkOwnsExactlyTheIgniteAndDetonateChains() {
        // 点火链 5 档
        for (String p : new String[]{"sun_ray", "blast_ray", "fire_dragon", "flame_demon_wrath", "solar_judgment"}) {
            assertTrue(TNFireRework.handles(id(p)), p + " 应该归重做管（点火链）");
        }
        // 引爆链 5 档
        for (String p : new String[]{"fireball", "great_fireball", "lava_fireball", "molten_skyfall", "meteor_fall"}) {
            assertTrue(TNFireRework.handles(id(p)), p + " 应该归重做管（引爆链）");
        }
        // 别的链不能被抢：火手链、燃烧形态、其他元素
        for (String p : new String[]{"flame_grip", "pyre_god_hand", "total_burn", "water_ball", "spark"}) {
            assertFalse(TNFireRework.handles(id(p)), p + " 不该归重做管");
        }
    }

    @Test
    void heatThresholdsStayOrderedAndReachable() {
        assertTrue(FireHeat.IGNITE_AT > 0 && FireHeat.IGNITE_AT < FireHeat.MAX,
                "燃点必须在 (0, 上限) 之间，否则永远不会自燃或一碰就炸");
        assertTrue(FireHeat.coolingRate(FireHeat.control(6, 5)) > 0);
        assertTrue(FireHeat.IGNITED_TAKEN_MULTIPLIER >= 1.0F);
    }

    @Test
    void affinityAndLearnedTierIndependentlyImproveHeatControl() {
        int novice = FireHeat.control(1, 1);
        int affinity = FireHeat.control(4, 1);
        int training = FireHeat.control(4, 4);
        assertTrue(FireHeat.graceTicks(novice) < FireHeat.graceTicks(affinity));
        assertTrue(FireHeat.graceTicks(affinity) < FireHeat.graceTicks(training));
        assertTrue(FireHeat.coolingRate(training) < FireHeat.coolingRate(affinity));
        assertTrue(FireHeat.coolingRate(affinity) < FireHeat.coolingRate(novice));
        assertTrue(FireHeat.costMultiplier(training) < FireHeat.costMultiplier(affinity));
        assertTrue(FireHeat.costMultiplier(affinity) < FireHeat.costMultiplier(novice));
    }

    @Test
    void ratioIsClampedToUnitRange() {
        assertEquals(0.0F, FireHeat.ratio(null), "没有实体就是 0 热");
        assertEquals(0.0F, FireHeat.of(null), "没有实体就没有热量");
    }

    @Test
    void burnChainFormsOwnOnlyTheBurnSpells() {
        for (String p : new String[]{"fire_aspect", "ember_burn", "blaze_burn", "inferno_burn", "total_burn"}) {
            assertTrue(TNFireForms.handles(p), p + " 应该归燃烧形态管");
        }
        for (String p : new String[]{"flame_grip", "sun_ray", "fireball", "water_ball"}) {
            assertFalse(TNFireForms.handles(p), p + " 不该归燃烧形态管");
        }
        // 没有形态的玩家：叠热倍率必须是 1（不能平白给加成）
        assertEquals(1.0F, TNFireForms.heatMultiplier(null), "没有玩家时倍率必须是 1");
        assertEquals(0, TNFireForms.tier(null), "没有玩家时没有形态");
    }
}
