package com.tnc.tnc.arcana.client;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
/** Three actual worn silhouettes: coiled thorns, overlapping moss plates, feeding flower mantle. */
public final class ArcanaArmorModels {
 private static final Map<String,HumanoidModel<LivingEntity>> MODELS=new HashMap<>();
 @SuppressWarnings({"rawtypes","unchecked"}) public static HumanoidModel<?> get(String id,HumanoidModel<?> base){var m=MODELS.computeIfAbsent(id,ArcanaArmorModels::build);((HumanoidModel)base).copyPropertiesTo(m);return m;}
 private static HumanoidModel<LivingEntity> build(String id){var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var b=mesh.getRoot().addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-4.6f,-.5f,-2.6f,9.2f,13,5.2f),PartPose.ZERO);
  if(id.equals("thorn_tyrant_vine")){for(int i=0;i<6;i++)b.addOrReplaceChild("vine"+i,CubeListBuilder.create().texOffs(0,0).addBox(-.7f,-7,-.7f,1.4f,10,1.4f).addBox(-1.5f,-6,-1,3,1,2),PartPose.offsetAndRotation(i%2==0?-4.5f:4.5f,3+(i/2)*4,2.5f, .2f,(i%2==0?1:-1)*.35f,(i%2==0?-1:1)*(.4f+i*.06f)));}
  else if(id.equals("emerald_lifemoss")){for(int i=0;i<6;i++)b.addOrReplaceChild("moss"+i,CubeListBuilder.create().texOffs(0,0).addBox(-2.7f,-1,-.5f,5.4f,5,1),PartPose.offsetAndRotation(i%2==0?-2:2,1+(i/2)*3.5f,-3,.08f,i%2==0?-.12f:.12f,i%2==0?-.15f:.15f));}
  else{for(int i=0;i<5;i++){float a=(float)(i*Math.PI*2/5);b.addOrReplaceChild("petal"+i,CubeListBuilder.create().texOffs(0,0).addBox(-2,-5,-1,4,9,2),PartPose.offsetAndRotation((float)Math.cos(a)*4,2+(float)Math.sin(a)*3,4.5f,.35f,0,a));}}
  b.addOrReplaceChild("skirt",CubeListBuilder.create().texOffs(0,0).addBox(-4.4f,0,-2.7f,8.8f,8,5.4f),PartPose.offset(0,11,0));
  return new HumanoidModel<>(LayerDefinition.create(mesh,16,16).bakeRoot());
 }
}
