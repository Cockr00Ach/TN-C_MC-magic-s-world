package com.tnc.tnc.world.ink;

/** Shared combat timings and geometry; all damaging events are server-owned. */
public final class InkCraneAttackRules {
    public static final int BARRAGE = 1, SWEEP = 2, SKY = 3, DRAKE = 4;
    public static final int SHOTS = 10, FIRST_SHOT = 24, SHOT_INTERVAL = 6;
    public static final int ROAR_TICKS = 42, AVATAR_FORM_TICKS = 12;
    public static final int SWEEP_HIT_START = 82, SWEEP_HIT_END = 96;
    public static final int SWEEP_INTERVAL = 26, SWEEP_COUNT = 3;
    public static final float BODY_SCALE = .84F, PHANTOM_MULTIPLIER = 2F, BALL_SCALE = 3F;
    public static final int RISE_TICKS = 30, SKY_RELEASE = 50, DIVE_TICKS = 32;
    public static final int BALL_EXPLODE = 100, RITUAL_LIFE = 220;
    public static final double SWEEP_REACH = 14.0, BLAST_RADIUS = 9.0;
    public static final int PAINTING_FACES = 12;
    public static final double PAINTING_RADIUS = 84, PAINTING_WIDTH = 34, PAINTING_HEIGHT = 24;
    public static final double EXPLOSION_VISUAL_SCALE = 1.6, PULL_RADIUS = 24, PULL_SPEED_CAP = .45;
    public static final double PULL_ACCELERATION = .22;
    public static final float SKY_SHROUD_SCALE = 2F;
    private InkCraneAttackRules() {}
    public static double pullAcceleration(int age,double distance,double inwardSpeed) {
        if(age<DIVE_TICKS || age>=BALL_EXPLODE || distance<.9 || distance>=PULL_RADIUS)return 0;
        double charge=(age-DIVE_TICKS)/(double)(BALL_EXPLODE-DIVE_TICKS);
        double force=PULL_ACCELERATION*(.7+.3*charge)*(1-.4*distance/PULL_RADIUS);
        return Math.max(0,Math.min(force,PULL_SPEED_CAP-inwardSpeed));
    }

    public static boolean firesAt(int tick) {
        return tick >= FIRST_SHOT && tick < FIRST_SHOT + SHOTS * SHOT_INTERVAL
                && (tick - FIRST_SHOT) % SHOT_INTERVAL == 0;
    }

    public static int duration(int move) {
        return switch (move) { case BARRAGE -> 105; case SWEEP -> 174; case SKY -> 210; case DRAKE -> 110;
            default -> throw new IllegalArgumentException("Unknown ink crane attack: " + move); };
    }

    public static double ballRadius(double ritualAge) {
        double t = Math.max(0, Math.min(1, (ritualAge - DIVE_TICKS) / (BALL_EXPLODE - DIVE_TICKS)));
        return BALL_SCALE * (0.12 + 3.9 * t * t * (3.0 - 2.0 * t));
    }

    public static int sweepPass(int age) {
        int offset = age - SWEEP_HIT_START;
        int pass = offset / SWEEP_INTERVAL;
        return offset >= 0 && pass < SWEEP_COUNT && offset % SWEEP_INTERVAL <= SWEEP_HIT_END - SWEEP_HIT_START
                ? pass : -1;
    }

    public static boolean inSweep(double dx, double dz, double forwardX, double forwardZ) {
        double distance = Math.hypot(dx, dz);
        return distance <= SWEEP_REACH && (distance < 0.001
                || (dx * forwardX + dz * forwardZ) / distance >= Math.cos(Math.toRadians(78)));
    }
}
