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
 private org.joml.Matrix4f gripPose;private org.joml.Matrix3f gripNormal;
 public CanglanEchoRenderer(EntityRendererProvider.Context c){super(c,new Model());shadowRadius=0;}
 public RenderType getRenderType(CanglanEchoEntity e,ResourceLocation t,MultiBufferSource b,float p){return RenderType.entityTranslucent(t);}
 public Color getRenderColor(CanglanEchoEntity e,float p,int light){return Color.ofRGBA(.62F,.94F,1F,.65F*e.opacity(p));}
 protected void applyRotations(CanglanEchoEntity e,PoseStack p,float age,float yaw,float partial){p.mulPose(Axis.YP.rotationDegrees(180-e.getYRot()));}
 public void render(CanglanEchoEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
  gripPose=null;gripNormal=null;
  CanglanManaLinks.render(e,partial,p,b);
  p.pushPose();float scale=e.visualScale();p.scale(scale,scale,scale);super.render(e,yaw,partial,p,b,LightTexture.FULL_BRIGHT);
  if(gripPose!=null){boolean spear=true;float size=spear?2.4F:2.1F;p.pushPose();p.last().pose().set(gripPose);p.last().normal().set(gripNormal);p.mulPose(Axis.ZP.rotationDegrees(spear?4:-8));p.mulPose(Axis.XP.rotationDegrees(spear?-80:-76));p.translate(0,0,spear?1.2:1.155);p.scale(size,size,size);WindConstructModels.phantomWeapon(spear?2:1,e.tickCount+partial,p,b,.68F*e.opacity(partial),false);p.popPose();}
  p.popPose();
 }
 public void renderRecursively(PoseStack p,CanglanEchoEntity e,GeoBone bone,RenderType type,MultiBufferSource b,VertexConsumer out,boolean re,float partial,int light,int overlay,float red,float green,float blue,float alpha){
  super.renderRecursively(p,e,bone,type,b,out,re,partial,LightTexture.FULL_BRIGHT,overlay,red,green,blue,alpha);
  if(!re&&bone.getName().equals("weapon_1")){p.pushPose();WindConstructModels.atBone(p,bone);gripPose=new org.joml.Matrix4f(p.last().pose());gripNormal=new org.joml.Matrix3f(p.last().normal());p.popPose();}
 }
 private static final class Model extends GeoModel<CanglanEchoEntity>{
  private ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("tnc",s);}
  public ResourceLocation getModelResource(CanglanEchoEntity e){return id("geo/entity/canglan_warlord.geo.json");}
  public ResourceLocation getTextureResource(CanglanEchoEntity e){return id("textures/entity/canglan_warlord.png");}
  public ResourceLocation getAnimationResource(CanglanEchoEntity e){return id("animations/entity/canglan_warlord.animation.json");}
 }
}
