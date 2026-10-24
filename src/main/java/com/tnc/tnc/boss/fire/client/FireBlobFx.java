package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import com.tnc.tnc.boss.fire.VolcanicFireballEntity;
import com.tnc.tnc.boss.fire.VolcanicMortarEntity;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 火球（飞行中）与喷吐蓄力的火团表现。
 *
 * <p>纯视觉：只读取服务端同步的实体位置与蓄力开始时刻，不参与碰撞与伤害。
 * 火团由大量小火苗在三维空间里堆出来：内部密、外缘疏、偶尔伸出火舌，
 * 不再依赖放大整团的圆形贴图。每 tick 补位、寿命很短，新进入的客户端也
 * 能在下一 tick 看到完整火团，不依赖“只发一次”的粒子包。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class FireBlobFx {

    private static final double MAX_DIST_SQR = 96.0D * 96.0D;

    /** 飞行火球的视觉半径（格）：整体大小保持原技能比例。 */
    private static final double FLIGHT_RADIUS = 1.40D;
    /** 蓄力火团最终半径（格）。 */
    private static final double CHARGE_RADIUS = 1.23D;

    /** 每 tick 补位上限，配合 4–9 tick 寿命把同时存活粒子控制在 ~150 以内。 */
    private static final int MAX_SPAWN_PER_TICK = 88;

    /** bossId -> {serverStart, localTickAtStart} */
    private static final Map<Integer, long[]> CHARGE_SEEN = new ConcurrentHashMap<>();
    /** entityId -> 相位，避免所有火球的粒子分布完全同步。 */
    private static final Map<Integer, Integer> PHASE = new ConcurrentHashMap<>();

    private FireBlobFx() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            CHARGE_SEEN.clear();
            PHASE.clear();
            return;
        }
        long now = level.getGameTime();
        Vec3 eye = mc.player.position();

        // 1) 飞行中的火球：内部密火苗 + 中层 + 少量外缘火舌 + 短尾焰
        for (VolcanicFireballEntity ball : level.getEntitiesOfClass(VolcanicFireballEntity.class,
                mc.player.getBoundingBox().inflate(96.0D))) {
            if (ball.position().distanceToSqr(eye) > MAX_DIST_SQR) {
                continue;
            }
            int phase = PHASE.computeIfAbsent(ball.getId(), id -> level.random.nextInt(360));
            Vec3 center = ball.position();
            Vec3 motion = ball.getDeltaMovement();
            spawnFlight(level, center, FLIGHT_RADIUS, now + phase, motion);
        }

        // 1b) 喷发弹幕：同一套火团，但更小、尾迹更短，整场共享粒子预算
        int mortarBudget = 260;
        FireBlobParticle.pushOverrides(2, 4, 1.75F);
        try {
            for (VolcanicMortarEntity ball : level.getEntitiesOfClass(VolcanicMortarEntity.class,
                    mc.player.getBoundingBox().inflate(96.0D))) {
                if (mortarBudget <= 0) {
                    break;
                }
                if (ball.position().distanceToSqr(eye) > MAX_DIST_SQR) {
                    continue;
                }
                int phase = PHASE.computeIfAbsent(ball.getId(), id -> level.random.nextInt(360));
                mortarBudget -= spawnMortar(level, ball.position(), ball.getDeltaMovement(), now + phase, mortarBudget);
            }
        } finally {
            FireBlobParticle.clearOverrides();
        }

        // 2) 蓄力：从几颗小火苗长成一团，随进度加密
        for (VolcanicTortoiseEntity boss : level.getEntitiesOfClass(VolcanicTortoiseEntity.class,
                mc.player.getBoundingBox().inflate(96.0D))) {
            int start = boss.spitChargeStart();
            if (start < 0) {
                CHARGE_SEEN.remove(boss.getId());
                continue;
            }
            long[] seen = CHARGE_SEEN.get(boss.getId());
            if (seen == null || seen[0] != start) {
                CHARGE_SEEN.put(boss.getId(), new long[]{start, now});
                continue;
            }
            int total = Math.max(1, boss.spitChargeTicks());
            long age = now - seen[1];
            if (age < 0 || age > total + 2) {
                continue;
            }
            float progress = Mth.clamp(age / (float) total, 0.0F, 1.0F);
            Vec3 center = SpitChargeFx.chargeCenter(boss);
            double radius = CHARGE_RADIUS * (0.18D + 0.82D * progress);
            spawnCharge(level, center, radius, progress);
        }

        Iterator<Map.Entry<Integer, long[]>> it = CHARGE_SEEN.entrySet().iterator();
        while (it.hasNext()) {
            if (!(level.getEntity(it.next().getKey()) instanceof VolcanicTortoiseEntity)) {
                it.remove();
            }
        }
    }

    /**
     * 飞行体：内部高斯分布 + 中层 + 外缘，外加几颗沿速度反向的尾焰。
     * 粒子速度很小，整体跟着投射物走，不会越飞越散。
     */
    private static void spawnFlight(ClientLevel level, Vec3 center, double radius, long seed, Vec3 motion) {
        int budget = MAX_SPAWN_PER_TICK;

        budget -= spawnGaussian(level, center, radius * 0.62D, 40,
                TortoiseParticles.FIRE_CORE.get(), TortoiseParticles.FIRE_MID.get(), budget);
        budget -= spawnShell(level, center, radius * 0.82D, 22,
                TortoiseParticles.FIRE_MID.get(), seed + 17L, budget);
        budget -= spawnShell(level, center, radius * 1.02D, 12,
                TortoiseParticles.FIRE_EDGE.get(), seed + 31L, budget);

        // 尾焰：沿速度反方向拉开，越靠后越小
        if (motion.lengthSqr() > 1.0E-6D && budget > 0) {
            Vec3 back = motion.normalize().scale(-1.0D);
            int tail = Math.min(budget, 4);
            for (int i = 0; i < tail; i++) {
                double dist = radius * (0.75D + 0.35D * level.random.nextFloat()) + i * 0.16D;
                double jitter = (level.random.nextDouble() - 0.5D) * 0.18D;
                spawn(level, TortoiseParticles.FIRE_EDGE.get(),
                        center.x + back.x * dist + jitter,
                        center.y + back.y * dist + jitter * 0.6D + (level.random.nextDouble() - 0.5D) * 0.10D,
                        center.z + back.z * dist + jitter,
                        back.x * 0.02D, 0.012D, back.z * 0.02D);
            }
        }
    }

    /** 弹幕火球：0.95 格半径，核心+边缘各一圈，尾部 2 颗，整颗约 29 颗粒子。 */
    private static int spawnMortar(ClientLevel level, Vec3 center, Vec3 motion, long seed, int budget) {
        int spawned = 0;
        spawned += spawnGaussian(level, center, 0.50D, 20,
                TortoiseParticles.FIRE_CORE.get(), TortoiseParticles.FIRE_MID.get(), budget - spawned);
        spawned += spawnShell(level, center, 0.72D, 14, TortoiseParticles.FIRE_MID.get(), seed + 7L, budget - spawned);
        spawned += spawnShell(level, center, 0.90D, 9, TortoiseParticles.FIRE_EDGE.get(), seed + 13L, budget - spawned);
        if (motion.lengthSqr() > 1.0E-6D && spawned < budget) {
            Vec3 back = motion.normalize().scale(-1.0D);
            for (int i = 0; i < 2 && spawned < budget; i++) {
                double dist = 0.95D + i * 0.18D;
                spawn(level, TortoiseParticles.FIRE_EDGE.get(),
                        center.x + back.x * dist, center.y + back.y * dist, center.z + back.z * dist,
                        back.x * 0.02D, -0.01D, back.z * 0.02D);
                spawned++;
            }
        }
        return spawned;
    }

    /** 蓄力火团：起手只有几颗，越接近发射越密，尺寸随进度长。 */
    private static void spawnCharge(ClientLevel level, Vec3 center, double radius, float progress) {
        int budget = 4 + (int) (progress * 8.0F);
        budget -= spawnGaussian(level, center, radius * 0.70D, 3 + (int) (progress * 5.0F),
                TortoiseParticles.FIRE_CHARGE.get(), TortoiseParticles.FIRE_CHARGE.get(), budget);
        spawnShell(level, center, radius, 1 + (int) (progress * 3.0F),
                TortoiseParticles.FIRE_CHARGE.get(), level.random.nextLong(), budget);
    }

    /** 高斯分布的火苗：中间密、边缘疏，分布不均匀。 */
    private static int spawnGaussian(ClientLevel level, Vec3 center, double radius, int count,
                                     SimpleParticleType primary, SimpleParticleType secondary, int budget) {
        int spawned = 0;
        for (int i = 0; i < count && spawned < budget; i++) {
            double x = level.random.nextGaussian() * radius * 0.42D;
            double y = level.random.nextGaussian() * radius * 0.42D;
            double z = level.random.nextGaussian() * radius * 0.42D;
            Vec3 v = new Vec3(x, y, z);
            double len = v.length();
            if (len > radius) {
                v = v.scale(radius / len);
            }
            SimpleParticleType type = (i % 4 == 0) ? secondary : primary;
            spawn(level, type,
                    center.x + v.x, center.y + v.y, center.z + v.z,
                    v.x * 0.03D, v.y * 0.03D + 0.006D, v.z * 0.03D);
            spawned++;
        }
        return spawned;
    }

    /** 外缘火舌：球面附近少量点位，带一点向外速度，然后自然消散。 */
    private static int spawnShell(ClientLevel level, Vec3 center, double radius, int count,
                                  SimpleParticleType type, long seed, int budget) {
        int spawned = 0;
        for (int i = 0; i < count && spawned < budget; i++) {
            double t = (seed + i) * 0.61803398875D + i * 0.37D;
            double y = 1.0D - 2.0D * ((t * 0.5D) % 1.0D);
            double r = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double a = 2.399963D * (seed + i);
            double ox = Math.cos(a) * r;
            double oz = Math.sin(a) * r;
            spawn(level, type,
                    center.x + ox * radius, center.y + y * radius, center.z + oz * radius,
                    ox * 0.020D, 0.008D + level.random.nextDouble() * 0.014D, oz * 0.020D);
            spawned++;
        }
        return spawned;
    }

    private static void spawn(ClientLevel level, SimpleParticleType type,
                              double x, double y, double z, double vx, double vy, double vz) {
        // addAlwaysVisibleParticle：不受距离/数量限制裁剪，避免火团被削成零星火点。
        level.addAlwaysVisibleParticle(type, true, x, y, z, vx, vy, vz);
    }
}
