package com.tnc.tnc.sanctum;

/** Server ticks, shared with the rig and cameras. Skill age zero is the animation packet's start. */
public final class SanctumTimings {
    public static final int AWAKEN = 120, EYES_START = 22, EYES_OPEN = 52, SMILE_OPEN = 82, TEARS_END = 114;
    public static final int RUPTURE = 160, FINGERS = 28, ARM_OUT = 76, SHELL_SPLIT = 108, BLACK_END = 120, REVEALED = 144;
    public static final int COMBO = 88, CUT_ONE = 14, LOCK_TWO = 18, CUT_TWO = 31, LOCK_HEAVY = 38, HEAVY_IMPACT = 70;
    public static final double FAST_CUT_RANGE = 32;
    public static final int GAZE_CONTACT = 19, GAZE_HOLD = 5 * 20;
    public static final int GAZE_STAIN_START = GAZE_CONTACT + GAZE_HOLD;
    public static final int GAZE_HEAD_END = GAZE_STAIN_START + 15, GAZE_END = GAZE_STAIN_START + 10 * 20;
    public static final int MOUTH_START = 46, TEARS_START = 72;
    public static final int BLEED_INTERVAL = 18, BLEED_PULSES = 3;
    public static final int DANCE_LOCK = 70, DANCE_IMPACT = 112;
    public static final int WINGCUT = 92, WING_LOCK = 19, WING_IN = 44, WING_IMPACT = 54, WING_OUT = 64, WING_LAND = 86;
    public static final int HOOK = 82, HOOK_LOCK = 20, HOOK_IMPACT = 38, HOOK_RELEASE = 66;
    public static final double PALM_REACH = 17, PALM_RADIUS = 3.3;
    public static final double SEATED_REACH = 16, DEMON_REACH = 27, DEMON_REST_CLAW = 15;
    private SanctumTimings() {}
}
