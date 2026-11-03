package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把"根骨骼（{@code bone}）绕竖轴的整圈转身"落到整只模型上。
 *
 * <p>为什么需要这一层：人形模型里手臂、头、腿都挂在模型根上，不是挂在 {@code body} 上。
 * 所以只把根骨骼的 yaw 加到 {@code body} 上，看起来只是"上身拧了一下"，腿和手臂各转
 * 各的 —— 回旋斩（长剑 QEQZZZ，根骨骼 0° → 687°）在游戏里就完全不转。
 *
 * <p>正确做法是在渲染整只模型之前旋转 {@code PoseStack}：转轴正好过玩家脚下，等价于
 * Blockbench 里旋转根骨骼。{@code RenderPlayerEvent.Pre} 拿到的栈就是"已经移到玩家位置、
 * 还没乘上模型自身朝向"的那一层，在这里绕 Y 转就是整只人连手上武器一起转。
 *
 * <p>符号：模型空间在渲染时会做 {@code scale(-1, -1, 1)}，所以模型内 {@code yRot = +θ}
 * 等价于这一层上的 {@code -θ}，和 {@link WarriorPose} 里的口径保持一致。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorPoseRender {

    /** 这一帧给谁 push 过：保证和 Post 一一对应，不会漏弹栈把后面的渲染弄歪。 */
    private static final Set<Integer> PUSHED = new HashSet<>();

    private WarriorPoseRender() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        float yaw = WarriorAnimationClient.rootYawDegrees(event.getEntity());
        if (yaw == 0f) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        PUSHED.add(event.getEntity().getId());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(RenderPlayerEvent.Post event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    /** 只给排查用：当前这一帧这个玩家的根骨骼角度。 */
    public static float rootYaw(Player player) {
        return WarriorAnimationClient.rootYawDegrees(player);
    }
}
