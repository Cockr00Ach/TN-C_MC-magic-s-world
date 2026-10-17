package com.tnc.tnc.client;

import com.tnc.tnc.magic.wind.WindBallEntity;
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
        if(mc.level==null||mc.player==null){impulse=0;return;}impulse*=.72F;
        if(mc.level.getGameTime()%2!=0)return;int particles=0;
        for(var ball:mc.level.getEntitiesOfClass(WindBallEntity.class,mc.player.getBoundingBox().inflate(64))){
            if(ball.phase()==2){double age=ball.phaseAge(0),distance=ball.distanceTo(mc.player);impulse=Math.max(impulse,(float)(Math.max(0,1-age/12)*Math.max(0,1-distance/50)*(ball.tier()==5?2.4:1.1)));}
            else if(ball.phase()==1&&ball.tier()>=3)for(int i=0;i<4&&particles<16;i++,particles++){
                double a=(mc.level.getGameTime()*.19+i*Math.PI/2),r=ball.radius(0)*1.8;
                double x=Math.cos(a)*r,y=Math.sin(a*.7)*r*.5,z=Math.sin(a)*r;
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,ball.getX()+x,ball.getY()+y,ball.getZ()+z,-x*.08,-y*.08,-z*.08);
            }
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        double t=mc.level.getGameTime()+event.getPartialTick();event.setPitch(event.getPitch()+(float)Math.sin(t*2.1)*impulse);event.setRoll(event.getRoll()+(float)Math.sin(t*1.7)*impulse*.4F);
    }
}
