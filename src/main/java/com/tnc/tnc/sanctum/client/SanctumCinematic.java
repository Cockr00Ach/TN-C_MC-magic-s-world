package com.tnc.tnc.sanctum.client;

import com.tnc.tnc.sanctum.SanctumAppearance.State;
import com.tnc.tnc.sanctum.SanctumTimings;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Camera transforms are captured once per shot. Entity head-turn packets never steer a close-up. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class SanctumCinematic {
    private static State shot;
    private static ArmorStand camera;
    private static CameraType previous;
    private static java.util.UUID lastUuid;
    private static long lastStart;
    private static int elapsed;
    private static float lockedYaw;
    private static boolean revealAngle;
    private static Vec3 anchor,front,right;

    public static void start(State packet){
        var mc=Minecraft.getInstance();
        if(packet.uuid().equals(lastUuid)&&packet.started()==lastStart)return;
        if(mc.level==null||mc.player==null||mc.player.isSpectator()||!mc.player.isAlive()||mc.screen!=null)return;
        if(mc.level.getGameTime()-packet.started()>12)return;
        lastUuid=packet.uuid();lastStart=packet.started();stop();shot=packet;
    }
    static boolean focuses(java.util.UUID id){return camera!=null&&shot!=null&&shot.uuid().equals(id);}
    static float yaw(java.util.UUID id,float fallback){return focuses(id)?lockedYaw:fallback;}
    public static void stop(){
        var mc=Minecraft.getInstance();
        if(camera!=null&&mc.getCameraEntity()==camera)mc.setCameraEntity(mc.player);
        if(previous!=null)mc.options.setCameraType(previous);
        camera=null;previous=null;shot=null;revealAngle=false;
    }
    private static void pin(Vec3 focus,Vec3 eye){
        var mc=Minecraft.getInstance();
        var clip=mc.level.clip(new ClipContext(focus,eye,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        if(clip.getType()==HitResult.Type.BLOCK)eye=clip.getLocation().lerp(focus,.07);
        Vec3 aim=focus.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance()));
        camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);
        camera.xo=camera.xOld=camera.getX();camera.yo=camera.yOld=camera.getY();camera.zo=camera.zOld=camera.getZ();
        camera.setYRot(yaw);camera.yRotO=camera.yHeadRot=camera.yHeadRotO=camera.yBodyRot=camera.yBodyRotO=yaw;
        camera.setXRot(pitch);camera.xRotO=pitch;
    }
    static void tick(){
        if(shot==null)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()
                ||mc.screen!=null||mc.options.keyShift.isDown()||!mc.level.dimension().location().equals(shot.dimension())
                ||(camera!=null&&mc.getCameraEntity()!=camera)){stop();return;}
        var entity=mc.level.getEntity(shot.id());
        if(entity==null||!entity.getUUID().equals(shot.uuid())||entity.distanceToSqr(mc.player)>150*150){stop();return;}
        elapsed=(int)(mc.level.getGameTime()-shot.started());
        if(elapsed>=shot.duration()-1){stop();return;}
        boolean rupture=shot.action().equals("rupture");
        if(camera==null){
            if(mc.getCameraEntity()!=mc.player){stop();return;}
            previous=mc.options.getCameraType();mc.options.setCameraType(CameraType.FIRST_PERSON);
            camera=new ArmorStand(mc.level,entity.getX(),entity.getY(),entity.getZ());camera.setInvisible(true);
            anchor=entity.position();lockedYaw=entity.getYRot();double yaw=Math.toRadians(lockedYaw);
            front=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));right=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
            Vec3 focus=anchor.add(0,rupture?30.4:52.0,0).add(front.scale(rupture?6:4));
            Vec3 eye=focus.add(front.scale(rupture?27.5:11.8)).add(right.scale(rupture?7.4:1.0)).add(0,rupture?-1.8:.8,0);
            pin(focus,eye);mc.setCameraEntity(camera);
        }
        if(rupture&&!revealAngle&&elapsed>=SanctumTimings.SHELL_SPLIT+8){
            // A close target makes the boss step back on server tick 113, under the black frame.
            revealAngle=elapsed>=SanctumTimings.BLACK_END-1;
            anchor=entity.position();lockedYaw=entity.getYRot();double yaw=Math.toRadians(lockedYaw);
            front=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));right=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
            Vec3 focus=anchor.add(0,11.3,0).add(front.scale(3));
            pin(focus,focus.add(front.scale(17)).add(right.scale(-3.7)).add(0,-3.4,0));
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void blackout(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||camera==null||shot==null
                ||!shot.action().equals("rupture")||elapsed<SanctumTimings.SHELL_SPLIT||elapsed>=SanctumTimings.BLACK_END)return;
        // Late HUDs such as minimap waypoints bypass Forge's named overlays. Clear the final target
        // for the short, deliberately opaque cut after the world and all HUD renderers have finished.
        var mc=Minecraft.getInstance();
        mc.getMainRenderTarget().bindWrite(false);
        var color=new float[4];
        org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE,color);
        com.mojang.blaze3d.systems.RenderSystem.clearColor(0,0,0,1);
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,Minecraft.ON_OSX);
        com.mojang.blaze3d.systems.RenderSystem.clearColor(color[0],color[1],color[2],color[3]);
    }
    @SubscribeEvent public static void hand(RenderHandEvent event){if(camera!=null)event.setCanceled(true);}
    @SubscribeEvent public static void overlays(RenderGuiOverlayEvent.Pre event){
        if(camera!=null&&!event.getOverlay().id().getPath().equals("boss_event_progress"))event.setCanceled(true);
    }
    @SubscribeEvent public static void frame(RenderGuiEvent.Post e){
        if(camera==null)return;var mc=Minecraft.getInstance();var g=e.getGuiGraphics();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),band=Math.max(12,h/22);
        g.fill(0,h-band,w,h,0xEB100B0E);g.drawCenteredString(mc.font,"潜行键跳过",w/2,h-band+(band-8)/2,0xB9ACAE);
        if(shot.action().equals("rupture")){
            float opacity=SanctumPose.ease(elapsed,SanctumTimings.SHELL_SPLIT-5,SanctumTimings.SHELL_SPLIT)
                    *(1-SanctumPose.ease(elapsed,SanctumTimings.BLACK_END,SanctumTimings.REVEALED));
            if(opacity>0)g.fill(0,0,w,h,Math.round(opacity*255)<<24);
        }
    }
}
