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
 * <p>火带由三层组成，但三层共用同一个半径函数，所以视觉上仍是一道环：
 * <ul>
 *   <li>内层：贴地的橙红火尾，低、向外渐隐；</li>
 *   <li>中层：主波峰，最高最厚，橙黄带少量亮黄；</li>
 *   <li>外层：传播前沿，火舌大幅向外倾倒、变薄。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SlamRingFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/SlamRing");
    /** 调试开关：-Dtnc.tortoise.attack.debug=true 时额外提供 /tnc_ring 并打印建环日志。 */
    public static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.attack.debug");
    /** 调试：临时隐藏火环本体，只观察地面波（/tnc_ringhide 切换）。 */
    public static boolean HIDE_RING;

    /** 传播时长（tick），与服务端伤害环一致。 */
    public static final int TICKS = VolcanicTortoiseEntity.SLAM_RING_TICKS;
    /** 最大半径（格）。 */
    public static final double MAX_RADIUS = VolcanicTortoiseEntity.SLAM_RING_RADIUS;
    /** 圆周间距（格）：0.45 格，配合 0.8~1.0 格宽的贴片，相邻至少重叠一倍。 */
    private static final double SPACING = 0.45D;
    /** 每一层的圆周位置数。 */
    public static final int COUNT = (int) Math.ceil(Math.PI * 2.0D * MAX_RADIUS / SPACING);
    /** 层数：内 / 中 / 外。 */
    public static final int LAYERS = 3;
    /** 额外的高火舌数量（叠在中层上，制造局部高峰）。 */
    public static final int EXTRA = 120;
    /** 全部粒子数。 */
    public static final int TOTAL = COUNT * LAYERS + EXTRA;

    /** 火带内边界 / 外边界（相对 R(t)，单位格）：主火带约 2 格宽。 */
    public static final double BAND_INNER = -0.95D;
    public static final double BAND_OUTER = 1.05D;

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
        /** 客户端第一次看到本环的 tick。 */
        final long bornAt;
        final double[] angle = new double[TOTAL];
        final double[] offset = new double[TOTAL];
        final double[] height = new double[TOTAL];
        final double[] width = new double[TOTAL];
        final double[] lean = new double[TOTAL];
        final float[] phase = new float[TOTAL];
        final boolean[] mirror = new boolean[TOTAL];
        /** 0=内侧火尾，1=主波峰，2=外侧前沿。 */
        final int[] layer = new int[TOTAL];
        boolean expired;

        Ring(Vec3 center, int serverStart, long bornAt, ClientLevel level) {
            this.centerX = center.x;
            this.centerY = center.y;
            this.centerZ = center.z;
            this.serverStart = serverStart;
            this.bornAt = bornAt;

            for (int l = 0; l < LAYERS; l++) {
                for (int i = 0; i < COUNT; i++) {
                    int idx = l * COUNT + i;
                    double a = Math.PI * 2.0D * i / COUNT;
                    // 圆周主波峰：缓慢连续起伏，几段较宽的隆起，不是每株独立随机。
                    double wave = 0.86D + 0.16D * Math.sin(3.0D * a + 1.1D) + 0.07D * Math.sin(6.0D * a + 2.2D);
                    layer[idx] = l;
                    angle[idx] = a + (level.random.nextDouble() - 0.5D) * (Math.PI * 2.0D / COUNT) * 0.45D;
                    phase[idx] = level.random.nextFloat();
                    mirror[idx] = level.random.nextBoolean();
                    switch (l) {
                        case 0 -> {   // 内侧：低矮、橙红的衰减火尾
                            offset[idx] = BAND_INNER + 0.10D + level.random.nextDouble() * 0.24D;
                            height[idx] = 0.26D + level.random.nextDouble() * 0.24D;
                            width[idx] = 0.80D + level.random.nextDouble() * 0.20D;
                            lean[idx] = 0.02D + level.random.nextDouble() * 0.06D;
                        }
                        case 1 -> {   // 中层：主波峰
                            offset[idx] = -0.24D + level.random.nextDouble() * 0.50D;
                            height[idx] = (0.78D + level.random.nextDouble() * 0.22D) * wave;
                            width[idx] = 0.85D + level.random.nextDouble() * 0.20D;
                            lean[idx] = 0.24D + level.random.nextDouble() * 0.14D;
                        }
                        default -> {  // 外侧：向外倾倒的传播前沿
                            offset[idx] = 0.70D + level.random.nextDouble() * 0.30D;
                            height[idx] = (0.54D + level.random.nextDouble() * 0.24D) * wave;
                            width[idx] = 0.78D + level.random.nextDouble() * 0.18D;
                            lean[idx] = 0.55D + level.random.nextDouble() * 0.30D;
                        }
                    }
                }
            }
            for (int idx = COUNT * LAYERS; idx < TOTAL; idx++) {
                angle[idx] = level.random.nextDouble() * Math.PI * 2.0D;
                offset[idx] = -0.10D + level.random.nextDouble() * 0.40D;
                height[idx] = 1.02D + level.random.nextDouble() * 0.30D;
                width[idx] = 0.55D + level.random.nextDouble() * 0.14D;
                lean[idx] = 0.38D + level.random.nextDouble() * 0.16D;
                phase[idx] = level.random.nextFloat();
                mirror[idx] = level.random.nextBoolean();
                layer[idx] = 1;
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
        event.getDispatcher().register(Commands.literal("tnc_ringhide").executes(ctx -> {
            HIDE_RING = !HIDE_RING;
            LOGGER.info("[TN-C SlamRing] hide ring = {}", HIDE_RING);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        HIDE_RING ? "火环已隐藏（只看地面波）" : "火环已恢复"), true);
            }
            return 1;
        }));
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
