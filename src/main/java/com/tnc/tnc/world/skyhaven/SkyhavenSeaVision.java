package com.tnc.tnc.world.skyhaven;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
/** Local, depth-tested dark surface currents. No distant chunks or persistent sea particles. */
@Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class SkyhavenSeaVision {
 private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(65536));
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();
  if(mc.level==null||!mc.level.dimension().equals(SkyhavenContent.KEY)||!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled())return;
  var camera=e.getCamera().getPosition();var matrix=new Matrix4f().translation((float)-camera.x,(float)-camera.y,(float)-camera.z);var v=BUFFER.getBuffer(RenderType.debugQuads());
  double time=(mc.level.getGameTime()+e.getPartialTick())*.008;int cx=(int)Math.floor(camera.x/24),cz=(int)Math.floor(camera.z/24);
  for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
   double x=(cx+dx)*24+12,z=(cz+dz)*24+12;var at=BlockPos.containing(x,48,z);
   if(!mc.level.hasChunkAt(at)||!mc.level.getFluidState(at).is(SkyhavenContent.SOURCE))continue;
   double seed=(cx+dx)*.73+(cz+dz)*1.37;
   for(int i=0;i<24;i++){
    double a=i*.24+time+seed,b=a+.22,r=3+i*.27;
    double ax=x+Math.cos(a)*r,az=z+Math.sin(a)*r,bx=x+Math.cos(b)*(r+.27),bz=z+Math.sin(b)*(r+.27);
    double width=.10+.04*Math.sin(a);double y=49.025+.025*Math.sin(a*2+time);
    quad(v,matrix,ax,az,bx,bz,y,width,3,4,8,210);
    quad(v,matrix,ax,az,bx,bz,y-.003,width+.035,37,41,51,105);
   }
  }BUFFER.endBatch(RenderType.debugQuads());
 }
 private static void quad(VertexConsumer v,Matrix4f m,double ax,double az,double bx,double bz,double y,double width,int r,int g,int b,int alpha){
  v.vertex(m,(float)(ax-width),(float)y,(float)az).color(r,g,b,alpha).endVertex();v.vertex(m,(float)(bx-width),(float)y,(float)bz).color(r,g,b,alpha).endVertex();v.vertex(m,(float)(bx+width),(float)y,(float)bz).color(r,g,b,alpha).endVertex();v.vertex(m,(float)(ax+width),(float)y,(float)az).color(r,g,b,alpha).endVertex();
 }
 private SkyhavenSeaVision(){}
}
