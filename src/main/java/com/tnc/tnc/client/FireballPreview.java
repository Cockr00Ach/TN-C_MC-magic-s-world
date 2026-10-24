package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 火球模型的游戏内预览（只给作者看尺寸/朝向，不参与任何技能逻辑）。
 *
 * <p>用法（需要 JVM 参数 {@code -Dtnc.fireball.debug=true}）：
 * <ul>
 *   <li>{@code /tnc_fireball} —— 在视线前方 6 格放一个预览，20 秒后自动消失；</li>
 *   <li>{@code /tnc_fireball off} —— 立即清除。</li>
 * </ul>
 *
 * <p>渲染用的关键约定（都是踩过的坑）：
 * <ul>
 *   <li>阶段用 {@code AFTER_WEATHER}，此时 RenderSystem 的 model-view 已含视角旋转，
 *       所以<b>不能</b>再套 {@code event.getPoseStack()}，只叠“目标点 - 相机”的平移
 *       （同 {@link PortalRitualRenderer}）；</li>
 *   <li>Oculus 会把 {@code mc.renderBuffers().bufferSource()} 换成
 *       {@code endBatch} 为 no-op 的分段缓冲，因此这里用自己的 immediate buffer；</li>
 *   <li>方块模型坐标是 0..16 单位 = 1 格，渲染前要 {@code translate(-0.5, -0.5, -0.5)}
 *       把中心对到实体位置。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class FireballPreview {

    /** 调试开关：默认关闭，避免正式游戏里多出命令。 */
    public static final boolean DEBUG = Boolean.getBoolean("tnc.fireball.debug");

    /** 渲染缩放：与将来投射物的 scale 保持一致，改这里就能看不同大小。 */
    public static final float SCALE = 2.0F;

    /** 模型本体半宽/半高/长度（单位：格，已含 SCALE）：X ±0.88、Y ±0.86、Z 0..-2.42。 */
    private static final double BOX_X = 0.88D;
    private static final double BOX_Y = 0.86D;
    private static final double BOX_Z = 2.42D;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 16));

    private static Vec3 pos;
    private static float yaw;
    private static long until;

    private FireballPreview() {
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        if (!DEBUG) {
            return;
        }
        event.getDispatcher().register(Commands.literal("tnc_fireball")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) {
                        return 0;
                    }
                    Vec3 look = mc.player.getLookAngle();
                    pos = mc.player.getEyePosition().add(look.scale(6.0D));
                    yaw = mc.player.getYRot();
                    until = mc.level == null ? 0L : mc.level.getGameTime() + 400L;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[TN-C] fireball preview: scale=" + SCALE
                                    + ", 主体约 " + (BOX_X * 2) + " x " + (BOX_Y * 2) + " 格, 长 " + BOX_Z + " 格"),
                            false);
                    return 1;
                })
                .then(Commands.literal("off").executes(ctx -> {
                    pos = null;
                    ctx.getSource().sendSuccess(() -> Component.literal("[TN-C] fireball preview cleared"), false);
                    return 1;
                }))
        );
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || pos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() > until) {
            pos = null;
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        if (pos.distanceToSqr(camera) > 96.0D * 96.0D) {
            return;
        }

        PoseStack pose = new PoseStack();
        pose.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        // 模型前方是 -Z；按生物朝向习惯换算，yaw=0（朝南）时模型正好转向 +Z。
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(SCALE, SCALE, SCALE);

        var model = mc.getModelManager().getModel(
                ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/fireball_core"));
        pose.pushPose();
        pose.translate(-0.5F, -0.5F, -0.5F);
        mc.getItemRenderer().renderModelLists(model, ItemStack.EMPTY, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose,
                BUFFERS.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();

        // 碰撞盒参考线：一个方块宽的立方体，方便判断“看起来多大”和“打不打得到”。
        LevelRenderer.renderLineBox(pose, BUFFERS.getBuffer(RenderType.lines()),
                -BOX_X, -BOX_Y, -BOX_Z, BOX_X, BOX_Y, 0.0D,
                1.0F, 1.0F, 0.35F, 1.0F);
        BUFFERS.endBatch();
    }
}
