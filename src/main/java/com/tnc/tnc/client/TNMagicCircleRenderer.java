package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 魔法阵 / 冲击波环的渲染器 —— 把 {@code textures/entity/magic_circle.png} 当作
 * <b>贴地的一张面片</b>画出来 ✓（作者要的"地上留下魔法阵"）。
 *
 * <h2>三种表现（同一条代码路径）</h2>
 * <ul>
 *   <li><b>魔法阵</b>：固定半径、缓慢自转、先亮起再淡出 ✓；</li>
 *   <li><b>冲击波环</b>（{@link TNShockwaveEntity}）：半径从 30% 扩到 100%、快速淡出 ✓；</li>
 *   <li>两者都走 {@code entityTranslucentEmissive}（发光、可半透明）✓ 不需要光照参数。</li>
 * </ul>
 *
 * <h2>为什么画两遍</h2>
 * 这是个**平面**，而半透明渲染类型是**剔除背面**的 ✗ —— 朝向判断反了就会整个看不见，
 * 而我在开发环境里没法"看一眼"验证 ✗。所以两个绕序各画一次，保证任何朝向都看得见 ✓
 * （代价只是半透明叠了一层，肉眼几乎看不出）。
 */
public class TNMagicCircleRenderer extends EntityRenderer<TNMagicCircleEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/entity/magic_circle.png");

    public TNMagicCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNMagicCircleEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNMagicCircleEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        float life = Math.max(1.0F, entity.life());
        float t = Math.min(1.0F, age / life);

        boolean wave = entity instanceof TNShockwaveEntity;
        double radius = entity.radius() * (wave ? (0.3D + 0.7D * t) : 1.0D);

        // 2026-09-27 作者："魔法阵出现的不是很流畅，我建议是释放出魔法的那一刻就出现，然后逐渐淡化消失"
        //   旧版：alpha = min(1, age/4) * …… —— 前 4 tick 是"淡入" ✗，看起来就是"糊上来/闪一下" ✗。
        //   现在：**第 0 tick 就满亮度**（出现即所见 ✓），先保持一小段，再用 smoothstep 平滑淡到 0 ✓
        //   （smoothstep = u²(3-2u)：两头斜率为 0 ✓ 起步和收尾都不会有突变，这就是"流畅"的关键 ✓）
        float alpha;
        if (wave) {
            // 冲击波环保持原样：一开始最亮，迅速向外淡出 ✓
            alpha = 1.0F - t;
        } else {
            // 保持时长：生命的前 25%，并夹在 15~40 tick 之间（低档法术生命短也不至于"没看清就没了" ✓；
            // 高级法术阵大、留得久，但也不会"亮着不散" ✗）
            float hold = Math.max(15.0F, Math.min(life * 0.25F, 40.0F));
            hold = Math.min(hold, life * 0.5F);
            if (age <= hold) {
                alpha = 1.0F;
            } else {
                float u = 1.0F - (age - hold) / Math.max(1.0F, life - hold);   // 1 -> 0
                u = Math.max(0.0F, Math.min(1.0F, u));
                alpha = u * u * (3.0F - 2.0F * u);                            // smoothstep ✓
            }
        }
        if (alpha <= 0.01F || radius <= 0.01D) {
            return;
        }
        // 魔法阵慢慢转；冲击波转得稍快，看起来"扫"出去
        float spin = age * (wave ? 6.0F : 1.6F);

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.03D, 0.0D);          // 稍微离地，避免和地面 z-fighting
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F)); // 立着的面片 -> 平铺到地面
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float r = (float) radius;
        drawQuad(consumer, matrix, r, alpha, false);
        drawQuad(consumer, matrix, r, alpha, true);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 在 XY 平面上画一个 -r..r 的方片（贴图铺满），flip = 反向绕序 ✓。 */
    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix, float r, float alpha, boolean flip) {
        float[][] corners = flip
                ? new float[][]{{-r, -r}, {-r, r}, {r, r}, {r, -r}}
                : new float[][]{{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        float[][] uvs = {{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0];
            float y = corners[i][1];
            consumer.vertex(matrix, x, y, 0.0F)
                    .color(1.0F, 1.0F, 1.0F, alpha)
                    .uv(uvs[i][0], uvs[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(0.0F, 0.0F, 1.0F)
                    .endVertex();
        }
    }
}
