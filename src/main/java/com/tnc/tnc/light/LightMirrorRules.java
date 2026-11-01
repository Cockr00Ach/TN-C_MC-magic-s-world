package com.tnc.tnc.light;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded, deterministic optical rules shared by combat and tests. */
public final class LightMirrorRules {
    public static final int PULSE_TICKS = 8;
    public static final int MAX_DEPTH = 2;
    public static final int MAX_BRANCHES = 48;
    private static final int[] COLORS = {0xffb94c, 0x61dcff, 0xdb87ff, 0x76ffc1, 0xff748d, 0x8e9bff, 0xffef96};
    private LightMirrorRules() {}
    public static int tier(int tier) { return Math.max(1, Math.min(5, tier)); }
    public static int rays(int tier) { return tier(tier) + 2; }
    public static int lifetime(int tier) { return (8 + tier(tier) * 4) * 20; }
    public static int afterimageTicks(int tier) { return 6 + tier(tier) * 2; }
    public static int color(int branch) { return COLORS[Math.floorMod(branch, COLORS.length)]; }

    /** A single incident ray spreads symmetrically around the mirror's outgoing axis. */
    public static List<Vec3> split(Vec3 axis, int tier) {
        return TNLightBeamMechanics.fanDirections(axis, rays(tier), 35 + tier(tier) * 7);
    }

    /** Six spokes, not +/-Y rays hidden in the ground. Useful for vertical sky-column impact. */
    public static List<Vec3> sixDirections(float yaw) {
        List<Vec3> directions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double angle=Math.toRadians(yaw+i*60);
            directions.add(new Vec3(-Math.sin(angle),0,Math.cos(angle)));
        }
        return directions;
    }

    public static boolean intersects(Vec3 start, Vec3 end, AABB box, double radius) {
        AABB expanded = box.inflate(radius);
        return expanded.contains(start) || expanded.clip(start, end).isPresent();
    }
}
