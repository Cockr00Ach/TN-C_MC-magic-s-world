package com.tnc.tnc.boss.earth;

import net.minecraft.world.phys.Vec3;

/** Shared force for authoritative victims and the local player's velocity prediction. */
public final class StoneGravityPull {
    private StoneGravityPull(){}
    public static Vec3 motion(Vec3 previous,Vec3 delta,boolean noGravity,double age){
        double distance=delta.length();
        double force=6*(.5+Math.min(1,Math.max(0,age)/20)*.5)*Math.min(1,distance/4);
        Vec3 pull=delta.normalize().scale(force);
        if(delta.y>-.25&&!noGravity)pull=pull.add(0,.16,0);
        Vec3 motion=previous.scale(.72).add(pull);
        double limit=Math.min(12,Math.max(.16,distance*.5));
        return motion.lengthSqr()>limit*limit?motion.normalize().scale(limit):motion;
    }
}
