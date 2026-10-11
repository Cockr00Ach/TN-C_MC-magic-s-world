package com.tnc.tnc.life.pasture.client;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
/** Two authored skeletons: slender prism antlers, and a low round rolling otter. */
public final class RoutePastureModels {
    public static LayerDefinition antelope(){var mesh=new MeshDefinition();var root=mesh.getRoot();var body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-4,-4,-7,8,8,16),PartPose.offset(0,12,1));
        var head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,26).addBox(-3,-3,-6,6,6,8).texOffs(28,26).addBox(-2,-1,-10,4,3,5),PartPose.offset(0,7,-7));
        head.addOrReplaceChild("horn_l",CubeListBuilder.create().texOffs(48,0).addBox(-1,-12,-1,2,12,2).texOffs(48,16).addBox(-4,-9,-1,4,2,2),PartPose.offsetAndRotation(-2,-3,0,-.15f,0,-.22f));
        head.addOrReplaceChild("horn_r",CubeListBuilder.create().texOffs(56,0).addBox(-1,-12,-1,2,12,2).texOffs(48,22).addBox(0,-9,-1,4,2,2),PartPose.offsetAndRotation(2,-3,0,-.15f,0,.22f));
        head.addOrReplaceChild("ear_l",CubeListBuilder.create().texOffs(36,38).addBox(-4,-1,-1,4,2,3),PartPose.offset(-2,-2,1));head.addOrReplaceChild("ear_r",CubeListBuilder.create().texOffs(36,38).addBox(0,-1,-1,4,2,3),PartPose.offset(2,-2,1));
        leg(root,"leg_fl",-3,16,-4,8);leg(root,"leg_fr",3,16,-4,8);leg(root,"leg_bl",-3,16,7,8);leg(root,"leg_br",3,16,7,8);body.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(30,44).addBox(-1,0,0,2,2,6),PartPose.offset(0,-1,8));return LayerDefinition.create(mesh,64,64);}
    public static LayerDefinition otter(){var mesh=new MeshDefinition();var root=mesh.getRoot();var body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-7,-5,-7,14,10,15).texOffs(0,40).addBox(-5,3,-5,10,2,11),PartPose.offset(0,15,2));
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,26).addBox(-4,-3,-5,8,6,6).texOffs(28,26).addBox(-3,-1,-7,6,3,3).texOffs(38,32).addBox(-4,-5,-1,2,3,2).texOffs(38,32).addBox(2,-5,-1,2,3,2),PartPose.offset(0,15,-6));
        leg(root,"leg_fl",-5,20,-3,4);leg(root,"leg_fr",5,20,-3,4);leg(root,"leg_bl",-5,20,7,4);leg(root,"leg_br",5,20,7,4);body.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(34,42).addBox(-2,-1,0,4,3,10),PartPose.offset(0,2,8));return LayerDefinition.create(mesh,64,64);}
    private static void leg(PartDefinition root,String name,int x,int y,int z,int h){root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(48,32).addBox(-1,0,-1,2,h,2).texOffs(54,40).addBox(-1,h-2,-2,2,2,3),PartPose.offset(x,y,z));}
    public static String[][] bones(boolean antelope){return antelope?new String[][]{{"body","root"},{"head","root"},{"horn_l","head"},{"horn_r","head"},{"ear_l","head"},{"ear_r","head"},{"leg_fl","root"},{"leg_fr","root"},{"leg_bl","root"},{"leg_br","root"},{"tail","body"}}:new String[][]{{"body","root"},{"head","root"},{"leg_fl","root"},{"leg_fr","root"},{"leg_bl","root"},{"leg_br","root"},{"tail","body"}};}
}
