package com.tnc.tnc.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeviathanMovementRulesTest {
    @Test void swimmingConvergesToTargetsAboveAndBelowWithoutSinkingAway() {
        for (double target : new double[]{-8.0D, 8.0D}) {
            double y = 0, speed = 0;
            for (int tick = 0; tick < 200; tick++) {
                speed = Math.max(-0.20D, Math.min(0.20D,
                        speed+LeviathanMovementRules.swimVerticalAcceleration(target-y)));
                y += speed;
                speed *= 0.88D;
            }
            assertEquals(target, y, 0.02D);
        }
    }
    @Test void groundTargetsCannotPullTheBossOntoTheGround() {
        assertEquals(76.0D, LeviathanMovementRules.airDestinationY(64, 64, 310));
        assertEquals(112.0D, LeviathanMovementRules.airDestinationY(70, 100, 310));
        assertEquals(140.0D, LeviathanMovementRules.airDestinationY(140, 100, 310));
    }

    @Test void flightNeverExceedsTheWorldCeiling() {
        assertEquals(310.0D, LeviathanMovementRules.airDestinationY(400, 100, 310));
        assertEquals(310.0D, LeviathanMovementRules.airDestinationY(308, 309, 310));
    }

    @Test void mistHasAFixedBudgetEvenWithManyBosses() {
        int maximumLiveParticles = (int)Math.ceil(LeviathanMovementRules.MIST_LIFETIME
                /(double)LeviathanMovementRules.MIST_INTERVAL)*LeviathanMovementRules.MIST_TOTAL_BUDGET;
        assertTrue(maximumLiveParticles<=132);
        assertEquals(4, LeviathanMovementRules.MIST_PER_BOSS);
    }
}
