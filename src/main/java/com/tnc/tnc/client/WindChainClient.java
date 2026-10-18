package com.tnc.tnc.client;

import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded particle budget and a cached, short explosion impulse. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WindChainClient {
    private static float impulse;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){impulse=0;return;}if(mc.isPaused())return;impulse*=.72F;
        int particles=0;long tick=mc.level.getGameTime();
        for(var ball:mc.level.getEntitiesOfClass(WindBallEntity.class,mc.player.getBoundingBox().inflate(128))){
            if(ball.phase()==2){double age=ball.phaseAge(0),distance=ball.distanceTo(mc.player);impulse=Math.max(impulse,(float)(Math.max(0,1-age/12)*Math.max(0,1-distance/100)*(ball.tier()==5?2.4:1.1)));}
            else for(int i=0;i<8&&particles<32;i++,particles++){
                double a=tick*.19+i*Math.PI/4,r=ball.radius(0)*(1.1+.3*Math.sin(tick*.13+i));
                double x=Math.cos(a)*r,y=Math.sin(a*1.7)*r*.55,z=Math.sin(a)*r;
                var drift=ball.phase()==0?ball.getDeltaMovement().scale(-.25):new net.minecraft.world.phys.Vec3(-x,-y,-z).scale(.045);
                var p=mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,ball.getX()+x,ball.getY()+y,ball.getZ()+z,drift.x,drift.y,drift.z);
                if(p!=null){p.setColor(.55F,.85F,.79F);p.scale((float)Math.min(5,1+ball.radius(0)*.12));p.setLifetime(20);}
                if(i%2==0)mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.55F,1,.88F),1.2F),ball.getX()+x,ball.getY()+y,ball.getZ()+z,drift.x,drift.y,drift.z);
            }
        }
        int mist=0,sparks=0;
        for(var volley:mc.level.getEntitiesOfClass(WindVolleyEntity.class,mc.player.getBoundingBox().inflate(128))){
            double age=volley.visualAge(0);
            for(int sample=0;sample<volley.count()&&mist<24;sample++){
                int slot=(int)((tick*7+sample)%volley.count());
                if(!volley.visible(slot,age)||volley.done(slot)||age<18)continue;
                var at=volley.weaponAt(slot,age);var direction=volley.direction(slot,age);
                var back=volley.weaponAt(slot,age-.6).subtract(at).scale(.14);
                for(int layer=0;layer<2&&mist<24;layer++,mist++){
                    var body=at.subtract(direction.scale(WindChainRules.modelScale(volley.tier())*(.12+layer*.36)));
                    double swirl=tick*.23+slot*2.4+layer*Math.PI;
                    body=body.add(Math.cos(swirl)*.28,Math.sin(swirl)*.28,Math.sin(swirl)*.28);
                    mist(mc,body,back,volley.tier()==5?3.5F:1.45F+volley.tier()*.13F);
                }
                if(sparks<8){
                    mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.62F,1,.88F),1.1F),at.x,at.y,at.z,back.x*.3,back.y*.3,back.z*.3);sparks++;
                }
            }
            if(volley.tier()<5&&age>=38&&age<100){
                for(int i=0;i<4&&mist<24;i++,mist++){
                    var at=volley.orbitPoint((int)((tick+i*3)%volley.count()),age);
                    var inward=volley.aimPoint().add(0,1.3,0).subtract(at).scale(.055);
                    mist(mc,at.lerp(volley.aimPoint().add(0,1.3,0),i*.16),inward,1.7F);
                }
            }
        }
    }
    private static void mist(Minecraft mc,net.minecraft.world.phys.Vec3 at,net.minecraft.world.phys.Vec3 drift,float size){
        var p=mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,at.x,at.y,at.z,drift.x,drift.y,drift.z);
        if(p!=null){p.setColor(.66F,.94F,.86F);p.scale(size);p.setLifetime(18);}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        double t=mc.level.getGameTime()+event.getPartialTick();event.setPitch(event.getPitch()+(float)Math.sin(t*2.1)*impulse);event.setRoll(event.getRoll()+(float)Math.sin(t*1.7)*impulse*.4F);
    }
}
