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
public final class CanglanTornadoClientAudit {
 private static final List<CanglanWarlordEntity> bodies=new ArrayList<>();
 private static final List<CanglanEchoEntity> echoes=new ArrayList<>();
 private static final List<String> failures=new ArrayList<>();
 private static int ticks,stage,waiting;private static boolean saving,opening;private static volatile boolean saved;
 @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Setup {
  @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){if(Boolean.getBoolean("tnc.tornadoAudit"))e.enqueueWork(()->{
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
  });}
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
  if(!Boolean.getBoolean("tnc.tornadoAudit")||event.phase!=TickEvent.Phase.END)return;
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
  for(var b:bodies){b.tickCount++;b.walkAnimation.update((stage==1||stage==2)?.65F:0,.4F);}
  for(var e:echoes)e.tickCount++;
  if(++ticks<55||saving)return;
  String name=new String[]{"tornado-turn-a.png","tornado-turn-b.png","tornado-turn-c.png","tornado-turn-d.png"}[stage];
  if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{saved=true;saving=false;});return;}
  if(++stage==4){
   var report=Map.of("failures",failures,"screenshots",4,"scope","Actual Minecraft Forge OBJ model baking and rotation views; not full-pack FPS testing");
   Files.writeString(Path.of(System.getProperty("tnc.tornadoReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();return;
  }
  ticks=stage==2?45:0;saved=false;mc.setScreen(new Gallery());
 }
 private static final class Gallery extends Screen {
  Gallery(){super(Component.literal("Canglan grip and motion verification"));}
  public boolean isPauseScreen(){return false;}
  public void render(GuiGraphics g,int mx,int my,float partial){
   g.fill(0,0,width,height,0xFF101D26);
   g.drawString(font,"ACTUAL MINECRAFT / BAKED CURVED TORNADO / ROTATION VIEWS",24,20,0xD9EEEE,false);
   int count=stage==3?3:3;for(int i=0;i<count;i++){
    int cell=width/count,x=i*cell;g.fill(x+10,58,x+cell-10,height-28,0xFF20313C);
    g.drawString(font,new String[]{"PLAYER GALE MESH","TURNED 55 DEGREES","BOSS CYCLONE MESH"}[i],x+22,76,0xBCD8DE,false);
    Entity entity=stage==3?echoes.get(i):bodies.get(i);
    try{
     g.flush();g.pose().pushPose();
     try{
      float scale=Math.min((height-220)/2.2F,(cell-80)/2.4F);g.pose().translate(x+cell*.5,height-120,450);g.pose().scale(scale,scale,-scale);
      g.pose().mulPose(Axis.ZP.rotationDegrees(180));g.pose().mulPose(Axis.YP.rotationDegrees(stage*35+i*55));g.pose().translate(-.5,0,-.5);
      var buffer=minecraft.renderBuffers().bufferSource();var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","projectile/wind_tornado");
      var model=minecraft.getModelManager().getModel(id);
      if(model==minecraft.getModelManager().getMissingModel())throw new IllegalStateException("Tornado missing model");
      var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));
      if(quads.size()!=600)throw new IllegalStateException("Expected 600 tornado faces, got "+quads.size());
      if(quads.stream().anyMatch(q->q.getSprite().contents().name().getPath().contains("missing")))throw new IllegalStateException("Tornado texture missing from atlas");
      minecraft.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,g.pose(),buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));buffer.endBatch();
     }finally{g.pose().popPose();}
    }catch(Throwable error){if(!failures.contains(error.toString()))failures.add(error.toString());}
   }
  }
 }
}
