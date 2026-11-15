package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.YanDarkBossEntity;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 公孙衍（迷失）的渲染器；模型本身 2.2125 格高，这里再放大 1.15 倍 ✓（只改这一个数） */
public class YanDarkRenderer extends GeoEntityRenderer<YanDarkBossEntity> {
    private static final float MODEL_SCALE = 1.15F;
    public YanDarkRenderer(EntityRendererProvider.Context context) {
        super(context, new YanDarkGeoModel());
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(YanDarkBossEntity entity, float yaw, float partial, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partial, poses, buffers, light);
        if (!YanDarkBossEntity.isYanArena(entity.level()) || !TNEffects.LIGHTNING_ASCENSION.isPresent()
                || !entity.hasEffect(TNEffects.LIGHTNING_ASCENSION.get())) return;

        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(entity.getPosition(partial));
        var buffer = buffers.getBuffer(LightningRenderTypes.BOLT);
        var matrix = poses.last().pose();
        double age = entity.tickCount + partial;
        for (int i = 0; i < 2; i++) {
            double angle = age * 0.25D + i * Math.PI;
            double end = angle + 0.58D;
            LightningBoltGeometry.arc(buffer, matrix,
                    Math.cos(angle) * 1.05D, 1.1D + Math.sin(angle * 2.0D) * 0.44D, Math.sin(angle) * 1.05D,
                    Math.cos(end) * 1.05D, 1.1D + Math.sin(end * 2.0D) * 0.44D, Math.sin(end) * 1.05D,
                    entity.getId() * 73L + entity.tickCount / 3 * 17L + i, .048F, .72F, LightningTheme.DARK,
                    camera.x, camera.y, camera.z);
        }
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                    YanDarkBossEntity animatable, BakedGeoModel model, boolean isReRender,
                                    float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
        if (MODEL_SCALE != 1.0F) {
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }
    }
}
