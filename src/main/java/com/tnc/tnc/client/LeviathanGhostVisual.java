package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.TNWaterLeviathanEntity;
import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * <b>真正的水渊兽残影</b>：不另画一套“类似蛇”的几何，而是把水系 Boss 自己的
 * GeckoLib 模型缩小、压暗透明度后，沿读条弧线飞进玩家胸口。
 *
 * <p>这里不调用 {@code EntityRenderer.render}/{@code GeoRenderer.reRender}，也不调用
 * {@code actuallyRender}：Boss 渲染器带 {@code withScale(4)}，直接复用会把残影放大 4 倍；
 * 而 {@code actuallyRender} 只负责骨骼，不会替我们做实体朝向。于是这里自己压好缩放和朝向，
 * 再对模型根骨骼调用 {@link GeoRenderer#renderRecursively}，只走一层原贴图、
 * 半透明的 {@link RenderType#entityTranslucent}，不碰 Boss 的发光贴图层。
 *
 * <p>残影不是实体，不会进入世界、不会同步、不会参与碰撞；只读客户端资源。
 */
public final class LeviathanGhostVisual {
    /** 原模型原始跨度约 17 格；再乘 0.12 后约 1.8–2.1 格，正好是“小型水渊兽”。 */
    private static final float GHOST_SCALE = .12F;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/water_leviathan_bedrock.png");
    private static final Logger LOG = LogManager.getLogger("TN-C/waterghost");

    private static TNWaterLeviathanEntity ghost;
    private static ClientLevel ghostLevel;
    private static boolean logged;

    private LeviathanGhostVisual() {
    }

    static void combat(PoseStack pose,MultiBufferSource buffers,ClientLevel level,Vec3 at,Vec3 direction,float alpha,float partial){
        pose.pushPose();
        // Authored jaw pivot (0,28,-77)/16, scaled exactly like the actual Boss.
        double scale=com.tnc.tnc.boss.TNWaterLeviathanEntity.SIZE_SCALE;
        Vec3 jaw=new Vec3(0,28D/16,-77D/16).scale(scale).yRot((float)Math.toRadians(180-yawFor(direction)));
        pose.translate(-jaw.x,-jaw.y,-jaw.z);
        pose.scale((float)(scale/GHOST_SCALE),(float)(scale/GHOST_SCALE),(float)(scale/GHOST_SCALE));
        render(pose,buffers,level,at,direction,1,alpha,partial);pose.popPose();
    }
    static float scale() {
        return GHOST_SCALE;
    }

    static RenderType renderType() {
        return RenderType.entityTranslucent(TEXTURE);
    }

    /** 与 Minecraft 的 yaw 约定一致：0° 朝 +Z，90° 朝 -X。 */
    static float yawFor(Vec3 forward) {
        if (forward == null || forward.lengthSqr() < 1e-9) {
            return 0F;
        }
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }

    /**
     * 在已经平移到目标点的 PoseStack 上画一只残影。
     *
     * @param pose      已包含“目标点 - 相机”平移的 PoseStack
     * @param buffers   世界渲染用的 BufferSource
     * @param level     当前客户端世界
     * @param at        残影锚点（世界坐标，只用于朝向/调试）
     * @param forward   飞行方向
     * @param size      0.72–1.0 的路径缩放
     * @param alpha     0–1 的最终透明度
     * @param partialTick 渲染插值
     * @return 是否真的提交了几何
     */
    static boolean render(PoseStack pose, MultiBufferSource buffers, ClientLevel level,
                          Vec3 at, Vec3 forward, double size, float alpha, float partialTick) {
        if (pose == null || buffers == null || level == null || at == null || alpha <= .002F) {
            return false;
        }
        TNWaterLeviathanEntity dummy = ensureGhost(level);
        if (dummy == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<?> raw = mc.getEntityRenderDispatcher().getRenderer(dummy);
        if (!(raw instanceof GeoRenderer<?>)) {
            logOnce("renderer is not a GeoRenderer: " + raw);
            return false;
        }
        @SuppressWarnings("unchecked")
        GeoRenderer<TNWaterLeviathanEntity> renderer =
                (GeoRenderer<TNWaterLeviathanEntity>) (GeoRenderer<?>) raw;
        BakedGeoModel model = renderer.getGeoModel()
                .getBakedModel(TNWaterLeviathanEntity.modelResource());
        if (model == null) {
            logOnce("BakedGeoModel missing: " + TNWaterLeviathanEntity.modelResource());
            return false;
        }
        if (!logged) {
            logged = true;
            LOG.info("TN-C/waterghost: direct GeckoLib render armed, model={}, topBones={}, texture={}",
                    TNWaterLeviathanEntity.modelResource(), model.topLevelBones().size(), TEXTURE);
        }

        float yaw = yawFor(forward);
        dummy.setPos(at.x, at.y, at.z);
        dummy.setYRot(yaw);
        dummy.yRotO = yaw;
        dummy.yBodyRot = yaw;
        dummy.yBodyRotO = yaw;
        dummy.yHeadRot = yaw;
        dummy.yHeadRotO = yaw;
        dummy.setXRot(0F);
        dummy.xRotO = 0F;
        dummy.tickCount = (int) (level.getGameTime() & 0x7FFFFFFF);
        dummy.setDeltaMovement(Vec3.ZERO);

        RenderType type = renderType();
        VertexConsumer buffer = buffers.getBuffer(type);
        float s = (float) (GHOST_SCALE * Mth.clamp(size, .55D, 1.15D));
        float a = Mth.clamp(alpha, 0F, 1F);

        pose.pushPose();
        pose.scale(s, s, s);
        // GeckoLib 的模型头朝 -Z；Minecraft 的实体朝向用 180° - yaw 校正。
        pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        // isReRender=true：跳过 Boss 的发光层，只画本体贴图这一层。
        for (GeoBone bone : model.topLevelBones()) {
            renderer.renderRecursively(pose, dummy, bone, type, buffers, buffer, true,
                    partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    1F, 1F, 1F, a);
        }
        pose.popPose();
        return true;
    }

    private static void logOnce(String reason) {
        if (!logged) {
            logged = true;
            LOG.warn("TN-C/waterghost: ghost render disabled once: {}", reason);
        }
    }

    private static TNWaterLeviathanEntity ensureGhost(ClientLevel level) {
        if (ghost != null && ghostLevel == level) {
            return ghost;
        }
        EntityType<TNWaterLeviathanEntity> type = TNNpcs.WATER_LEVIATHAN.get();
        ghost = type.create(level);
        ghostLevel = level;
        if (ghost != null) {
            ghost.setNoAi(true);
            ghost.setSilent(true);
            ghost.setInvulnerable(true);
        }
        return ghost;
    }
}
