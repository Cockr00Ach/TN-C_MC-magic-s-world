package com.tnc.tnc.world.stonecrest;

/** Pure, deterministic pit profile. The world floor is never cleared. */
public final class AbyssPitPlan {
    public static final int RADIUS_X = 204;
    public static final int RADIUS_Z = 152;
    public static final int WIDTH = 416;
    public static final int DEPTH = 320;
    public static final int FLOOR = -63;
    private AbyssPitPlan() { }

    public static double radius(int dx, int dz) {
        double angle = Math.atan2(dz, dx);
        double edge = 1 + 0.018 * Math.sin(angle * 5) + 0.012 * Math.sin(angle * 9 + 1);
        return Math.hypot(dx / (double) RADIUS_X, dz / (double) RADIUS_Z) / edge;
    }

    /** MIN_VALUE is outside the excavation; short terraces soften the wall. */
    public static int floor(int dx, int dz, int surface) {
        double r = radius(dx, dz);
        if (r >= 1) return Integer.MIN_VALUE;
        if (r <= 0.80) return FLOOR;
        double t = (r - 0.80) / 0.20;
        int height = FLOOR + (int) Math.floor(Math.pow(t, 1.4) * (surface - FLOOR));
        return r > 0.98 ? height : FLOOR + ((height - FLOOR) / 4) * 4;
    }
}
