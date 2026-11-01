package com.tnc.tnc.world;

/** Readiness uses actual chunk/render work; a minimum warm-up also lets late resources settle. */
public final class SkyhavenReadiness {
    public static final int CLIENT_RADIUS=3, CLIENT_CHUNKS=49, NEAR_SECTIONS=9;
    public static boolean ready(long millis,int chunks,int sections,int quietTicks) {
        return ready(millis,chunks,CLIENT_CHUNKS,sections,quietTicks);
    }
    public static boolean ready(long millis,int chunks,int required,int sections,int quietTicks) {
        return millis>=10_000 && chunks>=required && sections>=NEAR_SECTIONS
                && (quietTicks>=40 || millis>=45_000);
    }
    private SkyhavenReadiness(){}
}
