package com.tnc.tnc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Guards the water-dragon windup numbers: it has to stay <b>bigger than the lightning one</b>
 * (作者 2026-11-06：「要比较大只的水龙」), keep one uniform scale, and give every dragon a
 * sane flight (bezier endpoints are the gate and the caster's chest).
 */
class WaterDragonFxTest {

    /** 雷法小雷龙全长约 0.75 格（scale .28 那套）；水龙至少要明显大过它。 */
    private static final double LIGHTNING_WINDUP_DRAGON_LENGTH = .75D;

    @Test void waterDragonIsBiggerThanTheLightningWindupDragon() {
        for (int tier = 1; tier <= 5; tier++) {
            double length = WaterDragonFx.lengthFor(WaterDragonFx.scaleFor(tier));
            assertTrue(length > LIGHTNING_WINDUP_DRAGON_LENGTH * 3.5,
                    "tier " + tier + " dragon is only " + length + " blocks long");
            assertTrue(length > 2.8, "tier " + tier + " should be a big dragon, got " + length);
        }
    }

    @Test void scaleGrowsWithTierAndLengthIsOneUniformScale() {
        double previous = 0;
        for (int tier = 1; tier <= 5; tier++) {
            double scale = WaterDragonFx.scaleFor(tier);
            assertTrue(scale > previous, "tier " + tier + " should be bigger than tier " + (tier - 1));
            previous = scale;
            // 只有一个自由度：全长 = 常数 × 体型（不是"越到后面越细长"）。
            assertEquals(WaterDragonFx.lengthFor(1.0D), WaterDragonFx.lengthFor(scale) / scale, 1e-9);
        }
    }

    @Test void everyTierGetsAtLeastOneDragonAndRatiosAreOrdered() {
        int previousCount = 0;
        for (int tier = 1; tier <= 5; tier++) {
            int count = WaterDragonFx.spawnCountFor(tier);
            assertTrue(count >= 1, "tier " + tier + " must spawn at least one dragon");
            assertTrue(count >= previousCount, "higher tiers should not spawn fewer dragons");
            previousCount = count;

            float previous = 0;
            for (int i = 0; i < count; i++) {
                float ratio = WaterDragonFx.spawnRatioFor(tier, i);
                assertTrue(ratio > previous, "tier " + tier + " dragon " + i + " must come after the previous one");
                assertTrue(ratio > 0 && ratio < 1, "ratio out of range: " + ratio);
                previous = ratio;
            }
        }
    }

    @Test void dragonsFlyFromTheirGateIntoTheChest() {
        Vec3 from = new Vec3(3, 64, 4);
        Vec3 to = new Vec3(0, 65.2, 0);
        Vec3 ctrl = new Vec3(1.5, 66, 2);
        Vec3 start = WaterDragonFx.bezierFor(from, ctrl, to, 0F);
        Vec3 end = WaterDragonFx.bezierFor(from, ctrl, to, 1F);
        assertEquals(0, start.distanceTo(from), 1e-9);
        assertEquals(0, end.distanceTo(to), 1e-9);
        Vec3 mid = WaterDragonFx.bezierFor(from, ctrl, to, .5F);
        assertTrue(mid.y > Math.min(from.y, to.y), "the control point should arc the flight upwards");
        for (int i = 1; i <= 4; i++) {
            assertTrue(WaterDragonFx.lifeFor(i) >= 17 && WaterDragonFx.lifeFor(i) <= 26,
                    "life must stay short enough to read at cast speed");
        }
    }
}
