package com.tnc.tnc.world.eartharena;

/** Continuous, bounded, domain-warped mountain ridges, independent of chunk boundaries. */
public final class EarthArenaTerrain {
    public static final int MIN_Y = -64, HEIGHT = 512;
    public static final long DEFAULT_SEED = 20261018L;
    private EarthArenaTerrain() {}

    public static int height(int x, int z, long seed) {
        double wx = x + (noise(x / 530.0, z / 530.0, seed + 11) - .5) * 170;
        double wz = z + (noise(x / 530.0, z / 530.0, seed + 29) - .5) * 170;
        double ridge = 1 - Math.abs(2 * noise(wx / 235, wz / 235, seed + 47) - 1);
        double detail = 1 - Math.abs(2 * noise(wx / 83, wz / 83, seed + 71) - 1);
        double broad = noise(wx / 490, wz / 490, seed + 97);
        return (int)Math.round(91 + 73 * broad + 163 * Math.pow(ridge, 2.6)
                + 31 * detail + 10 * noise(wx / 27, wz / 27, seed + 131));
    }

    public static int layerOffset(int x, int z, long seed) {
        return (int)(24 * noise(x / 128.0, z / 128.0, seed + 211));
    }

    public static int shade(int y, int top, int offset) {
        int depth = top - y;
        int surface = 8 - Math.floorDiv(top - 110, 28);
        int band = depth > 3 ? Math.floorMod(Math.floorDiv(y + offset, 14), 2) : 0;
        return Math.max(1, Math.min(8, surface + depth / 36 + band));
    }

    private static double noise(double x, double z, long seed) {
        int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
        double fx = x - ix, fz = z - iz;
        double sx = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
        double sz = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed);
        double c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return (a + (b - a) * sx) * (1 - sz) + (c + (d - c) * sx) * sz;
    }

    private static double hash(int x, int z, long seed) {
        long value = seed ^ ((long)x * 0x9E3779B97F4A7C15L) ^ ((long)z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }
}
