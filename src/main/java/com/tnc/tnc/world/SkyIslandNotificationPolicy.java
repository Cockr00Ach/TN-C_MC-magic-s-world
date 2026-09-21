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
        if (islandComplete) {
            return new Decision(completionSeen ? Title.NONE : Title.COMPLETE, true);
        }
        return new Decision(awakeningSeen ? Title.NONE : Title.AWAKENING, false);
    }
}
