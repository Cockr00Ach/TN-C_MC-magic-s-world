package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import com.tnc.tnc.boss.fire.TortoiseQuake;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 大地震波（技能 B）的地表表现。
 *
 * <p>波经过的位置生成一块“临时的地表副本”，用当地方块的真实模型贴图绘制：
 * 使用原版方块渲染器绘制完整模型，保留模组地板的非剔除面与真实侧面。
 * 每块副本的 x/z 固定在原地，按固定格子的到达时刻抬起、径向倾斜，波过后轻微回弹、归位，凹带用短暂贴地暗影表现。</p>
 *
 * <p>方块本身、碰撞、伤害、击退都不受影响。发光波带（{@link QuakeBandFx}）
 * 默认关闭，需要时用 {@code -Dtnc.tortoise.quake.glow=true} 打开。</p>
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlamGroundWave {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/quake");
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.quake.debug")
            || VolcanicTortoiseEntity.ATTACK_DEBUG;

    private static final int MAX_PATCHES = 1024;
    private static final int MAX_WAVES = 3;
    private static final int MAX_SAMPLES_PER_TICK = 384;
    private static final int MAX_PARTICLES_PER_TICK = 48;
    private static final double RENDER_DIST = 64.0D;
    private static final int SEARCH_UP = 2;
    private static final int SEARCH_DOWN = 3;
    private static ClientLevel currentLevel;

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(262144));

    private static final List<Wave> ACTIVE = new ArrayList<>();

    private SlamGroundWave() {
    }

    private static final class Wave {
        final Vec3 center;
        final UUID owner;
        final int cast;
        final TortoiseQuake.Playback clock = new TortoiseQuake.Playback();
        List<TortoiseQuake.Cell> cells = List.of();
        int localTick;
        boolean samplingReported;
        boolean drawingReported;
        final Map<Long, Patch> patches = new HashMap<>();

        Wave(VolcanicTortoiseEntity boss) {
            this.center = boss.quakeWaveCenter();
            this.owner = boss.getUUID();
            this.cast = boss.quakeWaveStart();
        }
    }

    /** Capture the state and its actual surface; model quads are resolved by Minecraft when drawing. */
    private record Patch(BlockState state, BlockPos position, int light, double groundY) {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel) {
            ACTIVE.clear();
            currentLevel = mc.level;
        }
        if (mc.level == null || mc.player == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) return;
        var present = new HashSet<UUID>();
        int particleBudget = MAX_PARTICLES_PER_TICK;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss) || boss.quakeWaveStart() < 0
                    || boss.isDeadOrDying() || boss.quakeWaveCenter().distanceToSqr(mc.player.position())
                    > RENDER_DIST * RENDER_DIST) continue;
            Wave wave = null;
            for (Wave candidate : ACTIVE) {
                if (candidate.owner.equals(boss.getUUID())) { wave = candidate; break; }
            }
            if (wave != null && wave.cast != boss.quakeWaveStart()) {
                ACTIVE.remove(wave);
                wave = null;
            }
            if (wave == null) {
                if (ACTIVE.size() >= MAX_WAVES) continue;
                wave = new Wave(boss);
                ACTIVE.add(wave);
            }
            present.add(wave.owner);
            wave.localTick = boss.tickCount;
            wave.clock.receive(wave.cast, boss.quakeWaveAge(), boss.tickCount);
            double age = wave.clock.age(wave.localTick);
            wave.cells = TortoiseQuake.cells(wave.center.x, wave.center.z, age);
            if (age >= TortoiseQuake.LIFETIME) {
                wave.patches.clear();
                continue; // Keep the cast identity; unchanged metadata must not restart a wave.
            }
            int sampled = 0;
            for (var cell : wave.cells) {
                if (sampled >= MAX_SAMPLES_PER_TICK) break;
                if (!wave.patches.containsKey(cell.key())) {
                    Patch patch = sample(mc.level, cell.x(), cell.z(), wave.center.y);
                    wave.patches.put(cell.key(), patch == null ? MISS : patch);
                    sampled++;
                }
            }
            if (DEBUG && !wave.samplingReported && age >= 2.0D) {
                wave.samplingReported = true;
                long valid = wave.patches.values().stream().filter(patch -> patch != MISS).count();
                LOGGER.info("[TN-C QuakeWave] cast={} elapsed={} ground={} sampled={} center={}",
                        wave.cast, age, valid, wave.patches.size(), wave.center);
            }
            particleBudget -= dust(mc.level, wave, age, Math.min(18, particleBudget));
        }
        ACTIVE.removeIf(wave -> !present.contains(wave.owner));
    }

    /** Tick-bound emission: raising FPS cannot multiply debris. */
    private static int dust(ClientLevel level, Wave wave, double age, int budget) {
        if (budget <= 0 || age <= 0 || age > TortoiseQuake.TRAVEL_TICKS) return 0;
        List<TortoiseQuake.Cell> crest = new ArrayList<>();
        for (var cell : wave.cells) {
            if (Math.abs(age - TortoiseQuake.arrival(cell.distance())) <= 1.0D) crest.add(cell);
        }
        if (crest.isEmpty()) return 0;
        int count = Math.min(budget, crest.size());
        for (int i = 0; i < count; i++) {
            var cell = crest.get(Math.floorMod(i * crest.size() / count + wave.localTick * 7, crest.size()));
            Patch patch = wave.patches.get(cell.key());
            if (patch == null || patch == MISS) continue;
            double dx = (cell.x() + 0.5D - wave.center.x) / Math.max(1.0D, cell.distance());
            double dz = (cell.z() + 0.5D - wave.center.z) / Math.max(1.0D, cell.distance());
            double y = patch.groundY + TortoiseQuake.lift(age, cell.distance()) + 0.08D;
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.POOF, cell.x() + 0.5D, y, cell.z() + 0.5D,
                        dx * 0.06D, 0.025D, dz * 0.06D);
            } else {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, patch.state),
                        cell.x() + level.random.nextDouble(), y, cell.z() + level.random.nextDouble(),
                        dx * 0.10D, 0.12D + level.random.nextDouble() * 0.08D, dz * 0.10D);
            }
        }
        return count;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel || mc.level == null) {
            ACTIVE.clear();
            return;
        }
        try {
            draw(event, mc.level);
        } catch (Throwable t) {
            ACTIVE.clear();
            LOGGER.error("[TN-C QuakeWave] disabled after render error", t);
        }
    }

    private static void draw(RenderLevelStageEvent event, Level level) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = new PoseStack();
        List<GroundDraw> drawn = new ArrayList<>();
        VertexConsumer glow = QuakeBandFx.enabled() ? QuakeBandFx.buffer() : null;
        int remaining = MAX_PATCHES;
        int remainingWaves = ACTIVE.size();
        for (Wave wave : ACTIVE) {
            int allowance = remaining / remainingWaves--;
            if (wave.center.distanceToSqr(camera) > RENDER_DIST * RENDER_DIST) continue;
            double age = wave.clock.age(wave.localTick + (mc.isPaused() ? 0 : event.getPartialTick()));
            int count = Math.min(allowance, wave.cells.size());
            for (int i = 0; i < count; i++) {
                var cell = wave.cells.get(i * wave.cells.size() / count);
                if (!TortoiseQuake.visible(age, cell.distance())) continue;
                Patch patch = wave.patches.get(cell.key());
                if (patch == null || patch == MISS) continue;
                double lift = TortoiseQuake.lift(age, cell.distance());
                double shade = TortoiseQuake.shade(age, cell.distance());
                if (lift < 0.002D && shade > 0.995D) continue;
                if (lift >= 0.002D) {
                    drawPatch(mc, poses, camera, wave.center, cell, patch, lift);
                    if (DEBUG && !wave.drawingReported) {
                        wave.drawingReported = true;
                        LOGGER.info("[TN-C QuakeWave] native block draw cast={} state={}", wave.cast, patch.state);
                    }
                }
                if (shade < 0.995D) drawn.add(new GroundDraw(cell, patch.groundY, (float) (1.0D - shade)));
                if (glow != null) QuakeBandFx.draw(glow, new Matrix4f(), camera, cell, patch.groundY + lift, age);
                remaining--;
            }
        }
        BUFFERS.endBatch();
        if (glow != null) QuakeBandFx.endBatch();
        if (drawn.isEmpty()) return;
        // A separate position/color pass follows the native block pass. Never mix vertex formats.
        VertexConsumer shadow = BUFFERS.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = new Matrix4f();
        for (GroundDraw draw : drawn) {
            float x = (float) (draw.cell.x() - camera.x), z = (float) (draw.cell.z() - camera.z);
            float y = (float) (draw.groundY + 0.004D - camera.y);
            shadow.vertex(matrix, x, y, z).color(0.06F, 0.045F, 0.03F, draw.alpha).endVertex();
            shadow.vertex(matrix, x, y, z + 1).color(0.06F, 0.045F, 0.03F, draw.alpha).endVertex();
            shadow.vertex(matrix, x + 1, y, z + 1).color(0.06F, 0.045F, 0.03F, draw.alpha).endVertex();
            shadow.vertex(matrix, x + 1, y, z).color(0.06F, 0.045F, 0.03F, draw.alpha).endVertex();
        }
        BUFFERS.endBatch(RenderType.debugQuads());
    }

    private record GroundDraw(TortoiseQuake.Cell cell, double groundY, float alpha) {
    }

    private static final Patch MISS = new Patch(null, null, 0, 0.0D);

    /** Collision and state decide whether ground exists; a culled UP quad is not required. */
    @Nullable
    private static Patch sample(Level level, int bx, int bz, double centerY) {
        if (!level.hasChunkAt(BlockPos.containing(bx, centerY, bz))) return null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
        int start = Math.min(top, Mth.floor(centerY + SEARCH_UP));
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(centerY - SEARCH_DOWN));
        for (int y = start; y >= bottom; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                    || state.getRenderShape() != RenderShape.MODEL) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double surface = y + shape.max(Direction.Axis.Y);
            if (Math.abs(surface - centerY) > 2.0D) continue;
            BlockState above = level.getBlockState(pos.above());
            if (!above.getCollisionShape(level, pos.above()).isEmpty() || !above.getFluidState().isEmpty()) continue;
            return new Patch(state, pos, LevelRenderer.getLightColor(level, pos.above()), surface);
        }
        return null;
    }

    /** Use the same Forge/Minecraft block draw path as the persistent rock pile. */
    private static void drawPatch(Minecraft mc, PoseStack poses, Vec3 camera, Vec3 center,
                                  TortoiseQuake.Cell cell, Patch patch, double lift) {
        double distance = Math.max(1.0D, cell.distance());
        float pitch = (float) ((cell.z() + 0.5D - center.z) / distance * lift * 4.0D);
        float roll = (float) (-(cell.x() + 0.5D - center.x) / distance * lift * 4.0D);
        double pivotY = patch.groundY - patch.position.getY();
        poses.pushPose();
        poses.translate(cell.x() - camera.x, patch.position.getY() + lift + 0.003D - camera.y, cell.z() - camera.z);
        poses.translate(0.5D, pivotY, 0.5D);
        poses.mulPose(Axis.XP.rotationDegrees(pitch));
        poses.mulPose(Axis.ZP.rotationDegrees(roll));
        poses.translate(-0.5D, -pivotY, -0.5D);
        mc.getBlockRenderer().renderSingleBlock(patch.state, poses, BUFFERS, patch.light, OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }
}
