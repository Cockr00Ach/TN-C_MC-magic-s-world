package com.tnc.tnc.world.wind;

/** Bounded encounter budgets and shared visual/gameplay scale. */
public final class CanglanWarGodRules {
    public static final int TORNADO = 9, WAVE = 10, ARSENAL_4 = 11, ARSENAL_5 = 12;
    public static final int MAX_CONSECUTIVE_STEPS = 3;
    public static final int ANCHORS = 10, ANCHOR_LIFETIME = 1200, DODGE_COOLDOWN = 70;
    public static final float DODGE_CHANCE = .28F;
    public static float scale(int phase) { return phase == 2 ? 5F : 1F; }
    public static int choose(int cycle, double distance, float scale) {
        return switch (Math.floorMod(cycle, 8)) {
            case 1 -> distance <= sweepRange(scale) ? 5 : WAVE;
            case 2 -> TORNADO;
            case 3, 6 -> WAVE;
            case 4 -> ARSENAL_4;
            case 7 -> ARSENAL_5;
            default -> distance < 32 * scale ? 1 : WAVE;
        };
    }
    public static double sweepRange(float scale){return 7D*scale;}
    /** Tactical landing offset from the caster: two spacing moves, then a melee entry. */
    public static double[] stepOffset(double awayX,double awayZ,float scale,int stage,boolean dodge,int side){
        double distance=Math.hypot(awayX,awayZ);
        if(distance<.01)return new double[]{0,0};
        boolean close=Math.floorMod(stage,3)==2&&!dodge;
        double wanted=close?Math.min(28,5.5*scale):Math.floorMod(stage,3)==0?Math.min(38,14*scale):Math.min(48,20*scale);
        if(dodge)wanted=Math.max(distance,Math.min(38,14*scale));
        else if(close&&distance<=sweepRange(scale))return new double[]{0,0};
        else if(!close&&distance>Math.min(56,26*scale))wanted=Math.min(44,18*scale);
        else if(!close&&distance>=wanted-2)return new double[]{0,0};
        double angle=close?0:side*(dodge?.45:.22),c=Math.cos(angle),sin=Math.sin(angle);
        double x=(awayX*c-awayZ*sin)*wanted/distance-awayX,z=(awayX*sin+awayZ*c)*wanted/distance-awayZ;
        double length=Math.hypot(x,z),limit=dodge?50:75;
        if(length<3)return new double[]{0,0};
        if(length>limit){x*=limit/length;z*=limit/length;}
        return new double[]{x,z};
    }
    public static int duration(int move) { return move == TORNADO ? 48 : move >= ARSENAL_4 ? 46 : move == WAVE ? 30 : move == 5 ? 80 : move == 1 ? 34 : 20; }
    public static String animation(int move) { return move == TORNADO ? "cyclone" : move == 5 ? "sweep" : move >= ARSENAL_4 ? "sky_arsenal" : move == WAVE ? "gale_cut" : "thrust"; }
    /** Authored root-Z curve in blocks, extracted from the user's thrust clip. */
    public static double thrustDistance(int age) {
        double seconds = Math.max(0, Math.min(20, age)) / 20D;
        return seconds <= .0417 ? .125 * seconds / .0417 : .125 + 30.625 * (seconds - .0417) / .9583;
    }
    private CanglanWarGodRules() {}
}
