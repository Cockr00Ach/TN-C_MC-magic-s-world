package com.tnc.tnc.magic.water;

/** One timeline for the server impact and client sword animation. Times start at field release. */
public final class SeaGodSwordRules {
    public static final int APPEAR_TICK=100, DROP_TICK=124, IMPACT_TICK=140, END_TICK=180;
    public static final int FIELD_LIFE=600, INTERVAL=60, COUNT=1+(FIELD_LIFE-1-IMPACT_TICK)/INTERVAL;
    public static final double LENGTH=64, DROP_HEIGHT=64;
    public static final float DAMAGE=60;
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
