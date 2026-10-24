package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 喷吐蓄力的客户端表现（纯视觉，不参与任何判定）。
 *
 * <p>服务端只同步“开始时刻 + 总蓄力 tick”，客户端按自己的 tick 计数还原进度；
 * 火球在嘴前 1.4 格处由小长大，周围少量火星向核心汇聚，发射瞬间由真正的投射物接管。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SpitChargeFx {

    private static final float SCALE = 2.0F;
    private static final double MAX_RENDER_DIST_SQR = 64.0D * 64.0D;
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(1 << 16));

    /** bossId -> {serverStart, localTickAtStart} */
    private static final Map<Integer, long[]> SEEN = new ConcurrentHashMap<>();

    private SpitChargeFx() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SEEN.clear();
            return;
        }
        long now = mc.level.getGameTime();
        java.util.Set<Integer> alive = new java.util.HashSet<>();

        if (mc.player == null) {
            return;
        }
        for (VolcanicTortoiseEntity boss : mc.level.getEntitiesOfClass(VolcanicTortoiseEntity.class,
                mc.player.getBoundingBox().inflate(64.0D))) {
            int start = boss.spitChargeStart();
            if (start < 0) {
                SEEN.remove(boss.getId());
                continue;
            }
            alive.add(boss.getId());
            long[] seen = SEEN.get(boss.getId());
            if (seen == null || seen[0] != start) {
                SEEN.put(boss.getId(), new long[]{start, now});
                continue;
            }
            int total = Math.max(1, boss.spitChargeTicks());
            long age = now - seen[1];
            if (age < 0 || age > total + 2) {
                continue;
            }
            float progress = Mth.clamp(age / (float) total, 0.0F, 1.0F);
            Vec3 center = chargeCenter(boss);
            // 少量火星向核心汇聚：越接近发射越密集。
            int count = 1 + (int) (progress * 2.0F);
            for (int i = 0; i < count; i++) {
                double angle = mc.level.random.nextDouble() * Math.PI * 2.0D;
                double radius = 1.1D + mc.level.random.nextDouble() * 0.9D;
                double x = center.x + Math.cos(angle) * radius;
                double y = center.y + (mc.level.random.nextDouble() - 0.35D) * 1.2D;
                double z = center.z + Math.sin(angle) * radius;
                mc.level.addParticle(ParticleTypes.FLAME, x, y, z,
                        (center.x - x) * 0.12D, (center.y - y) * 0.12D, (center.z - z) * 0.12D);
            }
        }
        Iterator<Map.Entry<Integer, long[]>> it = SEEN.entrySet().iterator();
        while (it.hasNext()) {
            if (!alive.contains(it.next().getKey())) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SEEN.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        var model = mc.getModelManager().getModel(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "projectile/fireball_core"));

        for (Map.Entry<Integer, long[]> entry : SEEN.entrySet()) {
            if (!(mc.level.getEntity(entry.getKey()) instanceof VolcanicTortoiseEntity boss)) {
                continue;
            }
            int total = Math.max(1, boss.spitChargeTicks());
            long age = mc.level.getGameTime() - entry.getValue()[1];
            if (age < 0 || age > total) {
                continue;
            }
            float progress = Mth.clamp(age / (float) total, 0.0F, 1.0F);
            Vec3 center = chargeCenter(boss);
            if (center.distanceToSqr(camera) > MAX_RENDER_DIST_SQR) {
                continue;
            }
            float scale = SCALE * (0.16F + 0.84F * progress);

            PoseStack pose = new PoseStack();
            pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - boss.getYRot()));
            pose.scale(scale, scale, scale);
            pose.translate(-0.5F, -0.5F, -0.5F);
            mc.getItemRenderer().renderModelLists(model, ItemStack.EMPTY, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose,
                    BUFFERS.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));
        }
        BUFFERS.endBatch();
    }

    /** 与服务端 mouthPoint 完全一致的嘴前位置，再向正前方推 1.4 格。 */
    private static Vec3 chargeCenter(VolcanicTortoiseEntity boss) {
        boolean two = boss.isPhaseTwo();
        double fwd = two ? 6.6D : 5.3D;
        double up = two ? 0.70D : 1.35D;
        double rad = Math.toRadians(boss.getYRot());
        Vec3 forward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
        return boss.position().add(forward.scale(fwd + 1.4D)).add(0.0D, up, 0.0D);
    }
}
