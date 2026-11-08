package com.tnc.tnc.fish.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Sixteen structural cubes, one animated tail; no sword handles or baked frame copies. */
public final class FishSchoolModel {
    private final ModelPart root,tail;
    public FishSchoolModel(ModelPart root) { this.root=root;tail=root.getChild("tail"); }
    public static LayerDefinition layer() {
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-2,-2.5f,-3,4,5,7),PartPose.ZERO);
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,14).addBox(-2,-2,4,4,4,3),PartPose.ZERO);
        root.addOrReplaceChild("nose",CubeListBuilder.create().texOffs(18,14).addBox(-1.5f,-1.5f,7,3,3,1),PartPose.ZERO);
        PartDefinition tail=root.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(0,24).addBox(-.9f,-1.4f,-3,1.8f,2.8f,3),PartPose.offset(0,0,-3));
        tail.addOrReplaceChild("upper_lobe",CubeListBuilder.create().texOffs(16,24).addBox(-.35f,-3.5f,-5,.7f,3.8f,3.5f),PartPose.rotation(-.25f,0,0));
        tail.addOrReplaceChild("lower_lobe",CubeListBuilder.create().texOffs(16,24).addBox(-.35f,-.3f,-5,.7f,3.8f,3.5f),PartPose.rotation(.25f,0,0));
        root.addOrReplaceChild("dorsal",CubeListBuilder.create().texOffs(32,16).addBox(-.2f,-5,-2,.4f,3,5),PartPose.ZERO);
        root.addOrReplaceChild("belly",CubeListBuilder.create().texOffs(32,16).addBox(-.2f,2,-1,.4f,2,3),PartPose.ZERO);
        for (int side:new int[]{-1,1}) {
            root.addOrReplaceChild("fin"+side,CubeListBuilder.create().texOffs(32,24).addBox(side<0?-2.7f:1.9f,0,0,.8f,2.8f,3),PartPose.rotation(0,0,side*-.35f));
            root.addOrReplaceChild("eye_frame"+side,CubeListBuilder.create().texOffs(48,0).addBox(side<0?-2.35f:1.85f,-1.5f,5,.5f,1.5f,1.5f),PartPose.ZERO);
            root.addOrReplaceChild("eye"+side,CubeListBuilder.create().texOffs(56,0).addBox(side<0?-2.48f:2.35f,-1.15f,5.25f,.15f,.9f,.9f),PartPose.ZERO);
            root.addOrReplaceChild("gill"+side,CubeListBuilder.create().texOffs(48,8).addBox(side<0?-2.08f:2,-1.7f,3.25f,.08f,3.3f,.3f),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,64,64);
    }
    public void render(PoseStack pose,VertexConsumer vertices,int light,int overlay,double age,int lane,boolean metal) {
        tail.yRot=(float)Math.sin(age*.85+lane*1.3)*(metal?.12f:.35f);
        root.render(pose,vertices,light,overlay,1,1,1,1);
    }
}
