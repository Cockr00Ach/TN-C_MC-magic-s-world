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
        float grow = Math.min(1.0F, age / 2.0F);
        float left = Math.max(0.0F, entity.life() - age);
        float k = grow * Math.min(1.0F, left / 2.0F);
        if (k <= 0.02F) {
            return;
        }
        float s = (float) entity.scale() * k;

        poseStack.pushPose();
        poseStack.scale(s, s, s);
        poseStack.translate(-CENTER_X, BOTTOM_Y, -CENTER_Z);
        try {
            net.spell_engine.api.render.CustomModels.render(
                    net.spell_engine.client.render.SpellModelHelper.LAYERS
                            .get(net.spell_engine.api.render.LightEmission.RADIATE),
                    Minecraft.getInstance().getItemRenderer(), MODEL_ID,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
            // engine missing / API changed: the hit still has its particles, just no model
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
