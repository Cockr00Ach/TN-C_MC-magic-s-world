package com.tnc.tnc.npc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

import com.tnc.tnc.npc.SelfNpcEntity;

/**
 * Self 的模型：<b>直接用原版的人形模型</b>（{@link HumanoidModel}）。
 *
 * <p>因为外观走的是"64x64 皮肤贴图"这条路（用户 2026-09-21 定的方案），
 * 而原版人形模型就是给这种皮肤用的 —— 所以我们<b>不需要建模</b>，
 * 只要把原版骨架烘焙出来、套上我们自己的皮肤即可。
 *
 * <p>骨架尺寸用的是原版玩家/盔甲架的默认值（head 8 / body 8x12x4 / 四肢 4x12x4，
 * 即经典 4 像素手臂模型）。皮肤贴图 {@code assets/tnc/textures/entity/self.png}
 * 按同一套 UV 布局绘制，见 {@code tools/gen_self_skin.ps1}。
 */
public class SelfNpcModel extends HumanoidModel<SelfNpcEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "self"), "main");

    public SelfNpcModel(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    /**
     * 原版人形骨架（数值取自 HumanoidModel.createMesh 的经典尺寸）。
     *
     * <p>返回 {@link net.minecraft.client.model.geom.builders.LayerDefinition} 而不是 ModelPart ——
     * {@code RegisterLayerDefinitions#registerLayerDefinition} 收的正是
     * {@code Supplier<LayerDefinition>}（读字节码确认，不是猜的）。
     */
    public static net.minecraft.client.model.geom.builders.LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        CubeListBuilder rightArm = CubeListBuilder.create().texOffs(40, 16)
                .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F));
        root.addOrReplaceChild("right_arm", rightArm, PartPose.offset(-5.0F, 2.0F, 0.0F));

        CubeListBuilder leftArm = CubeListBuilder.create().texOffs(40, 16).mirror()
                .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F));
        root.addOrReplaceChild("left_arm", leftArm, PartPose.offset(5.0F, 2.0F, 0.0F));

        CubeListBuilder rightLeg = CubeListBuilder.create().texOffs(0, 16)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F));
        root.addOrReplaceChild("right_leg", rightLeg, PartPose.offset(-1.9F, 12.0F, 0.0F));

        CubeListBuilder leftLeg = CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F));
        root.addOrReplaceChild("left_leg", leftLeg, PartPose.offset(1.9F, 12.0F, 0.0F));

        // 皮肤是 64x64（见 tools/gen_self_skin.ps1）
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64);
    }
}
