package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eyes → hand and blade → return. Local, skippable and collision checked. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorUltimateCamera {
    private static ArmorStand camera;
    private static CameraType previous;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static int age;
    private static float yaw;
    public static void start(int entity){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.player.getId()!=entity||mc.screen!=null||mc.getCameraEntity()!=mc.player)return;
        previous=mc.options.getCameraType();world=mc.level;age=0;yaw=mc.player.getYRot();
        camera=new ArmorStand(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());camera.setInvisible(true);
        update();if(camera==null)return;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);
    }
    public static void stop(){
        var mc=Minecraft.getInstance();
        if(camera!=null&&mc.getCameraEntity()==camera){mc.setCameraEntity(mc.player);if(previous!=null)mc.options.setCameraType(previous);}
        camera=null;previous=null;world=null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||camera==null)return;var mc=Minecraft.getInstance();
        var anim=mc.player==null?null:WarriorAnimationClient.active(mc.player.getId());
        if(mc.level!=world||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||mc.screen!=null||!mc.isWindowActive()
            ||mc.options.keyShift.isDown()||mc.getCameraEntity()!=camera||mc.options.getCameraType()!=CameraType.FIRST_PERSON
            ||anim==null||anim.animation()!=WarriorAnimation.ULTIMATE){stop();return;}
        if(mc.isPaused())return;if(++age>=WarriorUltimates.INTRO){stop();return;}update();
    }
    private static void update(){
        var mc=Minecraft.getInstance();var p=mc.player;
        double t=Math.max(0,Math.min(1,(age-7)/14.0));t=t*t*(3-2*t);
        Vec3 f=faceDirection(1),side=new Vec3(f.z,0,-f.x).normalize();
        Vec3 focus=focus(1);
        Vec3 eye=focus.add(f.scale(.95+1.5*t)).add(side.scale(.06+.55*t)).add(0,.02+.12*t,0);
        var hit=world.clip(new ClipContext(focus,eye,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()==HitResult.Type.BLOCK){eye=hit.getLocation().lerp(focus,.2);if(eye.distanceTo(focus)<.45){stop();return;}}
        Vec3 d=focus.subtract(eye);
        camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
        camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);
        camera.setYRot(camera.yRotO+net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-d.x,d.z))-camera.yRotO));camera.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
        if(age==0){camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();}
    }
    private static Vec3 faceDirection(float partial){
        var p=Minecraft.getInstance().player;
        var active=WarriorAnimationClient.active(p.getId());
        float root=WarriorAnimationClient.rootYawDegrees(p),headYaw=0,headPitch=0;
        if(active!=null){
            var clip=WarriorMartialClips.clip(active.animation(),p.getMainHandItem());
            if(clip!=null){var h=clip.rotation("head",active.progress(partial)*clip.lengthSeconds());
                float weight=active.weight(partial),sign=p.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?-1:1;
                headYaw=h[1]*weight*sign;headPitch=h[0]*weight;
            }
        }
        return Vec3.directionFromRotation(headPitch,net.minecraft.util.Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot)+root+headYaw);
    }
    private static Vec3 focus(float partial){
        var p=Minecraft.getInstance().player;
        double scale=p.getEyeHeight()/1.62;
        // Player model head pivots at 1.5 blocks; face is on the front surface,
        // not at the gameplay eye position inside the head cube.
        Vec3 face=p.getPosition(partial).add(0,1.62*scale,0).add(faceDirection(partial).scale(.25*scale));
        double t=Math.max(0,Math.min(1,(age-7+partial)/14.0));t=t*t*(3-2*t);
        return face.lerp(p.getPosition(partial).add(0,1.30*scale,0),t*.65);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void aim(ViewportEvent.ComputeCameraAngles e){
        if(camera==null||Minecraft.getInstance().player==null)return;
        Vec3 d=focus((float)e.getPartialTick()).subtract(e.getCamera().getPosition());
        e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));e.setRoll(0);
    }
    @SubscribeEvent public static void hand(RenderHandEvent e){if(camera!=null)e.setCanceled(true);}
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e){if(camera!=null)e.setFOV(48+12*Math.min(1,age/22.0));}
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e){
        if(camera==null)return;var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        var g=e.getGuiGraphics();g.fill(0,0,w,18,0xCB050914);g.fill(0,h-28,w,h,0xCB050914);
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mc.player.getMainHandItem().getItem());
        g.drawCenteredString(mc.font,WarriorUltimates.forId(id.toString()).name()+" · 潜行跳过特写",w/2,h-19,0xFFE1AB);
    }
}
