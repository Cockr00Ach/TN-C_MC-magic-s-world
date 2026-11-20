package com.tnc.tnc.mount;

public record MountRiderPose(double rearOffset, float hipTuck) {
    public static final double ARMOR_CLEARANCE = 0.065;
    public static final float LEG_PITCH = -26;
    public static final float LEG_SPREAD = 51;
    public static final float LEG_YAW = 15;
    public static final float HIP_WIDTH = 3.5f;

    private static final MountRiderPose UNICORN = new MountRiderPose(0.46, 2.7f);
    private static final MountRiderPose GOAT = new MountRiderPose(0.34, 3.3f);
    private static final MountRiderPose WOLF = new MountRiderPose(0.36, 2.7f);

    public static MountRiderPose forSpecies(String species) {
        return switch (species) {
            case "mountain_goat" -> GOAT;
            case "snow_wolf" -> WOLF;
            default -> UNICORN;
        };
    }
}
