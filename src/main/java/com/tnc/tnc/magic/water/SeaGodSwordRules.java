package com.tnc.tnc.magic.water;

/** One timeline for the server impact and client sword animation. Times start at field release. */
public final class SeaGodSwordRules {
    public static final int APPEAR_TICK=100, DROP_TICK=124, IMPACT_TICK=140, END_TICK=180;
    public static final int FIELD_LIFE=600, INTERVAL=60, COUNT=1+(FIELD_LIFE-1-IMPACT_TICK)/INTERVAL;
    public static final double LENGTH=64, DROP_HEIGHT=64;
    public static final float DAMAGE=60;
    public static final int PATH_SEAL_COUNT=5, PATH_SEAL_SPACING=12;
    public static double pathSealHeight(int index) { return (index+1)*PATH_SEAL_SPACING; }
    /** Reveal from the sky downwards; every seal is ready before the sword drops. */
    public static float pathSealOpacity(double age,int index) {
        if(index<0 || index>=PATH_SEAL_COUNT || age<APPEAR_TICK || age>=END_TICK)return 0;
        double start=APPEAR_TICK+(PATH_SEAL_COUNT-1-index)*3;
        return (float)Math.max(0,Math.min(1,Math.min((age-start)/8,(END_TICK-age)/20)));
    }

    private SeaGodSwordRules() {}
    public static int impactIndex(int age) {
        if(age<IMPACT_TICK || age>=FIELD_LIFE || (age-IMPACT_TICK)%INTERVAL!=0)return -1;
        return (age-IMPACT_TICK)/INTERVAL;
    }
    public static boolean cue(int age,int firstTick) {
        return age>=firstTick && (age-firstTick)%INTERVAL==0 && (age-firstTick)/INTERVAL<COUNT;
    }
    public static boolean visible(double age) { return age>=APPEAR_TICK && age<END_TICK; }
    public static double tipHeight(double age) {
        double t=Math.max(0,Math.min(1,(age-DROP_TICK)/(IMPACT_TICK-DROP_TICK)));
        return DROP_HEIGHT*(1-t*t*t);
    }
    public static float opacity(double age) {
        if(!visible(age))return 0;
        return (float)Math.min(1,Math.min((age-APPEAR_TICK)/12,(END_TICK-age)/20));
    }
}
