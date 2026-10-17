package com.tnc.tnc.boss.earth;

import com.tnc.tnc.magic.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

final class StoneLancerCombat {
    static final int MAX_ATTACKS=24;
    static Vec3 ground(StoneLancerEntity boss,Vec3 at){
        var hit=boss.level().clip(new ClipContext(at.add(0,8,0),at.add(0,-16,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        return hit.getType()==HitResult.Type.MISS?at:hit.getLocation();
    }
    static void seal(StoneLancerEntity boss,Vec3 at,double radius,int life){
        var e=TNOrbEntities.MAGIC_CIRCLE.get().create(boss.level());if(e==null)return;
        e.configure(radius,life,TNMagicCircleEntity.STYLE_EARTH);e.setPos(at.add(0,.07,0));boss.level().addFreshEntity(e);
    }
    static void release(StoneLancerEntity b,int move,Vec3 target){
        Vec3 forward=new Vec3(target.x-b.getX(),0,target.z-b.getZ()).normalize();if(forward.lengthSqr()<.01)forward=new Vec3(0,0,1);
        Vec3 side=new Vec3(-forward.z,0,forward.x);
        if(move==0){
            int count=b.enraged()?8:6;
            for(int i=0;i<count;i++){
                Vec3 at=b.position().add(0,5.3+(i/2)*.4,0).add(side.scale(i%2==0?2.8:-2.8));
                spawn(b,0,"earth_spear_sharp",at,target.add(0,1,0),1.5F,3.7F,i*3,90,16,2.2);
            }
        }else if(move==1){
            for(int lane=-1;lane<=1;lane++)for(int step=0;step<5;step++){
                Vec3 p=ground(b,b.position().add(forward.scale(5+step*3.6)).add(side.scale(lane*3.5)));
                spawn(b,1,"earth_spike_sharp",p,p,1.7F,4+step*.5F,step*4,65,18,2.6);
            }
        }else if(move==2)spawn(b,3,"earth_spear_sharp",target.add(0,38,0),target,8,25,20,60,38,6);
        else spawn(b,2,"earth_orb",target.add(0,-4,0),target.add(0,9,0),10,10,0,104,32,9);
    }
    private static void spawn(StoneLancerEntity b,int mode,String model,Vec3 from,Vec3 to,float width,float height,int wait,int life,float damage,double radius){
        b.attacks.removeIf(e->!e.isAlive());if(b.attacks.size()>=MAX_ATTACKS)return;
        var e=WindEarthVisualMechanics.ATTACK.get().create(b.level());if(e==null)return;
        e.configure(b,mode,model,from,to,width,height,wait,life,damage,radius,b.level().damageSources().indirectMagic(e,b));
        if(b.level().addFreshEntity(e))b.attacks.add(e);
    }
}
