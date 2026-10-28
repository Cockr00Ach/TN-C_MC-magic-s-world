package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import com.tnc.tnc.boss.fire.TortoiseTransition;
import com.tnc.tnc.boss.fire.TortoiseDeath;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 发光裂纹层：贴图由 {@code tools/gen_volcanic_tortoise_art.py} 单独烘一张，
 * 只有岩浆裂缝和壳顶熔岩池是亮的，其余全黑。
 *
 * <p>这样裂纹在夜里/洞里自己发亮，而不是靠动态光源 —— 动态光源要每帧改区块光照，
 * 一只 Boss 就够把帧数拉下去；发光层是纯贴图，代价和普通渲染层一样。
 *
 * <p>一阶段也有一点余火（半亮），二阶段是满亮。
 */
public final class VolcanicTortoiseEmissiveLayer extends GeoRenderLayer<VolcanicTortoiseEntity> {
    private static final ResourceLocation GLOW_ONE = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/volcanic_tortoise_phase1_glow.png");
    private static final ResourceLocation GLOW_TWO = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/volcanic_tortoise_phase2_glow.png");

    public VolcanicTortoiseEmissiveLayer(GeoRenderer<VolcanicTortoiseEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VolcanicTortoiseEntity entity, BakedGeoModel model,
                       RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        boolean phaseTwo = entity.usesPhaseTwoModel();
        float alpha = entity.isDyingAnimation() ? TortoiseDeath.glow(entity.deathAge(partialTick))
                : entity.isTransitioning() ? TortoiseTransition.glow(entity.transitionAge(partialTick))
                : phaseTwo ? 1.0F : 0.55F;
        if (alpha <= 0) return;
        ResourceLocation texture = phaseTwo ? GLOW_TWO : GLOW_ONE;
        RenderType glow = RenderType.entityTranslucentEmissive(texture);
        this.getRenderer().reRender(model, poseStack, buffers, entity, glow, buffers.getBuffer(glow),
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, Math.min(1, alpha));
        if (alpha > 1) {
            RenderType flash = RenderType.eyes(texture);
            this.getRenderer().reRender(model, poseStack, buffers, entity, flash, buffers.getBuffer(flash),
                    partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha - 1);
        }
    }
}
