package com.tnc.tnc.world.wind;

import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in actual Minecraft/GeckoLib gallery; never included in scoped installed releases. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class CanglanAuthoredClientAudit {
 private static final List<CanglanWarlordEntity> bodies=new ArrayList<>();
 private static final List<CanglanEchoEntity> echoes=new ArrayList<>();
 private static final List<String> failures=new ArrayList<>();
 private static volatile int cameraBossId=-1;private static int cameraTicks,activeCameraTicks;private static boolean cameraRequested,cameraStarted,cameraCaptured;
 private static double maxCameraTurn;private static float lastCameraYaw;private static int ticks,stage,waiting;private static boolean saving,opening;private static volatile boolean saved;
 @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Setup {
  @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){if(Boolean.getBoolean("tnc.canglanAuthoredAudit"))e.enqueueWork(()->{
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
  });}
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
  if(!Boolean.getBoolean("tnc.canglanAuthoredAudit")||event.phase!=TickEvent.Phase.END||stage>=8)return;
  if(stage==6){effectsAudit();return;}
  if(stage==7){cameraAudit();return;}
  var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
  if(mc.level==null&&mc.getOverlay()==null&&mc.screen!=null){
   if(++waiting%100==1)System.out.println("CANGLAN AUDIT SCREEN: "+mc.screen.getClass().getSimpleName()+" / "+mc.screen.getTitle().getString());
   if(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&!opening){opening=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"casino-gallery");return;}
   String type=mc.screen.getClass().getSimpleName();
   if(type.contains("Confirm")||type.contains("Safety"))for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button){String text=button.getMessage().getString();if(text.equals("Proceed")||text.equals("Continue")||text.equals("I know what I'm doing!")||text.equals("继续")){button.onPress();break;}}
  }
  if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
  if(bodies.isEmpty()){
   mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.resizeDisplay();
   for(int i=0;i<3;i++){
    var b=CanglanWarlordRegistration.CANGLAN_WARLORD.create(mc.level);b.setPos(mc.player.position());b.setNoAi(true);
    var tag=new CompoundTag();b.saveWithoutId(tag);tag.putBoolean("CanglanWarlordSpawned",true);b.readAdditionalSaveData(tag);
    b.yBodyRot=b.yBodyRotO=0;b.setYRot(0);b.setYHeadRot(0);bodies.add(b);
    var echo=CanglanWarlordRegistration.ECHO.create(mc.level);echo.configure(b,b.position(),0,1);echo.setPoseIndex(i);echo.link(echo.position().add(6,0,0));echoes.add(echo);
   }
   mc.setScreen(new Gallery());
  }
  for(var b:bodies){b.tickCount++;b.walkAnimation.update(0,.4F);}
  for(var e:echoes)e.tickCount++;
  if(++ticks<new int[]{55,12,25,30,63,55}[stage]||saving)return;
  String name=new String[]{"author-idle.png","author-thrust-windup.png","author-thrust-strike.png","author-arsenal.png","author-sweep.png","author-echo.png"}[stage];
  if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{saved=true;saving=false;});return;}
  if(++stage==6){ticks=0;saved=false;mc.setScreen(new EffectsGallery());return;}
  ticks=0;saved=false;
  if(stage>0 && stage<5)for(var b:bodies){
   b.setCanglanMove(stage<3?1:stage==3?11:5);
   b.triggerAnim("action",stage<3?"thrust":stage==3?"sky_arsenal":"sweep");
  }
  mc.setScreen(new Gallery());
 }
 private static void effectsAudit()throws Exception {
  var mc=Minecraft.getInstance();if(++ticks<40||saving)return;
  if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,"canglan-green-tempest-and-blade.png",mc.getMainRenderTarget(),m->{saved=true;saving=false;});return;}
  stage=7;mc.setScreen(null);var uuid=mc.player.getUUID();
  mc.getSingleplayerServer().execute(()->{
   var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
   var boss=CanglanWarlordRegistration.CANGLAN_WARLORD.create(p.level());boss.setNoAi(true);boss.setNoGravity(true);boss.setPos(p.position().add(0,0,6));
   var tag=new CompoundTag();boss.saveWithoutId(tag);tag.putBoolean("CanglanWarlordSpawned",true);boss.readAdditionalSaveData(tag);
   p.serverLevel().addFreshEntity(boss);cameraBossId=boss.getId();
  });
 }
 private static final class EffectsGallery extends Screen {
  EffectsGallery(){super(Component.literal("Canglan shared wind models"));}
  public boolean isPauseScreen(){return false;}
  public void render(GuiGraphics g,int mx,int my,float partial){
   g.fill(0,0,width,height,0xFF142630);g.drawString(font,"ACTUAL MINECRAFT / SHARED PLAYER WIND BLADE / GREEN PHYSICAL CYCLONE",24,20,0xE0FFF0,false);
   for(int i=0;i<2;i++){
    int cell=width/2,x=i*cell;g.fill(x+10,58,x+cell-10,height-28,0xFF20313C);
    g.drawString(font,i==0?"REUSED PLAYER CRESCENT / BOSS WIDTH APPROX. 17 BLOCKS":"MINT GREEN / TWO PHYSICAL WIND LAYERS",x+24,80,0xBBFFE1,false);
    g.flush();g.pose().pushPose();
    try{
     float scale=i==0?(cell-120)/1.4F:(height-220)/2.2F;
     g.pose().translate(x+cell*.5,i==0?height*.52:height-120,450);g.pose().scale(scale,scale,-scale);g.pose().mulPose(Axis.ZP.rotationDegrees(180));
     if(i==0)g.pose().translate(-.5,-.5,-.5);else{g.pose().mulPose(Axis.YP.rotationDegrees(25));g.pose().translate(-.5,0,-.5);}
     var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",i==0?"projectile/windblade":"projectile/wind_tornado");
     var model=minecraft.getModelManager().getModel(id);
     if(model==minecraft.getModelManager().getMissingModel())throw new IllegalStateException("Shared wind mesh missing: "+id);
     var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));
     if(quads.stream().anyMatch(q->q.getSprite().contents().name().getPath().contains("missing")))throw new IllegalStateException("Shared wind sprite missing");
     var buffer=minecraft.renderBuffers().bufferSource();minecraft.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,g.pose(),buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));buffer.endBatch();
    }catch(Throwable error){if(!failures.contains(error.toString()))failures.add(error.toString());}finally{g.pose().popPose();}
   }
  }
 }
 private static void cameraAudit()throws Exception {
  var mc=Minecraft.getInstance();cameraTicks++;
  var entity=mc.level==null?null:mc.level.getEntity(cameraBossId);
  if(entity instanceof CanglanWarlordEntity boss&&!cameraRequested){
   cameraRequested=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
    var real=(CanglanWarlordEntity)p.level().getEntity(cameraBossId);real.battle().testAttack(5,p,false);
    // Show the finisher camera in a creative sandbox; execution damage is covered by server tests.
    CanglanCinematicNetwork.send(p,real,80,true);
   });
  }
  if(com.tnc.tnc.world.wind.client.CanglanCinematicCamera.active()){
   cameraStarted=true;activeCameraTicks++;
   float yaw=mc.getCameraEntity().getYRot();if(activeCameraTicks>12)maxCameraTurn=Math.max(maxCameraTurn,Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-lastCameraYaw)));lastCameraYaw=yaw;
   if(activeCameraTicks==35)Screenshot.grab(mc.gameDirectory,"canglan-stable-camera-windup.png",mc.getMainRenderTarget(),m->{});
   if(activeCameraTicks==35&&com.tnc.tnc.world.wind.client.CanglanCinematicCamera.aimAlignment()<.99)failures.add("Cinematic does not aim at boss");
   if(activeCameraTicks==64){
    Screenshot.grab(mc.gameDirectory,"canglan-cinematic-finisher.png",mc.getMainRenderTarget(),m->{});cameraCaptured=true;
    if(entity instanceof CanglanWarlordEntity b&&com.tnc.tnc.world.wind.client.CanglanSpearTrail.samples(b)<2)failures.add("Animated spear tip has no wind samples");
   }
  }
  if((cameraStarted&&!com.tnc.tnc.world.wind.client.CanglanCinematicCamera.active())||cameraTicks>220){
   if(!cameraStarted||!cameraCaptured)failures.add("Cinematic did not run to impact");
   if(maxCameraTurn>10)failures.add("Cinematic camera rotates too fast: "+maxCameraTurn);
   boolean restored=mc.getCameraEntity()==mc.player;if(!restored)failures.add("Camera did not restore player");
   if(org.lwjgl.opengl.GL11.glGetError()!=0)failures.add("OpenGL error in effects preview");
   Files.writeString(Path.of(System.getProperty("tnc.canglanAuthoredReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"screenshots",9,"maxCameraTurnPerTick",maxCameraTurn,"cameraRestored",restored,"scope","Actual Minecraft geometry, eye emission, flow links, spear tip samples and S2C cinematic camera; creative preview uses finisher camera flag, execution damage tested on server")));
   stage=8;mc.getSingleplayerServer().execute(()->{var e=mc.getSingleplayerServer().overworld().getEntity(cameraBossId);if(e!=null)e.discard();});mc.stop();
  }
 }
 private static final class Gallery extends Screen {
  Gallery(){super(Component.literal("Canglan grip and motion verification"));}
  public boolean isPauseScreen(){return false;}
  public void render(GuiGraphics g,int mx,int my,float partial){
   if(stage>=6)return;
   g.fill(0,0,width,height,0xFF101D26);
   g.drawString(font,"ACTUAL CLIENT / USER AUTHORED POSE AND ANIMATIONS / "+new String[]{"IDLE","THRUST WINDUP","THRUST STRIKE","SKY ARSENAL","SWEEP","ECHO"}[stage],24,20,0xD9EEEE,false);
   int count=3;for(int i=0;i<count;i++){
    int cell=width/count,x=i*cell;g.fill(x+10,58,x+cell-10,height-28,0xFF20313C);
    g.drawString(font,stage==5?new String[]{"GUARD / SIDE-ON","LOW LUNGE","RECOVERY / OPEN HAND"}[i]:new String[]{"FRONT","THREE QUARTER","SIDE"}[i],x+22,76,0xBCD8DE,false);
    Entity entity=stage==5?echoes.get(i):bodies.get(i);
    try{
     g.flush();g.pose().pushPose();
     try{
      float scale=stage==0||stage==5?Math.min((height-250)/10F,(cell-50)/7F):Math.min((height-150)/13F,(cell-50)/10F);g.pose().translate(x+cell*.5,height-180,450);g.pose().scale(scale,scale,-scale);
      g.pose().mulPose(Axis.ZP.rotationDegrees(180));g.pose().mulPose(Axis.YP.rotationDegrees(180+(stage==5?30:new int[]{0,38,90}[i])));
      var dispatcher=minecraft.getEntityRenderDispatcher();var buffer=minecraft.renderBuffers().bufferSource();dispatcher.setRenderShadow(false);
      dispatcher.render(entity,0,0,0,180,partial,g.pose(),buffer,stage==0?0:LightTexture.FULL_BRIGHT);buffer.endBatch();dispatcher.setRenderShadow(true);
     }finally{g.pose().popPose();}
    }catch(Throwable error){if(!failures.contains(error.toString()))failures.add(error.toString());}
   }
  }
 }
}
