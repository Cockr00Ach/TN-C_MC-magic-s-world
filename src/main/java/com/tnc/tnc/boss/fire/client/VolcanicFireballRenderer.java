package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.fire.VolcanicFireballEntity;
import com.tnc.tnc.client.TNProjectileModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 喷吐火球的渲染：直接用任务 4 的方块模型（{@code projectile/fireball_core}），
 * 以 {@code TOWARDS_MOTION} 方式朝向速度方向，全亮绘制。
 *
 * <p>模型约定：前进 -Z、尾迹 +Z、实体位置在尾部；缩放 2.0 时主体约 1.75 格。
 */
public final class VolcanicFireballRenderer extends EntityRenderer<VolcanicFireballEntity> {

    private static final ResourceLocation MODEL_ID =
            ResourceLocation.fromNamespaceAndPath("tnc", "projectile/fireball_core");
    private static final float SCALE = 2.0F;

    public VolcanicFireballRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VolcanicFireballEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(VolcanicFireballEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6D) {
            return;
        }
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(TNProjectileModels.standalone("projectile/fireball_core"));
        if (model == null) {
            return;
        }
        Vec3 dir = motion.normalize();
        float yawDeg = (float) Math.toDegrees(Math.atan2(-dir.x, -dir.z));
        float pitchDeg = (float) Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        pose.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, ItemStack.EMPTY,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers.getBuffer(
                        net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();
    }
}
