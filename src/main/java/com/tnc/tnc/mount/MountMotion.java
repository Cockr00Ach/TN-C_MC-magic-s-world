package com.tnc.tnc.mount;

/** Distances are blocks per game tick; periods are game ticks. */
public final class MountMotion {
    public static String clip(double speed){return speed<.012?"idle":speed<.10?"walk":speed<.21?"trot":"gallop";}
    public static double rate(String clip,double speed){if(clip.equals("idle"))return 1;return Math.max(.65,Math.min(1.65,speed/(clip.equals("walk")?.065:clip.equals("trot")?.155:.30)));}
    private static final int UNICORN_ACCELERATION_TICKS = 60;

    static int advanceUnicornRun(int ticks, boolean forwardInput, boolean movedForward) {
        return forwardInput && movedForward ? Math.min(ticks + 1, UNICORN_ACCELERATION_TICKS) : 0;
    }

    static float unicornSpeedMultiplier(int ticks) {
        return 1.5f + Math.min(ticks, UNICORN_ACCELERATION_TICKS) / (float) UNICORN_ACCELERATION_TICKS;
    }

    static boolean isUnicornRunAtCap(int ticks) {
        return ticks == UNICORN_ACCELERATION_TICKS;
    }

    static int reconcileUnicornRun(int prediction, int serverTicks) {
        if (prediction == 0 || serverTicks == 0) {
            return 0;
        }
        int difference = serverTicks - prediction;
        // A packet covers several ticks, so small client leads belong to normal prediction.
        return Math.abs(difference) > 6 ? prediction + Integer.signum(difference) * 3 : prediction;
    }

    private MountMotion(){}
}
