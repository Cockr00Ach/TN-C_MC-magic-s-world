package com.tnc.tnc.magic;
public final class EarthActionRules {
    private EarthActionRules(){}
    public static int shots(int tier){return tier==3?8:tier==4?10:1;}
    public static int spikes(int tier){return tier==2?3:tier==3?6:tier==4?36:tier==5?5:0;}
    public static double coefficient(int kind,int tier){return(kind==3?new double[]{1.1,1.6,2,2.4,2.9}:new double[]{.7,1.1,1.4,1.8,2.3})[tier-1];}
}
