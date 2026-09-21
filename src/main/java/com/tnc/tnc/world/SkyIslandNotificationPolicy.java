package com.tnc.tnc.world;

/** Pure decision table for login notifications. */
final class SkyIslandNotificationPolicy {
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
        if (!islandComplete) {
            return new Decision(Title.NONE, false);
        }
        if (completionSeen) {
            return new Decision(Title.NONE, true);
        }
        return awakeningSeen
                ? new Decision(Title.COMPLETE, true)
                : new Decision(Title.NONE, false);
    }

    static Decision onGenerationComplete(boolean awakeningSeen, boolean completionSeen) {
        return completionSeen || !awakeningSeen
                ? new Decision(Title.NONE, false)
                : new Decision(Title.COMPLETE, true);
    }

    static boolean countdownExpired(int remainingTicks) {
        return remainingTicks <= 1;
    }
}
