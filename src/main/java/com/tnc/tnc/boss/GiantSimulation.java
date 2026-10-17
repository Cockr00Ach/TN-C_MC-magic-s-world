package com.tnc.tnc.boss;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Full-size combat bounds, with bounded terrain/fluid work only inside a physics call. */
public final class GiantSimulation {
    private static final ThreadLocal<Entity> ACTIVE=new ThreadLocal<>();
    private GiantSimulation(){}
    public static boolean active(Entity entity){return ACTIVE.get()==entity;}
    public static AABB bounds(Entity entity){
        double half=Math.min(3,entity.getBbWidth())*.5;
        return new AABB(entity.getX()-half,entity.getY(),entity.getZ()-half,
                entity.getX()+half,entity.getY()+Math.min(6,entity.getBbHeight()),entity.getZ()+half);
    }
    public static void run(Entity entity,Runnable operation){
        if(active(entity)){operation.run();return;}
        Entity previous=ACTIVE.get();var body=entity.getBoundingBox();var start=entity.position();
        ACTIVE.set(entity);entity.setBoundingBox(bounds(entity));
        try{operation.run();}
        finally{
            entity.setBoundingBox(body.move(entity.position().subtract(start)));
            if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);
        }
    }
}
