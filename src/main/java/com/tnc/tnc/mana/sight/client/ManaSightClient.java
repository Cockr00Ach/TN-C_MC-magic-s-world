package com.tnc.tnc.mana.sight.client;
import com.tnc.tnc.mana.sight.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
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
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(524288));
 private static ClientLevel owner;private static boolean enabled;
 public static boolean enabled(){return enabled;}
 public static int activeCount(){return FLOWS.size();}
 public static void setEnabled(boolean on){enabled=on;FLOWS.clear();var mc=Minecraft.getInstance();if(mc.player!=null)com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(new ManaSightToggle(on));}
 public static void accept(ResourceLocation dimension,List<ManaFlow> flows){var mc=Minecraft.getInstance();if(!enabled||mc.level==null||!mc.level.dimension().location().equals(dimension))return;for(var f:flows){if(!f.valid())continue;var key=new Edge(f.from(),f.to(),f.color(),f.kind());FLOWS.put(key,new Active(f,mc.level.getGameTime()));while(FLOWS.size()>256)FLOWS.remove(FLOWS.keySet().iterator().next());}}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=owner){owner=mc.level;FLOWS.clear();if(owner==null)enabled=false;else if(enabled&&mc.player!=null)com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(new ManaSightToggle(true));}while(KEY.consumeClick())if(mc.player!=null&&mc.screen==null)setEnabled(!enabled);if(owner!=null)FLOWS.values().removeIf(f->owner.getGameTime()-f.seen()>60);}
 @SubscribeEvent public static void gui(RenderGuiEvent.Post e){if(!enabled||Minecraft.getInstance().options.hideGui)return;var g=e.getGuiGraphics();g.fill(5,5,144,20,0x99081520);g.drawString(Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("hud.tnc.mana_sight",KEY.getTranslatedKeyMessage()),10,9,0xB6FFF0);}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){if(!enabled||owner==null||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;Vec3 camera=e.getCamera().getPosition();double time=owner.getGameTime()+e.getPartialTick();var pose=new Matrix4f().translation((float)-camera.x,(float)-camera.y,(float)-camera.z);var out=BUFFERS.getBuffer(RenderType.lightning());
  // Broad, slow environmental ribbons: atmosphere only, no claim of usable energy.
  int[] palette={0x56D9CF,0x8297EF,0xF3BA89,0xA3EDC8,0xBE9AEF,0xE8D9AA};int cx=(int)Math.floor(camera.x/12),cz=(int)Math.floor(camera.z/12);
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){int hash=(cx+x)*73428767^(cz+z)*912931;double phase=(hash&65535)*.001;Vec3 a=new Vec3((cx+x)*12+3,Math.floor(camera.y/12)*12-5+Math.floorMod(hash,17),(cz+z)*12+4);Vec3 b=a.add(10*Math.cos(phase),4*Math.sin(phase*.7),10*Math.sin(phase));curve(out,pose,camera,a,b,palette[Math.floorMod(hash,palette.length)],time+phase*100,.015,28,.7);}
  int count=0;for(var active:FLOWS.values()){if(count++>=128)break;var f=active.flow();if(camera.distanceToSqr(f.from())>10000&&camera.distanceToSqr(f.to())>10000)continue;float fade=(float)Math.min(1,(60-(time-active.seen()))/12);int alpha=(int)(150*Math.max(0,fade));double width=.025+Math.min(32,f.amount())*.001;
   if(f.kind()==ManaFlow.EMISSION){for(int i=0;i<6;i++){double a=i*Math.PI/3;curve(out,pose,camera,f.from(),f.from().add(Math.cos(a)*1.45,.45+Math.sin(time*.02+i)*.2,Math.sin(a)*1.45),f.color(),time+i*5,width,alpha,.26);}}
   else {curve(out,pose,camera,f.from(),f.to(),f.color(),time,width,alpha,f.kind()==ManaFlow.FIBER?.04:.3);curve(out,pose,camera,f.from(),f.to(),0xE9FFFF,time,width*.28,Math.min(230,alpha+55),f.kind()==ManaFlow.FIBER?.04:.3);}
  }BUFFERS.endBatch(RenderType.lightning());
 }
 private static void curve(VertexConsumer out,Matrix4f m,Vec3 camera,Vec3 a,Vec3 b,int color,double time,double width,int alpha,double bend){int steps=18;Vec3 previous=a;for(int i=1;i<=steps;i++){double t=i/(double)steps;Vec3 next=a.lerp(b,t).add(0,Math.sin(t*Math.PI)*bend,Math.sin(t*Math.PI*2+time*.025)*Math.sin(t*Math.PI)*bend);double pulse=.4+.6*Math.pow((1+Math.cos(t*14-time*.2))*.5,6);ribbon(out,m,camera,previous,next,width*(.7+pulse),color,(int)(alpha*(.5+.5*pulse)));previous=next;}}
 private static void ribbon(VertexConsumer out,Matrix4f m,Vec3 camera,Vec3 a,Vec3 b,double width,int color,int alpha){Vec3 side=b.subtract(a).cross(camera.subtract(a)).normalize().scale(width);if(side.lengthSqr()<1e-10)side=new Vec3(width,0,0);vertex(out,m,a.add(side),color,alpha);vertex(out,m,b.add(side),color,alpha);vertex(out,m,b.subtract(side),color,alpha);vertex(out,m,a.subtract(side),color,alpha);}
 private static void vertex(VertexConsumer out,Matrix4f m,Vec3 p,int color,int alpha){out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(color>>16&255,color>>8&255,color&255,alpha).endVertex();}
}
