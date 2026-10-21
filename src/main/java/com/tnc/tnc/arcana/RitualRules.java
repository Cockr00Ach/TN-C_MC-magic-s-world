package com.tnc.tnc.arcana;
/** Pure deterministic rules shared by world simulation, UI and regression tests. */
public final class RitualRules {
    public static final int COLD=360,HOT=620,DANGER=800,PERFECT_TICKS=30;
    public enum Quality {
        CLOUDY(.3,25),STANDARD(1,10),MASTER(2,2),WASTE(0,0);
        public final double potency;public final int toxicity;
        Quality(double potency,int toxicity){this.potency=potency;this.toxicity=toxicity;}
    }
    public static Quality quality(int sync,int perfectRemaining,boolean ruined){
        if(ruined)return Quality.WASTE;
        if(sync>=100&&perfectRemaining>0)return Quality.MASTER;
        return sync>=60?Quality.STANDARD:Quality.CLOUDY;
    }
    public static boolean sweet(int t){return t>=COLD&&t<=HOT;}
    public static int temperatureColor(int t){return t<COLD?0x315a9c:t<=HOT?0x53f7a9:t<DANGER?0xffb63c:0xff303e;}
    public static int pressureStage(double pressure){return pressure<=40?0:pressure<=75?1:pressure<100?2:3;}
    public static double pressure(double old,int acceptedPerSecond,boolean formed){
        if(!formed)return Math.max(0,old-.15);
        return Math.max(0,Math.min(100,old+(acceptedPerSecond>320?Math.min(.65,(acceptedPerSecond-320)/1600.0):-.08)));
    }
    public static double moonFactor(int phase){return new double[]{3,2,1,.5,.2,.5,1,2}[Math.floorMod(phase,8)];}
    public static int clamp(int n,int max){return Math.max(0,Math.min(max,n));}
    private RitualRules(){}
}
