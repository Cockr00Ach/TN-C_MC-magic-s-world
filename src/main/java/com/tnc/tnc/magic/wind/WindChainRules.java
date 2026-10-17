package com.tnc.tnc.magic.wind;

/** Shared gameplay/presentation constants for the two replacement wind chains. */
public final class WindChainRules {
    public static int tier(int t){return Math.max(1,Math.min(5,t));}
    public static double ballRadius(int t){return new double[]{0,2,4.8,9,30,67.5}[tier(t)];}
    public static int compressionTicks(int t){return t==5?30:t==4?40:60;}
    public static double ballSize(int t,double age){
        if(t<3)return ballRadius(t);
        double f=Math.min(1,Math.max(0,age)/compressionTicks(t));
        return .12+(ballRadius(t)-.12)*Math.pow(1-f,2.4);
    }
    public static double pullRange(int t){return Math.min(112,ballRadius(t)*2.8);}
    public static int orbitStart(){return 38;}
    public static int dropStart(int t){return t==5?92:110;}
    public static int emerge(int slot){return 18+slot*12;}
    public static int swords(int t){return switch(tier(t)){case 1->3;case 2->10;case 4->20;case 5->3;default->0;};}
    public static int spears(int t){return switch(tier(t)){case 3->5;case 4->10;case 5->2;default->0;};}
    public static int count(int t){return swords(t)+spears(t);}
    public static int form(int t,int slot){return slot<swords(t)?1:2;}
    public static float modelScale(int t){return new float[]{0,2,3,5,5,60}[tier(t)];}
    public static int launch(int t){return dropStart(t);}
    public static double skyHeight(int t){return t==5?150:24;}
    public static float weaponDamage(int t,int form){return t==5?(form==1?55:85):t==4?(form==1?6:10):t==3?10:4;}
}
