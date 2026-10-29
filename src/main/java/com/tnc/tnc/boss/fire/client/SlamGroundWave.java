package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
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
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
 * 顶面用该方块顶面贴图，侧壁用同方块侧面贴图，高度等于当前抬升量。
 * 每块副本的 x/z 固定在原地，按固定格子的到达时刻抬起、径向倾斜，波过后轻微回弹、归位，凹带用短暂贴地暗影表现。</p>
 *
 * <p>方块本身、碰撞、伤害、击退都不受影响。发光波带（{@link QuakeBandFx}）
 * 默认关闭，需要时用 {@code -Dtnc.tortoise.quake.glow=true} 打开。</p>
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlamGroundWave {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/quake");

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
        final Map<Long, Patch> patches = new HashMap<>();

        Wave(VolcanicTortoiseEntity boss) {
            this.center = boss.quakeWaveCenter();
            this.owner = boss.getUUID();
            this.cast = boss.quakeWaveStart();
        }
    }

    /** 一块地表副本的静态数据。 */
    private static final class Patch {
        BlockState state;
        final TextureAtlasSprite top;
        final TextureAtlasSprite[] sides = new TextureAtlasSprite[4];
        final int[] sideTints = new int[4];
        final int tint;
        final int light;
        final double groundY;

        Patch(TextureAtlasSprite top, int tint, int light, double groundY) {
            this.top = top;
            this.tint = tint;
            this.light = light;
            this.groundY = groundY;
        }
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
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f();
        VertexConsumer buffer = BUFFERS.getBuffer(RenderType.solid());
        VertexConsumer glow = QuakeBandFx.enabled() ? QuakeBandFx.buffer() : null;
        int remaining = MAX_PATCHES;
        int remainingWaves = ACTIVE.size();
        for (Wave wave : ACTIVE) {
            int allowance = remaining / remainingWaves--;
            if (wave.center.distanceToSqr(camera) > RENDER_DIST * RENDER_DIST) continue;
            double age = wave.clock.age(wave.localTick
                    + (Minecraft.getInstance().isPaused() ? 0 : event.getPartialTick()));
            int count = Math.min(allowance, wave.cells.size());
            for (int i = 0; i < count; i++) {
                // Sample around the entire circumference if several waves share the budget.
                var cell = wave.cells.get(i * wave.cells.size() / count);
                if (!TortoiseQuake.visible(age, cell.distance())) continue;
                Patch patch = wave.patches.get(cell.key());
                if (patch == null || patch == MISS) continue;
                double lift = TortoiseQuake.lift(age, cell.distance());
                double shade = TortoiseQuake.shade(age, cell.distance());
                if (lift < 0.002D && shade > 0.995D) continue;
                drawPatch(buffer, matrix, cell.x(), cell.z(), patch, lift, shade, camera, wave.center);
                if (glow != null) QuakeBandFx.draw(glow, matrix, camera, cell, patch.groundY + lift, age);
                remaining--;
            }
        }
        BUFFERS.endBatch(RenderType.solid());
        if (glow != null) QuakeBandFx.endBatch();
    }

    /** 缓存里的“采样失败”标记。 */
    private static final Patch MISS = new Patch(null, 0xFFFFFF, 0, 0.0D);

    /** 采一块地表副本：真实方块状态、顶面与侧面贴图、染色与光照。 */
    @Nullable
    private static Patch sample(Level level, int bx, int bz, double centerY) {
        if (!level.hasChunkAt(BlockPos.containing(bx, centerY, bz))) return null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
        int start = Math.min(top, Mth.floor(centerY + SEARCH_UP));
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(centerY - SEARCH_DOWN));
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        RandomSource random = RandomSource.create(42L);

        for (int y = start; y >= bottom; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
                continue;
            }
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            var boxes = shape.toAabbs();
            if (boxes.size() != 1) continue;
            var bounds = boxes.get(0);
            if (bounds.minX != 0 || bounds.minZ != 0 || bounds.maxX != 1 || bounds.maxZ != 1) continue;
            BlockState above = level.getBlockState(pos.above());
            if (!above.getCollisionShape(level, pos.above()).isEmpty() || !above.getFluidState().isEmpty()) continue;
            double surface = shape.max(Direction.Axis.Y);
            if (Math.abs(y + surface - centerY) > 2.0D) continue;
            if (surface <= 0.02D) {
                continue;
            }
            var model = dispatcher.getBlockModel(state);
            List<BakedQuad> topQuads = model.getQuads(state, Direction.UP, random);
            if (topQuads.isEmpty()) {
                continue;
            }
            TextureAtlasSprite topSprite = topQuads.get(0).getSprite();
            Patch patch = new Patch(topSprite,
                    topQuads.get(0).isTinted()
                            ? mc.getBlockColors().getColor(state, level, pos, topQuads.get(0).getTintIndex()) : 0xFFFFFF,
                    LevelRenderer.getLightColor(level, pos.above()),
                    y + surface);
            patch.state = state;
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                random.setSeed(42L);
                List<BakedQuad> quads = model.getQuads(state, dir, random);
                patch.sides[sideIndex(dir)] = quads.isEmpty() ? topSprite : quads.get(0).getSprite();
                patch.sideTints[sideIndex(dir)] = !quads.isEmpty() && quads.get(0).isTinted()
                        ? mc.getBlockColors().getColor(state, level, pos, quads.get(0).getTintIndex()) : 0xFFFFFF;
            }
            return patch;
        }
        return null;
    }

    private static int sideIndex(Direction dir) {
        return switch (dir) {
            case NORTH -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /**
     * 画一块抬起的地表：顶面 + 四面侧壁。
     * 位置固定在原 x/z，只有高度随 lift 变化；波峰前后由调用方用同一包络计算。
     */
    private static void drawPatch(VertexConsumer buffer, Matrix4f matrix, int bx, int bz,
                                  Patch patch, double lift, double shade, Vec3 camera, Vec3 center) {
        float x0 = (float) (bx + 0.009D - camera.x);
        float z0 = (float) (bz + 0.009D - camera.z);
        float x1 = (float) (bx + 0.991D - camera.x);
        float z1 = (float) (bz + 0.991D - camera.z);
        float y0 = (float) (patch.groundY + 0.003D - camera.y);
        float top = y0 + (float) lift;

        // 轻微倾斜：四角有细小高差，避免整块像一块平板。
        double distance = Math.max(1.0D, Math.hypot(bx + 0.5D - center.x, bz + 0.5D - center.z));
        float tiltX = (float) ((bx + 0.5D - center.x) / distance * 0.5D);
        float tiltZ = (float) ((bz + 0.5D - center.z) / distance * 0.5D);
        float d = (float) lift * 0.12F;
        float t00 = top + (tiltX + tiltZ) * d;
        float t01 = top + (tiltX - tiltZ) * d;
        float t11 = top - (tiltX + tiltZ) * d;
        float t10 = top - (tiltX - tiltZ) * d;

        float r = ((patch.tint >> 16) & 0xFF) / 255.0F;
        float g = ((patch.tint >> 8) & 0xFF) / 255.0F;
        float b = (patch.tint & 0xFF) / 255.0F;
        r *= (float) shade; g *= (float) shade; b *= (float) shade;

        TextureAtlasSprite sprite = patch.top;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // 顶面：u 沿 +x，v 沿 +z
        vertex(buffer, matrix, x0, t00, z0, u0, v0, r, g, b, patch.light, 0.0F, 1.0F, 0.0F);
        vertex(buffer, matrix, x0, t01, z1, u0, v1, r, g, b, patch.light, 0.0F, 1.0F, 0.0F);
        vertex(buffer, matrix, x1, t11, z1, u1, v1, r, g, b, patch.light, 0.0F, 1.0F, 0.0F);
        vertex(buffer, matrix, x1, t10, z0, u1, v0, r, g, b, patch.light, 0.0F, 1.0F, 0.0F);

        if (lift < 0.04D) {
            return;
        }
        // 侧壁：底边在原地面，顶边在抬起的顶面；高度 = 当前抬升量。
        side(buffer, matrix, patch.sides[0], patch.sideTints[0], 0.0F, -1.0F, x1, y0, z0, x0, y0, z0, x0, t00, z0, x1, t10, z0,
                r, g, b, patch.light);
        side(buffer, matrix, patch.sides[1], patch.sideTints[1], 0.0F, 1.0F, x0, y0, z1, x1, y0, z1, x1, t11, z1, x0, t01, z1,
                r, g, b, patch.light);
        side(buffer, matrix, patch.sides[2], patch.sideTints[2], -1.0F, 0.0F, x0, y0, z0, x0, y0, z1, x0, t01, z1, x0, t00, z0,
                r, g, b, patch.light);
        side(buffer, matrix, patch.sides[3], patch.sideTints[3], 1.0F, 0.0F, x1, y0, z1, x1, y0, z0, x1, t10, z0, x1, t11, z1,
                r, g, b, patch.light);
    }

    /** 四面各用真实朝外绕序；不重复绘制背面。 */
    private static void side(VertexConsumer buffer, Matrix4f matrix, TextureAtlasSprite sprite, int tint, float nx, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, int light) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        r = ((tint >> 16) & 255) / 255.0F;
        g = ((tint >> 8) & 255) / 255.0F;
        b = (tint & 255) / 255.0F;
        float shade = 0.78F;
        // 底边在 v1，顶边在 v0
        vertex(buffer, matrix, ax, ay, az, u0, v1, r * shade, g * shade, b * shade, light, nx, 0.0F, nz);
        vertex(buffer, matrix, bx, by, bz, u1, v1, r * shade, g * shade, b * shade, light, nx, 0.0F, nz);
        vertex(buffer, matrix, cx, cy, cz, u1, v0, r * shade, g * shade, b * shade, light, nx, 0.0F, nz);
        vertex(buffer, matrix, dx, dy, dz, u0, v0, r * shade, g * shade, b * shade, light, nx, 0.0F, nz);

    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, int light,
                               float nx, float ny, float nz) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, 1.0F).uv(u, v).uv2(light)
                .normal(nx, ny, nz).endVertex();
    }
}
