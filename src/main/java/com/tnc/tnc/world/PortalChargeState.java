package com.tnc.tnc.world;

/** Pure server-side state transition for one player's portal charge. */
record PortalChargeState(boolean fromGround, long startedAt) {
    static final long DURATION_TICKS = 50L;

    Decision evaluate(boolean alive, boolean atGround, boolean atIsland, long currentTick) {
        if (!alive || (fromGround ? !atGround : !atIsland)) {
            return Decision.CANCEL;
        }
        return currentTick - startedAt >= DURATION_TICKS
                ? Decision.COMPLETE
                : Decision.CHARGING;
    }

    enum Decision {
        CHARGING,
        COMPLETE,
        CANCEL
    }
}
