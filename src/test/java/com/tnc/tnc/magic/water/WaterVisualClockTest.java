package com.tnc.tnc.magic.water;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterVisualClockTest {
    @Test void delayedRepeatedAndJumpingServerUpdatesDoNotSnapRotation() {
        var clock=new WaterVisualClock();
        assertEquals(40.25,clock.time(7,.25F,40));
        assertEquals(40.75,clock.time(7,.75F,40));
        assertEquals(41,clock.time(8,0,40));
        assertEquals(41.5,clock.time(8,.5F,46));
        assertEquals(42,clock.time(9,0,45));
        assertEquals(43.25,clock.time(10,.25F,40));
    }
    @Test void pausedLocalTimeAndLateTrackingKeepTheirInitialPhase() {
        var clock=new WaterVisualClock();
        assertEquals(560.5,clock.time(0,.5F,560));
        assertEquals(560.5,clock.time(0,.5F,700));
        assertEquals(561,clock.time(1,0,701));
        assertEquals(.5,new WaterVisualClock().time(0,.5F,0));
    }
}
