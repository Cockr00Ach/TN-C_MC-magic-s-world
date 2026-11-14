package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.magic.TNWindBladeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * ★ <b>风刃的渲染器</b> —— 2026-11-09 起走「战士剑气」那一套光带，不再画烘焙月牙 ✓
 *
 * <p>作者：「这个风刃也改一改，其实可以改成战士那里的剑气你觉得如何，体积统一一下」。
 *
 * <h2>改了什么</h2>
 * <ul>
 *   <li><b>不再画模型</b> ✗（旧版走 SpellEngine {@code CustomModels} 画
 *       {@code tnc:projectile/windblade}）⇒ 现在由 {@link WindBladeQi} 直接生成弯月顶点 ✓
 *       —— 和战士 {@code WarriorWaveRenderer} 同一种"四层渐隐月牙 + 残影 + 刃口"读法 ✓</li>
 *   <li><b>尺寸统一</b>：旧版按 5/10/20 倍缩放（t5 跨度 23.6 格 ✗）；现在几何按
 *       {@link TNWindBladeEntity#halfSpanBlocks()} 直接算世界尺寸 ⇒ 风法三档一样大 ✓
 *       （风守卫 Boss 自己的 2.6 / 4.2 倍照旧 ✓）</li>
 *   <li><b>淡入淡出</b>：出生 2 tick 淡入、最后 6 tick 淡出 ✓（旧版到点直接消失 ✗）</li>
 * </ul>
 *
 * <h2>朝向/自转没变</h2>
 * 还是 {@link WindBladeVisualPose#rotation(float, float, float)}：局部 +Z 始终对着飞行方向，
 * 自转只在这个平面里转 ✓（服务端同步的 yaw/pitch/spin，客户端不自己反推 ✗）。
 *
 * <p>⚠️ 这里**不用** poseStack 的缩放 ✗：几何已经是世界尺寸（格），再乘一次就会和判定
 * （{@code hitRadiusFor}）对不上 —— 那正是以前"打中了但看着没打到"的老毛病 ✗。
 */
public class TNWindBladeRenderer extends EntityRenderer<TNWindBladeEntity> {

    /** 加色光带要的白贴图（和龙卷风/闪电同一张 ✓）。 */
    private static final ResourceLocation TEXTURE = LightningRenderTypes.WHITE_TEXTURE;

    public TNWindBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNWindBladeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNWindBladeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        float fadeIn = Math.min(1.0F, age / 2.0F);
        float fadeOut = Math.min(1.0F, (TNWindBladeEntity.MAX_LIFE - age) / 6.0F);
        float alpha = Math.max(0.0F, Math.min(fadeIn, fadeOut));
        if (alpha <= 0.004F) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(WindBladeVisualPose.rotation(
                entity.bladeYaw(), entity.bladePitch(), entity.visualSpin(partialTick)));
        VertexConsumer vc = buffer.getBuffer(LightningRenderTypes.BOLT);
        WindBladeQi.draw(vc, poseStack.last().pose(), entity.halfSpanBlocks(), alpha);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
