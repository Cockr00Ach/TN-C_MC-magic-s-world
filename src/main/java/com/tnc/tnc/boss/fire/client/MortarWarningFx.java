package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.fire.VolcanicMortarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 火山喷发的落点预警（任务 8）。
 *
 * <p>每颗抛射火球在服务端发射时就已经锁定了落点与飞行时长，这里只读取同步过来的
 * 落点数据画圈，不做任何预测。圈内随剩余时间收缩，边缘闪动提示即将落地。
 *
 * <p>用自己的 {@link MultiBufferSource}（Oculus 会把 {@code mc.renderBuffers().bufferSource()}
 * 换成 endBatch 为空实现的缓冲），因此不会受光影影响；也不用粒子，粒子设“最少”时预警依旧可见。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MortarWarningFx {

    /** 实际爆炸半径（与服务端 {@link VolcanicMortarEntity#EXPLOSION_RADIUS} 一致）。 */
    private static final float RADIUS = (float) VolcanicMortarEntity.EXPLOSION_RADIUS;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(32768));

    private MortarWarningFx() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z);

        MultiBufferSource.BufferSource buffers = BUFFERS;
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        boolean any = false;

        for (VolcanicMortarEntity ball : level.getEntitiesOfClass(VolcanicMortarEntity.class,
                mc.player.getBoundingBox().inflate(96.0D))) {
            int flight = ball.flightTicks();
            int remain = flight - ball.tickCount;
            if (flight <= 0 || remain <= 0) {
                continue;
            }
            Vec3 land = ball.landing();
            float y = groundY(level, land);
            float progress = Mth.clamp(1.0F - remain / (float) flight, 0.0F, 1.0F);
            float pulse = 0.55F + 0.45F * Mth.sin((ball.tickCount + ball.getId()) * 0.55F);

            // 外圈：固定爆炸半径；内圈：随剩余时间收缩，最后 12 tick 闪得更快。
            ring(matrix, buffer, (float) land.x, y + 0.03F, (float) land.z, RADIUS, 0.55F * pulse + 0.25F,
                    0.95F, 0.28F, 0.05F, 0.90F);
            float inner = RADIUS * (0.20F + 0.80F * (1.0F - progress));
            ring(matrix, buffer, (float) land.x, y + 0.04F, land.z, Math.max(0.25F, inner), 0.22F,
                    0.62F, 0.03F, 0.03F, 0.95F);

            // 中心一点亮斑，落地前最后阶段明显一点。
            if (remain < 14) {
                ring(matrix, buffer, (float) land.x, y + 0.05F, land.z, 0.55F, 0.35F,
                        1.0F, 0.55F, 0.12F, 1.0F);
            }
            any = true;
        }

        if (any) {
            buffers.endBatch(RenderType.lightning());
        }
    }

    private static float groundY(Level level, Vec3 land) {
        BlockPos pos = BlockPos.containing(land.x, land.y, land.z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        if (Math.abs(top - land.y) > 4.0D) {
            return (float) land.y;
        }
        return top + 1.0F;
    }

    private static void ring(Matrix4f matrix, VertexConsumer buffer, double cx, float y, double cz,
                             float radius, float thickness, float r, float g, float b, float a) {
        int segments = 48;
        float inner = Math.max(0.0F, radius - thickness);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            float x0 = (float) (cx + Math.cos(a0) * radius);
            float z0 = (float) (cz + Math.sin(a0) * radius);
            float x1 = (float) (cx + Math.cos(a1) * radius);
            float z1 = (float) (cz + Math.sin(a1) * radius);
            float ix0 = (float) (cx + Math.cos(a0) * inner);
            float iz0 = (float) (cz + Math.sin(a0) * inner);
            float ix1 = (float) (cx + Math.cos(a1) * inner);
            float iz1 = (float) (cz + Math.sin(a1) * inner);
            quad(matrix, buffer, x0, y, z0, x1, y, z1, ix1, y, iz1, ix0, y, iz0, r, g, b, a);
        }
    }

    private static void quad(Matrix4f matrix, VertexConsumer buffer,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        vertex(matrix, buffer, x0, y0, z0, r, g, b, a);
        vertex(matrix, buffer, x1, y1, z1, r, g, b, a);
        vertex(matrix, buffer, x2, y2, z2, r, g, b, a);
        vertex(matrix, buffer, x3, y3, z3, r, g, b, a);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z,
                               float r, float g, float b, float a) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
    }
}
