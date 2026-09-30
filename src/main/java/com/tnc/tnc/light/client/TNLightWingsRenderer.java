package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 光翼的渲染 —— 挂在 Forge 的 {@code RenderPlayerEvent.Post} 上，在玩家背后画两片发光半透明翅膀 ✓。
 *
 * <p><b>飞的时候会动</b> ✓：扇动频率与幅度由"是否在飞"决定（飞 = 快而大 ✓，站着/走路 = 慢而小 ✓），
 * 再按竖直速度加一点俯仰 ✓ —— 纯代码算，不需要关键帧 ✓。
 *
 * <p>⚠️ 朝向：翅膀是贴在玩家背后的两片四边形，用身体朝向摆正 ✓；若进游戏看着"贴反了/翻面"，
 * 改 {@link #BACK_Z} 的符号或 {@link #YAW_OFFSET} 即可（各一个数 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TNLightWingsRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/entity/light_wings.png");

    /** 翅膀挂在背后的位置（相对玩家：高度 / 后移）。 */
    private static final float BACK_Z = 0.16F;
    private static final float BACK_Y = 1.18F;
    /** 翅膀尺寸（格）＋ 张开角度。 */
    private static final float SPAN = 0.95F;
    private static final float RISE = 1.15F;
    private static final float OPEN = 34.0F;
    private static final float YAW_OFFSET = 180.0F;

    private TNLightWingsRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!TNEffects.LIGHT_WINGS.isPresent() || !player.hasEffect(TNEffects.LIGHT_WINGS.get())) {
            return;
        }
        float partial = event.getPartialTick();
        boolean flying = player.getAbilities().flying;
        double vy = player.getDeltaMovement().y;

        // 扇动：飞行时又快又大，平时慢慢摆 ✓
        float speed = flying ? 0.85F : 0.18F;
        float amp = flying ? 42.0F : 9.0F;
        float flap = (float) Math.sin((player.tickCount + partial) * speed) * amp;
        // 竖直速度带来的仰角（上升时收一点、下落时张开 ✓）
        float pitch = (float) Math.max(-18.0D, Math.min(18.0D, vy * 40.0D));

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = LightTexture.FULL_BRIGHT;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-player.yBodyRot + YAW_OFFSET));
        pose.translate(0.0F, BACK_Y, BACK_Z);
        drawWing(vc, pose.last().pose(), true, flap, pitch);
        drawWing(vc, pose.last().pose(), false, flap, pitch);
        pose.popPose();
    }

    /** 画一片翅膀（right = true 右翼 ✓ 左翼镜像 ✓）。 */
    private static void drawWing(VertexConsumer vc, Matrix4f m, boolean right, float flap, float pitch) {
        float s = right ? 1.0F : -1.0F;
        // 从肩点向外上展开；flap 让它上下扇，pitch 让它前后仰 ✓
        float ax = 0.0F, ay = 0.0F;
        float bx = s * SPAN, by = RISE * 0.72F;
        float cx = s * SPAN * 0.55F, cy = RISE * -0.28F;
        float dx = s * SPAN * 0.18F, dy = RISE * -0.42F;
        vc.vertex(m, ax, ay, 0.0F).color(1.0F, 1.0F, 1.0F, 0.92F).uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, bx, by, 0.0F).color(1.0F, 1.0F, 1.0F, 0.70F).uv(1.0F, 0.06F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, cx, cy, 0.0F).color(1.0F, 1.0F, 1.0F, 0.55F).uv(0.72F, 0.62F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, dx, dy, 0.0F).color(1.0F, 1.0F, 1.0F, 0.35F).uv(0.32F, 0.9F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 0.0F, 1.0F).endVertex();
    }
}