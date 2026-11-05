package com.tnc.tnc.warrior.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import com.tnc.tnc.warrior.WarriorWeapons;

/**
 * 把动作角度扣到玩家模型的骨骼上。
 *
 * <p>两条来源：
 *
 * <ul>
 *   <li><b>Blockbench 关键帧</b>（长剑的四个连招）：{@link WarriorComboAnimations} 读
 *       {@code 战士连招/长剑连招.json}，这里把度换算成弧度逐根骨骼赋值 —— 美术改动画不用改代码；</li>
 *   <li><b>程序化曲线</b>（其余武器）：{@link WarriorPoseMath} 里的纯数学，先算再套。</li>
 * </ul>
 *
 * <p><b>只转骨骼、不碰顶点</b>：所以 Steve 和 Alex 两种手臂粗细、任何皮肤、以及手上
 * 拿着的武器都自动跟着动 —— 这就是"适配玩家体型"的做法。骨名（rightArm/leftArm/
 * body/head/legs）来自 {@link HumanoidModel}，不是写死的坐标。
 */
public final class WarriorPose {

    private static final float DEG = (float) (Math.PI / 180.0);

    private WarriorPose() {
    }

    public static void apply(Player player, HumanoidModel<?> model) {
        WarriorAnimationClient.Active active = WarriorAnimationClient.active(player.getId());
        if (active == null) {
            return;
        }
        float t = active.progress(Minecraft.getInstance().getFrameTime());
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        WarriorComboAnimations.Clip clip = WarriorComboAnimations.clip(active.animation());
        float weight=active.weight(Minecraft.getInstance().getFrameTime());
        if (clip != null) {
            applyClip(model, clip, t, leftHanded,weight);
            return;
        }
        WarriorPoseMath.Pose pose = WarriorPoseMath.of(active.animation(), t, leftHanded);

        model.rightArm.xRot = WarriorMotionBlend.angle(model.rightArm.xRot,pose.rightArmX(),weight);
        model.rightArm.yRot = WarriorMotionBlend.angle(model.rightArm.yRot,pose.rightArmY(),weight);
        model.rightArm.zRot = WarriorMotionBlend.angle(model.rightArm.zRot,pose.rightArmZ(),weight);
        model.leftArm.xRot = WarriorMotionBlend.angle(model.leftArm.xRot,pose.leftArmX(),weight);
        model.leftArm.yRot = WarriorMotionBlend.angle(model.leftArm.yRot,pose.leftArmY(),weight);
        model.leftArm.zRot = WarriorMotionBlend.angle(model.leftArm.zRot,pose.leftArmZ(),weight);

        if (active.animation() != com.tnc.tnc.warrior.WarriorAnimation.SPIN_SLASH)
            model.body.yRot += pose.bodyYaw()*weight;
        model.body.xRot += pose.bodyPitch()*weight;
        model.head.yRot += pose.headYaw()*weight;
        model.head.xRot += pose.headPitch()*weight;
        model.rightLeg.xRot += pose.rightLegX()*weight;
        model.leftLeg.xRot += pose.leftLegX()*weight;

        if(weight>=.999f)twoHandedSupport(player, model, pose, leftHanded);
    }

    /**
     * 双手武器的副手：让左臂跟着右臂一起动（镜像）。
     *
     * <p>为什么要强制覆盖：程序化姿势里左臂的角度是 0，也就是“垂在腿边”。
     * 长槍/重剑/战锤一出招，副手就掉下去，看起来像单手提着一根长杆子。
     * 镜像是最保守的做法：两只手的动作完全对称，不会出现“一只手在动、
     * 另一只手在抽筋”这种更难看的情况。
     *
     * <p>只在没有 Blockbench 关键帧的动作上生效：有关键帧时左臂是美术手摆的，
     * 不该被代码抢过去。
     */
    private static void twoHandedSupport(Player player, HumanoidModel<?> model,
                                         WarriorPoseMath.Pose pose, boolean leftHanded) {
        if (leftHanded) {
            return;
        }
        if (!WarriorWeapons.twoHanded(WarriorWeapons.family(player.getMainHandItem()))) {
            return;
        }
        model.leftArm.xRot = pose.rightArmX();
        model.leftArm.yRot = -pose.rightArmY();
        model.leftArm.zRot = -pose.rightArmZ();
    }

    /**
     * 按关键帧摆姿势。
     *
     * <p>左手玩家不是"重新做一套动画"，而是镜像：左右臂互换、绕竖轴（y）和张角（z）
     * 取反 —— 和 {@link WarriorPoseMath} 里 {@code mirror()} 的口径一致。
     *
     * <p>根骨骼 {@code bone} 只取抬头（x）和侧倾（z）落在 {@code body} 上；<b>绕竖轴
     * （y）的整圈转身不在这里</b>，见方法体里的注释和 {@link WarriorPoseRender}。
     */
    private static void applyClip(HumanoidModel<?> model, WarriorComboAnimations.Clip clip,
                                  float progress, boolean leftHanded,float weight) {
        float seconds = Math.max(0f, Math.min(1f, progress)) * clip.lengthSeconds();
        float sign = leftHanded ? -1f : 1f;

        setArm(model.rightArm, clip.rotation(leftHanded ? "armLeft" : "armRight", seconds), sign,weight);
        setArm(model.leftArm, clip.rotation(leftHanded ? "armRight" : "armLeft", seconds), sign,weight);

        float[] legRight = clip.rotation("legRight", seconds);
        float[] legLeft = clip.rotation("legLeft", seconds);
        setLimb(model.rightLeg, legRight, sign,weight);
        setLimb(model.leftLeg, legLeft, sign,weight);

        float[] head = clip.rotation("head", seconds);
        model.head.xRot = WarriorMotionBlend.angle(model.head.xRot,head[0] * DEG,weight);
        model.head.yRot = WarriorMotionBlend.angle(model.head.yRot,head[1] * DEG * sign,weight);
        model.head.zRot = WarriorMotionBlend.angle(model.head.zRot,head[2] * DEG * sign,weight);

        if (clip.moves("bone")) {
            float[] root = clip.rotation("bone", seconds);
            // 抬头/侧倾可以落在躯干上；**绕竖轴的那一圈不能**：手臂、腿都挂在模型根上，
            // 只转躯干等于只拧了一下腰（回旋斩看起来"完全不转"就是这个坑）。
            // 竖直方向的整圈转身由 WarriorPoseRender 在渲染前旋转 PoseStack 完成。
            model.body.xRot += root[0] * DEG * weight;
            model.body.zRot += root[2] * DEG * sign * weight;
        }
    }

    /**
     * 根骨骼这一帧要转多少度（整只模型一起转，由 {@link WarriorPoseRender} 施加）。
     *
     * <p>单独抽出来的原因：这是**唯一**一处把 {@code bone} 的 yaw 变成可视转身的地方，
     * 单元测试可以直接钉住它（旋身斩 687.5°、左手镜像取反），不用进游戏看。
     */
    public static float rootYaw(WarriorComboAnimations.Clip clip, float seconds, boolean leftHanded) {
        if (clip == null || !clip.moves("bone")) {
            return 0f;
        }
        float yaw = clip.rotation("bone", seconds)[1];
        return leftHanded ? -yaw : yaw;
    }

    private static void setArm(ModelPart arm, float[] degrees, float sign,float weight) {
        arm.xRot = WarriorMotionBlend.angle(arm.xRot,degrees[0] * DEG,weight);
        arm.yRot = WarriorMotionBlend.angle(arm.yRot,degrees[1] * DEG * sign,weight);
        arm.zRot = WarriorMotionBlend.angle(arm.zRot,degrees[2] * DEG * sign,weight);
    }

    private static void setLimb(ModelPart leg, float[] degrees, float sign,float weight) {
        leg.xRot = WarriorMotionBlend.angle(leg.xRot,degrees[0] * DEG,weight);
        leg.yRot = WarriorMotionBlend.angle(leg.yRot,degrees[1] * DEG * sign,weight);
        leg.zRot = WarriorMotionBlend.angle(leg.zRot,degrees[2] * DEG * sign,weight);
    }
}
