package com.tnc.tnc.world.ink;
public final class InkDragonEruptionRules {
    public static final int SEAL=20,LOCK=40,RISE=45,TOP=61,ORB=70,END=180;
    public static final double LIFT=48;
    private InkDragonEruptionRules(){}
    public static double rise(double age){double t=Math.max(0,Math.min(1,(age-RISE)/(TOP-RISE)));return t*t*(3-2*t);}
    public static float pitch(double age){if(age<20)return (float)(age/20*80);if(age<RISE)return 80;if(age<TOP)return -82;return (float)(-82*Math.max(0,1-(age-TOP)/34));}
    public static boolean hidden(int age){return age>=20&&age<RISE;}
}
