package com.tnc.tnc.magic.wind;

/** Shared gameplay/presentation constants for the two replacement wind chains. */
public final class WindChainRules {
    public static int tier(int t){return Math.max(1,Math.min(5,t));}
    public static double ballRadius(int t){return new double[]{0,1,1.6,1.8,3,4.5}[tier(t)];}
    public static double ballSize(int t,double age){return ballRadius(t)*(t>=3?1-.72*Math.min(1,Math.max(0,age)/60):1);}
    public static int swords(int t){return switch(tier(t)){case 1->3;case 2->10;case 4->20;case 5->3;default->0;};}
    public static int spears(int t){return switch(tier(t)){case 3->5;case 4->10;case 5->2;default->0;};}
    public static int count(int t){return swords(t)+spears(t);}
    public static int form(int t,int slot){return slot<swords(t)?1:2;}
    public static float modelScale(int t){return t==5?20:1;}
    public static int launch(int t){return t>=4?30:18;}
    public static double skyHeight(int t){return t==5?55:22;}
    public static float weaponDamage(int t,int form){return t==5?(form==1?55:85):t==4?(form==1?6:10):t==3?10:4;}
}
