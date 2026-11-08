package com.tnc.tnc.fish;

import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One shared, deterministic layout for server collision and client fish rendering. */
public final class FishSchoolGeometry {
    public static final int COUNT = 12, GATHER_TICKS = 8, TRAVEL_TICKS = 16;
    public static final int LIFETIME = GATHER_TICKS + TRAVEL_TICKS;
    public static final double RANGE = 10, WIDTH = 3, RADIUS = .38;
    public static final double HALF_WIDTH=.18, HALF_HEIGHT=.23, HALF_LENGTH=.36;
    public record Offset(double side, double up, double forward) {}
    private FishSchoolGeometry() {}
    public static int mana(boolean metal) { return metal ? 30 : 20; }
    public static int cooldown(boolean metal) { return metal ? 240 : 160; }
    public static float damage(boolean metal) { return metal ? 10 : 6; }
    public static double knockback(boolean metal) { return metal ? 1.25 : .65; }
    public static double progress(double age) { return Math.max(0, Math.min(1, (age-GATHER_TICKS)/TRAVEL_TICKS)); }
    public static Offset offset(int lane, double age, boolean metal) {
        if (lane<0 || lane>=COUNT) throw new IllegalArgumentException("fish lane");
        double gather=Math.max(.12,Math.min(1,age/GATHER_TICKS));
        double side=(lane%3-1)*1.22;
        double up=(lane/3-1.5)*.28;
        double lag=metal ? Math.abs(side)*.7+(lane/3)*.15 : ((lane*5)%4)*.19;
        double forward=.4+Math.max(0,progress(age)*(RANGE-RADIUS-.4)-lag);
        if (!metal) { side+=.05*Math.sin(age*.65+lane); up+=.05*Math.sin(age*.8+lane*1.7); }
        return new Offset(side*gather,up*gather,forward);
    }
    public static Vec3 world(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up, int lane, double age, boolean metal) {
        Offset o=offset(lane,age,metal);
        return origin.add(right.scale(o.side())).add(up.scale(o.up())).add(forward.scale(o.forward()));
    }
    public static Vec3 halfExtents(Vec3 forward,Vec3 right,Vec3 up) {
        return new Vec3(Math.abs(right.x)*HALF_WIDTH+Math.abs(up.x)*HALF_HEIGHT+Math.abs(forward.x)*HALF_LENGTH,
                Math.abs(right.y)*HALF_WIDTH+Math.abs(up.y)*HALF_HEIGHT+Math.abs(forward.y)*HALF_LENGTH,
                Math.abs(right.z)*HALF_WIDTH+Math.abs(up.z)*HALF_HEIGHT+Math.abs(forward.z)*HALF_LENGTH);
    }
    public static Vec3 horizontalKnockback(Vec3 forward,Vec3 right) {
        return forward.horizontalDistanceSqr()<1e-8 ? new Vec3(-right.z,0,right.x) : forward;
    }
    public static Optional<Vec3> segment(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) ? Optional.of(from) : box.clip(from,to);
    }
    public static boolean beforeWall(Vec3 from, Vec3 hit, Vec3 wall) {
        return wall==null || from.distanceToSqr(hit)+1e-8<from.distanceToSqr(wall);
    }
}
