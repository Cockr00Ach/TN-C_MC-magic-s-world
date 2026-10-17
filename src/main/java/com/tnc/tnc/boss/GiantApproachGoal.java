package com.tnc.tnc.boss;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Giant combat uses direct steering; a 56-block-tall A* search must never stall the server. */
public final class GiantApproachGoal extends Goal {
    private final Mob mob;
    private final BooleanSupplier enabled;
    private final double speed;
    private int attackDelay;
    public GiantApproachGoal(Mob mob,double speed,BooleanSupplier enabled){
        this.mob=mob;this.speed=speed;this.enabled=enabled;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    @Override public boolean canUse(){return enabled.getAsBoolean()&&mob.getTarget()!=null&&mob.getTarget().isAlive();}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){mob.getNavigation().stop();}
    @Override public void stop(){
        mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
    }
    @Override public void tick(){
        var target=mob.getTarget();if(target==null)return;
        mob.getLookControl().setLookAt(target,30,30);
        double dx=target.getX()-mob.getX(),dz=target.getZ()-mob.getZ();
        double reach=mob.getBbWidth()*.5+target.getBbWidth()*.5+2;
        boolean close=dx*dx+dz*dz<=reach*reach;
        if(!close||target.getY()>mob.getBoundingBox().maxY||target.getBoundingBox().maxY<mob.getY())
            mob.getMoveControl().setWantedPosition(target.getX(),target.getY(),target.getZ(),speed);
        else mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
        if(attackDelay>0)attackDelay--;
        if(close&&attackDelay==0&&target.getBoundingBox().maxY>=mob.getY()
                &&target.getY()<=mob.getBoundingBox().maxY&&mob.hasLineOfSight(target)){
            attackDelay=20;mob.doHurtTarget(target);
        }
    }
}
