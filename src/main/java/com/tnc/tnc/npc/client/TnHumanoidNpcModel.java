package com.tnc.tnc.npc.client;

import com.tnc.tnc.npc.TnDialogueNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * TN-C 所有"皮肤 NPC"共用的模型：<b>原版人形模型</b>。
 *
 * <p>因为外观走的是"64×64 皮肤贴图"这条路（用户 2026-09-21 定的方案），而原版人形模型
 * 就是给这种皮肤用的 —— 所以<b>不需要建模</b>，只要把原版骨架烘焙出来、套上各自的皮肤即可。
 * 骨架数值用原版玩家/盔甲的默认值（头 8、身 8×12×4、四肢 4×12×4，即经典 4 像素手臂）。
 *
 * <p>所以 Self / cava / riggen <b>共用同一个模型层</b>，区别只有贴图 ——
 * 见 {@link TnNpcRenderer#skinOf(TnDialogueNpc)}。
 */
public class TnHumanoidNpcModel extends HumanoidModel<TnDialogueNpc> {

    /** 所有皮肤 NPC 共用的模型层（形状相同，只有贴图不同）。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(com.tnc.tnc.TNMod.MODID, "npc_humanoid"), "main");

    public TnHumanoidNpcModel(ModelPart root) {
        super(root);
    }

    /**
     * 原版人形骨架（数值取自 HumanoidModel.createMesh 的经典尺寸）。
     *
     * <p>必须返回 {@link LayerDefinition} 而不是 ModelPart ——
     * {@code RegisterLayerDefinitions#registerLayerDefinition} 收的是 {@code Supplier<LayerDefinition>}
     * （读字节码确认，不是猜的）。贴图 64×64。
     */
    public static LayerDefinition createBodyLayer() {
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

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 16).mirror()
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.9F, 12.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 16).mirror()
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
