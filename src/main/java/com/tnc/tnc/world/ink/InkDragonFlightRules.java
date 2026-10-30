package com.tnc.tnc.world.ink;
import net.minecraft.world.phys.*;
/** Rest-rig coordinates shared by server mouth, renderer and flight/culling bounds. */
public final class InkDragonFlightRules {
    public static final Vec3 HEAD=new Vec3(-4,5,0),MOUTH=new Vec3(-7.65,4.14,0),JAW=new Vec3(-5.7,4.15,0);
    public static final double SUMMON_DISTANCE=144,SUMMON_RISE=4,HOVER_CLEARANCE=8;
    public static final double GROUND_MODEL_DROP=18;
    private InkDragonFlightRules(){}
    public static AABB restingBounds(Vec3 at,float yaw){
        AABB b=bounds(at,yaw).move(0,-GROUND_MODEL_DROP,0);
        return new AABB(b.minX,at.y+.01,b.minZ,b.maxX,b.maxY,b.maxZ);
    }
    public static AABB bounds(Vec3 at,float yaw){
        Vec3 min=new Vec3(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY),max=min.scale(-1);
        for(double x:new double[]{-12,65})for(double y:new double[]{0,12})for(double z:new double[]{-7,7}){
            Vec3 p=new Vec3(x,y+1,z).scale(InkDragonCombatRules.BODY_SCALE).yRot((float)Math.toRadians(90-yaw)).add(at);
            min=new Vec3(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));max=new Vec3(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));
        }return new AABB(min,max);
    }
}
