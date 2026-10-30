package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.client.WindConstructModels;
import com.tnc.tnc.warrior.InstructorEntity;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * 把教官装备槽里的剑/盾画到前臂骨骼上。
 *
 * <p>用 GeckoLib 自带的 {@link BlockAndItemGeoLayer}：引擎先按骨骼矩阵（含当前动画帧）压栈，
 * 再回调这一层，所以武器天然跟着挥砍走。自己往 {@code renderRecursively} 里塞的话，
 * {@code super} 返回时栈里是<b>父骨骼</b>坐标系，武器会钉在肩膀上且完全不跟动画。
 *
 * <p><b>握姿是自己算的，没走原版的 thirdperson 显示变换。</b>原版那套是为"原点在肩膀"的
 * 人形手臂标定的（{@code ItemInHandLayer} 里那串 0.0625/0.125/0.625 的魔数是给整条手臂用的），
 * 直接套到<b>手腕</b>骨骼上，剑会浮在拳头上方约 6 px、后面 3 px —— 一只手长的距离，
 * 看得见。所以这里显式给出：
 *
 * <ul>
 *   <li>沿骨骼局部 -Y 挪 6 px 到拳头（肘 y=18，拳 y=12）；</li>
 *   <li>把物品模型里"握把"那一点（sprite 的 8,1 px）挪到原点，剑才是握在手里而不是插在手背上；</li>
 *   <li>刀身朝<b>上偏前</b>：只绕 X 转 -55°，物品 +Y → (0, 0.574, -0.819)，即离竖直 55°、
 *       朝角色正前方（模型 -Z 是正面，见贴图里眼睛画在 {@code north} 面）。单轴是为了和
 *       Blockbench 对齐，理由见 {@link #GRIP_ROT_X}；</li>
 *   <li>显示变换用 {@link ItemDisplayContext#NONE}（单位矩阵），再用 0.85 缩放到物品大小。</li>
 * </ul>
 *
 * <p>主手挂 {@code forearm_r}、副手挂 {@code forearm_l}；只有盾卫的副手有东西
 * （见 {@link InstructorEntity#equipForRole()}）。
 */
public final class InstructorHeldItemLayer extends BlockAndItemGeoLayer<InstructorEntity> {
    /** 肘（y=18）到握点（y=12）的距离，模型像素 → 方块。 */
    private static final float GRIP_DROP = 6.0F / 16.0F;
    /** 精灵图里握把的位置（像素）——物品模型的 (0,0) 是贴图左下角。 */
    private static final float HANDLE_X = 8.0F / 16.0F;
    private static final float HANDLE_Y = 1.0F / 16.0F;
    /** 物品在手上的缩放；原版 thirdperson 也是 0.85。 */
    private static final float SCALE = 0.85F;
    /**
     * 握姿：只绕 X 转 -55°，刀身从"顺着手臂向上"倒向角色正前方 55°。
     *
     * <p><b>只有一个轴</b>是故意的：多轴欧拉角会撞上"Blockbench（three.js）按 XYZ 合成、
     * 而 Bedrock/GeckoLib 按 ZYX 合成"这个分歧，同一组数字在两处会摆出不同姿势——
     * 单轴旋转无论按哪种顺序合成结果都一样，Blockbench 里看到的必然就是游戏里的。
     * 叉刃（+X）不受影响，所以护手始终横在刀身两侧。
     */
    public static final float GRIP_ROT_X = -55.0F;

    public InstructorHeldItemLayer(GeoRenderer<InstructorEntity> renderer) {
        super(renderer,
            (bone, entity) -> switch (bone.getName()) {
                case "forearm_r" -> entity.getMainHandItem();
                case "forearm_l" -> entity.getOffhandItem();
                default -> ItemStack.EMPTY;
            },
            (bone, entity) -> null);
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, InstructorEntity entity) {
        return ItemDisplayContext.NONE;
    }

    @Override
    protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack, InstructorEntity entity,
                                      MultiBufferSource buffers, float partialTick, int packedLight,
                                      int packedOverlay) {
        if (stack.isEmpty()) return;
        boolean left = "forearm_l".equals(bone.getName());
        pose.pushPose();
        try {
            WindConstructModels.atBone(pose, bone);      // 回到当前骨骼的坐标系
            pose.translate(0.0F, -GRIP_DROP, 0.0F);      // 肘 → 拳头
            if (left) {
                // 盾：平板贴在左前臂外侧，面朝前（不旋转）。
                pose.translate(-2.0F / 16.0F, 1.0F / 16.0F, -2.0F / 16.0F);
                pose.scale(0.8F * SCALE, 0.8F * SCALE, 0.8F * SCALE);
            } else {
                // 剑：握把挪到原点，再摆成"刀身上偏前 55°、刀面朝左右"。
                pose.mulPose(Axis.XP.rotationDegrees(GRIP_ROT_X));
                pose.scale(SCALE, SCALE, SCALE);
                pose.translate(-HANDLE_X, -HANDLE_Y, 0.0F);
            }
            super.renderStackForBone(pose, bone, stack, entity, buffers, partialTick, packedLight, packedOverlay);
        } finally {
            pose.popPose();
        }
    }
}
