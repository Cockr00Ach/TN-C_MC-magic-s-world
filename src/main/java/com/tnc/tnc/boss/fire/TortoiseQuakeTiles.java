package com.tnc.tnc.boss.fire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fixed ground pieces roll up and land as a quake passes, without modifying world blocks. */
public final class TortoiseQuakeTiles {
    public static final double PEAK_LIFT = 1.30D;
    public static final double MAX_TILT = Math.toRadians(30.0D);
    private static final double LEAD = 3.0D;
    private static final double FALL = 6.0D;
    private static final double END = 10.0D;

    private TortoiseQuakeTiles() {
    }

    public static double arrival(double distance) {
        return Math.min(distance, TortoiseQuake.RADIUS) * TortoiseQuake.TRAVEL_TICKS / TortoiseQuake.RADIUS;
    }

    public static double lift(double age, double distance) {
        double t = age - arrival(distance);
        if (age < 0 || distance > TortoiseQuake.RADIUS + 0.71D || t <= -LEAD || t >= END) return 0;
        if (t < 0) return PEAK_LIFT * smooth((t + LEAD) / LEAD);
        if (t <= FALL) return PEAK_LIFT * (1.0D - smooth(t / FALL));
        double settle = (t - FALL) / (END - FALL);
        return 0.08D * Math.pow(Math.sin(Math.PI * settle), 2) * (1.0D - settle);
    }

    public static float opacity(double age, double distance) {
        double t = age - arrival(distance);
        if (t <= -LEAD || t >= END) return 0;
        return (float) ((1.0D - smooth((t - FALL) / (END - FALL))) * TortoiseQuake.opacity(age));
    }

    public record Cell(int x, int z, double distance) {
        public long key() {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }

    public static List<Cell> cells(double centerX, double centerZ, double age) {
        return cells(centerX, centerZ, age, 0);
    }

    public static List<Cell> cells(double centerX, double centerZ, double age, double yaw) {
        List<Cell> cells = new ArrayList<>();
        if (age < 0 || age >= TortoiseQuake.LIFETIME) return cells;
        int cx = (int) Math.floor(centerX), cz = (int) Math.floor(centerZ);
        for (int x = cx - 24; x <= cx + 24; x++) for (int z = cz - 24; z <= cz + 24; z++) {
            double distance = Math.hypot(x + 0.5D - centerX, z + 0.5D - centerZ);
            if (!TortoiseQuake.contains(x+0.5D-centerX,z+0.5D-centerZ,yaw,0.71D)) continue;
            if (lift(age, distance) > 0.003D || lift(age + 1.0D, distance) > 0.003D) cells.add(new Cell(x,z,distance));
        }
        cells.sort(Comparator.comparingDouble(cell -> Math.atan2(cell.z + 0.5D - centerZ, cell.x + 0.5D - centerX)));
        return cells;
    }

    public static Pose pose(Cell cell, double centerX, double centerZ, double surfaceY, double modelTop, double age) {
        double height = lift(age, cell.distance);
        double distance = cell.distance;
        double dx = distance > 1.0E-6D ? (cell.x + 0.5D - centerX) / distance : 1.0D;
        double dz = distance > 1.0E-6D ? (cell.z + 0.5D - centerZ) / distance : 0.0D;
        double angle = MAX_TILT * height / PEAK_LIFT;
        double sin = Math.sin(angle), cos = Math.cos(angle);
        // The lowest TOP corner, even on diagonal tiles, remains above the sampled surface.
        double clearance = Math.abs(sin) * 0.5D * (Math.abs(dx) + Math.abs(dz)) + 0.025D;
        return new Pose(cell.x, cell.z, surfaceY, modelTop, height, dx, dz, sin, cos, clearance);
    }

    public record Pose(int x, int z, double surfaceY, double pivotY, double lift,
                       double dx, double dz, double sin, double cos, double clearance) {
        private double radial(double lx, double lz) {
            return (lx - 0.5D) * dx + (lz - 0.5D) * dz;
        }
        public double x(double lx, double ly, double lz) {
            return x + lx + dx * ((cos - 1.0D) * radial(lx,lz) - sin * (ly - pivotY));
        }
        public double y(double lx, double ly, double lz) {
            return surfaceY + lift + clearance + sin * radial(lx,lz) + cos * (ly - pivotY);
        }
        public double z(double lx, double ly, double lz) {
            return z + lz + dz * ((cos - 1.0D) * radial(lx,lz) - sin * (ly - pivotY));
        }
    }

    private static double smooth(double t) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        return t*t*(3.0D - 2.0D*t);
    }
}
