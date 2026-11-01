package com.tnc.tnc.world.ink;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Idle stalking stays on normal pathfinding and continually watches the player. */
final class InkOrbitGoal extends Goal {
    private final Mob mob;private final BooleanSupplier idle;private LivingEntity watched;
    InkOrbitGoal(Mob mob,BooleanSupplier idle){this.mob=mob;this.idle=idle;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){
        if(!idle.getAsBoolean()||mob.isNoAi())return false;
        watched=mob.getTarget();if(watched==null)watched=mob.level().getNearestPlayer(mob,96);
        return watched!=null&&watched.isAlive()&&!watched.isSpectator();
    }
    @Override public boolean canContinueToUse(){return idle.getAsBoolean()&&watched!=null&&watched.isAlive()&&watched.level()==mob.level()&&mob.distanceToSqr(watched)<128*128;}
    @Override public void tick(){
        mob.getLookControl().setLookAt(watched,25,20);
        if(mob.tickCount%10==0){
            double a=Math.atan2(mob.getZ()-watched.getZ(),mob.getX()-watched.getX())+.28;
            double radius=Math.max(22,mob.getBbWidth()*1.7);
            double x=watched.getX()+Math.cos(a)*radius,z=watched.getZ()+Math.sin(a)*radius;
            if(mob.level().hasChunkAt(net.minecraft.core.BlockPos.containing(x,watched.getY(),z)))mob.getNavigation().moveTo(x,watched.getY(),z,1.1);
        }
    }
    @Override public void stop(){mob.getNavigation().stop();watched=null;}
}
