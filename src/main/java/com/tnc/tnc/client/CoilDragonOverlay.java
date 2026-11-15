package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.client.dragon.LightningDragonGeometry;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.client.lightning.LightningTheme;
import com.tnc.tnc.magic.TNLightningDragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;


/**
 * ★ <b>缠绕形态的雷龙改在"实体全部画完之后"再画</b>（作者 2026-11-06：
 * 「缠绕着玩家的龙模型加载怎么在玩家模型之后，这样从侧面看龙始终在玩家模型背后没有绕着的感觉」）。
 *
 * <h2>为什么原来是"永远在背后"</h2>
 * 雷系的 {@link LightningRenderTypes#BOLT} 是<b>加色混合 ＋ 不写深度</b>
 * （{@code COLOR_WRITE}）；龙和玩家同在实体层里画，谁先谁后由排序决定 ——
 * 龙先画、玩家后画，玩家的片元就把龙整段盖掉 ⇒ 从侧面看龙像"贴在背后"✗。
 *
 * <h2>现在怎么做</h2>
 * 龙从 {@link TNLightningDragonRenderer} 的实体层里搬到这里，在
 * {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES}（所有实体、包括玩家都画完）里补画：
 * <ul>
 *   <li>玩家已经把深度写进缓冲 ⇒ 龙<b>朝着镜头那一半</b>的片元通过 {@code LEQUAL} 测试、可见 ✓；</li>
 *   <li>被玩家挡住的<b>后半圈</b>深度更深、被测试丢掉 ⇒ 看不见 ✓。</li>
 * </ul>
 * 于是"绕"这件事第一次真的成立：头的半圈在前、尾的半圈在后，镜头怎么转都对 ✓。
 *
 * <p>坐标和实体渲染器一致：矩阵里放 {@code at − cam} 的平移，几何再按
 * {@code cam − at} 取相机位置；所以这里自己拼一个平移矩阵，不再借 poseStack ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class CoilDragonOverlay {

    /** 超过这个距离就不画（和渲染器的 culling 同一个量级）✓。 */
    private static final double RANGE = 128.0D;

    /**
     * 每 tick 只查一次世界里的龙（渲染帧率可以是 tick 的 2~10 倍，
     * 每帧都 {@code getEntitiesOfClass} 是白烧 CPU ✗）。缓存的是<b>实体引用</b>，
     * 位置每帧重新读 ⇒ 插值照样顺 ✓。
     */
    private static long cachedTick = Long.MIN_VALUE;
    private static final java.util.List<TNLightningDragonEntity> CACHE = new java.util.ArrayList<>();

    private CoilDragonOverlay() {
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long tick = mc.level.getGameTime();
        if (tick != cachedTick) {
            cachedTick = tick;
            CACHE.clear();
            CACHE.addAll(mc.level.getEntitiesOfClass(
                    TNLightningDragonEntity.class,
                    new AABB(cam.x - RANGE, cam.y - RANGE, cam.z - RANGE,
                            cam.x + RANGE, cam.y + RANGE, cam.z + RANGE)));
        }
        if (CACHE.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(LightningRenderTypes.BOLT);
        boolean any = false;
        for (TNLightningDragonEntity entity : CACHE) {
            if (!entity.coil() || entity.isRemoved()) {
                continue;
            }
            float age = entity.tickCount + partial - entity.delay();
            if (age < 0.0F) {
                continue;
            }
            float life = entity.life();
            float fade = Math.min(1.0F, age / 8.0F);
            float remain = life - age;
            if (remain < 20.0F) {
                fade *= Math.max(0.0F, remain / 20.0F);
            }
            if (fade <= 0.004F) {
                continue;
            }
            double scale = Math.max(0.4D, entity.scale());
            int theme = LightningTheme.clamp(entity.theme());
            // ★ 2026-11-10 修「登神链的雷龙显示位置异常」：
            //   以前用 `entity.getPosition()` —— 但雷龙是**非生物实体**，客户端那份位置只在
            //   服务端发位置包时才更新（非生物大约每 20 tick 一次，即最多晚 1 秒）✗；
            //   玩家一跑一跳，龙就明显"拖在人后面/浮在空中" ✗。
            //   现在直接**挂到穿戴者身上**（读客户端那份带插值的玩家位置）⇒ 永远贴着人 ✓。
            Vec3 at = wearerPosition(mc, entity, partial).orElseGet(() -> entity.getPosition(partial));

            // ★ 2026-11-14 修「换视角之后龙不在人物脚下」：
            //   以前这里自己拼一个 `translation(at − cam)` —— 只补了平移、**没带相机的旋转** ✗。
            //   第一人称时相机就在玩家脑袋里，转视角时误差正好被"看不见自己脚下"盖住 ✓；
            //   换成第三人称（相机被拉到身后、朝向也完全不同）⇒ 少掉的那次旋转就暴露了，
            //   龙被摆到人旁边去 ✗。
            //   现在改用引擎给的 {@code event.getPoseStack()}（它已经含"相机旋转 + 平移到相机"），
            //   只在上面平移世界坐标 ⇒ 不管第几人称都对 ✓。
            var pose = event.getPoseStack();
            double camX = cam.x - at.x;
            double camY = cam.y - at.y;
            double camZ = cam.z - at.z;

            // ★ 2026-11-13 作者：「登神链，龙不要围绕在玩家身上吧，改成围绕在玩家脚上，
            //   这样起飞就有踩着龙的感觉」✓
            //   改法：半径放宽（像个盘起来的圈）＋ 高度压到脚踝（不再爬到胸口）＋ 整体再往下压 0.40 格
            //   ⇒ 玩家等于**踩在龙背上**；起飞时读作"御龙" ✓
            double radius = 1.10D + 0.55D * scale;
            double height = 0.18D + 0.22D * scale;
            pose.pushPose();
            pose.translate(at.x, at.y - 0.40D, at.z);       // 世界坐标；相机变换由引擎那一层负责 ✓
            Matrix4f coilMatrix = pose.last().pose();
            double coilCamY = camY + 0.40D;                 // 原点下移了 ⇒ 相机相对向量跟着抬 ✓
            LightningDragonGeometry.coil(vc, coilMatrix, entity.seed(), age, scale, theme,
                    0.85F * fade, camX, coilCamY, camZ, radius, height, age * 0.11D);
            // 龙首那点白：一眼看出头在哪 ✓（贴着脚边的圈）
            LightningBoltGeometry.billboardFlash(vc, coilMatrix, 0.0D, height + 0.18D, 0.0D,
                    (float) (0.55D + 0.7D * scale), theme, 0.28F * fade, camX, coilCamY, camZ);
            pose.popPose();
            any = true;
        }
        if (any) {
            buffers.endBatch(LightningRenderTypes.BOLT);
        }
    }

    /**
     * 这条龙"戴"在谁身上 ⇒ 返回那个人（客户端、带插值）的位置 ✓。
     *
     * <p>为什么不在实体自己身上读位置：非生物实体的位置同步很稀（见上面注释）✗。
     * 这里按 {@link TNLightningDragonEntity#ownerId()} 在客户端实体表里找人（玩家/生物都行）✓。
     */
    private static java.util.Optional<Vec3> wearerPosition(Minecraft mc, TNLightningDragonEntity dragon, float partial) {
        java.util.UUID owner = dragon.ownerId();
        if (owner == null || mc.level == null) {
            return java.util.Optional.empty();
        }
        // 玩家优先（绝大多数情况就是玩家）✓
        var player = mc.level.getPlayerByUUID(owner);
        if (player != null) {
            return java.util.Optional.of(player.getPosition(partial));
        }
        for (var candidate : mc.level.entitiesForRendering()) {
            if (candidate.getUUID().equals(owner)) {
                return java.util.Optional.of(candidate.getPosition(partial));
            }
        }
        return java.util.Optional.empty();
    }
}
