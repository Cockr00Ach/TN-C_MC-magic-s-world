package com.tnc.tnc.life.routes;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
/** Compact shoulders, a blue-green chest cell and a physical back pack. */
public final class RouteMechModel {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","mana_mech"),"armor");
    public static LayerDefinition layer(){var mesh=net.minecraft.client.model.HumanoidModel.createMesh(new CubeDeformation(.6f),0);var root=mesh.getRoot();var body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(16,16).addBox(-4,0,-2,8,12,4,new CubeDeformation(.6f)).texOffs(0,0).addBox(-3,2,-3.5f,6,6,1).texOffs(32,0).addBox(-4,1,2,8,10,4).texOffs(0,9).addBox(-1,1,6,2,9,2),PartPose.ZERO);root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(40,16).addBox(-3,-2,-2,4,12,4,new CubeDeformation(.35f)).texOffs(40,0).addBox(-4,-3,-3,6,4,6),PartPose.offset(-5,2,0));root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(40,16).mirror().addBox(-1,-2,-2,4,12,4,new CubeDeformation(.35f)).texOffs(40,0).addBox(-2,-3,-3,6,4,6),PartPose.offset(5,2,0));return LayerDefinition.create(mesh,64,32);}
}
