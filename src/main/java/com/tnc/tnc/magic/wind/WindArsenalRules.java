package com.tnc.tnc.magic.wind;

/** Shared palette of real projectile forms; the summon chain retains its existing damage. */
public final class WindArsenalRules {
    public static final int MAX_PROJECTILES=16,LIFE=32;
    private WindArsenalRules(){}
    public static int blades(int tier){return tier<=1?4:6;}
    public static int escorts(int tier){return Math.max(0,Math.min(3,tier-2));}
    public static int weapon(int tier,int slot,long cycle){return tier<=2?0:tier==3?1:tier==4?2:1+Math.floorMod(slot+(int)(cycle%3),3);}
    public static double radius(int tier){return 5.5+Math.max(1,Math.min(5,tier))*.5;}
    public static double scale(int tier){return .85+Math.max(1,Math.min(5,tier))*.12;}
}
