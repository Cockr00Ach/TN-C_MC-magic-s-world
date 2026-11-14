package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNWindSpiritEntity;
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
 * <b>读条前戏里的那只小风灵</b>：不另画一套“类似鸟”的几何，而是把召唤链里真正的
 * {@link TNWindSpiritEntity} 的 GeckoLib 模型缩小、提亮后，沿螺旋线飞进玩家胸口。
 *
 * <p>为什么复用真模型（2026-11-07 作者："你学习一下雷法暗法他们怎么做的"）：
 * 前戏里出现的形象必须和玩家之后召唤出来的东西**是同一个东西** ✓，否则读条时看到一只鸟、
 * 施法后飞出来另一只鸟 ✗。水系那边已经用 {@link LeviathanGhostVisual} 验证过这条路 ✓。
 *
 * <p>和水系残影的差别：风灵本体是**会发光的小光灵**，所以这里用
 * {@link RenderType#entityTranslucentEmissive}（全亮 + 半透明）而不是普通半透明；
 * 顶点色压成薄荷白，读起来是“风做的灵体”而不是“被染色的实体” ✓。
 *
 * <p>残影不是实体：不进入世界、不同步、不碰撞、不参与 AI，只读客户端资源 ✓。
 * 模型烘焙失败时返回 {@code false}，调用方会退回程序化风球兜底（不能让前戏整个消失 ✗）。
 */
final class WindSpiritGhost {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tnc", "textures/entity/wind_sprite_bedrock.png");
    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(
            "tnc", "geo/entity/wind_sprite.geo.json");
    private static final Logger LOG = LogManager.getLogger("TN-C/windghost");

    private static TNWindSpiritEntity ghost;
    private static ClientLevel ghostLevel;
    private static boolean logged;
    private static boolean failed;

    private WindSpiritGhost() {
    }

    static RenderType renderType() {
        return RenderType.entityTranslucentEmissive(TEXTURE);
    }

    /** 与 Minecraft 的 yaw 约定一致：0° 朝 +Z，90° 朝 -X。 */
    static float yawFor(Vec3 forward) {
        if (forward == null || forward.lengthSqr() < 1e-9) {
            return 0F;
        }
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }

    /**
     * 在已经平移到目标点的 PoseStack 上画一只风灵残影。
     *
     * @param pose     已包含“目标点 - 相机”平移的 PoseStack
     * @param buffers  世界渲染用的 BufferSource
     * @param level    当前客户端世界
     * @param at       残影锚点（世界坐标，只用于朝向/日志）
     * @param forward  飞行方向（决定模型朝向）
     * @param size     缩放（0.55–1.35，普通前戏约 0.9）
     * @param alpha    0–1 的最终透明度
     * @param partialTick 渲染插值
     * @return 是否真的提交了几何
     */
    static boolean render(PoseStack pose, MultiBufferSource buffers, ClientLevel level,
                          Vec3 at, Vec3 forward, double size, float alpha, float partialTick) {
        if (failed || pose == null || buffers == null || level == null || at == null || alpha <= .002F) {
            return false;
        }
        TNWindSpiritEntity dummy = ensureGhost(level);
        if (dummy == null) {
            return false;
        }
        EntityRenderer<?> raw = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        if (!(raw instanceof GeoRenderer<?>)) {
            fail("renderer is not a GeoRenderer: " + raw);
            return false;
        }
        @SuppressWarnings("unchecked")
        GeoRenderer<TNWindSpiritEntity> renderer =
                (GeoRenderer<TNWindSpiritEntity>) (GeoRenderer<?>) raw;
        BakedGeoModel model = renderer.getGeoModel().getBakedModel(GEO);
        if (model == null) {
            fail("BakedGeoModel missing: " + GEO);
            return false;
        }
        if (!logged) {
            logged = true;
            LOG.info("TN-C/windghost: direct GeckoLib render armed, model={}, topBones={}, texture={}",
                    GEO, model.topLevelBones().size(), TEXTURE);
        }

        float yaw = yawFor(forward);
        dummy.setPos(at.x, at.y, at.z);
        dummy.setYRot(yaw);
        dummy.yRotO = yaw;
        dummy.setXRot(0F);
        dummy.xRotO = 0F;
        dummy.tickCount = (int) (level.getGameTime() & 0x7FFFFFFF);
        dummy.setDeltaMovement(Vec3.ZERO);

        RenderType type = renderType();
        VertexConsumer buffer = buffers.getBuffer(type);
        float s = (float) Mth.clamp(size, .45D, 1.8D);
        float a = Mth.clamp(alpha, 0F, 1F);

        pose.pushPose();
        pose.scale(s, s, s);
        // GeckoLib 的模型头朝 -Z；Minecraft 的实体朝向用 180° - yaw 校正。
        pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        // isReRender=true：跳过额外的发光层，只画本体贴图这一层。
        for (GeoBone bone : model.topLevelBones()) {
            renderer.renderRecursively(pose, dummy, bone, type, buffers, buffer, true,
                    partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    .88F, 1F, .97F, a);
        }
        pose.popPose();
        return true;
    }

    private static void fail(String reason) {
        failed = true;
        LOG.warn("TN-C/windghost: spirit ghost disabled: {}", reason);
    }

    private static TNWindSpiritEntity ensureGhost(ClientLevel level) {
        if (ghost != null && ghostLevel == level) {
            return ghost;
        }
        EntityType<TNWindSpiritEntity> type = TNOrbEntities.WIND_SPIRIT.get();
        ghost = type.create(level);
        ghostLevel = level;
        if (ghost != null) {
            ghost.setSilent(true);
            ghost.setInvulnerable(true);
            // 3 级以下的风灵渲染成一颗风球；前戏要的是“风灵本体”，所以固定用高阶形态。
            ghost.previewTier(5);
        }
        return ghost;
    }
}
