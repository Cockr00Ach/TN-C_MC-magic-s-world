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
        // Weapon slipstreams are continuous meshes in their renderers.
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        double t=mc.level.getGameTime()+event.getPartialTick();event.setPitch(event.getPitch()+(float)Math.sin(t*2.1)*impulse);event.setRoll(event.getRoll()+(float)Math.sin(t*1.7)*impulse*.4F);
    }
}
