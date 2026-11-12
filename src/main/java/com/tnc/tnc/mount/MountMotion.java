package com.tnc.tnc.mount;

/** Distances are blocks per game tick; periods are game ticks. */
public final class MountMotion {
    public static String clip(double speed){return speed<.012?"idle":speed<.10?"walk":speed<.21?"trot":"gallop";}
    public static double rate(String clip,double speed){if(clip.equals("idle"))return 1;return Math.max(.65,Math.min(1.65,speed/(clip.equals("walk")?.065:clip.equals("trot")?.155:.30)));}
    private MountMotion(){}
}
