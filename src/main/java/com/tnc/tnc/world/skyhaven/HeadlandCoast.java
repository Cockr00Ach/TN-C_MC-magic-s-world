package com.tnc.tnc.world.skyhaven;

import java.io.DataInputStream;
import java.io.IOException;

/** Signed distance to the land and detached islets baked with the revised starter. */
final class HeadlandCoast {
    private static final int RADIUS = 640, STEP = 8, SIDE = 2 * RADIUS / STEP + 1;
    private static final short[] HEADLANDS = load("headland_coast_v5.bin");
    private static final short[] HIGHLANDS = load("headland_coast.bin");
    private HeadlandCoast() {}

    private static short[] load(String name) {
        try (var stream = HeadlandCoast.class.getResourceAsStream("/data/tnc/skyhaven/" + name)) {
            if (stream == null) throw new IOException("missing shore field " + name);
            var in = new DataInputStream(stream);
            short[] field = new short[SIDE * SIDE];
            for (int i = 0; i < field.length; i++) field[i] = in.readShort();
            if (in.read() != -1) throw new IOException("unexpected shore field length");
            return field;
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid Skyhaven coast field", ex);
        }
    }

    static double distance(double x, double z, SeaStormCoast.Layout layout) {
        short[] field = layout == SeaStormCoast.Layout.HIGHLANDS ? HIGHLANDS : HEADLANDS;
        double bx = Math.max(-RADIUS, Math.min(RADIUS, x));
        double bz = Math.max(-RADIUS, Math.min(RADIUS, z));
        double gx = (bx + RADIUS) / STEP, gz = (bz + RADIUS) / STEP;
        int ix = Math.min(SIDE - 2, (int)gx), iz = Math.min(SIDE - 2, (int)gz);
        double u = gx - ix, v = gz - iz;
        int i = iz * SIDE + ix;
        double a = (1 - u) * field[i] + u * field[i + 1];
        double b = (1 - u) * field[i + SIDE] + u * field[i + SIDE + 1];
        double d = a * (1 - v) + b * v;
        if (bx == x && bz == z) return d;
        return Math.hypot(d, Math.hypot(x - bx, z - bz));
    }
}
