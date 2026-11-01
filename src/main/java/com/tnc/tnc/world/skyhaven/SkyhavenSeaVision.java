package com.tnc.tnc.world.skyhaven;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
/** A few broad, world-anchored sea gyres. Never loads distant chunks. */
@Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class SkyhavenSeaVision {
 private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(262144));
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;var mc=Minecraft.getInstance();
  if(mc.level==null||!mc.level.dimension().equals(SkyhavenContent.KEY)||!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled())return;
  var camera=e.getCamera().getPosition();var matrix=new Matrix4f().translation((float)-camera.x,(float)-camera.y,(float)-camera.z);var v=BUFFER.getBuffer(SeaFlowRenderType.FLOW);
  double time=(mc.level.getGameTime()+e.getPartialTick())*.003;
  for(var gyre:SeaGyreLayout.near(camera.x,camera.z)){
   var at=BlockPos.containing(gyre.x(),48,gyre.z());
   if(!mc.level.hasChunkAt(at)||!mc.level.getFluidState(at).is(SkyhavenContent.SOURCE))continue;
   if(mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,at.getX(),at.getZ())>51)continue;
   double spin=time*gyre.direction()+gyre.phase();
   // Texture alpha remains smoothly interpolated even when a shader pack uses
   // flat vertex colours (Complementary's gbuffers_basic does exactly that).
   double eye=gyre.radius()*.38;
   point(v,matrix,gyre.x()-eye,49.035,gyre.z()-eye,0,0,170);point(v,matrix,gyre.x()+eye,49.035,gyre.z()-eye,1,0,170);
   point(v,matrix,gyre.x()+eye,49.035,gyre.z()+eye,1,1,170);point(v,matrix,gyre.x()-eye,49.035,gyre.z()+eye,0,1,170);
   for(int arm=0;arm<2;arm++)for(int i=0;i<128;i++){
    double t=i/128.,u=(i+1)/128.;var a=bend(gyre,spin,arm,t);var b=bend(gyre,spin,arm,u);
    // Analytic endpoint normals are shared by adjacent segments. This removes the
    // radial seams that made the first mesh resemble a stack of mechanical rings.
    double wa=(.3+2.1*Math.sin(Math.PI*t))*(1-.2*t),wb=(.3+2.1*Math.sin(Math.PI*u))*(1-.2*u);
    int alpha=(int)(Math.pow(Math.sin(Math.PI*(t+u)*.5),.65)*(180+14*Math.sin((t+u)*9+spin)));
    flow(v,matrix,a,-wa*2.8,0,alpha);flow(v,matrix,b,-wb*2.8,0,alpha);
    flow(v,matrix,b,wb*2.8,1,alpha);flow(v,matrix,a,wa*2.8,1,alpha);
   }
  }BUFFER.endBatch(SeaFlowRenderType.FLOW);
 }
 private record Bend(double x,double z,double nx,double nz){}
 private static Bend bend(SeaGyreLayout.Gyre g,double spin,int arm,double t){
  double pulse=1+.045*Math.sin(t*5+g.phase()),r=g.radius()*(.025+.975*t)*pulse;
  double dr=g.radius()*(.975*pulse+(.025+.975*t)*.225*Math.cos(t*5+g.phase()));
  double a=spin+arm*2.9+t*Math.PI*2.4+.16*Math.sin(t*6+g.phase()),da=Math.PI*2.4+.96*Math.cos(t*6+g.phase());
  double x=Math.cos(a)*r,z=Math.sin(a)*r*.88,dx=dr*Math.cos(a)-r*Math.sin(a)*da,dz=(dr*Math.sin(a)+r*Math.cos(a)*da)*.88;
  double turn=g.phase()*.3,c=Math.cos(turn),s=Math.sin(turn),wx=x*c-z*s,wz=x*s+z*c,vx=dx*c-dz*s,vz=dx*s+dz*c,len=Math.hypot(vx,vz);
  return new Bend(g.x()+wx,g.z()+wz,-vz/len,vx/len);
 }
 private static void flow(VertexConsumer v,Matrix4f m,Bend p,double width,float textureV,int alpha){point(v,m,p.x+p.nx*width,49.045,p.z+p.nz*width,.5f,textureV,alpha);}
 private static void point(VertexConsumer v,Matrix4f m,double x,double y,double z,float u,float w,int alpha){v.vertex(m,(float)x,(float)y,(float)z).color(2,5,11,alpha).uv(u,w).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0,1,0).endVertex();}
 private SkyhavenSeaVision(){}
}
