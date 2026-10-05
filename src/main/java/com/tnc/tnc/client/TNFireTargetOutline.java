package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.fire.FireSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * <b>锁定目标的红色边框</b>（作者 2026-10-05：「准心指向生物时，生物的边框会有红色线条
 * 表示该法术锁定的目标」）。
 *
 * <h2>几点设计决定</h2>
 * <ul>
 *   <li><b>不用原版的准心判定</b>：原版 {@code hitResult} 的实体距离只有约 3 格 ✗，
 *       而火球链最远打到 64 格 —— 那样"锁定"基本没意义。这里自己按 {@link #REACH} 做扫掠 ✓</li>
 *   <li><b>判据和火球完全一致</b>：用同一个 {@link FireSpellRules#hittable} +
 *       同一套「线段 × 包围盒」扫掠 ✓ ⇒ <b>框到谁就一定会打到谁</b>，
 *       不会出现"框了但穿过去"或者"没框却打中了" ✗</li>
 *   <li><b>纯客户端</b>：不产生任何网络包、不改服务端逻辑 ✓（别人看不到你的框，这是你自己的准心提示 ✓）</li>
 * </ul>
 *
 * <p>画法：借原版的 {@code LevelRenderer.renderLineBox} 画一个红色线框 ✓
 * （和选中方块/碰撞箱同一套，风格统一 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNFireTargetOutline {

    private TNFireTargetOutline() {
    }

    /**
     * 判定距离（格）。
     *
     * <p>取 32：比原版准心（约 3 格）远得多、又不必到 t4 的 64 格那么夸张 ✓ ——
     * 这是一个"我瞄着谁"的提示，不是"法术一定能打到那么远"的承诺 ✓
     */
    private static final double REACH = 32.0D;

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        // 只在实体画完之后补一个线框 —— 这样它盖在生物身上，不会被生物自己的模型挡住 ✓
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        LivingEntity target = aimedAt(minecraft);
        if (target == null) {
            return;
        }

        // ⚠️ 事件里的姿态已经是"相机相对"的 ⇒ 包围盒要先减去相机坐标，否则会画到天边去 ✗
        //    （法阵渲染器那儿踩过一模一样的坑）
        Vec3 camera = event.getCamera().getPosition();
        AABB box = target.getBoundingBox().inflate(0.06D)
                .move(-camera.x, -camera.y, -camera.z);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 0.12F, 0.12F, 1.0F);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /**
     * 沿视线找<b>最近的、火球链能打中的</b>生物（没有就是 null）。
     *
     * <p>和 {@code TNFireBoltEntity} 用同一套判据，所以"框出来的"就是"会命中的" ✓
     */
    private static LivingEntity aimedAt(Minecraft minecraft) {
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(REACH));

        LivingEntity best = null;
        double closest = REACH * REACH;
        for (LivingEntity candidate : minecraft.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D),
                t -> FireSpellRules.hittable(minecraft.player, t))) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.25D).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < closest) {
                closest = hit.get().distanceToSqr(eye);
                best = candidate;
            }
        }
        return best;
    }
}
