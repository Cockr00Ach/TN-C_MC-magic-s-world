package com.tnc.tnc.world.skyhaven;

/** Only for the fixed Skyhaven realm: authored chunks span -17..16, with flat sea beyond. */
public final class SeaStormCoast {
    // SkyIslandManager places the 520-wide realm island at (-260,-260).
    // tools/bake_skyhaven_starter.py includes its enclosing -17..16 chunk rectangle.
    public static final double LAND_MIN=-272,LAND_MAX=272,CLEAR_WATER=80;
    private SeaStormCoast(){}
    public static double distanceToIsland(double x,double z){
        double dx=Math.max(0,Math.max(LAND_MIN-x,x-LAND_MAX));
        double dz=Math.max(0,Math.max(LAND_MIN-z,z-LAND_MAX));
        return Math.hypot(dx,dz);
    }
    public static double footprint(SeaGyreLayout.Gyre storm){return storm.radius()*2.55+6;}
    public static boolean offshore(SeaGyreLayout.Gyre storm){
        return distanceToIsland(storm.x(),storm.z())>=CLEAR_WATER+footprint(storm);
    }
    /** A 30-block ribbon envelope, plus the half-sample spacing, makes this conservative. */
    public static boolean linkOffshore(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b){
        if(!offshore(a)||!offshore(b))return false;
        double length=Math.hypot(a.x()-b.x(),a.z()-b.z());
        for(int i=0;i<=16;i++){
            double t=i/16.;
            if(distanceToIsland(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t)<CLEAR_WATER+30+length/32)return false;
        }
        return true;
    }
}
