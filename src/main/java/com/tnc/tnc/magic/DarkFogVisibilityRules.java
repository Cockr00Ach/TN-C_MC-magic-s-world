package com.tnc.tnc.magic;

/** Pure visibility rules shared by the black-fog client effect and offline tests. */
public final class DarkFogVisibilityRules {
    private static final float[] DARKNESS_BY_TIER = {0.60F, 0.72F, 0.82F, 0.90F, 0.97F};
    private static final float CASTER_SCALE = 0.18F;

    private DarkFogVisibilityRules() {
    }

    public static int tierCount() {
        return DARKNESS_BY_TIER.length;
    }

    public static float maximumDarkness(int tier, boolean casterOwned) {
        if (tier < 1 || tier > DARKNESS_BY_TIER.length) {
            return 0.0F;
        }
        float darkness = DARKNESS_BY_TIER[tier - 1];
        return casterOwned ? darkness * CASTER_SCALE : darkness;
    }
}
