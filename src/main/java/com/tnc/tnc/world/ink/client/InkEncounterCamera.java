package com.tnc.tnc.world.ink.client;

import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Camera-only local stand, never spawned into the world or used to teleport a player. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkEncounterCamera {
    private static InkEncounterNetwork.Intro shot;
    private static ArmorStand camera;
    private static CameraType previous;
    private static int age;
    private static Vec3 lastFocus=Vec3.ZERO;
    public static boolean active(){return shot!=null&&camera!=null;}
    public static double aimAlignment(){return !active()?0:camera.getViewVector(1).dot(lastFocus.subtract(camera.getEyePosition(1)).normalize());}
    public static void start(InkEncounterNetwork.Intro packet){
        var mc=Minecraft.getInstance();stop();
        if(packet.phase()==0)return;
        if(mc.level==null||mc.player==null||!mc.level.dimension().location().equals(packet.dimension())||mc.player.isSpectator())return;
        shot=packet;age=0;previous=mc.options.getCameraType();mc.options.setCameraType(CameraType.FIRST_PERSON);
        camera=new ArmorStand(mc.level,packet.position().x,packet.position().y,packet.position().z);camera.setInvisible(true);
        update();if(camera!=null)mc.setCameraEntity(camera);
    }
    private static void stop(){var mc=Minecraft.getInstance();if(camera!=null&&mc.getCameraEntity()==camera)mc.setCameraEntity(mc.player);if(previous!=null)mc.options.setCameraType(previous);shot=null;camera=null;previous=null;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||shot==null)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||!mc.level.dimension().location().equals(shot.dimension())
                ||mc.options.keyShift.isDown()||mc.screen!=null||mc.getCameraEntity()!=camera){stop();return;}
        if(mc.isPaused())return;if(++age>=(shot.phase()==4?64:80)){stop();return;}update();
    }
    private static void update(){
        var mc=Minecraft.getInstance();double t=age/80.0,s=t*t*(3-2*t);Vec3 base=shot.position();
        var boss=mc.level.getEntity(shot.entity());if(boss!=null)base=boss.position();
        if(shot.phase()==4&&boss!=null&&!boss.isAlive()){stop();return;}
        float facing=boss instanceof net.minecraft.world.entity.LivingEntity living&&shot.phase()!=3?living.yBodyRot:shot.yaw();
        Vec3 focus=base.add(0,shot.phase()==1?13.5:shot.phase()==2||shot.phase()==4?11.2:18,0);
        if(shot.phase()==2||shot.phase()==4)focus=focus.add(Vec3.directionFromRotation(0,facing).scale(11));
        if(shot.phase()==4&&boss instanceof InkWingedTigerEntity tiger)focus=tiger.mouthPosition(false).add(0,1.3,0);
        // Dragon's origin is its neck; the authored head sits forward of it.
        if(shot.phase()==3)focus=base.add(InkDragonFlightRules.HEAD.scale(InkDragonCombatRules.BODY_SCALE).yRot((float)Math.toRadians(90-shot.yaw())));
        double radius=shot.phase()==3?64-24*s:shot.phase()==2?30-8*s:shot.phase()==4?7-2*s:25.5-10.5*s;
        double angle=Math.toRadians(facing)+(shot.phase()==4?.6+.18*Math.sin(age*.08):.6-s*1.2);
        Vec3 eye=focus.add(-Math.sin(angle)*radius,shot.phase()==2?-2+s*3:3-s*2,Math.cos(angle)*radius);
        var collision=mc.level.clip(new net.minecraft.world.level.ClipContext(focus,eye,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(collision.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)eye=collision.getLocation().lerp(focus,.12);
        lastFocus=focus;
        Vec3 d=focus.subtract(eye);camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
        camera.yHeadRotO=camera.yHeadRot;
        camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);camera.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));camera.yHeadRot=camera.getYRot();camera.yBodyRot=camera.getYRot();camera.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
        if(age==0){camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.yHeadRotO=camera.yHeadRot;camera.xRotO=camera.getXRot();}
    }
    @SubscribeEvent public static void hideHud(RenderGuiOverlayEvent.Pre event){if(active())event.setCanceled(true);}
    @SubscribeEvent public static void hideHand(RenderHandEvent event){if(active())event.setCanceled(true);}
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post event){
        if(!active())return;
        var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        event.getGuiGraphics().fill(0,0,w,24,0xDD050505);event.getGuiGraphics().fill(0,h-36,w,h,0xDD050505);
        event.getGuiGraphics().drawCenteredString(mc.font,shot.phase()==4?"翼虎 · 裂魂撕咬":InkEncounter.name(shot.phase()),w/2,h-29,0xEEEEEE);
        event.getGuiGraphics().drawCenteredString(mc.font,"潜行键跳过",w/2,h-15,0xAAAAAA);
    }
}
