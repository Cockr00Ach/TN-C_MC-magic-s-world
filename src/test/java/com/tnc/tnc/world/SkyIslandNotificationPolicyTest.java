package com.tnc.tnc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyIslandNotificationPolicyTest {
    @Test
    void firstLoginDuringGenerationShowsOnlyTheAwakeningTitle() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(false, false, false);

        assertEquals(SkyIslandNotificationPolicy.Title.AWAKENING, decision.title());
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
    void firstLoginAfterCompletionShowsTheCoordinatesTitleAndReminder() {
        SkyIslandNotificationPolicy.Decision decision =
                SkyIslandNotificationPolicy.onLogin(true, false, false);

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
}
