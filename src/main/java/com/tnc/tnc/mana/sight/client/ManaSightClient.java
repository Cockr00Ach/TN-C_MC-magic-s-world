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
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediateWithBuffers(new java.util.LinkedHashMap<>(java.util.Map.of(ManaSightRenderTypes.BODY,new BufferBuilder(1048576),RenderType.lightning(),new BufferBuilder(1048576))),new BufferBuilder(65536));
 private static ClientLevel owner;private static boolean enabled;private static float entrance;
 public static boolean enabled(){return enabled;}
 public static int activeCount(){return FLOWS.size();}
 public static void setEnabled(boolean on){enabled=on;entrance=0;FLOWS.clear();var mc=Minecraft.getInstance();if(mc.player!=null)com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(new ManaSightToggle(on));}
 public static void accept(ResourceLocation dimension,List<ManaFlow> flows){var mc=Minecraft.getInstance();if(!enabled||mc.level==null||!mc.level.dimension().location().equals(dimension))return;for(var f:flows){if(!f.valid())continue;var key=new Edge(f.from(),f.to(),f.color(),f.kind());FLOWS.put(key,new Active(f,mc.level.getGameTime()));while(FLOWS.size()>256)FLOWS.remove(FLOWS.keySet().iterator().next());}}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=owner){owner=mc.level;FLOWS.clear();if(owner==null)enabled=false;else if(enabled&&mc.player!=null)com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.sendToServer(new ManaSightToggle(true));}while(KEY.consumeClick())if(mc.player!=null&&mc.screen==null)setEnabled(!enabled);if(enabled)entrance=Math.min(1,entrance+.07f);if(owner!=null)FLOWS.values().removeIf(f->owner.getGameTime()-f.seen()>60);}
 @SubscribeEvent public static void gui(RenderGuiEvent.Post e){if(!enabled||Minecraft.getInstance().options.hideGui)return;var g=e.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight(),a=(int)(entrance*48);g.fillGradient(0,0,w,22,(a<<24)|0x123756,0);g.fillGradient(0,h-22,w,h,0,(a<<24)|0x254b56);g.fill(5,5,151,21,0xb00b1521);g.fill(5,5,7,21,0xff7ad6d6);g.drawString(Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("hud.tnc.mana_sight",KEY.getTranslatedKeyMessage()),12,9,0xC7EEE4);}
 private static final int[] ELEMENTS={0x19BDE8,0xEE7243,0xB87DFA,0x39DBA1,0xD4B357,0xEEE1A1,0xE567BB};
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(!enabled||owner==null||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
  Vec3 camera=e.getCamera().getPosition();double time=owner.getGameTime()+e.getPartialTick();var pose=new Matrix4f().translation((float)-camera.x,(float)-camera.y,(float)-camera.z);
  var body=BUFFERS.getBuffer(ManaSightRenderTypes.BODY);var glow=BUFFERS.getBuffer(RenderType.lightning());
  int cx=(int)Math.floor(camera.x/10),cz=(int)Math.floor(camera.z/10),cy=(int)Math.floor(camera.y/4);float fade=entrance;
  // Two world-anchored layers of elemental rivers surround the observer. Cosmetic only.
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int layer=0;layer<2;layer++){
   int hash=(cx+x)*73428767^(cz+z)*912931^layer*1997;double phase=(hash&65535)*.001;
   Vec3 a=new Vec3((cx+x)*10+2,cy*4-2+layer*8+Math.floorMod(hash,7),(cz+z)*10+3);
   Vec3 b=a.add(13*Math.cos(phase),5*Math.sin(phase*.7),13*Math.sin(phase));int color=ELEMENTS[Math.floorMod(hash,ELEMENTS.length)];
   curve(body,glow,pose,camera,a,b,color,time+phase*80,.065,(int)(150*fade),1.8,0);
  }
  // Distinct elemental maelstroms: three climbing currents around a hollow, rotating heart.
  int vx=(int)Math.floor(camera.x/24),vz=(int)Math.floor(camera.z/24);double baseY=Math.floor(camera.y/4)*4-2;
  for(int i=0;i<7;i++){
   double a=i*Math.PI*2/7;Vec3 center=new Vec3(vx*24+12+Math.cos(a)*19,baseY+(i%3)*2,vz*24+12+Math.sin(a)*19);
   int color=ELEMENTS[i];double radius=2.5+(i%3)*.85,height=12+(i%3)*4;
   for(int strand=0;strand<3;strand++){Vec3 prev=null;for(int n=0;n<=40;n++){double t=n/40.,angle=t*Math.PI*4+time*.018+strand*Math.PI*2/3;
    double r=radius*(.6+.4*Math.sin(t*Math.PI));Vec3 next=center.add(Math.cos(angle)*r,t*height,Math.sin(angle)*r);
    if(prev!=null){ribbon(body,pose,camera,prev,next,.105,color,(int)(170*fade));ribbon(glow,pose,camera,prev,next,.28,color,(int)(22*fade));}prev=next;
   }}
  }
  // Broad auroral bands high above the landscape; no screen-space wallpaper or camera lock.
  double anchorX=Math.floor(camera.x/32)*32,anchorZ=Math.floor(camera.z/32)*32;
  for(int band=0;band<2;band++)for(int element=0;element<7;element++){
   Vec3 prev=null;for(int n=0;n<=56;n++){double t=n/56.,x=anchorX-44+t*88;
    Vec3 next=new Vec3(x,Math.floor(camera.y/4)*4+13+band*10+Math.sin(t*5+time*.004+band+element*.25)*4+element*.48,anchorZ+(band==0?-18:27)+Math.sin(t*8+band+time*.003+element*.2)*11);
    if(prev!=null){ribbon(body,pose,camera,prev,next,.16,ELEMENTS[element],(int)(100*fade));ribbon(glow,pose,camera,prev,next,.35,ELEMENTS[element],(int)(14*fade));}prev=next;
   }
  }
  // Small drifting motes give the intervening air depth without spawning persistent entities.
  for(int i=0;i<96;i++){int hash=i*7919+(cx/2)*71+(cz/2)*139;double a=(hash&65535)*.007,r=5+Math.floorMod(hash,29);Vec3 p=new Vec3(Math.floor(camera.x/20)*20+10+Math.cos(a)*r,cy*4-2+Math.floorMod(hash/9,23)+Math.sin(time*.015+i)*.5,Math.floor(camera.z/20)*20+10+Math.sin(a)*r);ribbon(body,pose,camera,p,p.add(0,.20,0),.045,ELEMENTS[i%7],(int)(190*fade));ribbon(glow,pose,camera,p.add(-.11,.1,0),p.add(.11,.1,0),.035,ELEMENTS[i%7],(int)(115*fade));}
  // Server-confirmed energy transfers remain brighter and move strictly from source to sink.
  int count=0;for(var active:FLOWS.values()){if(count++>=96)break;var f=active.flow();if(camera.distanceToSqr(f.from())>10000&&camera.distanceToSqr(f.to())>10000)continue;
   float life=(float)Math.min(1,(60-(time-active.seen()))/12);int alpha=(int)(220*Math.max(0,life)*fade);double width=.045+Math.min(32,f.amount())*.0015;
   if(f.kind()==ManaFlow.EMISSION){for(int i=0;i<5;i++){double angle=i*Math.PI*2/5;curve(body,glow,pose,camera,f.from(),f.from().add(Math.cos(angle)*1.5,.65,Math.sin(angle)*1.5),f.color(),time+i*4,width,alpha,.45,1);}}
   else for(int strand=0;strand<3;strand++)curve(body,glow,pose,camera,f.from(),f.to(),f.color(),time+strand*5,width*.7,alpha,f.kind()==ManaFlow.FIBER?.05:.42,strand-1);
  }
  BUFFERS.endBatch(ManaSightRenderTypes.BODY);BUFFERS.endBatch(RenderType.lightning());
 }
 private static void curve(VertexConsumer body,VertexConsumer glow,Matrix4f m,Vec3 camera,Vec3 a,Vec3 b,int color,double time,double width,int alpha,double bend,int strand){
  int steps=18;Vec3 previous=a;Vec3 lateral=b.subtract(a).cross(new Vec3(0,1,0)).normalize();
  for(int i=1;i<=steps;i++){double t=i/(double)steps,wave=Math.sin(t*Math.PI);Vec3 next=a.lerp(b,t).add(0,wave*bend,Math.sin(t*Math.PI*2+time*.025)*wave*bend).add(lateral.scale(strand*.13*wave*Math.cos(t*10-time*.05)));
   double pulse=.45+.55*Math.pow((1+Math.cos(t*16-time*.22))*.5,6);ribbon(body,m,camera,previous,next,width*(.7+pulse),color,(int)(alpha*(.6+.4*pulse)));ribbon(glow,m,camera,previous,next,width*2.8,color,(int)(alpha*.18*pulse));previous=next;
  }
 }
 private static void ribbon(VertexConsumer out,Matrix4f m,Vec3 camera,Vec3 a,Vec3 b,double width,int color,int alpha){Vec3 side=b.subtract(a).cross(camera.subtract(a)).normalize().scale(width);if(side.lengthSqr()<1e-10)side=new Vec3(width,0,0);vertex(out,m,a.add(side),color,alpha);vertex(out,m,b.add(side),color,alpha);vertex(out,m,b.subtract(side),color,alpha);vertex(out,m,a.subtract(side),color,alpha);}
 private static void vertex(VertexConsumer out,Matrix4f m,Vec3 p,int color,int alpha){out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(color>>16&255,color>>8&255,color&255,alpha).endVertex();}
}
