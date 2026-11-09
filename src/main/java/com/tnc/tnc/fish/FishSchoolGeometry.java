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
        return offset(lane,age,metal,0);
    }
    // Irregular 3D samples, not rows. Lane 1 is central and lane 0 tests the outer corridor.
    private static final double[] SIDE={-1.12,-.08,.72,-.54,1.13,.31,-.89,.9,-.28,.49,-.7,.1};
    private static final double[] UP={.08,-.04,.42,-.46,-.13,.11,.51,-.4,.34,-.25,-.21,.57};
    private static final double[] LAG={.52,.08,1.48,.91,1.1,.65,1.72,.29,1.04,1.89,.21,1.33};
    public static Offset offset(int lane,double age,boolean metal,int seed) {
        if (lane<0 || lane>=COUNT) throw new IllegalArgumentException("fish lane");
        double phase=(seed&65535)*.001+lane*2.399;
        double gather=Math.max(.12,Math.min(1,age/GATHER_TICKS));
        double amplitude=metal?.035:.065;
        double side=SIDE[lane]+amplitude*Math.sin(age*.31+phase);
        double up=UP[lane]+amplitude*Math.sin(age*.37+phase*1.31);
        double depth=LAG[lane]+.08*Math.sin(phase);
        double delay=.5+.65*(1+Math.sin(phase));
        double travel=Math.max(0,Math.min(1,(age-GATHER_TICKS-delay)/(TRAVEL_TICKS-delay)));
        // Individual release times and modest speed variation, with monotone forward travel.
        travel+=.025*Math.sin(Math.PI*travel)*Math.sin(2*Math.PI*travel+phase);
        double start=.4+.2*depth,end=RANGE-RADIUS-depth;
        return new Offset(side*gather,up*gather,start+(end-start)*travel);
    }
    public static Vec3 world(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up, int lane, double age, boolean metal) {
        return world(origin,forward,right,up,lane,age,metal,0);
    }
    public static Vec3 world(Vec3 origin,Vec3 forward,Vec3 right,Vec3 up,int lane,double age,boolean metal,int seed) {
        Offset o=offset(lane,age,metal,seed);
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
