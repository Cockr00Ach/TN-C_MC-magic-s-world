package com.tnc.tnc.world.ink;

/** Shared server/renderer timings: telegraphs lock before damage and the flood has a real safe wedge. */
public final class InkDragonStormRules {
    public static final int PILLARS=7, LOCK=20, FIRST_HIT=60, PILLAR_DELAY=5, END=125;
    public static final double PILLAR_RADIUS=8, GAP=.5, GAP_HALF_WIDTH=.42;
    private InkDragonStormRules(){}
    public static double pillarX(int index){return index==0?0:Math.cos((index-1)*Math.PI/3)*30;}
    public static double pillarZ(int index){return index==0?0:Math.sin((index-1)*Math.PI/3)*30;}
    public static int hitTick(int index){return FIRST_HIT+index*PILLAR_DELAY;}
    public static double ringRadius(double age){return Math.max(0,Math.min(64,(age-45)*.85));}
    public static boolean safeAngle(double angle){return Math.abs(Math.atan2(Math.sin(angle-GAP),Math.cos(angle-GAP)))<GAP_HALF_WIDTH;}
    public static boolean ringHits(double age,double x,double z,double height){
        return age>=45&&age<120&&height<3&&height> -2&&Math.abs(Math.hypot(x,z)-ringRadius(age))<2&&!safeAngle(Math.atan2(z,x));
    }
}
