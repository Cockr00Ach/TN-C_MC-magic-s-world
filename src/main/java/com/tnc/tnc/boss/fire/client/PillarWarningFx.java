package com.tnc.tnc.boss.fire.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import com.tnc.tnc.boss.fire.PillarLayout;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 熔岩之柱（任务 7）的客户端表现：地面预警 + 按服务端时刻分批冲起的火柱。
 *
 * <p>预警不使用粒子，而是独立的地面四边形（自己的 BufferSource + {@code AFTER_WEATHER}），
 * 因此玩家把粒子调成「最少」也不会丢失危险提示。柱体本身复用 {@link FirePillarFx}。</p>
 */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PillarWarningFx {

    /** 与服务端一致的柱间间隔（tick）。 */
    private static final int STAGGER = 4;
    private static final int WARN_TICKS = VolcanicTortoiseEntity.PILLAR_WARN_TICKS;
    /** 走廊尺寸（服务端同源）。 */
    private static final double LENGTH = PillarLayout.LENGTH;
    private static final double HALF_WIDTH = PillarLayout.WIDTH / 2.0D;
    /** 地面采样网格：沿走廊 2 格一格。 */
    private static final double CELL = 2.0D;
    private static final double EDGE = 0.28D;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(65536));

    private static final Map<Integer, Warn> ACTIVE = new HashMap<>();

    private PillarWarningFx() {
    }

    private static final class Warn {
        final long localStart;
        final long seed;
        final Vec3 origin;
        final float yaw;
        final List<PillarLayout.Spot> spots;
        int spawned;

        Warn(long localStart, long seed, Vec3 origin, float yaw) {
            this.localStart = localStart;
            this.seed = seed;
            this.origin = origin;
            this.yaw = yaw;
            this.spots = PillarLayout.spots(seed);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        List<VolcanicTortoiseEntity> bosses = mc.level.getEntitiesOfClass(
                VolcanicTortoiseEntity.class, mc.player.getBoundingBox().inflate(96.0D));
        for (VolcanicTortoiseEntity boss : bosses) {
            int start = boss.pillarWaveStart();
            if (start < 0) {
                ACTIVE.remove(boss.getId());
                continue;
            }
            Warn warn = ACTIVE.get(boss.getId());
            if (warn == null || warn.seed != start) {
                warn = new Warn(now, start, boss.pillarWaveOrigin(), boss.pillarWaveYaw());
                ACTIVE.put(boss.getId(), warn);
            }
            long age = now - warn.localStart;
            // 按服务端时刻分批点火，保证每根柱子都先有完整预警
            while (warn.spawned < warn.spots.size()
                    && age >= WARN_TICKS + (long) warn.spawned * STAGGER) {
                spawnPillar(mc.level, warn, warn.spots.get(warn.spawned), warn.spawned);
                warn.spawned++;
            }
            if (age > WARN_TICKS + (long) warn.spots.size() * STAGGER + 120L) {
                ACTIVE.remove(boss.getId());
            }
        }
    }

    private static void spawnPillar(Level level, Warn warn, PillarLayout.Spot spot, int index) {
        Vec3 base = spotCenter(warn, spot);
        double ground = groundY(level, base.x, base.z, warn.origin.y);
        double height = 15.0D + (warn.seed % 7 + index * 3) % 5 * 1.1D;
        double width = 1.2D + ((warn.seed + index) % 4) * 0.18D;
        FirePillarFx.spawnAt(new Vec3(base.x, ground, base.z), height, width);
    }

    private static Vec3 spotCenter(Warn warn, PillarLayout.Spot spot) {
        return VolcanicTortoiseEntity.pillarSpot(warn.origin, warn.yaw, spot);
    }

    private static double groundY(Level level, double x, double z, double reference) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
        double y = top + 0.04D;
        if (Math.abs(y - reference) > 3.0D) {
            return reference + 0.04D;
        }
        return y;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z);
        VertexConsumer buffer = BUFFERS.getBuffer(RenderType.lightning());
        for (Warn warn : ACTIVE.values()) {
            long age = now - warn.localStart;
            if (age < 0 || age > WARN_TICKS + (long) warn.spots.size() * STAGGER + 10L) {
                continue;
            }
            drawWarning(mc.level, warn, age, matrix, buffer);
        }
        BUFFERS.endBatch();
    }

    private static void drawWarning(Level level, Warn warn, long age, Matrix4f matrix, VertexConsumer buffer) {
        double pulse = 0.5D + 0.5D * Math.sin(age * 0.28D);
        float fillA = (float) (0.20D + 0.10D * pulse);
        int cells = (int) Math.ceil(LENGTH / CELL);
        for (int i = 0; i < cells; i++) {
            double f0 = i * CELL;
            double f1 = Math.min(LENGTH, f0 + CELL);
            for (int j = 0; j < 3; j++) {
                double l0 = -HALF_WIDTH + j * (HALF_WIDTH * 2.0D / 3.0D);
                double l1 = l0 + HALF_WIDTH * 2.0D / 3.0D;
                groundQuad(level, warn, f0, f1, l0, l1, fillA, 0.42F, 0.03F, 0.03F, matrix, buffer);
            }
        }
        // 边框：四条边各画一条窄带
        borderStrip(level, warn, 0.0D, LENGTH, -HALF_WIDTH, -HALF_WIDTH + EDGE, matrix, buffer);
        borderStrip(level, warn, 0.0D, LENGTH, HALF_WIDTH - EDGE, HALF_WIDTH, matrix, buffer);
        borderStrip(level, warn, 0.0D, EDGE, -HALF_WIDTH, HALF_WIDTH, matrix, buffer);
        borderStrip(level, warn, LENGTH - EDGE, LENGTH, -HALF_WIDTH, HALF_WIDTH, matrix, buffer);
    }

    private static void borderStrip(Level level, Warn warn, double f0, double f1,
                                    double l0, double l1, Matrix4f matrix, VertexConsumer buffer) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(f1 - f0, l1 - l0) / CELL));
        for (int s = 0; s < steps; s++) {
            double a = 1.0D / steps;
            double fa = f0 + (f1 - f0) * s * a;
            double fb = f0 + (f1 - f0) * (s + 1) * a;
            double la = l0 + (l1 - l0) * s * a;
            double lb = l0 + (l1 - l0) * (s + 1) * a;
            groundQuad(level, warn, fa, fb, la, lb, 0.88F, 0.62F, 0.03F, 0.03F, matrix, buffer);
        }
    }

    private static void groundQuad(Level level, Warn warn, double f0, double f1, double l0, double l1,
                                   float alpha, float r, float g, float b,
                                   Matrix4f matrix, VertexConsumer buffer) {
        double[] f = {f0, f1, f1, f0};
        double[] l = {l0, l0, l1, l1};
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        for (int i = 0; i < 4; i++) {
            Vec3 p = spotCenter(warn, new PillarLayout.Spot(f[i], l[i]));
            xs[i] = (float) p.x;
            zs[i] = (float) p.z;
            ys[i] = (float) groundY(level, p.x, p.z, warn.origin.y);
        }
        quad(buffer, matrix, xs[0], ys[0], zs[0], xs[1], ys[1], zs[1],
                xs[2], ys[2], zs[2], xs[3], ys[3], zs[3], r, g, b, alpha);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        vertex(buffer, matrix, x0, y0, z0, r, g, b, a);
        vertex(buffer, matrix, x1, y1, z1, r, g, b, a);
        vertex(buffer, matrix, x2, y2, z2, r, g, b, a);
        vertex(buffer, matrix, x3, y3, z3, r, g, b, a);
        vertex(buffer, matrix, x3, y3, z3, r, g, b, a);
        vertex(buffer, matrix, x2, y2, z2, r, g, b, a);
        vertex(buffer, matrix, x1, y1, z1, r, g, b, a);
        vertex(buffer, matrix, x0, y0, z0, r, g, b, a);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, float r, float g, float b, float a) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
    }
}
