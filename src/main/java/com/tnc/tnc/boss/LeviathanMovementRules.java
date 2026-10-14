package com.tnc.tnc.boss;

/** World-independent flight clearance and particle budgets, also used by focused tests. */
public final class LeviathanMovementRules {
    public static final double AIR_TARGET_HEIGHT = 2.8D;
    public static final double FLOOR_CLEARANCE = 3.0D;
    public static final int MIST_INTERVAL = 3;
    public static final int MIST_PER_BOSS = 4;
    public static final int MIST_TOTAL_BUDGET = 12;
    public static final int MIST_LIFETIME = 32;

    private LeviathanMovementRules() {}

    public static double airDestinationY(double desired, double floor, double ceiling) {
        return Math.min(ceiling, Math.max(desired, floor+FLOOR_CLEARANCE));
    }

    public static double swimVerticalAcceleration(double depthDifference) {
        return Math.max(-0.025D, Math.min(0.025D, depthDifference*0.009D));
    }
}
