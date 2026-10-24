package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 重踏火环的客户端实例管理。
 *
 * <p>服务端在砸地瞬间同步「中心 + 开始时刻」（{@link VolcanicTortoiseEntity#slamRingCenter()} /
 * {@link VolcanicTortoiseEntity#slamRingStart()}）。客户端第一次看到某个 start 值时创建一次
 * {@link Ring}，一次性生成 {@link #COUNT} 颗粒子；之后每颗粒子都按同一个 {@code R(t)} 推进。
 *
 * <p>关键点：所有粒子共享同一个半径函数，只有很小的径向偏移用于形成火带宽度，
 * 所以任何时刻屏幕上只有一条火带；环消失时整批粒子一起移除，不会在旧半径留下火圈。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SlamRingFx {

    /** 传播时长（tick），与服务端伤害环一致。 */
    public static final int TICKS = VolcanicTortoiseEntity.SLAM_RING_TICKS;
    /** 最大半径（格）。 */
    public static final double MAX_RADIUS = VolcanicTortoiseEntity.SLAM_RING_RADIUS;
    /** 目标圆周间距（格）：约 0.30 格 → 环上约 460 个位置，相邻火焰互相重叠。 */
    private static final double SPACING = 0.30D;
    /** 圆周位置数：2πR / 间距。 */
    public static final int COUNT = (int) Math.ceil(Math.PI * 2.0D * MAX_RADIUS / SPACING);
    /** 火带半宽（格）：粒子在自己的半径上再 ±这个值，形成约 1 格宽的带。 */
    private static final double BAND_HALF = 0.42D;

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
        final double[] angle = new double[COUNT];
        final double[] offset = new double[COUNT];
        final double[] height = new double[COUNT];
        final float[] size = new float[COUNT];
        final float[] phase = new float[COUNT];
        boolean expired;

        Ring(Vec3 center, int serverStart, long bornAt, ClientLevel level) {
            this.centerX = center.x;
            this.centerY = center.y;
            this.centerZ = center.z;
            this.serverStart = serverStart;
            this.bornAt = bornAt;
            for (int i = 0; i < COUNT; i++) {
                // 均匀铺满整圈，再各自抖动一点，避免规则的点阵感，但不会产生空隙。
                double base = Math.PI * 2.0D * i / COUNT;
                angle[i] = base + (level.random.nextDouble() - 0.5D) * (Math.PI * 2.0D / COUNT) * 0.55D;
                offset[i] = (level.random.nextDouble() - 0.5D) * 2.0D * BAND_HALF;
                height[i] = 0.02D + level.random.nextDouble() * 0.10D;
                size[i] = 0.85F + level.random.nextFloat() * 0.45F;
                phase[i] = level.random.nextFloat();
            }
        }

        /** 当前传播半径；到最大半径后停住，由 {@link #expired} 统一清理。 */
        double radius(float age) {
            double t = Math.max(0.0D, Math.min(1.0D, age / (double) TICKS));
            return MAX_RADIUS * t;
        }
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
                if (boss.slamRingCenter().distanceToSqr(camera) <= 96.0D * 96.0D) {
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
            for (int i = 0; i < COUNT; i++) {
                level.addParticle(TortoiseParticles.SLAM_FLAME.get(),
                        ring.centerX, ring.centerY, ring.centerZ, 0.0D, 0.0D, 0.0D);
            }
        } finally {
            pending = null;
        }
    }
}
