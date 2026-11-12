package com.tnc.tnc.warrior.client;
import com.tnc.tnc.warrior.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorSwordFxRenderer {
 private record Visible(WarriorSwordFx p,long at){}
 private static final java.util.List<Visible> FX=new java.util.ArrayList<>();
 private static net.minecraft.client.multiplayer.ClientLevel world;
 private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource BUFFER=net.minecraft.client.renderer.MultiBufferSource.immediate(new BufferBuilder(65536));
 public static void accept(WarriorSwordFx p){var level=Minecraft.getInstance().level;if(world!=level){FX.clear();world=level;}if(level!=null){if(FX.size()>=96)FX.remove(0);FX.add(new Visible(p,level.getGameTime()));}}
 @SubscribeEvent public static void hand(RenderHandEvent e){var p=Minecraft.getInstance().player;if(p==null)return;var a=WarriorAnimationClient.active(p.getId());if(a!=null&&a.animation()==WarriorAnimation.ULTIMATE&&WarriorWeapons.family(p.getMainHandItem())==WarriorWeaponFamily.LONGSWORD){float age=a.progress(e.getPartialTick())*96;if(age>=26&&age<60)e.setCanceled(true);}}
 @SubscribeEvent public static void ghost(RenderPlayerEvent.Pre e){var a=WarriorAnimationClient.active(e.getEntity().getId());if(a!=null&&a.animation()==WarriorAnimation.ULTIMATE&&WarriorWeapons.family(e.getEntity().getMainHandItem())==WarriorWeaponFamily.LONGSWORD){float age=a.progress(e.getPartialTick())*96;if(age>=26&&age<60)e.setCanceled(true);}}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();if(mc.level!=world){FX.clear();world=mc.level;}if(world==null)return;
  long now=world.getGameTime();FX.removeIf(v->now-v.at()>80);var out=WarriorTrailRenderType.buffer(BUFFER);Vec3 cam=e.getCamera().getPosition();
  for(var v:FX){double age=now-v.at()+e.getPartialTick();var p=v.p();if(p.from().distanceToSqr(cam)>64*64)continue;
   if(p.kind()==0){float fade=(float)Math.max(0,1-age/80);Vec3 dir=p.to().subtract(p.from()).normalize(),side=new Vec3(-dir.z,0,dir.x);
    for(int i=0;i<32;i++){Vec3 a=ground(p.from().lerp(p.to(),i/32.0)),b=ground(p.from().lerp(p.to(),(i+1)/32.0));if(a==null||b==null)continue;
     for(int layer=0;layer<3;layer++){double w=.025+layer*.075;quad(out,cam,a.add(side.scale(w)),a.subtract(side.scale(w)),b.subtract(side.scale(w)),b.add(side.scale(w)),fade*(layer==0?.8f:.12f));}}
    if(age<16)for(int i=0;i<5;i++){Vec3 at=p.from().lerp(p.to(),i/4.0);float alpha=(float)((1-age/16)*.22);box(out,cam,at.add(0,1.05,0),.25,.45,.14,alpha);box(out,cam,at.add(0,1.65,0),.23,.23,.23,alpha);for(int sign:new int[]{-1,1}){box(out,cam,at.add(side.scale(sign*.17)).add(0,.4,0),.11,.4,.12,alpha);box(out,cam,at.add(side.scale(sign*.34)).add(dir.scale(.2)).add(0,1.15,0),.10,.35,.12,alpha);}}
   }else if(age<22){Vec3 at=ground(p.from());if(at==null)continue;double r=.3+age*.18,height=Math.sin(age/22*Math.PI)*3;float fade=(float)(1-age/22);
    for(int i=0;i<64;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;Vec3 x=at.add(Math.cos(a)*r,0,Math.sin(a)*r),y=at.add(Math.cos(b)*r,0,Math.sin(b)*r);quad(out,cam,x,y,y.add(0,height,0),x.add(0,height,0),fade*.18f);quad(out,cam,x,y,at.add(Math.cos(b)*(r+.12),0,Math.sin(b)*(r+.12)),at.add(Math.cos(a)*(r+.12),0,Math.sin(a)*(r+.12)),fade*.8f);}}
  }BUFFER.endBatch(WarriorTrailRenderType.TYPE);
 }
 private static Vec3 ground(Vec3 p){var hit=world.clip(new net.minecraft.world.level.ClipContext(p.add(0,.5,0),p.add(0,-3,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,null));return hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?null:hit.getLocation().add(0,.035,0);}
 private static void box(VertexConsumer o,Vec3 c,Vec3 p,double x,double y,double z,float a){for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){Vec3[] v=new Vec3[4];int k=0;for(int[] pair:new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}}){double[] d=new double[3];d[axis]=sign;d[(axis+1)%3]=pair[0];d[(axis+2)%3]=pair[1];v[k++]=p.add(d[0]*x,d[1]*y,d[2]*z);}quad(o,c,v[0],v[1],v[2],v[3],a);}}
 private static void quad(VertexConsumer o,Vec3 c,Vec3 a,Vec3 b,Vec3 d,Vec3 e,float alpha){for(Vec3 p:new Vec3[]{a,b,d,e})o.vertex(p.x-c.x,p.y-c.y,p.z-c.z).color(.60f,.82f,1f,alpha).endVertex();}
}
