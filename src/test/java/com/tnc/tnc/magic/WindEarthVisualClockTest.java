package com.tnc.tnc.magic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WindEarthVisualClockTest {
    @Test void wrappedOrDelayedPacketsCannotRewindAirflow() {
        var clock=new WindEarthVisualClock();
        assertEquals(351,clock.phase(13,0,351,27));
        assertEquals(364.5,clock.phase(13,.5F,18,27));
        assertEquals(378,clock.phase(14,0,18,27));
        assertEquals(405,clock.phase(15,0,0,27));
    }
    @Test void lateJoinAndPartialFramesKeepPhaseAndRateChangeIsContinuous() {
        var clock=new WindEarthVisualClock();
        assertEquals(108,clock.phase(1,0,108,27));
        assertEquals(128.25,clock.phase(1,.75F,0,27));
        assertEquals(128.25,clock.phase(1,.75F,0,20));
        assertEquals(133.25,clock.phase(2,0,0,20));
    }
}
