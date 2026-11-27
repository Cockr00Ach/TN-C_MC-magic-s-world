package com.tnc.tnc.client;

import java.util.random.RandomGenerator;

/** Pure timing policy kept separate from the Minecraft client loop for verification. */
final class TravelMusicTiming {
    static final int MIN_DELAY_TICKS = 20 * 60 * 10;
    static final int MAX_DELAY_TICKS = 20 * 60 * 15;
    static final int INITIAL_MIN_DELAY_TICKS = MIN_DELAY_TICKS;
    static final int INITIAL_MAX_DELAY_TICKS = MAX_DELAY_TICKS;

    private TravelMusicTiming() {
    }

    static int randomInitialDelay(RandomGenerator random) {
        return random.nextInt(INITIAL_MIN_DELAY_TICKS, INITIAL_MAX_DELAY_TICKS + 1);
    }

    static int randomInterTrackDelay(RandomGenerator random) {
        return random.nextInt(MIN_DELAY_TICKS, MAX_DELAY_TICKS + 1);
    }
}
