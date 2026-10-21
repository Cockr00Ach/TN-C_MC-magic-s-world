package com.tnc.tnc.arcana.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
public final class ArcanaEntityModel<T extends LivingEntity> extends EntityModel<T>{
    public final ModelPart root;private final boolean spirit;
    public ArcanaEntityModel(ModelPart root,boolean spirit){this.root=root;this.spirit=spirit;}
    public static LayerDefinition layer(boolean spirit){var mesh=new MeshDefinition();var r=mesh.getRoot();
        r.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(spirit?-4:-8,spirit?-18:-22,-7,spirit?8:16,spirit?8:14,14),PartPose.offset(0,24,0));
        r.addOrReplaceChild("eye",CubeListBuilder.create().texOffs(0,0).addBox(spirit?-2:-5,spirit?-16:-19,-8,spirit?4:10,spirit?4:5,2),PartPose.offset(0,24,0));
        if(spirit){for(int k=0;k<3;k++){var ring=r.addOrReplaceChild("ring"+k,CubeListBuilder.create(),PartPose.offsetAndRotation(0,10,0,k*.7f,k*.6f,0));for(int i=0;i<12;i++){float a=(float)(i*Math.PI/6);ring.addOrReplaceChild("segment"+i,CubeListBuilder.create().texOffs(0,0).addBox(-1,-1,-2,2,2,4),PartPose.offsetAndRotation((float)Math.cos(a)*(10+k*2),(float)Math.sin(a)*(10+k*2),0,0,0,a));}}}
        else{r.addOrReplaceChild("jaw",CubeListBuilder.create().texOffs(0,0).addBox(-7,-11,-10,14,3,7),PartPose.offset(0,24,0));for(int i=0;i<6;i++){float a=(float)(i*Math.PI/3);r.addOrReplaceChild("limb"+i,CubeListBuilder.create().texOffs(0,0).addBox(-1,0,-1,3,16,3).addBox(-2,13,-3,5,3,6),PartPose.offsetAndRotation((float)Math.cos(a)*7,12,(float)Math.sin(a)*6,.2f,0,(float)Math.sin(a)*.35f));}}
        return LayerDefinition.create(mesh,16,16);
    }
    @Override public void setupAnim(T entity,float limb,float amount,float age,float head,float pitch){root.getAllParts().forEach(ModelPart::resetPose);if(spirit)for(int i=0;i<3;i++){var ring=root.getChild("ring"+i);ring.yRot+=age*.018f*(i%2==0?1:-1);ring.zRot+=(float)Math.sin(age*.023+i)*.4f;}else for(int i=0;i<6;i++){root.getChild("limb"+i).xRot+=(float)Math.sin(age*.09+i)*.35f;root.getChild("limb"+i).zRot+=(float)Math.cos(age*.065+i)*.18f;}}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float r,float g,float b,float alpha){root.render(pose,buffer,light,overlay,r,g,b,alpha);}
}
