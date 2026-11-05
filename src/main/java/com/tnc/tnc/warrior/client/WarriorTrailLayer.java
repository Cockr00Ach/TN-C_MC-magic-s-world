package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Draws the already captured held-weapon sweep and the independent combat-step afterimage.
 * The vanilla held-item layer runs earlier, so its mount is sampled in the same frame.
 */
final class WarriorTrailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    WarriorTrailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // The earlier held-item draw captured the weapon mount; never reconstruct it from an arm.
        WarriorTrail.render(player, buffer);
        // 冲刺残影和刀光共用这一帧的渲染时机，各自只画几片四边形
        WarriorDash.sample(player);
        WarriorDash.render(player, buffer);
    }
}
