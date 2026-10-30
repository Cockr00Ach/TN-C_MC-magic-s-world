package com.tnc.tnc.world.wind.client;
import com.tnc.tnc.world.wind.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A short, skippable boss-following shot; restore perspective on every exit path. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class CanglanCinematicCamera {
 private static CanglanCinematicNetwork.Shot shot;private static ArmorStand camera;private static CameraType previous;
 private static int age;private static boolean acquired;private static Vec3 focus=Vec3.ZERO,viewSide=Vec3.ZERO;
 public static boolean active(){return shot!=null&&acquired;}
 public static double aimAlignment(){return !active()?0:camera.getViewVector(1).dot(focus.subtract(camera.getEyePosition(1)).normalize());}
 public static void start(CanglanCinematicNetwork.Shot packet){
  var mc=Minecraft.getInstance();
  if(packet.ticks()==0){if(shot!=null&&shot.boss()==packet.boss())stop();return;}
  if(!CanglanWarGodRules.executionCamera(packet.ticks(),packet.execution()))return;
  if(mc.level==null||mc.player==null||!mc.level.dimension().location().equals(packet.dimension())||mc.player.isSpectator())return;
  if(shot==null&&mc.getCameraEntity()!=mc.player)return;
  stop();shot=packet;age=0;previous=mc.options.getCameraType();camera=new ArmorStand(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());camera.setInvisible(true);camera.setNoGravity(true);
  update();
 }
 public static void stop(){
  var mc=Minecraft.getInstance();if(camera!=null&&mc.getCameraEntity()==camera){mc.setCameraEntity(mc.player);if(previous!=null)mc.options.setCameraType(previous);}
  shot=null;camera=null;previous=null;acquired=false;
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END||shot==null)return;var mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||mc.options.keyShift.isDown()||mc.screen!=null
    ||!mc.level.dimension().location().equals(shot.dimension())||(acquired&&(mc.getCameraEntity()!=camera||mc.options.getCameraType()!=CameraType.FIRST_PERSON))){stop();return;}
  if(mc.isPaused())return;if(++age>=Math.min(160,shot.ticks())){stop();return;}update();
 }
 private static void update(){
  var mc=Minecraft.getInstance();var entity=mc.level.getEntity(shot.boss());
  if(!(entity instanceof CanglanWarlordEntity boss)||!boss.isAlive()){if(acquired||age>12)stop();return;}
  double scale=boss.battleScale();
  // Hold one camera side for the whole shot. Root spin changes the subject, never the orbit.
  if(!acquired){
   double angle=Math.toRadians(boss.yBodyRot)+.5;
   viewSide=new Vec3(-Math.sin(angle),0,Math.cos(angle));
  }
  Vec3 face=boss.position().add(CanglanWarlordRenderer.face(boss));
  Vec3 tip=boss.position().add(CanglanWarlordRenderer.tip(boss));
  Vec3 span=tip.subtract(face);if(span.length()>8*scale)span=span.normalize().scale(8*scale);
  var execution=CanglanExecutionClient.state(shot.boss());
  boolean flung=execution!=null&&execution.phase()==CanglanExecutionPose.FLUNG;
  Vec3 playerPoint=mc.player.getPosition(1).add(0,mc.player.getBbHeight()*.55,0);
  if(execution!=null&&execution.phase()==CanglanExecutionPose.PINNED)playerPoint=CanglanExecutionPose.tip(boss,CanglanExecutionClient.age(execution,0));
  Vec3 wantedFocus=execution==null?face.add(span.scale(.3)):flung?playerPoint:face.lerp(playerPoint,.75);
  focus=flung?playerPoint:acquired?focus.lerp(wantedFocus,.3):wantedFocus;
  // Wide enough to retain the face and spear; no rapid orbit or push-in during the strike.
  Vec3 eye=focus.add(viewSide.scale((shot.execution()?10:11)*scale)).add(0,1.2*scale,0);
  if(flung){Vec3 velocity=mc.player.getDeltaMovement().multiply(1,0,1).normalize();if(velocity.lengthSqr()<.1)velocity=Vec3.directionFromRotation(0,boss.yBodyRot);eye=playerPoint.subtract(velocity.scale(7)).add(viewSide.scale(4)).add(0,3.2,0);}
  var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(focus,eye,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
  if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)eye=hit.getLocation().lerp(focus,.15);
  if(acquired)eye=camera.getEyePosition(1).lerp(eye,flung?.48:.22);
  Vec3 direction=focus.subtract(eye);camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.xRotO=camera.getXRot();camera.yRotO=camera.getYRot();
  camera.setPos(eye.x,eye.y-camera.getEyeHeight(),eye.z);camera.setYRot(camera.yRotO+net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-direction.x,direction.z))-camera.yRotO));camera.yHeadRot=camera.yBodyRot=camera.getYRot();camera.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));
  if(!acquired){camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();camera.xRotO=camera.getXRot();camera.yRotO=camera.getYRot();mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);acquired=true;}
 }
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
 public static void stabilize(ViewportEvent.ComputeCameraAngles event){
  if(!active())return;float partial=(float)event.getPartialTick();
  var execution=CanglanExecutionClient.state(shot.boss());
  if(execution!=null&&execution.phase()==CanglanExecutionPose.FLUNG){
   var player=Minecraft.getInstance().player;Vec3 point=player.getPosition(partial).add(0,player.getBbHeight()*.55,0);
   Vec3 delta=point.subtract(camera.getEyePosition(partial));
   event.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));event.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));event.setRoll(0);return;
  }
  event.setYaw(net.minecraft.util.Mth.rotLerp(partial,camera.yRotO,camera.getYRot()));
  event.setPitch(net.minecraft.util.Mth.lerp(partial,camera.xRotO,camera.getXRot()));event.setRoll(0);
 }
 @SubscribeEvent public static void hideHand(RenderHandEvent event){if(active())event.setCanceled(true);}
 @SubscribeEvent public static void overlay(RenderGuiEvent.Post event){
  if(!active())return;var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
  event.getGuiGraphics().fill(0,0,w,18,0xBB05130F);event.getGuiGraphics().fill(0,h-30,w,h,0xBB05130F);
  var state=CanglanExecutionClient.state(shot.boss());
  event.getGuiGraphics().drawCenteredString(mc.font,state!=null&&state.phase()==CanglanExecutionPose.FLUNG?"苍岚战将 · 掷落":state!=null&&state.phase()==CanglanExecutionPose.PINNED?"苍岚战将 · 穿身挑举":"苍岚战将 · 长矛处决",w/2,h-26,0xBFFFE1);
  event.getGuiGraphics().drawCenteredString(mc.font,"潜行键跳过镜头",w/2,h-13,0xAAAAAA);
 }
}
