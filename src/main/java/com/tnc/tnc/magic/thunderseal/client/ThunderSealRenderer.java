package com.tnc.tnc.magic.thunderseal.client;
import com.tnc.tnc.magic.thunderseal.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ThunderSealRenderer extends EntityRenderer<ThunderSealEntity> {
 private static final ResourceLocation MODEL=ThunderSealContent.id("thunder_seal_pylon"),GLOW=ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/beacon_beam.png"),CIRCLE=ThunderSealContent.id("textures/entity/magic_circle.png");
 public ThunderSealRenderer(EntityRendererProvider.Context context){super(context);}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ThunderSealContent.SEAL,ThunderSealRenderer::new);}
 @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(new net.minecraft.client.resources.model.ModelResourceLocation(MODEL,"inventory"));}
 public ResourceLocation getTextureLocation(ThunderSealEntity e){return TextureAtlas.LOCATION_BLOCKS;}
 public void render(ThunderSealEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){
  double age=e.age(partial);int t=e.tier(),finish=ThunderSealRules.finish(t);float fade=(float)Math.min(1,Math.max(0,(ThunderSealRules.lifetime(t)-age)/15));
  double radius=ThunderSealRules.radius(t),growth=Math.min(1,age/18),height=ThunderSealRules.height(t),impact=Math.max(0,age-finish);
  ring(p,buffers,radius,.045,age*.12F,fade*.55F);
  if(t>=4){ring(p,buffers,radius*.78,.065,-age*.08F,fade*.25F);ring(p,buffers,radius,18,age*.05F,fade*.3F);}
  var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(new net.minecraft.client.resources.model.ModelResourceLocation(MODEL,"inventory"));int count=ThunderSealRules.count(t);
  boolean pulse=age>=ThunderSealRules.charge(t)&&age<finish&&((int)(age-ThunderSealRules.charge(t))%12)<5;
  for(int slot=0;slot<count;slot++){
   Vec3 node=e.node(slot,age);p.pushPose();p.translate(node.x,node.y-(1-growth)*height,node.z);p.mulPose(Axis.YP.rotationDegrees((float)(slot*360D/count)));
   float scale=(float)(height/1.75);p.scale(scale,scale,scale);p.translate(-.25,0,-.25);
   mc.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p,buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS)));p.popPose();
   Vec3 tip=node.add(0,height*.85,0);
   if(age>14){Vec3 next=count==1?new Vec3(0,.5,0):e.node((slot+1)%count,age).add(0,height*.85,0);bolt(e,p,buffers,tip,next,age,slot,fade*(pulse?.95F:.35F),pulse?.14:.055);}
   if(pulse)bolt(e,p,buffers,tip,new Vec3(0,.6,0),age,slot+20,fade,.13);
   if(age>=finish-3&&age<finish+14){Vec3 from=t==5?node.add(0,height,0):new Vec3(node.x,24,node.z);bolt(e,p,buffers,from,new Vec3(0,.2,0),age,slot+80,fade*(float)Math.max(0,1-impact/15),t==5?.23:.12);}
  }
  if(age>=finish&&age<finish+18){ring(p,buffers,radius*(1+impact*.08),.1,0,fade*(float)(1-impact/18));if(t==5)bolt(e,p,buffers,new Vec3(0,38,0),new Vec3(0,.1,0),age,300,fade*(float)(1-impact/18),.6);}
 }
 private static void ring(PoseStack p,MultiBufferSource b,double r,double y,double angle,float alpha){p.pushPose();p.translate(0,y,0);p.mulPose(Axis.YP.rotationDegrees((float)angle));var v=b.getBuffer(RenderType.entityTranslucentEmissive(CIRCLE));var m=p.last();vertex(v,m,new Vec3(-r,0,-r),0,0,alpha,150,175,255);vertex(v,m,new Vec3(-r,0,r),0,1,alpha,150,175,255);vertex(v,m,new Vec3(r,0,r),1,1,alpha,150,175,255);vertex(v,m,new Vec3(r,0,-r),1,0,alpha,150,175,255);p.popPose();}
 private static void bolt(ThunderSealEntity e,PoseStack p,MultiBufferSource b,Vec3 from,Vec3 to,double age,int seed,float alpha,double width){
  Vec3 line=to.subtract(from),axis=line.normalize(),camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.position()).subtract(from.add(to).scale(.5));Vec3 side=axis.cross(camera).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);Vec3 up=axis.cross(side).normalize();
  int pieces=Math.min(36,Math.max(6,(int)(line.length()*1.7)));var out=b.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
  for(int layer=0;layer<2;layer++)for(int i=0;i<pieces;i++){
   Vec3 a=point(from,line,side,up,i/(double)pieces,(int)(age/2),seed),z=point(from,line,side,up,(i+1D)/pieces,(int)(age/2),seed);var edge=side.scale(width*(layer==0?2: .5));float opacity=alpha*(layer==0?.28F:1);
   vertex(out,p.last(),a.subtract(edge),0,0,opacity,layer==0?130:220,layer==0?100:245,255);vertex(out,p.last(),z.subtract(edge),1,0,opacity,layer==0?130:220,layer==0?100:245,255);vertex(out,p.last(),z.add(edge),1,1,opacity,layer==0?130:220,layer==0?100:245,255);vertex(out,p.last(),a.add(edge),0,1,opacity,layer==0?130:220,layer==0?100:245,255);
  }
 }
 private static Vec3 point(Vec3 from,Vec3 line,Vec3 side,Vec3 up,double t,int frame,int seed){double envelope=Math.sin(t*Math.PI)*.42,zig=Math.sin(t*87+frame*2.7+seed*4.1);return from.add(line.scale(t)).add(side.scale(zig*envelope)).add(up.scale(Math.cos(t*71-frame*1.9+seed)*envelope*.6));}
 private static void vertex(VertexConsumer v,PoseStack.Pose m,Vec3 point,float u,float w,float a,int r,int g,int b){v.vertex(m.pose(),(float)point.x,(float)point.y,(float)point.z).color(r,g,b,(int)(Math.max(0,Math.min(1,a))*255)).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m.normal(),0,1,0).endVertex();}
}
