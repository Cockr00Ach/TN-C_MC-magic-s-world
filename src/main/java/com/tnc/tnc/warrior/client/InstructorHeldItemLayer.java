package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.warrior.InstructorEntity;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Anchors real equipment at the animated fist, with one elbow transform.
 *
 * <p>The bone callback already contains its position, rotation and scale. Only returning
 * to its pivot is necessary; applying the bone again twists the weapon behind the actor.
 * Custom weapon meshes carry their actual grip in {@code tnc_grip} and point along +Z,
 * while the vanilla sword sprite runs diagonally from (3,3) toward (15,15).
 */
public final class InstructorHeldItemLayer extends BlockAndItemGeoLayer<InstructorEntity> {
    /** 肘（y=18）到握点（y=12）的距离，模型像素 → 方块。 */
    private static final float GRIP_DROP = 6.0F / 16.0F;
    private static final Vec3 SPRITE_GRIP = new Vec3(3.0 / 16.0, 3.0 / 16.0, 0.5);
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
    public void renderForBone(PoseStack pose, InstructorEntity entity, GeoBone bone, RenderType renderType,
                              MultiBufferSource buffers, VertexConsumer vertices, float partialTick,
                              int packedLight, int packedOverlay) {
        var stack = getStackForBone(bone, entity);
        if (stack == null || stack.isEmpty() || bone.isHidden()) return;
        pose.pushPose();
        try {
            // GeoRenderer has already applied T(pos) T(pivot) R S T(-pivot).
            // The stock item layer adds another R; use only T(pivot) to reach this joint.
            moveToHand(pose, bone);
            renderStackForBone(pose, bone, stack, entity, buffers, partialTick, packedLight, packedOverlay);
        } finally {
            pose.popPose();
            buffers.getBuffer(renderType);
        }
    }

    /** Called from the renderer's already animated bone frame, including bone scale. */
    public static void moveToHand(PoseStack pose, GeoBone bone) {
        RenderUtils.translateToPivotPoint(pose, bone);
        pose.translate(0.0F, -GRIP_DROP, 0.0F);
    }

    /** Maps an authored grip to the fist before ItemRenderer applies its model-centering offset. */
    public static void orientSword(PoseStack pose, Vec3 grip, boolean longitudinalMesh) {
        orientSword(pose,grip,longitudinalMesh,GRIP_ROT_X);
    }

    public static void orientSword(PoseStack pose, Vec3 grip, boolean longitudinalMesh, float wristPitch) {
        pose.mulPose(Axis.XP.rotationDegrees(wristPitch));
        pose.scale(SCALE, SCALE, SCALE);
        if (longitudinalMesh) {
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F)); // model +Z -> upright blade +Y
        } else {
            pose.mulPose(Axis.ZP.rotationDegrees(45.0F)); // diagonal vanilla sprite -> +Y
        }
        // renderStatic translates by (-.5,-.5,-.5) after the NONE display transform.
        pose.translate(0.5 - grip.x, 0.5 - grip.y, 0.5 - grip.z);
    }

    /** Relaxed at the hip; straighten the wrist as the animated arm reaches out or above the head. */
    public static float wristPitch(GeoBone forearm) {
        var rotation=new org.joml.Quaternionf();
        modelRotation(forearm,rotation);
        var arm=rotation.transform(new org.joml.Vector3f(0,-1,0));
        float lift=(float)Math.toDegrees(Math.acos(net.minecraft.util.Mth.clamp(-arm.y,-1,1)));
        float blend=net.minecraft.util.Mth.clamp((lift-10)/40,0,1);
        blend=blend*blend*(3-2*blend);
        // The forearm extends along local -Y. A 180-degree wrist makes the canonical +Y
        // blade continue from the fist; the idle -55-degree angle would point back at the elbow.
        return GRIP_ROT_X+(-180-GRIP_ROT_X)*blend;
    }

    private static void modelRotation(GeoBone bone,org.joml.Quaternionf rotation) {
        if(bone.getParent()!=null)modelRotation(bone.getParent(),rotation);
        rotation.rotateZYX(bone.getRotZ(),bone.getRotY(),bone.getRotX());
    }

    @Override
    protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack, InstructorEntity entity,
                                      MultiBufferSource buffers, float partialTick, int packedLight,
                                      int packedOverlay) {
        if (stack.isEmpty()) return;
        boolean left = "forearm_l".equals(bone.getName());
        pose.pushPose();
        try {
            if (left) {
                // 盾：平板贴在左前臂外侧，面朝前（不旋转）。
                pose.translate(-2.0F / 16.0F, 1.0F / 16.0F, -2.0F / 16.0F);
                pose.scale(0.8F * SCALE, 0.8F * SCALE, 0.8F * SCALE);
            } else {
                var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                var authored = key == null ? java.util.Optional.<Vec3>empty() : WarriorModelGrip.find(key);
                orientSword(pose, authored.orElse(SPRITE_GRIP), authored.isPresent(),wristPitch(bone));
            }
            super.renderStackForBone(pose, bone, stack, entity, buffers, partialTick, packedLight, packedOverlay);
        } finally {
            pose.popPose();
        }
    }
}
