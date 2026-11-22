package com.tnc.tnc.mana.sight.client;
import com.tnc.tnc.mana.sight.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
/** Cosmetic currents and server-confirmed transfers are kept separate. Never alters energy. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ManaSightClient {
 public static final KeyMapping KEY=new KeyMapping("key.tnc.mana_sight",net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_F8,"key.categories.tnc");
 @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Keys {@SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(KEY);}}
 private record Edge(Vec3 a,Vec3 b,int color,int kind){}
 private record Active(ManaFlow flow,long seen){}
 private static final LinkedHashMap<Edge,Active> FLOWS=new LinkedHashMap<>();

 private static ClientLevel owner;private static boolean enabled;private static float entrance;
 public static boolean enabled(){return enabled;}
 public static int activeCount(){return FLOWS.size();}
 public static void setEnabled(boolean on){enabled=on;entrance=0;FLOWS.clear();ManaSightParticles.clear();var mc=Minecraft.getInstance();if(mc.player!=null)com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(new ManaSightToggle(on));}
 public static void accept(ResourceLocation dimension,List<ManaFlow> flows){var mc=Minecraft.getInstance();if(!enabled||mc.level==null||!mc.level.dimension().location().equals(dimension))return;for(var f:flows){if(!f.valid())continue;var key=new Edge(f.from(),f.to(),f.color(),f.kind());FLOWS.put(key,new Active(f,mc.level.getGameTime()));while(FLOWS.size()>256)FLOWS.remove(FLOWS.keySet().iterator().next());}}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=owner){owner=mc.level;FLOWS.clear();ManaSightParticles.reloadClear();if(owner==null)enabled=false;else if(enabled&&mc.player!=null)com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(new ManaSightToggle(true));}while(KEY.consumeClick())if(mc.player!=null&&mc.screen==null&&!com.tnc.tnc.skyacademy.client.FirstSightScene.consumeSightKey())setEnabled(!enabled);if(enabled)entrance=Math.min(1,entrance+.07f);if(owner!=null)FLOWS.values().removeIf(f->owner.getGameTime()-f.seen()>60);if(enabled&&owner!=null&&mc.player!=null&&!mc.isPaused())ManaSightParticles.update(owner,mc.gameRenderer.getMainCamera().getPosition(),FLOWS.values().stream().map(Active::flow).toList(),entrance);}
 private static final ResourceLocation EYE=ResourceLocation.parse("tnc:textures/gui/mana_sight_eye.png");
 private static final int EYE_WIDTH=40,EYE_HEIGHT=30,EYE_FRAMES=7;
 private static float lid,previousLid;
 @SubscribeEvent public static void animateEye(TickEvent.ClientTickEvent e){
     if(e.phase!=TickEvent.Phase.END)return;
     previousLid=lid;
     lid=net.minecraft.util.Mth.approach(lid,enabled?1f:0f,.20f);
 }
 @SubscribeEvent public static void gui(RenderGuiEvent.Post e){
     var mc=Minecraft.getInstance();
     if(mc.player==null||mc.options.hideGui||mc.options.renderDebug)return;
     var g=e.getGuiGraphics();
     if(enabled){
         int w=g.guiWidth(),h=g.guiHeight(),a=(int)(entrance*48);
         g.fillGradient(0,0,w,22,(a<<24)|0x123756,0);
         g.fillGradient(0,h-22,w,h,0,(a<<24)|0x254b56);
     }
     int frame=Math.round(net.minecraft.util.Mth.lerp(e.getPartialTick(),previousLid,lid)*(EYE_FRAMES-1));
     com.mojang.blaze3d.systems.RenderSystem.enableBlend();
     g.blit(EYE,5,13,0,frame*EYE_HEIGHT,EYE_WIDTH,EYE_HEIGHT,EYE_WIDTH,EYE_HEIGHT*EYE_FRAMES);
     com.mojang.blaze3d.systems.RenderSystem.disableBlend();
 }

}
