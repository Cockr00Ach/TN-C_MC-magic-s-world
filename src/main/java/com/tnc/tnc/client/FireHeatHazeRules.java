package com.tnc.tnc.client;

/** Bounded screen-space refraction. These limits do not depend on render resolution. */
public final class FireHeatHazeRules {
    public static final int MASKS = 4, CANDIDATES = 24;
    public static final float MAX_PIXELS = 2F;
    private FireHeatHazeRules() {}
    public static float strength(float value) {
        return Float.isFinite(value) ? Math.max(0F, Math.min(1F, value)) : 0F;
    }
    public static double radius(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(12, value)) : 0;
    }
    public static float edge(float distance) {
        if (!Float.isFinite(distance)) return 0;
        float t = Math.max(0F, Math.min(1F, (distance - .12F) / .88F));
        return 1F - t * t * (3F - 2F * t);
    }
}
