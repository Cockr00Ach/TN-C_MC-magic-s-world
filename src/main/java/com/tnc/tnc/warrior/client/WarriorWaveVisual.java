package com.tnc.tnc.warrior.client;

import net.minecraft.world.phys.Vec3;

/** An air crescent spans across the view, rather than presenting only its thin edge to the caster. */
public final class WarriorWaveVisual {
    private WarriorWaveVisual() {}
    public static Vec3 span(Vec3 side,Vec3 up,double width,boolean ground) {
        return ground?side.scale(width):side.scale(width).add(up.scale(width*.42));
    }
    public static double age(long now,long received,float partial){return Math.max(0,now-received+partial);}
    public static Vec3 point(Vec3 center,Vec3 direction,Vec3 span,double t,double width,boolean ground) {
        double taper=Math.max(0,1-t*t);
        if(ground)return center.add(span.scale(t)).add(direction.scale((.55-width)*taper));
        Vec3 side=new Vec3(-direction.z,0,direction.x).normalize();
        Vec3 up=side.cross(direction).normalize();
        // Width must open UP in the cross-section. Offsetting only along flight direction
        // makes even a horizontal ribbon edge-on to the caster looking down that direction.
        return center.add(span.scale(t)).add(up.scale((.55-width)*taper)).add(direction.scale(.10*taper));
    }
}
