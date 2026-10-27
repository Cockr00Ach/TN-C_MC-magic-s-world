package com.tnc.tnc.warrior;

/** Innate aptitude is assigned by character creation/story, never earned from grinding. */
public final class WarriorTalentRules {
    private static final int[] CAPS = {0, 200, 1000, 3000, 6000, 8000, 10000};
    private WarriorTalentRules() {}
    public static int clamp(int talent) { return Math.max(0, Math.min(6, talent)); }
    public static int cap(int talent) { return CAPS[clamp(talent)]; }
    public static double multiplier(int talent) { return clamp(talent) * .5; }
    public static int gain(int base, int talent) {
        return (int)Math.min(TemperingRules.MAX_POINTS, Math.max(0L, (long)base) * clamp(talent) / 2L);
    }
    public static long halfUnits(int base, int talent, int remainder) {
        if (base <= 0 || clamp(talent) == 0) return 0;
        return (long)base * clamp(talent) + Math.max(0, Math.min(1, remainder));
    }
}
