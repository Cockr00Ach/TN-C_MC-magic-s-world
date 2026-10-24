package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 火球实体共用的内核渲染器。
 *
 * <p>内核几何来自作者手改的 {@code fireball_core.bbmodel}，由
 * {@code tools/gen_fireball_core_mesh.py} 直接生成 {@link FireballCoreMesh} 的顶点表，
 * 再用贴图直绘 —— 不走方块模型烘焙，避免模型/图集缺失时又变成紫黑方块。
 *
 * <p>模型朝 -Z（Blockbench 约定），这里按速度方向做 yaw + pitch；模型中心在 (8,8,8)，
 * 所以顶点表已按 1/16 换算并整体平移 -0.5。
 */
public abstract class FireballModelRenderer<T extends Entity> extends EntityRenderer<T> {

    private final float scale;

    protected FireballModelRenderer(EntityRendererProvider.Context context, float scale) {
        super(context);
        this.scale = scale;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        Vec3 motion = entity.getDeltaMovement();
        double horiz = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, -motion.z));
        float pitch = (float) Math.toDegrees(Math.atan2(motion.y, Math.max(1.0E-4D, horiz)));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        FireballCoreMesh.render(pose, buffer, this.scale);
        pose.popPose();

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
