package com.tnc.tnc.boss.fire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TortoiseDeathClockTest {
    @Test void noDeathBeforeAnEventOrSample() {
        assertEquals(-1, new TortoiseDeathClock().age(240000, .5F));
    }
    @Test void localTickOffsetsCannotFreezeOrSkipDeath() {
        for (int offset : new int[]{0, 50, 240000, -10000}) {
            TortoiseDeathClock clock = new TortoiseDeathClock();
            clock.receive(0, offset);
            for (int age = 0; age <= 244; age++) assertEquals(age, clock.age(offset + age, 0));
        }
    }
    @Test void lateTrackingStartsAtTheReceivedPose() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        clock.receive(180, 6000);
        assertEquals(180.5F, clock.age(6000, .5F));
        assertEquals(182, clock.age(6002, 0));
    }
    @Test void vanillaEventCanStartBeforeMetadata() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        clock.startIfAbsent(100);
        assertEquals(2, clock.age(102, 0));
        clock.receive(8, 103);
        assertEquals(8, clock.age(103, 0));
    }
    @Test void laterDeathEventDoesNotRestartReceivedProgress() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        clock.receive(180, 6000);
        clock.startIfAbsent(6005);
        assertEquals(185, clock.age(6005, 0));
    }
    @Test void restoredAgeDoesNotDependOnPreviousWorldTime() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        clock.receive(170, 0);
        assertEquals(170, clock.age(0, 0));
        assertEquals(172, clock.age(2, 0));
    }
    @Test void pauseDoesNotAdvanceAndNoAiIsNotAnInput() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        clock.receive(60, 500);
        for (int frame = 0; frame < 100; frame++) assertEquals(60.25F, clock.age(500, .25F));
        assertEquals(62.25F, clock.age(502, .25F));
    }
    @Test void regularSamplesKeepAllDeathConsumersOnTheSameAge() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        for (int age = 0; age <= 244; age++) {
            if (age % 2 == 0) clock.receive(age, 10000 + age);
            assertEquals(age, clock.age(10000 + age, 0));
        }
    }
    @Test void endIsClampedAndAResetStopsPlayback() {
        TortoiseDeathClock clock = new TortoiseDeathClock();
        clock.receive(240, 20);
        assertEquals(244, clock.age(6000, .5F));
        clock.receive(-1, 6000);
        assertEquals(-1, clock.age(6001, 0));
    }
}
