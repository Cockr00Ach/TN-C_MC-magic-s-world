package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNThunderOrbEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 环绕雷球的模型 —— <b>照着 {@code assets/tnc/models/projectile/lightingball.json} 的 7 个方块</b>
 * 用代码搭出来的（作者要求"用我们的实体雷球" ✓）。
 *
 * <h2>为什么是"代码搭"而不是读那个 JSON</h2>
 * 那个 JSON 是**投射物模型**（原版方块/物品模型格式，{@code elements}），而**实体**必须用
 * {@code ModelPart} 骨架 ✗ —— 两套系统不通用。所以把这 7 个方块的坐标原样搬过来：
 * <pre>
 *   核心   [6,8,7]  → [10,12,11]   4x4x4（球心在 8,10,9）
 *   六面各一块 1~2 单位的"尖"：西/东/上/下/北/南
 * </pre>
 *
 * <p><b>坐标换算</b>（很关键，写错球就会偏）：
 * <ul>
 *   <li>模型空间里 <b>+Y 朝下</b>（和 JSON 的 +Y 朝上相反 ✗）→ {@code modelY = -(jsonY - 10)}；</li>
 *   <li>球心在 JSON 的 (8,10,9) → 减去它，球就正好居中在实体原点 ✓；</li>
 *   <li>1 单位 = 1/16 格 ✓ 与 JSON 一致，所以数字直接搬。</li>
 * </ul>
 *
 * <p><b>UV 故意不较真</b>：{@code lightingball.png} 是**纯色**贴图（64×64 只有 1 种不透明颜色 ✓），
 * 所以 texOffs 取哪个位置画出来都一样 ✓；换成带图案的贴图（如 {@code thunder_ball.png}）时
 * 需要按 JSON 里的 per-face uv 重新算 ✗（那时建议直接用 GeckoLib + bedrock 模型）。
 */
public class TNThunderOrbModel extends EntityModel<TNThunderOrbEntity> {

    /** 模型层（客户端在 RegisterLayerDefinitions 里烘焙）。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "thunder_orb"), "main");

    private final ModelPart root;

    public TNThunderOrbModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 坐标 = lightingball.json 的 from/to（球心 8,10,9 平移到原点）
        addBox(root, "core", 6, 8, 7, 4, 4, 4, 0, 0);
        addBox(root, "west", 5, 9, 8, 1, 2, 2, 0, 4);
        addBox(root, "east", 10, 9, 8, 1, 2, 2, 8, 8);
        addBox(root, "up", 7, 12, 8, 2, 1, 2, 0, 12);
        addBox(root, "down", 7, 7, 8, 2, 1, 2, 12, 11);
        addBox(root, "north", 7, 9, 6, 2, 2, 1, 10, 8);
        addBox(root, "south", 7, 9, 11, 2, 2, 1, 4, 8);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** JSON 里的 (x,y,z,w,h,d) + 贴图原点 → model 空间的立方体。 */
    private static void addBox(PartDefinition root, String name,
                               int x, int y, int z, int w, int h, int d, int u, int v) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(
                        x - 8.0F,                 // 球心 x = 8
                        -(y + h - 10.0F),         // 球心 y = 10，且模型 +Y 朝下
                        z - 9.0F,                 // 球心 z = 9
                        w, h, d),
                PartPose.ZERO);
    }

    @Override
    public void setupAnim(TNThunderOrbEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 不需要骨骼动画：球本身在自转由渲染器做（见 TNThunderOrbRenderer）✓
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
