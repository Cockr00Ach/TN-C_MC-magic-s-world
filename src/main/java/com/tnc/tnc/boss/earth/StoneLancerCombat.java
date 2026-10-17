package com.tnc.tnc.boss.earth;

import com.tnc.tnc.magic.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

final class StoneLancerCombat {
    static final int MAX_ATTACKS=24;
    static Vec3 ground(StoneLancerEntity boss,Vec3 at){
        // A huge quake must not generate distant terrain while looking for its surface.
        if(!boss.level().hasChunkAt(net.minecraft.core.BlockPos.containing(at)))return at;
        var hit=boss.level().clip(new ClipContext(at.add(0,40,0),at.add(0,-80,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
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
                Vec3 at=b.position().add(0,b.getBbHeight()+26+(i/2)*8,0).add(side.scale(i%2==0?12:-12));
                spawn(b,0,"earth_spear_sharp",at,target.add(0,5,0),1.5F,3.7F,i*3,90,16,2.2);
            }
        }else if(move==1){
            for(int lane=-1;lane<=1;lane++)for(int step=0;step<5;step++){
                Vec3 p=ground(b,b.position().add(forward.scale(25+step*18)).add(side.scale(lane*17.5)));
                spawn(b,1,"earth_spike_sharp",p,p,1.7F,4+step*.5F,step*4,65,18,2.6);
            }
        }else if(move==2)spawn(b,3,"earth_spear_sharp",target.add(0,190,0),target,8,25,20,60,38,6);
        else if(move==3||move==5)spawn(b,2,"earth_orb",target.add(0,-20,0),target.add(0,45,0),10,10,0,104,32,9);
        else if(move==6)spawn(b,2,"gravity_core",b.position().add(0,28,0),b.position().add(0,85,0),30,30,0,104,44,27);
        else if(move==4){
            b.startQuake();seal(b,b.position(),StoneLancerEntity.QUAKE_RADIUS,80);
            // Six directions, four expanding fronts: fixed 24-entity ceiling even at 200 blocks.
            for(int step=0;step<4;step++)for(int ray=0;ray<6;ray++){
                double angle=ray*Math.PI/3;double distance=45+step*45;
                Vec3 p=ground(b,b.position().add(Math.cos(angle)*distance,0,Math.sin(angle)*distance));
                spawn(b,1,"earth_spike_sharp",p,p,2.4F+step*.5F,5+step*1.2F,step*15,100,26,9);
            }
        }
    }
    private static void spawn(StoneLancerEntity b,int mode,String model,Vec3 from,Vec3 to,float width,float height,int wait,int life,float damage,double radius){
        b.attacks.removeIf(e->!e.isAlive());if(b.attacks.size()>=MAX_ATTACKS)b.attacks.remove(0).discard();
        var e=StoneLancerRegistration.ATTACK.create(b.level());if(e==null)return;
        float rockScale=(model.equals("earth_orb")||model.equals("gravity_core"))?5:10;
        e.configure(b,mode,"stone_lancer_"+model,from,to,width*rockScale,height*rockScale,wait,life,damage,radius*rockScale,b.level().damageSources().indirectMagic(e,b));
        if(b.level().addFreshEntity(e))b.attacks.add(e);
    }
}
