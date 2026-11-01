package com.tnc.tnc.world.ink.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local input feedback is leased; all actual holds still belong to the server. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkCombatFeedback {
    private static int locked,shake,total;private static float strength;private static ResourceLocation dimension;
    public static void hold(ResourceLocation dim,int ticks){dimension=dim;locked=Math.max(0,Math.min(200,ticks));}
    public static void shake(ResourceLocation dim,int ticks,float amount){dimension=dim;shake=Math.max(shake,Math.min(60,ticks));total=shake;strength=Math.max(strength,Math.min(12,Math.max(0,amount)));}
    @SubscribeEvent public static void input(MovementInputUpdateEvent event){
        if(locked<=0)return;var i=event.getInput();i.forwardImpulse=0;i.leftImpulse=0;i.jumping=false;i.up=false;i.down=false;i.left=false;i.right=false;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.player.isAlive()||dimension!=null&&!mc.level.dimension().location().equals(dimension)){locked=0;shake=0;strength=0;return;}
        if(mc.isPaused())return;if(locked>0)locked--;if(shake>0)shake--;else strength=0;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(shake<=0||mc.level==null||mc.isPaused())return;
        float t=(float)(mc.level.getGameTime()+event.getPartialTick()),fade=shake/(float)Math.max(1,total),v=strength*fade*fade;
        event.setPitch(event.getPitch()+(float)Math.sin(t*2.37)*v);
        event.setYaw(event.getYaw()+(float)Math.sin(t*1.91+.8)*v*.85F);
        event.setRoll(event.getRoll()+(float)Math.sin(t*2.73+.3)*v*.65F);
    }
}
