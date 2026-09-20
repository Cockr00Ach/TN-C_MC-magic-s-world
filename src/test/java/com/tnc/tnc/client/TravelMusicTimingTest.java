package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelMusicTimingTest {
    @Test
    void configuredRangesMatchTheApprovedMinutes() {
        assertEquals(2 * 60 * 20, TravelMusicTiming.INITIAL_MIN_DELAY_TICKS);
        assertEquals(4 * 60 * 20, TravelMusicTiming.INITIAL_MAX_DELAY_TICKS);
        assertEquals(270 * 20, TravelMusicTiming.MIN_DELAY_TICKS);
        assertEquals(330 * 20, TravelMusicTiming.MAX_DELAY_TICKS);
    }

    @Test
    void randomDelaysStayInsideTheirInclusiveRanges() {
        Random random = new Random(0x54_4E_43L);
        for (int attempt = 0; attempt < 10_000; attempt++) {
            int initial = TravelMusicTiming.randomInitialDelay(random);
            int between = TravelMusicTiming.randomInterTrackDelay(random);
            assertTrue(initial >= TravelMusicTiming.INITIAL_MIN_DELAY_TICKS);
            assertTrue(initial <= TravelMusicTiming.INITIAL_MAX_DELAY_TICKS);
            assertTrue(between >= TravelMusicTiming.MIN_DELAY_TICKS);
            assertTrue(between <= TravelMusicTiming.MAX_DELAY_TICKS);
        }
    }
}
