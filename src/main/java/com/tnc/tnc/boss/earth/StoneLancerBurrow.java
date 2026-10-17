package com.tnc.tnc.boss.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Sink -> bounded loaded-chunk dash -> emerge -> core, without A* or terrain edits. */
final class StoneLancerBurrow {
    static final int SINK=24,RISE=28;
    private final StoneLancerEntity boss;
    private LivingEntity quarry;
    private int age,cooldown;
    private boolean wasNoGravity,wasNoPhysics,combo;
    StoneLancerBurrow(StoneLancerEntity boss){this.boss=boss;}
    int age(){return age;}
    boolean begin(LivingEntity target){
        if(boss.burrowPhase()!=0||cooldown>0||!valid(target)||boss.distanceToSqr(target)<=80*80||boss.distanceToSqr(target)>360*360)return false;
        quarry=target;combo=true;wasNoGravity=boss.isNoGravity();wasNoPhysics=boss.noPhysics;
        boss.getNavigation().stop();boss.setNoGravity(true);boss.setDeltaMovement(Vec3.ZERO);
        boss.setCasting(true);phase(1);boss.triggerAnim("action","burrow");
        boss.playSound(SoundEvents.STONE_BREAK,3F,.45F);return true;
    }
    private boolean valid(LivingEntity e){return e!=null&&e.isAlive()&&!e.isSpectator()&&e.level()==boss.level();}
    private void phase(int stage){age=0;boss.setBurrowPhase(stage);}
    /** Called before ordinary spell scheduling on every server AI tick. */
    boolean tick(){
        if(cooldown>0)cooldown--;
        int stage=boss.burrowPhase();if(stage==0)return false;
        boss.getNavigation().stop();boss.setDeltaMovement(Vec3.ZERO);boss.fallDistance=0;age++;
        if(!valid(quarry))combo=false;
        if(stage==1&&age>=SINK){boss.noPhysics=true;phase(2);}
        else if(stage==2){
            if(!combo||boss.distanceToSqr(quarry)>400*400){rise(false);return true;}
            Vec3 delta=quarry.position().subtract(boss.position()).multiply(1,0,1);
            double distance=delta.length();
            if(distance<=34||age>=36){rise(distance<100);return true;}
            Vec3 next=boss.position().add(delta.normalize().scale(Math.min(12,distance-32)));
            if(!boss.level().hasChunkAt(BlockPos.containing(next))){rise(false);return true;}
            // One vertical ray in an already loaded column, never a giant-volume search.
            Vec3 from=new Vec3(next.x,quarry.getY()+6,next.z),to=from.add(0,-46,0);
            var floor=boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            boss.setPos(next.x,floor.getType()==HitResult.Type.MISS?boss.getY():floor.getLocation().y+.02,next.z);
            boss.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));boss.yBodyRot=boss.getYRot();
            if(distance<=46)rise(true);
        }else if(stage==3&&age>=RISE){
            boolean follow=combo&&valid(quarry)&&boss.distanceToSqr(quarry)<120*120;
            LivingEntity target=quarry;reset();cooldown=260;
            if(follow)boss.beginCast(5,target.position());
            else boss.finishBurrowWithoutTarget();
        }
        return true;
    }
    private void rise(boolean follow){
        combo&=follow;boss.noPhysics=wasNoPhysics;phase(3);
        boss.triggerAnim("action","emerge_core");boss.playSound(SoundEvents.GENERIC_EXPLODE,3F,.5F);
        StoneLancerCombat.seal(boss,boss.position(),28,72);
    }
    void reset(){
        if(boss.burrowPhase()!=0){boss.setNoGravity(wasNoGravity);boss.noPhysics=wasNoPhysics;}
        phase(0);quarry=null;combo=false;boss.setCasting(false);boss.setDeltaMovement(Vec3.ZERO);
    }
    void save(CompoundTag tag){
        tag.putInt("StoneBurrowCooldown",cooldown);tag.putBoolean("StoneBurrowing",boss.burrowPhase()!=0);
        if(boss.burrowPhase()!=0)tag.putBoolean("StoneBurrowWasNoGravity",wasNoGravity);
    }
    void load(CompoundTag tag){
        reset();cooldown=Math.max(0,Math.min(260,tag.getInt("StoneBurrowCooldown")));
        if(tag.getBoolean("StoneBurrowing")){boss.setNoGravity(tag.getBoolean("StoneBurrowWasNoGravity"));boss.noPhysics=false;}
    }
}
