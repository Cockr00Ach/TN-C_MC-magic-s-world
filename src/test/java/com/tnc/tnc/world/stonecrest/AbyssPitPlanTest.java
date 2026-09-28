package com.tnc.tnc.world.stonecrest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbyssPitPlanTest {
    @Test void sourceRectangleFitsInsideTheDeepArea() {
        for (int z=-70;z<=70;z++) for (int x=-122;x<=121;x++)
            assertEquals(-63,AbyssPitPlan.floor(x,z,80),"Source buried at "+x+","+z);
    }
    @Test void outsideAndLookoutAreNeverExcavated() {
        assertEquals(Integer.MIN_VALUE,AbyssPitPlan.floor(0,168,80));
        assertEquals(Integer.MIN_VALUE,AbyssPitPlan.floor(208,160,80));
    }
    @Test void floorNeverCutsWorldBoundaryOrRisesAboveGround() {
        for (int z=-160;z<160;z++) for (int x=-208;x<208;x++) {
            int y=AbyssPitPlan.floor(x,z,90);
            if (y!=Integer.MIN_VALUE) { assertTrue(y>=-63); assertTrue(y<=90); }
        }
    }
}
