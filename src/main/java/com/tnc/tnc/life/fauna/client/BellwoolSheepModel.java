package com.tnc.tnc.life.fauna.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.fauna.BellwoolSheepEntity;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Complete body geometry for the separate species, not a vanilla sheep layer. */
public final class BellwoolSheepModel extends QuadrupedModel<BellwoolSheepEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "bellwool_sheep"), "main");
    private final ModelPart fleece;
    private final ModelPart bell;
    private final ModelPart tail;

    public BellwoolSheepModel(ModelPart root) {
        super(root, false, 8.0F, 4.0F, 2.0F, 2.0F, 24);
        fleece = body.getChild("fleece");
        bell = head.getChild("bell");
        tail = body.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -4.0F, -5.0F, 7.0F, 7.0F, 7.0F)
                        .texOffs(32, 0).addBox(-5.5F, -5.5F, -2.0F, 2.0F, 4.0F, 4.0F)
                        .texOffs(32, 0).addBox(-6.5F, -7.0F, -1.0F, 2.0F, 3.0F, 3.0F)
                        .texOffs(32, 0).addBox(3.5F, -5.5F, -2.0F, 2.0F, 4.0F, 4.0F)
                        .texOffs(32, 0).addBox(4.5F, -7.0F, -1.0F, 2.0F, 3.0F, 3.0F)
                        .texOffs(32, 0).addBox(-3.8F, 1.0F, -3.0F, 7.6F, 2.0F, 5.0F),
                PartPose.offset(0.0F, 9.0F, -8.0F));
        head.addOrReplaceChild("bell", CubeListBuilder.create()
                .texOffs(52, 42).addBox(-1.5F, 2.0F, -5.5F, 3.0F, 3.0F, 3.0F)
                .texOffs(32, 0).addBox(-0.5F, 4.0F, -4.5F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 18)
                        .addBox(-5.0F, -5.0F, -8.0F, 10.0F, 10.0F, 16.0F),
                PartPose.offset(0.0F, 10.0F, 1.0F));
        body.addOrReplaceChild("fleece", CubeListBuilder.create()
                .texOffs(0, 47).addBox(-5.2F, -6.5F, -7.0F, 10.4F, 2.0F, 3.0F)
                .texOffs(0, 47).addBox(-5.5F, -6.8F, -1.0F, 11.0F, 2.0F, 3.0F)
                .texOffs(0, 47).addBox(-5.2F, -6.5F, 5.0F, 10.4F, 2.0F, 3.0F)
                .texOffs(0, 46).addBox(-5.8F, -1.0F, -7.0F, 1.0F, 4.0F, 14.0F)
                .texOffs(0, 46).addBox(4.8F, -1.0F, -7.0F, 1.0F, 4.0F, 14.0F), PartPose.ZERO);
        body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(0, 47).addBox(-1.5F, -2.0F, 0.0F, 3.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, -1.0F, 8.0F));
        CubeListBuilder legs = CubeListBuilder.create().texOffs(52, 18)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F);
        root.addOrReplaceChild("right_hind_leg", legs, PartPose.offset(-3.5F, 17.0F, 6.0F));
        root.addOrReplaceChild("left_hind_leg", legs, PartPose.offset(3.5F, 17.0F, 6.0F));
        root.addOrReplaceChild("right_front_leg", legs, PartPose.offset(-3.5F, 17.0F, -5.0F));
        root.addOrReplaceChild("left_front_leg", legs, PartPose.offset(3.5F, 17.0F, -5.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override public void setupAnim(BellwoolSheepEntity sheep, float limbSwing, float limbSwingAmount,
                                    float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(sheep, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        head.xRot += sheep.grazingPitch(ageInTicks - sheep.tickCount)
                + sheep.nuzzlePitch(ageInTicks - sheep.tickCount);
        fleece.visible = !sheep.isSheared();
        float sway = Mth.sin(ageInTicks * 0.35F) * (0.08F + Math.min(0.25F, limbSwingAmount * 0.35F));
        bell.zRot = sway;
        tail.xRot = Mth.sin(ageInTicks * 0.12F) * 0.09F;
    }
}
