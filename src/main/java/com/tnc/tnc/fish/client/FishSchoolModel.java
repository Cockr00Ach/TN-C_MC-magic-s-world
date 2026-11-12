package com.tnc.tnc.fish.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Generated from model-source/fish-school/detail-spec.json; texture density is 8 texels/model unit. */
public final class FishSchoolModel {
    private final ModelPart root,tail;
    public FishSchoolModel(ModelPart root) { this.root=root;tail=root.getChild("tail"); }
    public static LayerDefinition layer(boolean metal) {
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        PartDefinition tail=root.addOrReplaceChild("tail",CubeListBuilder.create(),PartPose.offset(0,0,-24));
        root.addOrReplaceChild("streamlined_core",CubeListBuilder.create().texOffs(2,2).addBox(-11.6f,-15.6f,-24.8f,23.2f,31.2f,49.6f),PartPose.offsetAndRotation(0f,0f,0f,0f,0f,0f));
        root.addOrReplaceChild("shoulders",CubeListBuilder.create().texOffs(152,2).addBox(-13.2f,-15.2f,-14.4f,26.4f,30.4f,28.8f),PartPose.offsetAndRotation(0f,-0.8f,22.4f,0f,0f,0f));
        root.addOrReplaceChild("tapered_rear",CubeListBuilder.create().texOffs(267,2).addBox(-8.4f,-11.2f,-10f,16.8f,22.4f,20f),PartPose.offsetAndRotation(0f,0f,-24f,0f,0f,0f));
        root.addOrReplaceChild("belly",CubeListBuilder.create().texOffs(345,2).addBox(-10f,-2.8f,-21.6f,20f,5.6f,43.2f),PartPose.offsetAndRotation(0f,13.2f,1.6f,0f,0f,0f));
        root.addOrReplaceChild("flank_bevel_-1_-1",CubeListBuilder.create().texOffs(2,87).addBox(-2.6f,-4.8f,-23.2f,5.2f,9.6f,46.4f),PartPose.offsetAndRotation(-10.8f,-11.2f,0.8f,0f,0f,-0.392699f));
        root.addOrReplaceChild("flank_bevel_-1_1",CubeListBuilder.create().texOffs(110,87).addBox(-2.6f,-4.8f,-23.2f,5.2f,9.6f,46.4f),PartPose.offsetAndRotation(-10.8f,11.2f,0.8f,0f,0f,0.392699f));
        root.addOrReplaceChild("flank_bevel_1_-1",CubeListBuilder.create().texOffs(218,87).addBox(-2.6f,-4.8f,-23.2f,5.2f,9.6f,46.4f),PartPose.offsetAndRotation(10.8f,-11.2f,0.8f,0f,0f,0.392699f));
        root.addOrReplaceChild("flank_bevel_1_1",CubeListBuilder.create().texOffs(326,87).addBox(-2.6f,-4.8f,-23.2f,5.2f,9.6f,46.4f),PartPose.offsetAndRotation(10.8f,11.2f,0.8f,0f,0f,-0.392699f));
        root.addOrReplaceChild("cheeks",CubeListBuilder.create().texOffs(2,147).addBox(-11.6f,-12.6f,-10f,23.2f,25.2f,20f),PartPose.offsetAndRotation(0f,-1.2f,37.2f,0f,0f,0f));
        root.addOrReplaceChild("upper_snout",CubeListBuilder.create().texOffs(93,147).addBox(-9.2f,-9.4f,-6f,18.4f,18.8f,12f),PartPose.offsetAndRotation(0f,-2f,48.8f,0f,0f,0f));
        root.addOrReplaceChild("snout_bevel",CubeListBuilder.create().texOffs(158,147).addBox(-6.6f,-7.4f,-6.6f,13.2f,14.8f,13.2f),PartPose.offsetAndRotation(0f,-2f,54.8f,0f,0.785398f,0f));
        root.addOrReplaceChild("lower_jaw",CubeListBuilder.create().texOffs(215,147).addBox(-8.4f,-1.8f,-8.8f,16.8f,3.6f,17.6f),PartPose.offsetAndRotation(0f,8f,46.8f,0f,0f,0f));
        root.addOrReplaceChild("mouth_-1",CubeListBuilder.create().texOffs(288,147).addBox(-0.32f,-0.48f,-6.8f,0.64f,0.96f,13.6f),PartPose.offsetAndRotation(-9.36f,4.4f,48.8f,0f,0f,0f));
        root.addOrReplaceChild("gill_cover_-1",CubeListBuilder.create().texOffs(321,147).addBox(-1.4f,-11.2f,-5.6f,2.8f,22.4f,11.2f),PartPose.offsetAndRotation(-12.4f,0.4f,29.6f,-0.139626f,0f,0f));
        root.addOrReplaceChild("gill_slit_-1_0",CubeListBuilder.create().texOffs(353,147).addBox(-0.32f,-7.4f,-0.4f,0.64f,14.8f,0.8f),PartPose.offsetAndRotation(-14.12f,1.2f,26.4f,-0.20944f,0f,0f));
        root.addOrReplaceChild("gill_slit_-1_1",CubeListBuilder.create().texOffs(360,147).addBox(-0.32f,-7.4f,-0.4f,0.64f,14.8f,0.8f),PartPose.offsetAndRotation(-14.12f,1.2f,29.04f,-0.20944f,0f,0f));
        root.addOrReplaceChild("gill_slit_-1_2",CubeListBuilder.create().texOffs(367,147).addBox(-0.32f,-7.4f,-0.4f,0.64f,14.8f,0.8f),PartPose.offsetAndRotation(-14.12f,1.2f,31.68f,-0.20944f,0f,0f));
        root.addOrReplaceChild("pectoral_-1",CubeListBuilder.create().texOffs(374,147).addBox(-1.6f,-10.4f,-8.8f,3.2f,20.8f,17.6f),PartPose.offsetAndRotation(-15.2f,8f,14.4f,0f,-0.20944f,0.349066f));
        root.addOrReplaceChild("pelvic_-1",CubeListBuilder.create().texOffs(420,147).addBox(-1f,-6.8f,-7.6f,2f,13.6f,15.2f),PartPose.offsetAndRotation(-6.4f,16.8f,-10.4f,-0.261799f,0f,-0.261799f));
        if (!metal) {
        root.addOrReplaceChild("eye_socket_-1",CubeListBuilder.create().texOffs(459,147).addBox(-1.28f,-6.8f,-6.8f,2.56f,13.6f,13.6f),PartPose.offsetAndRotation(-12.4f,-4.8f,40.4f,0f,0f,0f));
        }
        if (!metal) {
        root.addOrReplaceChild("eye_white_-1",CubeListBuilder.create().texOffs(2,197).addBox(-0.88f,-5.6f,-5.6f,1.76f,11.2f,11.2f),PartPose.offsetAndRotation(-14.16f,-4.8f,40.4f,0f,0f,0f));
        }
        if (!metal) {
        root.addOrReplaceChild("eye_pupil_-1",CubeListBuilder.create().texOffs(32,197).addBox(-0.48f,-3.6f,-3.28f,0.96f,7.2f,6.56f),PartPose.offsetAndRotation(-15.28f,-4.4f,42.24f,0f,0f,0f));
        }
        if (!metal) {
        root.addOrReplaceChild("eye_glint_-1",CubeListBuilder.create().texOffs(52,197).addBox(-0.32f,-1.08f,-1.08f,0.64f,2.16f,2.16f),PartPose.offsetAndRotation(-16f,-6.8f,44f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("eye_bezel_-1",CubeListBuilder.create().texOffs(62,197).addBox(-1.52f,-5.6f,-5.6f,3.04f,11.2f,11.2f),PartPose.offsetAndRotation(-13.04f,-4.8f,40.4f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("eye_recess_-1",CubeListBuilder.create().texOffs(95,197).addBox(-0.52f,-4.48f,-4.48f,1.04f,8.96f,8.96f),PartPose.offsetAndRotation(-14.88f,-4.8f,40.4f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("eye_crystal_-1",CubeListBuilder.create().texOffs(119,197).addBox(-0.68f,-3.32f,-3.32f,1.36f,6.64f,6.64f),PartPose.offsetAndRotation(-15.76f,-4.8f,40.4f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("crystal_glint_-1",CubeListBuilder.create().texOffs(139,197).addBox(-0.36f,-1f,-1.4f,0.72f,2f,2.8f),PartPose.offsetAndRotation(-16.64f,-6.8f,40.96f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_-1_0",CubeListBuilder.create().texOffs(151,197).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(-12.96f,-0.4f,13.6f,-0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_-1_0",CubeListBuilder.create().texOffs(178,197).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(-14.24f,-7.2f,15.2f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_-1_1",CubeListBuilder.create().texOffs(188,197).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(-12.96f,-0.4f,4f,-0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_-1_1",CubeListBuilder.create().texOffs(215,197).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(-14.24f,-7.2f,5.6f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_-1_2",CubeListBuilder.create().texOffs(225,197).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(-12.96f,-0.4f,-5.6f,-0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_-1_2",CubeListBuilder.create().texOffs(252,197).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(-14.24f,-7.2f,-4f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_-1_3",CubeListBuilder.create().texOffs(262,197).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(-12.96f,-0.4f,-15.2f,-0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_-1_3",CubeListBuilder.create().texOffs(289,197).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(-14.24f,-7.2f,-13.6f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("operculum_rim_-1",CubeListBuilder.create().texOffs(299,197).addBox(-0.48f,-0.72f,-5.6f,0.96f,1.44f,11.2f),PartPose.offsetAndRotation(-14.08f,-9.2f,30.8f,0f,0f,0f));
        }
        root.addOrReplaceChild("mouth_1",CubeListBuilder.create().texOffs(328,197).addBox(-0.32f,-0.48f,-6.8f,0.64f,0.96f,13.6f),PartPose.offsetAndRotation(9.36f,4.4f,48.8f,0f,0f,0f));
        root.addOrReplaceChild("gill_cover_1",CubeListBuilder.create().texOffs(361,197).addBox(-1.4f,-11.2f,-5.6f,2.8f,22.4f,11.2f),PartPose.offsetAndRotation(12.4f,0.4f,29.6f,0.139626f,0f,0f));
        root.addOrReplaceChild("gill_slit_1_0",CubeListBuilder.create().texOffs(393,197).addBox(-0.32f,-7.4f,-0.4f,0.64f,14.8f,0.8f),PartPose.offsetAndRotation(14.12f,1.2f,26.4f,0.20944f,0f,0f));
        root.addOrReplaceChild("gill_slit_1_1",CubeListBuilder.create().texOffs(400,197).addBox(-0.32f,-7.4f,-0.4f,0.64f,14.8f,0.8f),PartPose.offsetAndRotation(14.12f,1.2f,29.04f,0.20944f,0f,0f));
        root.addOrReplaceChild("gill_slit_1_2",CubeListBuilder.create().texOffs(407,197).addBox(-0.32f,-7.4f,-0.4f,0.64f,14.8f,0.8f),PartPose.offsetAndRotation(14.12f,1.2f,31.68f,0.20944f,0f,0f));
        root.addOrReplaceChild("pectoral_1",CubeListBuilder.create().texOffs(414,197).addBox(-1.6f,-10.4f,-8.8f,3.2f,20.8f,17.6f),PartPose.offsetAndRotation(15.2f,8f,14.4f,0f,0.20944f,-0.349066f));
        root.addOrReplaceChild("pelvic_1",CubeListBuilder.create().texOffs(460,197).addBox(-1f,-6.8f,-7.6f,2f,13.6f,15.2f),PartPose.offsetAndRotation(6.4f,16.8f,-10.4f,-0.261799f,0f,0.261799f));
        if (!metal) {
        root.addOrReplaceChild("eye_socket_1",CubeListBuilder.create().texOffs(2,240).addBox(-1.28f,-6.8f,-6.8f,2.56f,13.6f,13.6f),PartPose.offsetAndRotation(12.4f,-4.8f,40.4f,0f,0f,0f));
        }
        if (!metal) {
        root.addOrReplaceChild("eye_white_1",CubeListBuilder.create().texOffs(39,240).addBox(-0.88f,-5.6f,-5.6f,1.76f,11.2f,11.2f),PartPose.offsetAndRotation(14.16f,-4.8f,40.4f,0f,0f,0f));
        }
        if (!metal) {
        root.addOrReplaceChild("eye_pupil_1",CubeListBuilder.create().texOffs(69,240).addBox(-0.48f,-3.6f,-3.28f,0.96f,7.2f,6.56f),PartPose.offsetAndRotation(15.28f,-4.4f,42.24f,0f,0f,0f));
        }
        if (!metal) {
        root.addOrReplaceChild("eye_glint_1",CubeListBuilder.create().texOffs(89,240).addBox(-0.32f,-1.08f,-1.08f,0.64f,2.16f,2.16f),PartPose.offsetAndRotation(16f,-6.8f,44f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("eye_bezel_1",CubeListBuilder.create().texOffs(99,240).addBox(-1.52f,-5.6f,-5.6f,3.04f,11.2f,11.2f),PartPose.offsetAndRotation(13.04f,-4.8f,40.4f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("eye_recess_1",CubeListBuilder.create().texOffs(132,240).addBox(-0.52f,-4.48f,-4.48f,1.04f,8.96f,8.96f),PartPose.offsetAndRotation(14.88f,-4.8f,40.4f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("eye_crystal_1",CubeListBuilder.create().texOffs(156,240).addBox(-0.68f,-3.32f,-3.32f,1.36f,6.64f,6.64f),PartPose.offsetAndRotation(15.76f,-4.8f,40.4f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("crystal_glint_1",CubeListBuilder.create().texOffs(176,240).addBox(-0.36f,-1f,-1.4f,0.72f,2f,2.8f),PartPose.offsetAndRotation(16.64f,-6.8f,40.96f,0.785398f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_1_0",CubeListBuilder.create().texOffs(188,240).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(12.96f,-0.4f,13.6f,0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_1_0",CubeListBuilder.create().texOffs(215,240).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(14.24f,-7.2f,15.2f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_1_1",CubeListBuilder.create().texOffs(225,240).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(12.96f,-0.4f,4f,0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_1_1",CubeListBuilder.create().texOffs(252,240).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(14.24f,-7.2f,5.6f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_1_2",CubeListBuilder.create().texOffs(262,240).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(12.96f,-0.4f,-5.6f,0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_1_2",CubeListBuilder.create().texOffs(289,240).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(14.24f,-7.2f,-4f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_1_3",CubeListBuilder.create().texOffs(299,240).addBox(-0.92f,-11.2f,-4.6f,1.84f,22.4f,9.2f),PartPose.offsetAndRotation(12.96f,-0.4f,-15.2f,0.069813f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("armor_rivet_1_3",CubeListBuilder.create().texOffs(326,240).addBox(-0.52f,-0.88f,-0.88f,1.04f,1.76f,1.76f),PartPose.offsetAndRotation(14.24f,-7.2f,-13.6f,0f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("operculum_rim_1",CubeListBuilder.create().texOffs(336,240).addBox(-0.48f,-0.72f,-5.6f,0.96f,1.44f,11.2f),PartPose.offsetAndRotation(14.08f,-9.2f,30.8f,0f,0f,0f));
        }
        root.addOrReplaceChild("dorsal_rear",CubeListBuilder.create().texOffs(365,240).addBox(-1.4f,-8.8f,-14.4f,2.8f,17.6f,28.8f),PartPose.offsetAndRotation(0f,-22.4f,-11.6f,-0.314159f,0f,0f));
        root.addOrReplaceChild("dorsal_leading",CubeListBuilder.create().texOffs(433,240).addBox(-1.52f,-7.2f,-7.2f,3.04f,14.4f,14.4f),PartPose.offsetAndRotation(0f,-21.6f,9.6f,0.392699f,0f,0f));
        if (metal) {
        root.addOrReplaceChild("dorsal_metal_edge",CubeListBuilder.create().texOffs(2,291).addBox(-1.68f,-0.8f,-12.8f,3.36f,1.6f,25.6f),PartPose.offsetAndRotation(0f,-31.2f,-12f,-0.314159f,0f,0f));
        }
        if (metal) {
        root.addOrReplaceChild("dorsal_copper_tip",CubeListBuilder.create().texOffs(64,291).addBox(-2f,-1.4f,-2.2f,4f,2.8f,4.4f),PartPose.offsetAndRotation(0f,-32.8f,-20f,0f,0f,0f));
        }
        tail.addOrReplaceChild("tail_peduncle",CubeListBuilder.create().texOffs(85,291).addBox(-5.2f,-7.2f,-11.2f,10.4f,14.4f,22.4f),PartPose.offsetAndRotation(0f,0f,-12f,0f,0f,0f));
        tail.addOrReplaceChild("tail_upper",CubeListBuilder.create().texOffs(155,291).addBox(-1.8f,-10.4f,-10.4f,3.6f,20.8f,20.8f),PartPose.offsetAndRotation(0f,-13.6f,-27.6f,-0.314159f,0f,0f));
        tail.addOrReplaceChild("tail_lower",CubeListBuilder.create().texOffs(208,291).addBox(-1.8f,-10.4f,-10.4f,3.6f,20.8f,20.8f),PartPose.offsetAndRotation(0f,13.6f,-27.6f,0.314159f,0f,0f));
        tail.addOrReplaceChild("tail_upper_tip",CubeListBuilder.create().texOffs(261,291).addBox(-1.6f,-6f,-6f,3.2f,12f,12f),PartPose.offsetAndRotation(0f,-21.6f,-32.8f,-0.488692f,0f,0f));
        tail.addOrReplaceChild("tail_lower_tip",CubeListBuilder.create().texOffs(296,291).addBox(-1.6f,-6f,-6f,3.2f,12f,12f),PartPose.offsetAndRotation(0f,21.6f,-32.8f,0.488692f,0f,0f));
        if (metal) {
        tail.addOrReplaceChild("tail_upper_edge",CubeListBuilder.create().texOffs(331,291).addBox(-2f,-0.8f,-6f,4f,1.6f,12f),PartPose.offsetAndRotation(0f,-25.6f,-33.6f,-0.488692f,0f,0f));
        }
        if (metal) {
        tail.addOrReplaceChild("tail_lower_edge",CubeListBuilder.create().texOffs(367,291).addBox(-2f,-0.8f,-6f,4f,1.6f,12f),PartPose.offsetAndRotation(0f,25.6f,-33.6f,0.488692f,0f,0f));
        }
        if (metal) {
        tail.addOrReplaceChild("tail_socket",CubeListBuilder.create().texOffs(403,291).addBox(-5.6f,-7.8f,-1.8f,11.2f,15.6f,3.6f),PartPose.offsetAndRotation(0f,0f,-17.6f,0f,0f,0f));
        }
        return LayerDefinition.create(mesh,512,512);
    }
    public void render(PoseStack pose,VertexConsumer vertices,int light,int overlay,double age,int lane,boolean metal) {
        tail.yRot=(float)Math.sin(age*.85+lane*1.3)*(metal?.12f:.35f);
        pose.pushPose();pose.scale(.125f,.125f,.125f);
        root.render(pose,vertices,light,overlay,1,1,1,1);
        pose.popPose();
    }
}
