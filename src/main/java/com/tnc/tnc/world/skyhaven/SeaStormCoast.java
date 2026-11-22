package com.tnc.tnc.world.skyhaven;

/** Storm clearance for the original starter bounds and the sculpted island coast. */
public final class SeaStormCoast {
    // SkyIslandManager places the 520-wide realm island at (-260,-260).
    // tools/bake_skyhaven_starter.py includes its enclosing -17..16 chunk rectangle.
    public static final double LAND_MIN=-272,LAND_MAX=272,CLEAR_WATER=54;
    public enum Layout { ORIGINAL, HEADLANDS, HIGHLANDS }
    private SeaStormCoast(){}
    public static double distanceToIsland(double x,double z){
        double dx=Math.max(0,Math.max(LAND_MIN-x,x-LAND_MAX));
        double dz=Math.max(0,Math.max(LAND_MIN-z,z-LAND_MAX));
        return Math.hypot(dx,dz);
    }
    static double distanceToIsland(double x, double z, Layout layout) {
        return layout == Layout.ORIGINAL ? distanceToIsland(x,z) : HeadlandCoast.distance(x,z,layout);
    }
    public static double footprint(SeaGyreLayout.Gyre storm){return storm.radius()*2.55+6;}
    public static boolean foreground(SeaGyreLayout.Gyre storm){
        double distance = distanceToIsland(storm.x(), storm.z());
        return storm.radius() < 40 && distance >= 90 && distance < 120;
    }
    public static boolean offshore(SeaGyreLayout.Gyre storm){
        return offshore(storm, Layout.ORIGINAL);
    }
    static boolean offshore(SeaGyreLayout.Gyre storm, Layout layout) {
        double distance = distanceToIsland(storm.x(), storm.z(), layout);
        double clearance = storm.radius() < 40 && distance >= 90 && distance < 120 ? 15 : CLEAR_WATER;
        // Allow half a sample diagonal for the interpolated rebuilt coastline.
        return distance >= clearance + footprint(storm) + (layout != Layout.ORIGINAL ? 6 : 0);
    }
    /** A 30-block ribbon envelope, plus the half-sample spacing, makes this conservative. */
    public static boolean linkOffshore(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b){
        return linkOffshore(a,b,Layout.ORIGINAL);
    }
    static boolean linkOffshore(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b,Layout layout) {
        if(!offshore(a,layout)||!offshore(b,layout))return false;
        double length=Math.hypot(a.x()-b.x(),a.z()-b.z());
        for(int i=0;i<=16;i++){
            double t=i/16.;
            if(distanceToIsland(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t,layout)<CLEAR_WATER+30+length/32+(layout != Layout.ORIGINAL ? 6 : 0))return false;
        }
        return true;
    }
}
