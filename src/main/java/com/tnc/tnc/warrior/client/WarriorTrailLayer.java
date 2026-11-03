package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * 玩家渲染的最后一层：采样剑尖、画刀光。
 *
 * <p>挂在玩家渲染器上（见 {@code WarriorPoseRender.layers}），所以它拿到的
 * {@code PoseStack} 已经带好了这个玩家的朝向/姿态变换，{@code getParentModel()}
 * 就是当帧摆好的骨骼 —— 直接读手臂就能得到"这一帧剑在哪"。
 *
 * <p>顺序很重要：先 {@link WarriorTrail#sample} 记录这一帧，再 {@link WarriorTrail#render}
 * 把包括这一帧在内的整条历史画出来；两者都在同一层里，光带才不会和模型差一帧。
 */
final class WarriorTrailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    WarriorTrailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 先采样：这一帧的剑在哪，必须在画之前记下来
        WarriorTrail.sample(player, poseStack, this.getParentModel());
        // Weapon ghosts are emitted by the actual held-item draw, not this independent arm layer.
        WarriorTrail.render(player, buffer);
        // 冲刺残影和刀光共用这一帧的渲染时机，各自只画几片四边形
        WarriorDash.sample(player);
        WarriorDash.render(player, buffer);
    }
}
