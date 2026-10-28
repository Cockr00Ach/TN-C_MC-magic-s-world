package com.tnc.tnc.boss.fire;

/** Server game-time death beats, shared by animation, cooling and mesh dissolution. */
public final class TortoiseDeath {
    public static final int FREEZE_END = 12;
    public static final int FALL_START = 34;
    public static final int IMPACT = 44;
    public static final int COOL_START = 50;
    public static final int COOL_END = 94;
    public static final int EMBER_END = 116;
    public static final int DISSOLVE_START = 128;
    public static final int DURATION = 188;

    private TortoiseDeath() {}

    public static int particleBudget(float age) {
        if (age < IMPACT || age >= DURATION || (age >= EMBER_END && age < DISSOLVE_START)) return 0;
        if (age >= DISSOLVE_START || age < IMPACT + 3) return 40;
        if (age >= COOL_END) return 3;
        return age >= COOL_START ? 6 : 0;
    }

    public static float age(long start, long now, float partial) {
        return start < 0 ? -1 : Math.max(0, Math.min(DURATION, now - start + partial));
    }

    public static float glow(float age) {
        if (age < 0) return 1;
        if (age < FREEZE_END) return 1 + 0.4F * Math.max(0, 1 - Math.abs(age - 3) / 3);
        if (age < COOL_START) return ((int) age == 18 || (int) age == 26 || (int) age == 32) ? 0.2F : 0.85F;
        if (age < COOL_END) return 0.85F * (1 - TortoiseTransition.smooth((age - COOL_START) / (COOL_END - COOL_START)));
        return age >= 106 && age < 112 ? 0.08F * (1 - Math.abs(age - 109) / 3) : 0;
    }

    public static float cooling(float age) {
        return TortoiseTransition.smooth((age - COOL_START) / (COOL_END - COOL_START));
    }

    public static float dissolved(float age) {
        return Math.max(0, Math.min(1, (age - DISSOLVE_START) / (DURATION - DISSOLVE_START)));
    }

    /** Bounds enclose the final collapsed poses; height is measured above the entity origin. */
    public static float cutoff(float age, boolean phaseTwo) {
        float top = phaseTwo ? 7.28F : 4.42F;
        return top + (-0.15F - top) * dissolved(age);
    }

    public static float shake(float age) {
        return age >= IMPACT && age < IMPACT + 8 ? 0.14F * (IMPACT + 8 - age) / 8 : 0;
    }
}
