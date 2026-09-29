package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNLightningStrikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the author's {@code tnc:projectile/flash} model as a standing lightning bolt.
 *
 * <p>Same trick as TNThunderOrbRenderer (that one is verified in game): never look the
 * model up yourself - go through the engine's own projectile render path
 * ({@code CustomModels.render} + {@code SpellModelHelper.LAYERS}). A plain
 * {@code ResourceLocation} is required; the {@code #standalone} variant misses and Forge
 * then hands back a missing model that is NOT {@code getMissingModel()}, which silently
 * rendered a purple-black cube once already.
 *
 * <p>Author's flash model bbox (units, 1 = 1/16 block): x 8..13, y -5..31, z 8..10.
 * So to stand the bolt ON the entity position: scale, then translate
 * (-10.5/16, +5/16, -9/16) - centre it in x/z and lift its bottom up to y = 0.
 */
public class TNLightningStrikeRenderer extends EntityRenderer<TNLightningStrikeEntity> {

    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.FLASH);

    private static final float CENTER_X = 10.5F / 16.0F;
    private static final float CENTER_Z = 9.0F / 16.0F;
    private static final float BOTTOM_Y = 5.0F / 16.0F;
    /** 神脚底那圈环的贴图 ✓ */
    private static final ResourceLocation GOD_RING = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/god_ring.png");

    public TNLightningStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLightningStrikeEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/spell_projectile/flash.png");
    }

    @Override
    public void render(TNLightningStrikeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        // pop in over 2 ticks, shrink away over the last 2 - a strike has no time to fade
        // 2026-09-27: 原来这里是 grow * min(1, (life-age)/2) ✗ —— life 只有 4 tick 而其中
        // 大部分被"下落"吃掉，于是雷还在天上时 k 已经归零、整道雷不画 ✗（作者："有的时候
        // 模型会缺失"）。雷劈本来就是瞬时的，直接满尺寸出现即可 ✓ 消失交给服务端 discard。
        float k = Math.min(1.0F, age / 2.0F);
        float s = (float) entity.scale() * k;
        // 球形态（神在投篮 t5）用 t5 那颗大雷球的模型 ✓
        ResourceLocation modelId = entity.isBall()
                ? ResourceLocation.fromNamespaceAndPath(TNMod.MODID, TNProjectileModels.LIGHTNINGBALL_2)
                : MODEL_ID;

        poseStack.pushPose();
        poseStack.scale(s, s, s);
        if (entity.isBall()) {
            poseStack.translate(-0.59375F, -0.71875F, -0.53125F);   // 球心落在实体原点 ✓
        } else {
            poseStack.translate(-CENTER_X, BOTTOM_Y, -CENTER_Z);    // 闪电底端立在地面 ✓
        }
        try {
            net.spell_engine.api.render.CustomModels.render(
                    net.spell_engine.client.render.SpellModelHelper.LAYERS
                            .get(net.spell_engine.api.render.LightEmission.RADIATE),
                    Minecraft.getInstance().getItemRenderer(), modelId,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
            // engine missing / API changed: the hit still has its particles, just no model
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
