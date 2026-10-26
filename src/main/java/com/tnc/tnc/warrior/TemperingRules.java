package com.tnc.tnc.warrior;

/** Prototype balance: permanent experience, separate from armor and consumable resources. */
public final class TemperingRules {
    public static final int MAX_POINTS = 10000;
    private TemperingRules() {}
    public static int clamp(int points) { return Math.max(0, Math.min(MAX_POINTS, points)); }
    public static int add(int points, int gain) {
        return (int)Math.min(MAX_POINTS, (long)clamp(points) + Math.max(0, gain));
    }
    public static double reduction(int points) { return .30 * clamp(points) / (clamp(points) + 2000.0); }
    public static int rank(int points) {
        int p = clamp(points);
        return p >= 10000 ? 4 : p >= 3000 ? 3 : p >= 1000 ? 2 : p >= 200 ? 1 : 0;
    }
    public static String rankName(int points) {
        return new String[]{"初习", "扎根", "坚韧", "百炼", "久战"}[rank(points)];
    }
    public static int killReward(float maxHealth) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0) return 0;
        return Math.max(2, Math.min(20, (int)(maxHealth / 10)));
    }
}
