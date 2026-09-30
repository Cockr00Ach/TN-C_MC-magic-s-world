package com.tnc.tnc.world.stonecrest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandmarkPlacementPlanTest {
    @Test void fullLandscapeAllowsRollingLandButNotSteepMountains() {
        assertFalse(LandmarkPlacementPlan.terrainFits(64,100,false));
        assertTrue(LandmarkPlacementPlan.terrainFits(64,100,true));
        assertTrue(LandmarkPlacementPlan.terrainFits(64,112,true));
        assertFalse(LandmarkPlacementPlan.terrainFits(64,113,true));
        assertTrue(LandmarkPlacementPlan.terrainFits(64,94,false));
        assertFalse(LandmarkPlacementPlan.terrainFits(64,95,false));
    }
    @Test void cathedralIsUnscaledButFitsEntireWorldHeight() {
        int y=LandmarkPlacementPlan.originY(70,80,1,358,320,false,true);
        assertEquals(-42,y); assertEquals(315,y+358-1);
        assertTrue(LandmarkPlacementPlan.fits(y,358,-64,320));
    }
    @Test void floatingIslandKeepsGroundClearanceAndRejectsMountains() {
        int y=LandmarkPlacementPlan.originY(64,100,0,165,320,true,false);
        assertEquals(132,y); assertTrue(LandmarkPlacementPlan.fits(y,165,-64,320));
        int high=LandmarkPlacementPlan.originY(150,180,0,165,320,true,false);
        assertFalse(LandmarkPlacementPlan.fits(high,165,-64,320));
    }
    @Test void palaceKeepsOriginalUndergroundOffset() {
        int y=LandmarkPlacementPlan.originY(70,75,38,192,320,false,false);
        assertEquals(32,y); assertEquals(70,y+38);
        assertTrue(LandmarkPlacementPlan.fits(y,192,-64,320));
    }
}
