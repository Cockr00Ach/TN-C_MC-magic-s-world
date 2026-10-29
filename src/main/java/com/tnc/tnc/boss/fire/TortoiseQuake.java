package com.tnc.tnc.boss.fire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared propagation and fixed-cell motion for the ground quake, with no client dependencies. */
public final class TortoiseQuake {
    public static final double RADIUS = 22.0D;
    public static final int TRAVEL_TICKS = 40;
    public static final int SETTLE_TICKS = 11;
    public static final int LIFETIME = TRAVEL_TICKS + SETTLE_TICKS;
    public static final double PEAK_LIFT = 1.05D;
    private static final double LEAD_TICKS = 3.0D;
    private static final double FALL_TICKS = 5.5D;

    private TortoiseQuake() {
    }

    public static double radius(double age) {
        return RADIUS * clamp(age / TRAVEL_TICKS);
    }

    public static double arrival(double distance) {
        return Math.min(distance, RADIUS) * TRAVEL_TICKS / RADIUS;
    }

    /** Each fixed block rises ahead of the crest, lands, then makes one small settling bounce. */
    public static double lift(double age, double distance) {
        if (distance > RADIUS + 0.71D || age < 0.0D) return 0.0D;
        double t = age - arrival(distance);
        if (t < -LEAD_TICKS || t >= SETTLE_TICKS) return 0.0D;
        if (t < 0.0D) return PEAK_LIFT * smooth((t + LEAD_TICKS) / LEAD_TICKS);
        if (t < FALL_TICKS) return PEAK_LIFT * (1.0D - smooth(t / FALL_TICKS));
        double bounce = (t - FALL_TICKS) / (SETTLE_TICKS - FALL_TICKS);
        return 0.13D * Math.pow(Math.sin(Math.PI * bounce), 2.0D) * (1.0D - bounce);
    }

    /** A shallow dark trough trails the raised crest without removing the real world surface. */
    public static double shade(double age, double distance) {
        double t = age - arrival(distance);
        if (t <= FALL_TICKS || t >= SETTLE_TICKS) return 1.0D;
        return 1.0D - 0.28D * Math.sin(Math.PI * (t - FALL_TICKS) / (SETTLE_TICKS - FALL_TICKS));
    }

    public static boolean visible(double age, double distance) {
        double t = age - arrival(distance);
        return age >= 0.0D && distance <= RADIUS + 0.71D
                && t > -LEAD_TICKS && t < SETTLE_TICKS;
    }

    /** One entry per world cell; ordering distributes a limited render budget around the whole circle. */
    public static List<Cell> cells(double centerX, double centerZ, double age) {
        List<Cell> result = new ArrayList<>();
        if (age < 0.0D || age >= LIFETIME) return result;
        int extent = (int) Math.ceil(RADIUS + 1.0D);
        int cx = (int) Math.floor(centerX), cz = (int) Math.floor(centerZ);
        for (int x = cx - extent; x <= cx + extent; x++) {
            for (int z = cz - extent; z <= cz + extent; z++) {
                double distance = Math.hypot(x + 0.5D - centerX, z + 0.5D - centerZ);
                // Pre-sample the next partial tick so newly reached cells rise from zero.
                if (visible(age, distance) || visible(age + 1.0D, distance)) result.add(new Cell(x, z, distance));
            }
        }
        result.sort(Comparator.comparingDouble(c -> Math.atan2(c.z + 0.5D - centerZ, c.x + 0.5D - centerX)));
        return result;
    }

    public record Cell(int x, int z, double distance) {
        public long key() {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }

    /** Server elapsed age, anchored to local entity ticks; an expired cast cannot restart itself. */
    public static final class Playback {
        private int cast = -1;
        private int receivedAge = -1;
        private int anchorTick;

        public void receive(int nextCast, int serverAge, int localTick) {
            if (nextCast < 0 || serverAge < 0) {
                cast = -1;
                receivedAge = -1;
            } else if (cast != nextCast || receivedAge != serverAge) {
                cast = nextCast;
                receivedAge = serverAge;
                anchorTick = localTick;
            }
        }

        public double age(double localTick) {
            return receivedAge < 0 ? -1.0D : receivedAge + Math.max(0.0D, localTick - anchorTick);
        }
    }

    private static double smooth(double value) {
        double t = clamp(value);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
