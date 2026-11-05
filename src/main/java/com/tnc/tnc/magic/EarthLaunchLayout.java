package com.tnc.tnc.magic;

/** Pure math for the giant pillar that lifts a target into the sky. */
public final class EarthLaunchLayout {
    /** Ticks the column takes to reach full height. */
    public static final double RISE = 45.0D;
    private EarthLaunchLayout() {}

    /** Ease-out growth in 0..1; the top sprints first, then settles. */
    public static double grow(double t) {
        t=Math.max(0,Math.min(1,t));
        return t*t*(3-2*t);
    }
    /** Height of the pillar top at a tick age. */
    public static double top(int age,double height) {
        return grow(age/RISE)*height;
    }
    /** Upward speed that keeps a carried target on the moving top. */
    public static double lift(int age,double height) {
        return Math.max(.05,top(age,height)-top(age-1,height));
    }
}
