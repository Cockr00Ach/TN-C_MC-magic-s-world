package com.tnc.tnc.world.eartharena;

/** Fixed basin and boss dais, immediately east of the arrival point. */
public final class EarthArenaLayout {
    public static final int X = 128, Z = 0, RADIUS = 112, FLOOR = 160, VERSION = 3;
    public static final String CHUNK_VERSION = "TncEarthArenaLayout";
    private EarthArenaLayout() {}
    public static double distance(double x, double z) { return Math.hypot(x - (X + .5), z - (Z + .5)); }
    public static boolean inside(double x, double y, double z) {
        return distance(x,z) <= RADIUS && y >= FLOOR - 2 && y <= FLOOR + 100;
    }
    public static boolean platform(int x, int z) { return Math.hypot(x - X, z - Z) <= RADIUS; }
    public static boolean blackTile(int x, int z) {
        double radius = Math.hypot(x - X, z - Z);
        return radius >= RADIUS - 3 || radius >= 14 && ((int)Math.floor(radius)) % 16 < 2;
    }
    public static int height(int x, int z, int natural) {
        double r = Math.hypot(x - X, z - Z);
        if (r <= RADIUS) return FLOOR;
        if (r <= 145) {
            if (Math.abs(z) <= 10 && x >= 4 && x <= 20) return Math.min(FLOOR, 156 + Math.max(0,(x - 4) / 3));
            return 156;
        }
        if (r >= 270) return natural;
        double t = (r - 145) / 125;
        double smooth = t * t * (3 - 2 * t);
        // The basin has a raised mountain rim rather than a sheer artificial wall.
        double rim = 50 * Math.sin(Math.PI * t);
        return (int)Math.round(156 + (natural - 156) * smooth + rim);
    }
}
