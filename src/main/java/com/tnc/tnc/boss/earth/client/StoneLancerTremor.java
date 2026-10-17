package com.tnc.tnc.boss.earth.client;

import com.tnc.tnc.boss.earth.StoneLancerEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One bounded lookup per five ticks; camera frames only interpolate the cached impulse. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class StoneLancerTremor {
    private static float impulse;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=world){world=mc.level;impulse=0;}
        if(mc.level==null||mc.player==null){impulse=0;return;}
        impulse*=.82F;
        if(mc.level.getGameTime()%5!=0)return;
        for(var boss:mc.level.getEntitiesOfClass(StoneLancerEntity.class,mc.player.getBoundingBox().inflate(220),b->b.isAlive())){
            double distance=mc.player.position().distanceTo(boss.position());
            if(!boss.isCasting()&&boss.walkAnimation.isMoving()&&boss.onGround()&&distance<110)
                impulse=Math.max(impulse,(float)(1.7*(1-distance/110)*(.4+.6*Math.abs(Math.cos(boss.tickCount*Math.PI/18)))));
            float age=boss.quakeAge(0);
            double ringDistance=mc.player.position().subtract(boss.quakeCentre()).horizontalDistance();
            if(age>=0&&ringDistance<StoneLancerEntity.QUAKE_RADIUS)
                impulse=Math.max(impulse,(float)(4.5*(1-ringDistance/260)*(1-age/100)));
        }
        for(var blast:mc.level.getEntitiesOfClass(com.tnc.tnc.magic.EarthAttackEntity.class,mc.player.getBoundingBox().inflate(240),
                b->b.done()&&b.model().startsWith("stone_lancer_")&&(b.mode()==2||b.mode()==3))){
            double age=blast.age(0)-blast.burstAge(),distance=mc.player.position().distanceTo(blast.position());
            if(age>=0&&age<20&&distance<240)impulse=Math.max(impulse,(float)(5*(1-age/20)*(1-distance/240)));
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        double t=mc.level.getGameTime()+e.getPartialTick();
        e.setPitch(e.getPitch()+(float)Math.sin(t*1.37)*impulse);
        e.setRoll(e.getRoll()+(float)Math.sin(t*.97)*impulse*.65F);
    }
}
