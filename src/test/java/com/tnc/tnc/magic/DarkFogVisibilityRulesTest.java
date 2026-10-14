package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkFogVisibilityRulesTest {
    @Test
    void enemyFogBecomesNearlyBlackByTierFive() {
        float previous = 0.0F;
        for (int tier = 1; tier <= DarkFogVisibilityRules.tierCount(); tier++) {
            float darkness = DarkFogVisibilityRules.maximumDarkness(tier, false);
            assertTrue(darkness > previous, "higher fog tiers must be darker");
            previous = darkness;
        }
        assertTrue(previous >= 0.95F, "tier five should be nearly pure black");
    }

    @Test
    void casterCanStillSeeInsideOwnFog() {
        for (int tier = 1; tier <= DarkFogVisibilityRules.tierCount(); tier++) {
            assertTrue(DarkFogVisibilityRules.maximumDarkness(tier, true) <= 0.18F,
                    "caster overlay must preserve target visibility");
        }
        assertEquals(0.0F, DarkFogVisibilityRules.maximumDarkness(0, false));
        assertEquals(0.0F, DarkFogVisibilityRules.maximumDarkness(6, true));
    }
}
