package com.tnc.tnc.combat;

/** Shared deterministic tuning for the first co-op combat iteration. */
public final class CombatRules {
    private CombatRules() {}
    public static final double HEALTH_BONUS=20;
    public static final int RESCUE_TICKS=60, SOLO_COOLDOWN=6000;
    public static final double RESCUE_DISTANCE=3;
    public static float revivedHealth(float maximum) { return Math.max(1,maximum*.6F); }
    public static boolean canSoloDown(long now,long until) { return now>=until; }
    public static boolean wipe(int present,int standing) { return present>0&&standing==0; }
}
