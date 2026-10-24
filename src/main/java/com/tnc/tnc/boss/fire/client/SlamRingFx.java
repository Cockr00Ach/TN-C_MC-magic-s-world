package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 重踏火环的客户端实例管理。
 *
 * <p>服务端在砸地瞬间同步「中心 + 开始时刻」（{@link VolcanicTortoiseEntity#slamRingCenter()} /
 * {@link VolcanicTortoiseEntity#slamRingStart()}）。客户端第一次看到某个 start 值时创建一次
 * {@link Ring}，一次性生成整圈粒子；之后每颗粒子都按同一个 {@code R(t)} 推进。
 *
 * <p>关键点：所有粒子共享同一个半径函数，只有很小的径向偏移用于形成火带宽度，
 * 所以任何时刻屏幕上只有一条火带；环消失时整批粒子一起移除，不会在旧半径留下火圈。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SlamRingFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/SlamRing");
    /** 调试开关：-Dtnc.tortoise.attack.debug=true 时额外提供 /tnc_ring 并打印建环日志。 */
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.attack.debug");

    /** 传播时长（tick），与服务端伤害环一致。 */
    public static final int TICKS = VolcanicTortoiseEntity.SLAM_RING_TICKS;
    /** 最大半径（格）。 */
    public static final double MAX_RADIUS = VolcanicTortoiseEntity.SLAM_RING_RADIUS;
    /** 目标圆周间距（格）：约 0.30 格 → 环上约 460 个位置，相邻火焰互相重叠。 */
    private static final double SPACING = 0.30D;
    /** 主环圆周位置数：2πR / 间距。 */
    public static final int COUNT = (int) Math.ceil(Math.PI * 2.0D * MAX_RADIUS / SPACING);
    /** 额外的高火舌数量：叠在低矮主环上，制造局部的高峰，不参与铺满整圈。 */
    public static final int EXTRA = 200;
    /** 全部粒子数（主环 + 高火舌）。 */
    public static final int TOTAL = COUNT + EXTRA;
    /** 火带半宽（格）：粒子在自己的半径上再 ±这个值，形成约 1 格宽的带。 */
    private static final double BAND_HALF = 0.34D;

    private static final Map<Integer, Ring> ACTIVE = new HashMap<>();

    /** 正在批量生成的环：{@code addParticle} 是同步创建粒子，provider 从这里取下标。 */
    static Ring pending;
    static int pendingIndex;

    private SlamRingFx() {
    }

    /** 一道火环的共享数据。 */
    public static final class Ring {
        final double centerX;
        final double centerY;
        final double centerZ;
        final int serverStart;
        /** 客户端第一次看到本环的 tick，粒子从这里开始计龄。 */
        final long bornAt;
        final double[] angle = new double[TOTAL];
        final double[] offset = new double[TOTAL];
        /** 竖直高度（格），0.5~0.9 为主，少量 1.0~1.35。 */
        final double[] height = new double[TOTAL];
        /** 贴片宽度（格）。 */
        final double[] width = new double[TOTAL];
        /** 顶部沿半径向外倾倒的距离（格），表现冲击波向外推进。 */
        final double[] lean = new double[TOTAL];
        final float[] phase = new float[TOTAL];
        final boolean[] mirror = new boolean[TOTAL];
        boolean expired;

        Ring(Vec3 center, int serverStart, long bornAt, ClientLevel level) {
            this.centerX = center.x;
            this.centerY = center.y;
            this.centerZ = center.z;
            this.serverStart = serverStart;
            this.bornAt = bornAt;

            // 主环：等距铺满整圈，高度沿圆周连续起伏（几段自然波峰），不是每株独立随机。
            for (int i = 0; i < COUNT; i++) {
                double a = Math.PI * 2.0D * i / COUNT;
                angle[i] = a + (level.random.nextDouble() - 0.5D) * (Math.PI * 2.0D / COUNT) * 0.45D;
                offset[i] = (level.random.nextDouble() - 0.5D) * 2.0D * BAND_HALF;
                double wave = 0.64D + 0.15D * Math.sin(3.0D * a + 1.3D) + 0.08D * Math.sin(7.0D * a + 0.4D);
                height[i] = clamp(wave, 0.50D, 0.90D);
                width[i] = 0.60D + level.random.nextDouble() * 0.12D;
                lean[i] = 0.14D + 0.18D * height[i];
                phase[i] = level.random.nextFloat();
                mirror[i] = level.random.nextBoolean();
            }
            // 额外火舌：少量局部高峰，少量更窄更斜，用来打破一圈等高的围栏感。
            for (int i = COUNT; i < TOTAL; i++) {
                angle[i] = level.random.nextDouble() * Math.PI * 2.0D;
                offset[i] = (level.random.nextDouble() - 0.5D) * 2.0D * BAND_HALF;
                height[i] = 0.98D + level.random.nextDouble() * 0.32D;
                width[i] = 0.44D + level.random.nextDouble() * 0.16D;
                lean[i] = 0.26D + 0.16D * level.random.nextDouble();
                phase[i] = level.random.nextFloat();
                mirror[i] = level.random.nextBoolean();
            }
        }

        /** 当前传播半径；到最大半径后停住，由 {@link #expired} 统一清理。 */
        double radius(float age) {
            double t = Math.max(0.0D, Math.min(1.0D, age / (double) TICKS));
            return MAX_RADIUS * t;
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss)) {
                continue;
            }
            int start = boss.slamRingStart();
            Ring ring = ACTIVE.get(boss.getId());
            if (start < 0) {
                if (ring != null) {
                    ring.expired = true;
                    ACTIVE.remove(boss.getId());
                }
                continue;
            }
            if (ring == null || ring.serverStart != start) {
                if (ring != null) {
                    ring.expired = true;
                }
                Ring fresh = new Ring(boss.slamRingCenter(), start, level.getGameTime(), level);
                ACTIVE.put(boss.getId(), fresh);
                // 离得太远的环不生成粒子（伤害仍由服务端正常结算）。
                var camera = mc.gameRenderer.getMainCamera().getPosition();
                double dist = boss.slamRingCenter().distanceToSqr(camera);
                if (DEBUG) {
                    LOGGER.info("[TN-C SlamRing] new ring id={} start={} center={} dist={}",
                            boss.getId(), start, boss.slamRingCenter(), Math.sqrt(dist));
                }
                if (dist <= 96.0D * 96.0D) {
                    spawn(level, fresh);
                }
            }
        }
        if (ACTIVE.size() > 16) {
            ACTIVE.values().forEach(r -> r.expired = true);
            ACTIVE.clear();
        }
    }

    private static void spawn(ClientLevel level, Ring ring) {
        pending = ring;
        pendingIndex = 0;
        try {
            for (int i = 0; i < TOTAL; i++) {
                // alwaysVisible：跳过距离/粒子数量限制，保证主环不会因为客户端设置被裁掉。
                level.addAlwaysVisibleParticle(TortoiseParticles.SLAM_FLAME.get(),
                        ring.centerX, ring.centerY, ring.centerZ, 0.0D, 0.0D, 0.0D);
            }
        } finally {
            pending = null;
        }
        if (DEBUG) {
            LOGGER.info("[TN-C SlamRing] spawned {} particles, index={}", TOTAL, pendingIndex);
        }
    }

    /** 调试用：在指定位置直接放一道环（客户端本地，不经过 Boss / 服务端）。 */
    public static void debugRing(Vec3 center) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Ring ring = new Ring(center, (int) (System.nanoTime() & 0x3FFFFFFF), level.getGameTime(), level);
        ACTIVE.put(-1, ring);
        spawn(level, ring);
    }

    /** 调试命令 /tnc_ring：在脚下放一道环，用来单独确认粒子环本身能不能显示。 */
    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        if (!DEBUG) {
            return;
        }
        event.getDispatcher().register(Commands.literal("tnc_ring").executes(ctx -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return 0;
            }
            debugRing(player.position());
            LOGGER.info("[TN-C SlamRing] debug ring at {}", player.position());
            return 1;
        }));
    }
}
