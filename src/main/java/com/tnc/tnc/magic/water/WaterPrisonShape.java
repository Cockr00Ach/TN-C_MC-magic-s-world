package com.tnc.tnc.magic.water;

/** Geometry shared by the water arches and their falling droplets. */
public final class WaterPrisonShape {
    private WaterPrisonShape() {}

    public static boolean usesArches(int tier) { return tier == 2; }

    public static double domeHeight(double radius, double fieldHeight) { return Math.max(fieldHeight, radius * 1.39); }

    public static int bays(double radius) {
        return Math.max(6, Math.min(12, (int)Math.round(radius * .55)));
    }

    public static double rise(double age) {
        double t = Math.min(1, Math.max(0, age / 14));
        return t * t * (3 - 2 * t);
    }

    public static double height(double height, double arch, double flow) {
        return .18 + height * arch * (.91 + .015 * Math.sin(flow));
    }
}
