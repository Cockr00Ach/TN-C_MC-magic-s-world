package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 火球实体共用的方块模型渲染器。
 *
 * <p>火球本体现在是作者手改的 {@code fireball_core.bbmodel}（导入成方块模型），
 * 外面再由 {@link FireBlobFx} 覆盖一层很小的火焰粒子表现“表面燃烧”。
 *
 * <p>模型朝 -Z（Blockbench 约定），这里按速度方向做 yaw + pitch；模型中心在 (8,8,8)，
 * 所以先把模型平移 -0.5 再把实体原点放到球心。
 */
public abstract class FireballModelRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final Logger LOGGER = LogManager.getLogger("TN-C/fireball-model");
    private static final ResourceLocation MODEL_ID =
            ResourceLocation.fromNamespaceAndPath("tnc", "projectile/fireball_core");
    private static final ModelResourceLocation STANDALONE =
            new ModelResourceLocation(MODEL_ID, "standalone");

    private static boolean warned;

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
        drawModel(pose, buffer, this.scale);
        pose.popPose();

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /** Draws the shared core model at the current pose (model centre is (8,8,8)). */
    public static void drawModel(PoseStack pose, MultiBufferSource buffer, float scale) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(STANDALONE);
        if (model == mc.getModelManager().getMissingModel()) {
            model = mc.getModelManager().getModel(MODEL_ID);
        }
        if (model == mc.getModelManager().getMissingModel()) {
            if (!warned) {
                warned = true;
                LOGGER.warn("TN-C: fireball core model missing: {}", MODEL_ID);
            }
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, -0.5F, -0.5F);
        mc.getItemRenderer().renderModelLists(model, ItemStack.EMPTY,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

}
