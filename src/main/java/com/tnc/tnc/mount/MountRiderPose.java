package com.tnc.tnc.mount;

/** Coordinates mirror the v22 rider_seat bone, with +Z pointing to the mount's rear. */
public record MountRiderPose(double seatY, double seatZ, float pitch, float spread,
                            float hipWidth, float legYaw, float hipTuck) {
    private static final double MODEL_UNIT = (0.65f * LandMounts.SIZE_MULTIPLIER) / 16.0;
    private static final double PLAYER_HIP_CORRECTION = 0.35;

    private static final MountRiderPose UNICORN = new MountRiderPose(34.64827, 11.98939, -30f, 47f, 4.1f, 15f, 3.5f);
    private static final MountRiderPose GOAT = new MountRiderPose(32.14854, 7.21485, -26f, 51f, 3.9f, 15f, 3.45f);
    private static final MountRiderPose WOLF = new MountRiderPose(31.41857, 7.63926, -30f, 47f, 4.04f, 15f, 3.75f);

    public double rearOffset() {
        return seatZ * MODEL_UNIT;
    }

    public double ridingOffset() {
        return seatY * MODEL_UNIT - PLAYER_HIP_CORRECTION;
    }

    public static MountRiderPose forSpecies(String species) {
        return switch (species) {
            case "mountain_goat" -> GOAT;
            case "snow_wolf" -> WOLF;
            default -> UNICORN;
        };
    }
}
