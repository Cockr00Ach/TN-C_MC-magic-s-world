package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The launch column must climb fast, finish exactly at the requested height and keep carrying. */
class EarthLaunchLayoutTest {
    @Test void columnReachesFullHeightInRiseTicks() {
        assertEquals(0.0,EarthLaunchLayout.top(0,96),1e-9);
        assertEquals(96.0,EarthLaunchLayout.top((int)EarthLaunchLayout.RISE,96),1e-9);
        assertTrue(EarthLaunchLayout.top((int)(EarthLaunchLayout.RISE*2),96)<=96.0+1e-9,"Never exceeds its height");
    }
    @Test void topIsMonotonic() {
        double last=-1;
        for(int age=0;age<=45;age++) {
            double top=EarthLaunchLayout.top(age,96);
            assertTrue(top>=last,"Top only rises");
            last=top;
        }
    }
    @Test void carriedTargetNeverSlidesDown() {
        for(int age=1;age<=(int)EarthLaunchLayout.RISE;age++)
            assertTrue(EarthLaunchLayout.lift(age,96)>.01,"Lift stays positive while the column climbs");
    }
    @Test void launchIsExtremelyHigh() {
        double peak=EarthLaunchLayout.top((int)EarthLaunchLayout.RISE,96);
        assertTrue(peak>=90,"Tier five should carry a target far above the trees");
    }
}
