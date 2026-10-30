package com.tnc.tnc.world.ink;

public final class InkDragonDrainRules {
    public static final int APPROACH=40,MARK=80,RECOVER=125,END=165;
    public static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float pitch(double age){return (float)(60*smooth(age/APPROACH)*(1-smooth((age-RECOVER)/(END-RECOVER))));}
    private InkDragonDrainRules(){}
}
