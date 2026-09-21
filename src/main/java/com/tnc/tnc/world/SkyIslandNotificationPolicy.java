package com.tnc.tnc.world;

/** Pure decision table for login notifications. */
final class SkyIslandNotificationPolicy {
    private static final int INITIAL_COUNTDOWN_TICKS = 20 * 60 * 2;
    private static final int COMPLETION_GAP_TICKS = 20 * 8;
    private static final int PERIODIC_SAVE_TICKS = 20 * 20;

    private SkyIslandNotificationPolicy() {
    }

    enum Title {
        NONE,
        AWAKENING,
        COMPLETE
    }

    record Decision(Title title, boolean sendCoordinates) {
    }

    static Decision onLogin(boolean islandComplete, boolean awakeningSeen, boolean completionSeen) {
        return onLogin(islandComplete, awakeningSeen, completionSeen, false, 0);
    }

    static Decision onLogin(boolean islandComplete, boolean awakeningSeen, boolean completionSeen,
                            boolean completionPending, int completionDelayTicks) {
        if (!islandComplete) {
            return new Decision(Title.NONE, false);
        }
        if (completionSeen) {
            return new Decision(Title.NONE, true);
        }
        if (completionPending && completionDelayTicks > 0) {
            return new Decision(Title.NONE, false);
        }
        return awakeningSeen
                ? new Decision(Title.COMPLETE, true)
                : new Decision(Title.NONE, false);
    }

    static Decision onGenerationComplete(boolean awakeningSeen, boolean completionSeen,
                                         boolean completionPending) {
        return completionSeen || !awakeningSeen || completionPending
                ? new Decision(Title.NONE, false)
                : new Decision(Title.COMPLETE, true);
    }

    static boolean countdownExpired(int remainingTicks) {
        return remainingTicks <= 1;
    }

    static int initialCountdownTicks() {
        return INITIAL_COUNTDOWN_TICKS;
    }

    static int completionGapTicks() {
        return COMPLETION_GAP_TICKS;
    }

    static int advanceDelay(int remainingTicks) {
        return Math.max(0, remainingTicks - 1);
    }

    static boolean periodicSaveDue(boolean activeSequence, long gameTime) {
        return activeSequence && gameTime % PERIODIC_SAVE_TICKS == 0L;
    }
}
