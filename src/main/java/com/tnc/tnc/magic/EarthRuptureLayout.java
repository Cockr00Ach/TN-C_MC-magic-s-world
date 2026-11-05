package com.tnc.tnc.magic;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation-only layout for earth ruptures: the overshoot of a stone column
 * punching through the surface, the splinters thrown from its base, and the ring
 * of small columns a detonation throws out.
 *
 * <p>Pure math with no level, block or registry access, so it is headless-testable
 * and cannot modify terrain. The client renderers and the debris particles both
 * read the same layout, which keeps the flying chips aligned with the columns.</p>
 */
public final class EarthRuptureLayout {
    /** Local ground offset: {@code angle} in radians, {@code radius} in blocks, {@code delay} in ticks. */
    public record Shard(double angle,double radius,double delay,double size,double spin,double lift) {}

    /** Pillar punch duration in ticks; debris lives a little longer than the column rise. */
    public static final double PUNCH_TICKS=10.0D;
    public static final double CHIP_TICKS=13.0D;

    private static final double C1=2.2D,C3=C1+1;
    private EarthRuptureLayout() {}

    /**
     * 0..1 eased with a short overshoot (~1.15 near t=0.55) before settling: the
     * column shoots out of the ground, hangs for a beat, then locks into place.
     */
    public static double rise(double t) {
        if(t<=0)return 0;
        if(t>=1)return 1;
        double u=t-1;
        return Math.min(1.2D,1+C3*u*u*u+C1*u*u);
    }

    /** 1 at the surface break and fades to 0: drives the outward lean of fresh columns. */
    public static double impact(double t) {
        if(t<=0)return 1;
        if(t>=1)return 0;
        double u=1-t;
        return u*u;
    }

    /** Splinters flung from every column base as the field breaks through. */
    public static List<Shard> ejecta(int tier,int columns,double fieldRadius,boolean aura,double spacing) {
        int count=Math.min(18,6+Math.max(1,Math.min(5,tier))*2);
        int ring=Math.max(1,columns);
        double gap=Math.max(0,Math.min(4,spacing));
        var out=new ArrayList<Shard>(count);
        for(int i=0;i<count;i++) {
            // Each chip is anchored to one column base, so the debris reads as
            // "that rock just broke open" instead of popping at the field centre.
            int column=i%ring;
            double angle=column*Math.PI*2/ring+(jitter(i,1)-.5)*.42;
            double base=aura?fieldRadius*.78:fieldRadius*(.42+.22*(column%3)/2);
            double radius=base+(jitter(i,2)-.5)*.34;
            out.add(new Shard(angle,radius,(column%5)*gap+jitter(i,3)*1.1,
                    .13+jitter(i,4)*.17,jitter(i,5)*360.0,.5+jitter(i,6)*.95));
        }
        return List.copyOf(out);
    }

    /** Small stone columns erupting around a detonation, scattered up to {@code spread}. */
    public static List<Shard> burstColumns(double spread,boolean giant) {
        double reach=Math.max(.6,Math.min(12.0,spread));
        int count=giant?14:9;
        var out=new ArrayList<Shard>(count);
        for(int i=0;i<count;i++) {
            double angle=i*Math.PI*2/count+(jitter(i,21)-.5)*.55;
            double radius=reach*(giant?(.22+.75*jitter(i,22)):(.35+.6*jitter(i,23)));
            out.add(new Shard(angle,radius,i*.42+jitter(i,24)*.9,
                    .2+jitter(i,25)*.22,jitter(i,26)*360.0,.55+jitter(i,27)*.95));
        }
        return List.copyOf(out);
    }

    /** Deterministic 0..1 value; avoids Random so previews and tests stay reproducible. */
    static double jitter(int index,int salt) {
        int x=index*374761393+salt*668265263;
        x=(x^(x>>>13))*1274126177;
        return ((x^(x>>>16))&0x7fffffff)/2147483647.0D;
    }
}
