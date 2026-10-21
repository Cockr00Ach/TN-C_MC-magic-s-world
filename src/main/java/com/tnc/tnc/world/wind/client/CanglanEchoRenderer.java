package com.tnc.tnc.world.wind.client;
import com.tnc.tnc.world.wind.CanglanEchoEntity;
import com.tnc.tnc.client.WindConstructModels;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.cache.object.GeoBone;
public final class CanglanEchoRenderer extends GeoEntityRenderer<CanglanEchoEntity> {
 public CanglanEchoRenderer(EntityRendererProvider.Context c){super(c,new Model());shadowRadius=0;}
 public RenderType getRenderType(CanglanEchoEntity e,ResourceLocation t,MultiBufferSource b,float p){return RenderType.entityTranslucent(t);}
 public Color getRenderColor(CanglanEchoEntity e,float p,int light){return Color.ofRGBA(.62F,.94F,1F,.30F);}
 protected void applyRotations(CanglanEchoEntity e,PoseStack p,float age,float yaw,float partial){p.mulPose(Axis.YP.rotationDegrees(180-e.getYRot()));}
 public void render(CanglanEchoEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
  // Two counter-wound threads form a segmented wind-magic chain, rendered without packets per segment.
  var link=e.link();double len=link.length();if(len>.1){var out=b.getBuffer(RenderType.lines());var matrix=p.last();
   int n=Math.min(64,(int)(len*3)+8);for(int strand=0;strand<2;strand++)for(int i=0;i<n;i++){
    double a=i/(double)n,c=(i+1)/(double)n;double phase=(e.tickCount+partial)*.12+strand*Math.PI;
    var x=link.scale(a).add(0,.18+Math.sin(a*len*2+phase)*.10,Math.cos(a*len*2+phase)*.10);
    var y=link.scale(c).add(0,.18+Math.sin(c*len*2+phase)*.10,Math.cos(c*len*2+phase)*.10);
    var d=y.subtract(x).normalize();out.vertex(matrix.pose(),(float)x.x,(float)x.y,(float)x.z).color(130,235,240,180).normal(matrix.normal(),(float)d.x,(float)d.y,(float)d.z).endVertex();
    out.vertex(matrix.pose(),(float)y.x,(float)y.y,(float)y.z).color(180,250,255,180).normal(matrix.normal(),(float)d.x,(float)d.y,(float)d.z).endVertex();
   }
  }
  p.pushPose();float scale=e.visualScale();p.scale(scale,scale,scale);super.render(e,yaw,partial,p,b,LightTexture.FULL_BRIGHT);p.popPose();
 }
 public void renderRecursively(PoseStack p,CanglanEchoEntity e,GeoBone bone,RenderType type,MultiBufferSource b,VertexConsumer out,boolean re,float partial,int light,int overlay,float red,float green,float blue,float alpha){
  super.renderRecursively(p,e,bone,type,b,out,re,partial,LightTexture.FULL_BRIGHT,overlay,red,green,blue,alpha);
  if(!re&&bone.getName().equals("weapon_1")){boolean spear=e.visualScale()>1;float size=spear?2.4F:2.1F;p.pushPose();WindConstructModels.atBone(p,bone);p.mulPose(Axis.ZP.rotationDegrees(spear?4:-8));p.mulPose(Axis.XP.rotationDegrees(spear?-80:-76));p.translate(0,0,spear?.55:.25);p.scale(size,size,size);WindConstructModels.phantomWeapon(spear?2:1,e.tickCount+partial,p,b,.45F,false);p.popPose();}
 }
 private static final class Model extends GeoModel<CanglanEchoEntity>{
  private ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("tnc",s);}
  public ResourceLocation getModelResource(CanglanEchoEntity e){return id("geo/entity/canglan_echo.geo.json");}
  public ResourceLocation getTextureResource(CanglanEchoEntity e){return id("textures/entity/canglan_warlord.png");}
  public ResourceLocation getAnimationResource(CanglanEchoEntity e){return id("animations/entity/canglan_warlord.animation.json");}
 }
}
