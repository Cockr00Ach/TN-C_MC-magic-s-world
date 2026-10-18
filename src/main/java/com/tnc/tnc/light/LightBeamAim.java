package com.tnc.tnc.light;

import net.minecraft.world.phys.Vec3;

/** Manual aiming, shared by server damage and client presentation. */
public final class LightBeamAim {
    private LightBeamAim() {}
    public static Vec3 direction(Vec3 look,double fanDegrees) {
        Vec3 d=look.normalize();
        Vec3 axis=Math.abs(d.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);
        double angle=Math.toRadians(fanDegrees),c=Math.cos(angle),s=Math.sin(angle);
        return d.scale(c).add(axis.cross(d).scale(s)).add(axis.scale(axis.dot(d)*(1-c))).normalize();
    }
    public static double offset(int index,int count,double spread) {
        return count<=1?0:(index/(double)(count-1)-.5)*spread;
    }
}
