package com.tnc.tnc.world.ink;
import net.minecraft.world.phys.Vec3;
public final class InkDragonCombatRules {
    public static final float BODY_SCALE=3;
    private InkDragonCombatRules(){}
    public static double segmentDistanceSquared(Vec3 point,Vec3 start,Vec3 end){Vec3 d=end.subtract(start);double t=d.lengthSqr()<1e-9?0:Math.max(0,Math.min(1,point.subtract(start).dot(d)/d.lengthSqr()));return point.distanceToSqr(start.add(d.scale(t)));}
}
