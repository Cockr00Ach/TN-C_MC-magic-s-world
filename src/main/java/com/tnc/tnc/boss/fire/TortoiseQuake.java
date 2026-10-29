package com.tnc.tnc.boss.fire;

/** Server-compatible propagation and client elapsed-age interpolation for the quake band. */
public final class TortoiseQuake {
    public static final double RADIUS = 22.0D;
    public static final int TRAVEL_TICKS = 40;
    public static final int FADE_TICKS = 10;
    public static final int LIFETIME = TRAVEL_TICKS + FADE_TICKS;

    private TortoiseQuake() {
    }

    public static double radius(double age) {
        return RADIUS * clamp(age / TRAVEL_TICKS);
    }

    public static float opacity(double age) {
        if (age <= 0.0D || age >= LIFETIME) return 0.0F;
        double enter = smooth(age / 2.0D);
        double leave = 1.0D - smooth((age - TRAVEL_TICKS) / FADE_TICKS);
        return (float) (enter * leave);
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
