package com.tnc.tnc.world.stonecrest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StonecrestTerrainPlannerTest {
    @Test
    void buildingColumnsAreLevelAndClearedThroughTheTemplateVolume() {
        StonecrestTerrainPlanner.ColumnPlan plan = StonecrestTerrainPlanner.plan(
                121, 100, 0, 24, true, 62, 253);

        assertEquals(100, plan.targetY());
        assertTrue(plan.clearsTemplateVolume());
        assertEquals(62, plan.clearFromY());
        assertEquals(253, plan.clearToY());
    }

    @Test
    void transitionColumnsBlendWithoutCarvingOpenAir() {
        StonecrestTerrainPlanner.ColumnPlan plan = StonecrestTerrainPlanner.plan(
                121, 100, 6, 24, false, 62, 253);

        assertEquals(109, plan.targetY());
        assertFalse(plan.clearsTemplateVolume());
    }
}
