package com.tnc.tnc.world.wind;

/** Bounded encounter budgets and shared visual/gameplay scale. */
public final class CanglanWarGodRules {
    public static final int TORNADO = 9, WAVE = 10, ARSENAL_4 = 11, ARSENAL_5 = 12;
    public static final int ANCHORS = 10, ANCHOR_LIFETIME = 1200, DODGE_COOLDOWN = 70;
    public static final float DODGE_CHANCE = .28F;
    public static float scale(int phase) { return phase == 2 ? 5F : 1F; }
    public static int choose(int cycle, double distance, float scale) {
        return switch (Math.floorMod(cycle, 8)) {
            case 1 -> 5;
            case 2 -> TORNADO;
            case 3, 6 -> WAVE;
            case 4 -> ARSENAL_4;
            case 7 -> ARSENAL_5;
            default -> distance < 7 * scale ? 1 : WAVE;
        };
    }
    public static int duration(int move) { return move == TORNADO ? 48 : move >= ARSENAL_4 ? 62 : move == WAVE ? 30 : move == 5 ? 48 : 20; }
    public static String animation(int move) { return move == TORNADO || move == 5 ? "cyclone" : move >= ARSENAL_4 ? "sky_arsenal" : move == WAVE ? "gale_cut" : "cleave_left"; }
    private CanglanWarGodRules() {}
}
