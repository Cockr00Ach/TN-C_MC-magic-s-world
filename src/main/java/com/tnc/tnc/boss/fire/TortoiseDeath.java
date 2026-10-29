package com.tnc.tnc.boss.fire;
/** Shared server clock: cool for 3 seconds, bow for 3, then fade for 3. */
public final class TortoiseDeath {
    public static final int COOL_START = 0, COOL_END = 60, BOW_END = 120;
    public static final int FADE_START = BOW_END, DURATION = 180;
    private TortoiseDeath() {}
    public static float age(long start, long now, float partial) {
        return start < 0 ? -1 : Math.max(0, Math.min(DURATION, now - start + partial));
    }
    public static float cooling(float age) { return TortoiseTransition.smooth(age / COOL_END); }
    public static float glow(float age) { return 1 - cooling(age); }
    public static float opacity(float age) {
        return 1 - TortoiseTransition.smooth((age - FADE_START) / (DURATION - FADE_START));
    }
    public static int particleBudget(float age) { return age >= 0 && age < COOL_END ? 2 : 0; }
}
