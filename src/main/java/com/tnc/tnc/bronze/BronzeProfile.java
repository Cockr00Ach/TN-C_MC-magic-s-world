package com.tnc.tnc.bronze;

/** Item statistics only; the existing warrior system owns all melee timing and animations. */
public enum BronzeProfile {
    GREATSWORD(10,1.0f),SPEAR(7,1.5f),DAO(8,1.3f),DAGGER(4,2.5f);

    public final double damage;
    public final float speed;
    BronzeProfile(double damage,float speed) {
        this.damage=damage;this.speed=speed;
    }
    public double upgraded(int level){return damage*(1+0.1*Math.max(0,Math.min(10,level)));}
    public static float charge(int ticks){return Math.max(0,Math.min(30,ticks))/30f;}
    public static float throwMultiplier(int ticks){return 1+charge(ticks);}
    public String id(){return "bronze_"+name().toLowerCase(java.util.Locale.ROOT);}
}
