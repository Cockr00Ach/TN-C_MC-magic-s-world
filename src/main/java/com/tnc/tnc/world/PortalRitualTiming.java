package com.tnc.tnc.world;

/** Shared deterministic visual timing; no client classes on the dedicated server. */
public final class PortalRitualTiming {
    public static final int DURATION = 80;
    public static final int CONVERGE = 20;
    public static final int RELEASE = 60;

    private PortalRitualTiming() {}

    public static double convergence(double tick) {
        return clamp((tick - CONVERGE) / 16.0);
    }

    public static double release(double tick) {
        return clamp((tick - RELEASE) / 12.0);
    }

    public static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
