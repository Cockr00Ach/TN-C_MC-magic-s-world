package com.tnc.tnc.world.stonecrest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StonecrestTerrainPlannerTest {
    @Test
    void landscapeTransitionIsFlatAtBothEndsAndKeepsSourceElevation() {
        assertEquals(112, StonecrestTerrainPlanner.plan(64,112,0,96,false,0,0,true).targetY());
        assertEquals(112, StonecrestTerrainPlanner.plan(64,112,1,96,false,0,0,true).targetY());
        assertEquals(64, StonecrestTerrainPlanner.plan(64,112,95,96,false,0,0,true).targetY());
        assertEquals(64, StonecrestTerrainPlanner.plan(64,112,96,96,false,0,0,true).targetY());
        assertEquals(88, StonecrestTerrainPlanner.plan(64,112,48,96,false,0,0,true).targetY());
    }
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
