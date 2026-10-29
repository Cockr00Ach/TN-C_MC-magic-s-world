package com.tnc.tnc.boss.fire;
/** Cool and bow, slowly fade, disperse into surface dust, then reveal the remains. */
public final class TortoiseDeath {
    public static final int COOL_START = 0, COOL_END = 60, BOW_END = 120;
    public static final int FADE_START = 100, FADE_END = 216;
    public static final int SCATTER_START = 164, SCATTER_END = FADE_END;
    public static final int MAX_PARTICLE_LIFE = 24, REMAINS_AT = SCATTER_END + MAX_PARTICLE_LIFE;
    public static final int DURATION = REMAINS_AT + 4;
    private TortoiseDeath() {}
    public static float age(long start, long now, float partial) {
        return start < 0 ? -1 : Math.max(0, Math.min(DURATION, now - start + partial));
    }
    public static float cooling(float age) { return TortoiseTransition.smooth(age / COOL_END); }
    public static float glow(float age) { return 1 - cooling(age); }
    public static float opacity(float age) {
        float remaining = 1 - TortoiseTransition.smooth((age - FADE_START) / (FADE_END - FADE_START));
        // Overlapping rock faces compound their opacity. Ease each surface down earlier.
        return remaining * remaining;
    }
    public static int particleBudget(float age) {
        if (age >= SCATTER_START && age < SCATTER_END) {
            return 12 + (int)(28 * TortoiseTransition.smooth((age - SCATTER_START) / 30));
        }
        return age >= 0 && age < COOL_END ? 2 : 0;
    }
}
