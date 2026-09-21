package com.tnc.tnc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyIslandNotificationPolicyTest {
    @Test
    void firstLoginDuringGenerationWaitsForTheOnlineCountdown() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(false, false, false);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void repeatedLoginDuringGenerationStaysQuiet() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(false, true, false);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void firstLoginAfterEarlyCompletionStillWaitsForAwakening() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(true, false, false);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void completionAfterAwakeningShowsTheCoordinatesImmediately() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(true, true, false);

        assertEquals(SkyIslandNotificationPolicy.Title.COMPLETE, decision.title());
        assertTrue(decision.sendCoordinates());
    }

    @Test
    void returningToACompletedWorldOnlyRepeatsTheChatReminder() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(true, true, true);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertTrue(decision.sendCoordinates());
    }

    @Test
    void recoveryDoesNotRepeatAnAlreadyDeliveredCompletionNotification() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(true, true, false);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void earlyGenerationCompletionWaitsBehindAwakening() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(false, false, false);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void generationCompletionAfterAwakeningIsImmediate() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(true, false, false);

        assertEquals(SkyIslandNotificationPolicy.Title.COMPLETE, decision.title());
        assertTrue(decision.sendCoordinates());
    }

    @Test
    void countdownExpiresOnItsLastOnlineTick() {
        assertFalse(SkyIslandNotificationPolicy.countdownExpired(2));
        assertTrue(SkyIslandNotificationPolicy.countdownExpired(1));
        assertTrue(SkyIslandNotificationPolicy.countdownExpired(0));
    }

    @Test
    void loginDuringThePostAwakeningGapDoesNotBypassIt() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(true, true, false, true, 80);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void completionAfterAwakeningIsNotDelayedWithoutAnEarlyCompletionPending() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(true, false, false);

        assertEquals(SkyIslandNotificationPolicy.Title.COMPLETE, decision.title());
        assertTrue(decision.sendCoordinates());
    }

    @Test
    void duplicateCompletionDuringAnExistingGapRemainsDeferred() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onGenerationComplete(true, false, true);

        assertEquals(SkyIslandNotificationPolicy.Title.NONE, decision.title());
        assertFalse(decision.sendCoordinates());
    }

    @Test
    void timingConstantsAreExactlyTwoMinutesAndEightSeconds() {
        assertEquals(2_400, SkyIslandNotificationPolicy.initialCountdownTicks());
        assertEquals(160, SkyIslandNotificationPolicy.completionGapTicks());
    }

    @Test
    void eightSecondGapExpiresOnExactlyIts160thTick() {
        int remaining = SkyIslandNotificationPolicy.completionGapTicks();
        for (int tick = 1; tick < 160; tick++) {
            remaining = SkyIslandNotificationPolicy.advanceDelay(remaining);
            assertTrue(remaining > 0);
        }
        assertEquals(0, SkyIslandNotificationPolicy.advanceDelay(remaining));
    }

    @Test
    void activeSequencesArePersistedEveryTwentySeconds() {
        assertFalse(SkyIslandNotificationPolicy.periodicSaveDue(true, 399));
        assertTrue(SkyIslandNotificationPolicy.periodicSaveDue(true, 400));
        assertFalse(SkyIslandNotificationPolicy.periodicSaveDue(false, 400));
    }
}
