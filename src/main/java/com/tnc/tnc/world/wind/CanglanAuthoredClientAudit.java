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
 private static int ticks,stage,waiting;private static boolean saving,opening;private static volatile boolean saved;
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
  if(stage>=6||!Boolean.getBoolean("tnc.canglanAuthoredAudit")||event.phase!=TickEvent.Phase.END)return;
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
    var echo=CanglanWarlordRegistration.ECHO.create(mc.level);echo.configure(b,b.position(),0,1);echo.setPoseIndex(i);echoes.add(echo);
   }
   mc.setScreen(new Gallery());
  }
  for(var b:bodies){b.tickCount++;b.walkAnimation.update(0,.4F);}
  for(var e:echoes)e.tickCount++;
  if(++ticks<new int[]{55,12,25,30,63,55}[stage]||saving)return;
  String name=new String[]{"author-idle.png","author-thrust-windup.png","author-thrust-strike.png","author-arsenal.png","author-sweep.png","author-echo.png"}[stage];
  if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{saved=true;saving=false;});return;}
  if(++stage==6){
   var report=Map.of("failures",failures,"screenshots",6,"scope","Actual Minecraft GeckoLib/baked-weapon rendering in a disposable development world; not full-pack FPS testing");
   Files.writeString(Path.of(System.getProperty("tnc.canglanAuthoredReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();return;
  }
  ticks=0;saved=false;
  if(stage>0 && stage<5)for(var b:bodies){
   b.setCanglanMove(stage<3?1:stage==3?11:5);
   b.triggerAnim("action",stage<3?"thrust":stage==3?"sky_arsenal":"sweep");
  }
  mc.setScreen(new Gallery());
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
      dispatcher.render(entity,0,0,0,180,partial,g.pose(),buffer,LightTexture.FULL_BRIGHT);buffer.endBatch();dispatcher.setRenderShadow(true);
     }finally{g.pose().popPose();}
    }catch(Throwable error){if(!failures.contains(error.toString()))failures.add(error.toString());}
   }
  }
 }
}
