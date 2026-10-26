package com.tnc.tnc.warrior;

public final class WindInfusionRules {
    public static final int REQUIRED_TEMPERING = 200;
    public static final int COOLDOWN_TICKS = 160;
    public static final float DAMAGE = 4;
    private WindInfusionRules() {}
    public static double range(boolean windRealm) { return windRealm ? 9 : 6; }
    public static boolean ready(long now, long readyAt) { return now >= readyAt; }
}
