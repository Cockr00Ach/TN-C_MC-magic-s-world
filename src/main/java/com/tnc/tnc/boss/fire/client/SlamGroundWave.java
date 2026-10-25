package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
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
 * 每块副本的 x/z 固定在原地，只随波峰抬起、轻微倾斜，波过后回落到 0 并停止绘制。</p>
 *
 * <p>方块本身、碰撞、伤害、击退都不受影响。发光波带（{@link QuakeBandFx}）
 * 默认关闭，需要时用 {@code -Dtnc.tortoise.quake.glow=true} 打开。</p>
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlamGroundWave {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/quake");

    /** 圆周采样数。 */
    private static final int ANGLES = 128;
    /** 波峰前后各补一层，避免只画一条细线。 */
    private static final double[] RADIAL_OFFSETS = {-1.05D, 0.0D, 1.05D};
    /** 首轮波峰抬升（格）。 */
    private static final float MAX_LIFT = 0.68F;
    /** 抬升包络宽度（格）。 */
    private static final double SIGMA = 0.95D;
    /** 单帧最多绘制多少块。 */
    private static final int MAX_PATCHES = 460;
    /** 超过这个距离不画（火环仍可见）。 */
    private static final double RENDER_DIST = 64.0D;
    /** 波结束后的淡出 tick。 */
    private static final int FADE_OUT = 6;
    /** 采样窗口：以施法高度为中心，避免跳到洞穴屋顶。 */
    private static final int SEARCH_UP = 6;
    private static final int SEARCH_DOWN = 8;
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.quake.debug");

    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(262144));

    private static final List<Wave> ACTIVE = new ArrayList<>();

    private SlamGroundWave() {
    }

    private static final class Wave {
        final Vec3 center;
        final long startTick;
        double bornAt;
        final Map<Long, Patch> patches = new HashMap<>();
        int lastLogTick;

        Wave(Vec3 center, long startTick) {
            this.center = center;
            this.startTick = startTick;
        }
    }

    /** 一块地表副本的静态数据。 */
    private static final class Patch {
        final TextureAtlasSprite top;
        final TextureAtlasSprite[] sides = new TextureAtlasSprite[4];
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
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ACTIVE.clear();
            return;
        }
        for (VolcanicTortoiseEntity boss : mc.level.getEntitiesOfClass(VolcanicTortoiseEntity.class,
                mc.player.getBoundingBox().inflate(96.0D))) {
            long start = boss.quakeWaveStart();
            if (start < 0) {
                continue;
            }
            Vec3 center = boss.quakeWaveCenter();
            boolean known = false;
            for (Wave wave : ACTIVE) {
                if (wave.startTick == start) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                Wave wave = new Wave(center, start);
                wave.bornAt = mc.level.getGameTime();
                ACTIVE.add(wave);
            }
        }
        ACTIVE.removeIf(wave -> mc.level.getGameTime() - wave.bornAt
                > VolcanicTortoiseEntity.QUAKE_RING_TICKS + FADE_OUT + 4);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
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
        double now = level.getGameTime() + event.getPartialTick();
        Matrix4f matrix = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z);

        VertexConsumer buffer = BUFFERS.getBuffer(RenderType.solid());
        int drawn = 0;

        for (Wave wave : ACTIVE) {
            double age = now - wave.bornAt;
            int life = VolcanicTortoiseEntity.QUAKE_RING_TICKS;
            if (age > life + FADE_OUT) {
                continue;
            }
            double radius = VolcanicTortoiseEntity.QUAKE_RADIUS * Mth.clamp(age / life, 0.0D, 1.0D);
            float fade = age <= life ? 1.0F : (float) (1.0D - (age - life) / FADE_OUT);
            if (wave.center.distanceToSqr(camera) > RENDER_DIST * RENDER_DIST) {
                continue;
            }

            for (int i = 0; i < ANGLES && drawn < MAX_PATCHES; i++) {
                double angle = (Math.PI * 2.0D) * i / ANGLES;
                double dirX = Math.cos(angle);
                double dirZ = Math.sin(angle);
                for (double offset : RADIAL_OFFSETS) {
                    double r = radius + offset;
                    if (r <= 0.0D) {
                        continue;
                    }
                    double x = wave.center.x + dirX * r;
                    double z = wave.center.z + dirZ * r;
                    int bx = Mth.floor(x);
                    int bz = Mth.floor(z);
                    double dist = Math.sqrt((x - wave.center.x) * (x - wave.center.x)
                            + (z - wave.center.z) * (z - wave.center.z));
                    double delta = radius - dist;
                    double lift = MAX_LIFT * Math.exp(-(delta * delta) / (SIGMA * SIGMA)) * fade;
                    if (lift < 0.03D) {
                        continue;
                    }

                    long key = BlockPos.asLong(bx, 0, bz);
                    Patch patch = wave.patches.get(key);
                    if (patch == null) {
                        patch = sample(level, bx, bz, wave.center.y);
                        if (patch == null) {
                            patch = MISS;
                        }
                        wave.patches.put(key, patch);
                    }
                    if (patch == MISS || Math.abs(patch.groundY - wave.center.y) > 6.0D) {
                        continue;
                    }
                    drawPatch(buffer, matrix, bx, bz, patch, lift);
                    drawn++;
                }
            }

            if (DEBUG && level.getGameTime() - wave.lastLogTick > 20) {
                wave.lastLogTick = (int) level.getGameTime();
                LOGGER.info("[TN-C QuakeWave] radius={} patches={} cache={}",
                        String.format("%.1f", radius), drawn, wave.patches.size());
            }
        }

        BUFFERS.endBatch(RenderType.solid());
    }

    /** 缓存里的“采样失败”标记。 */
    private static final Patch MISS = new Patch(null, 0xFFFFFF, 0, 0.0D);

    /** 采一块地表副本：真实方块状态、顶面与侧面贴图、染色与光照。 */
    @Nullable
    private static Patch sample(Level level, int bx, int bz, double centerY) {
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
            double surface = shape.max(Direction.Axis.Y);
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
                    mc.getBlockColors().getColor(state, level, pos, 0),
                    LevelRenderer.getLightColor(level, pos.above()),
                    y + surface);
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                List<BakedQuad> quads = model.getQuads(state, dir, random);
                patch.sides[sideIndex(dir)] = quads.isEmpty() ? topSprite : quads.get(0).getSprite();
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
                                  Patch patch, double lift) {
        float x0 = bx;
        float z0 = bz;
        float x1 = bx + 1.0F;
        float z1 = bz + 1.0F;
        float y0 = (float) patch.groundY;
        float top = y0 + (float) lift;

        // 轻微倾斜：四角有细小高差，避免整块像一块平板。
        float tiltX = ((bx * 31 + bz * 17) & 7) / 7.0F - 0.5F;
        float tiltZ = ((bx * 13 + bz * 29) & 7) / 7.0F - 0.5F;
        float d = (float) lift * 0.12F;
        float t00 = top + (tiltX + tiltZ) * d;
        float t01 = top + (tiltX - tiltZ) * d;
        float t11 = top - (tiltX + tiltZ) * d;
        float t10 = top - (tiltX - tiltZ) * d;

        float r = ((patch.tint >> 16) & 0xFF) / 255.0F;
        float g = ((patch.tint >> 8) & 0xFF) / 255.0F;
        float b = (patch.tint & 0xFF) / 255.0F;

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
        side(buffer, matrix, patch.sides[0], x1, y0, z0, x0, y0, z0, x0, t00, z0, x1, t10, z0,
                r, g, b, patch.light);
        side(buffer, matrix, patch.sides[1], x0, y0, z1, x1, y0, z1, x1, t11, z1, x0, t01, z1,
                r, g, b, patch.light);
        side(buffer, matrix, patch.sides[2], x0, y0, z0, x0, y0, z1, x0, t01, z1, x0, t00, z0,
                r, g, b, patch.light);
        side(buffer, matrix, patch.sides[3], x1, y0, z1, x1, y0, z0, x1, t10, z0, x1, t11, z1,
                r, g, b, patch.light);
    }

    /** 一面侧壁；正反两种绕序各写一次，避免观察角度不同导致整面消失。 */
    private static void side(VertexConsumer buffer, Matrix4f matrix, TextureAtlasSprite sprite,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, int light) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float shade = 0.78F;
        // 底边在 v1，顶边在 v0
        vertex(buffer, matrix, ax, ay, az, u0, v1, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, 1.0F);
        vertex(buffer, matrix, bx, by, bz, u1, v1, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, 1.0F);
        vertex(buffer, matrix, cx, cy, cz, u1, v0, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, 1.0F);
        vertex(buffer, matrix, dx, dy, dz, u0, v0, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, 1.0F);
        // 反向绕序
        vertex(buffer, matrix, dx, dy, dz, u0, v0, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, -1.0F);
        vertex(buffer, matrix, cx, cy, cz, u1, v0, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, -1.0F);
        vertex(buffer, matrix, bx, by, bz, u1, v1, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, -1.0F);
        vertex(buffer, matrix, ax, ay, az, u0, v1, r * shade, g * shade, b * shade, light, 0.0F, 0.0F, -1.0F);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, int light,
                               float nx, float ny, float nz) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, 1.0F).uv(u, v).uv2(light)
                .normal(nx, ny, nz).endVertex();
    }
}
