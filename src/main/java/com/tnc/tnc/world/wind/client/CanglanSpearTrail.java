package com.tnc.tnc.world.wind.client;
import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.world.wind.CanglanWarlordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-only wind slipstream sampled from the actual animated spear tip. */
public final class CanglanSpearTrail {
 private static final Map<CanglanWarlordEntity,Trail> TRAILS=new WeakHashMap<>();
 private static final RenderType GLOW=RenderType.eyes(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/canglan_mana_flow.png"));
 private static final class Trail {final ArrayDeque<Vec3> points=new ArrayDeque<>();int tick=-1;}
 public static int samples(CanglanWarlordEntity e){Trail t=TRAILS.get(e);return t==null?0:t.points.size();}
 private CanglanSpearTrail(){}
 public static void render(CanglanWarlordEntity e,Vec3 localTip,float partial,PoseStack pose,MultiBufferSource buffers){
  if(e.level().getEntity(e.getId())!=e)return;
  Vec3 origin=e.getPosition(partial),tip=origin.add(localTip);float scale=e.battleScale();
  Trail trail=TRAILS.computeIfAbsent(e,k->new Trail());
  if(trail.tick!=e.tickCount){
   Vec3 previous=trail.points.peekFirst(),motion=previous==null?Vec3.ZERO:tip.subtract(previous);
   if(motion.length()>24*scale)trail.points.clear();
   trail.points.addFirst(tip);while(trail.points.size()>14)trail.points.removeLast();
   while(trail.points.size()>2&&tip.distanceTo(trail.points.peekLast())>14*scale)trail.points.removeLast();
   trail.tick=e.tickCount;
   if(e.tickCount%4==0)e.level().addParticle(ParticleTypes.CLOUD,tip.x,tip.y,tip.z,-motion.x*.025,.015,-motion.z*.025);
   e.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.35F,1F,.68F),Math.min(2,.55F*scale)),tip.x,tip.y,tip.z,-motion.x*.03,.01,-motion.z*.03);
   if(motion.lengthSqr()>.12)e.level().addParticle(ParticleTypes.WHITE_ASH,tip.x,tip.y,tip.z,-motion.x*.05,.01,-motion.z*.05);
  }
  if(trail.points.size()<2)return;
  var points=new ArrayList<>(trail.points);points.set(0,tip);var out=buffers.getBuffer(GLOW);var matrix=pose.last();
  Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
  for(int layer=0;layer<2;layer++)for(int i=0;i<points.size()-1;i++){
   Vec3 a=points.get(i),b=points.get(i+1),direction=b.subtract(a);if(direction.lengthSqr()<1E-6)continue;
   Vec3 side=direction.cross(camera.subtract(a)).normalize();if(side.lengthSqr()<.01)side=new Vec3(0,1,0);
   double fade=1-i/(double)points.size(),width=(layer==0?.16:.04)*scale*fade;
   Vec3 edge=side.scale(width);int alpha=(int)(255*fade*(layer==0?.23:.8));
   a=a.subtract(origin);b=b.subtract(origin);float u=(e.tickCount+partial)*-.06F+i*.2F;
   vertex(out,matrix,a.subtract(edge),u,0,alpha);vertex(out,matrix,b.subtract(edge),u+.2F,0,alpha);
   vertex(out,matrix,b.add(edge),u+.2F,1,alpha);vertex(out,matrix,a.add(edge),u,1,alpha);
  }
 }
 private static void vertex(VertexConsumer out,PoseStack.Pose m,Vec3 p,float u,float v,int alpha){
  out.vertex(m.pose(),(float)p.x,(float)p.y,(float)p.z).color(150,255,210,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY)
    .uv2(LightTexture.FULL_BRIGHT).normal(m.normal(),0,1,0).endVertex();
 }
}
