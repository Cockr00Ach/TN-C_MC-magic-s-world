package com.tnc.tnc.magic;

/** Shared gate formation and release timing, measured in game ticks. */
public final class NightHandPresentation {
    public static final int FORM_TICKS = 8;
    public static final int PREPARE_TICKS = 16;
    private NightHandPresentation() {}
    public static boolean ready(int age) { return age > PREPARE_TICKS; }
    public static float formation(float age) {
        float t = Math.max(0.0F, Math.min(1.0F, age / FORM_TICKS));
        return t * t * (3.0F - 2.0F * t);
    }
}
