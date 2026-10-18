package com.tnc.tnc.magic;
public final class NightHandReleaseRules {
    private NightHandReleaseRules(){}
    public static int count(int tier){return new int[]{1,2,3,5,7}[Math.max(1,Math.min(5,tier))-1];}
    public static int delay(int slot,boolean sky){return 0;}
    public static double spacing(double modelHalf){return modelHalf*2+.8;}
}
