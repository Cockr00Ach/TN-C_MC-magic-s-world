package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseParticles;
import com.tnc.tnc.boss.fire.VolcanicFireballEntity;
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
 * 火球（飞行中）与喷吐蓄力的球面火团。
 *
 * <p>纯视觉：只读取服务端同步过来的实体位置与蓄力开始时刻，不参与碰撞与伤害。
 * 每 tick 在球体内部与三层球面上补位，粒子寿命短，新进入的客户端下一 tick
 * 就能看到完整球体，不依赖“只发一次”的粒子包。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class FireBlobFx {

    private static final double MAX_DIST_SQR = 96.0D * 96.0D;

    /** 飞行火球的视觉半径（格）：直径约 1.6 格，按 Boss 嘴部比例校准。 */
    private static final double FLIGHT_RADIUS = 0.80D;
    /** 蓄力球最终半径（格）。 */
    private static final double CHARGE_RADIUS = 0.78D;

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

        // 1) 飞行中的火球：内部 + 三层球面 + 短尾迹
        for (VolcanicFireballEntity ball : level.getEntitiesOfClass(VolcanicFireballEntity.class,
                mc.player.getBoundingBox().inflate(96.0D))) {
            if (ball.position().distanceToSqr(eye) > MAX_DIST_SQR) {
                continue;
            }
            int phase = PHASE.computeIfAbsent(ball.getId(), id -> level.random.nextInt(360));
            Vec3 center = ball.position();
            Vec3 motion = ball.getDeltaMovement();
            spawnFlight(level, center, FLIGHT_RADIUS, now + phase);
            if (motion.lengthSqr() > 1.0E-6D) {
                Vec3 back = motion.normalize().scale(-0.62D);
                for (int i = 0; i < 2; i++) {
                    double jitter = (level.random.nextDouble() - 0.5D) * 0.24D;
                    spawn(level, TortoiseParticles.FIRE_EDGE.get(),
                            center.x + back.x + jitter, center.y + back.y + jitter, center.z + back.z + jitter,
                            -motion.x * 0.05D, -motion.y * 0.05D + 0.01D, -motion.z * 0.05D);
                }
            }
        }

        // 2) 蓄力：球体随进度长大，外圈火星向核心汇聚
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
            double radius = CHARGE_RADIUS * (0.20D + 0.80D * progress);
            spawnCharge(level, center, radius, progress);
        }

        Iterator<Map.Entry<Integer, long[]>> it = CHARGE_SEEN.entrySet().iterator();
        while (it.hasNext()) {
            if (!(level.getEntity(it.next().getKey()) instanceof VolcanicTortoiseEntity)) {
                it.remove();
            }
        }
    }

    /** 飞行体：内部随机 8 点 + 三层球面，形成实心感而不是空心球。 */
    private static void spawnFlight(ClientLevel level, Vec3 center, double radius, long seed) {
        for (int i = 0; i < 8; i++) {
            Vec3 p = randomInside(level, radius * 0.62D);
            spawn(level, (i % 3 == 0 ? TortoiseParticles.FIRE_CORE : TortoiseParticles.FIRE_MID).get(),
                    center.x + p.x, center.y + p.y, center.z + p.z,
                    p.x * 0.02D, p.y * 0.02D + 0.01D, p.z * 0.02D);
        }
        shell(level, center, radius * 0.45D, 6, TortoiseParticles.FIRE_CORE.get(), seed);
        shell(level, center, radius * 0.78D, 10, TortoiseParticles.FIRE_MID.get(), seed + 17L);
        shell(level, center, radius, 8, TortoiseParticles.FIRE_EDGE.get(), seed + 31L);
    }

    /** 蓄力体：短寿命粒子，发射瞬间不会在嘴前留下第二颗球。 */
    private static void spawnCharge(ClientLevel level, Vec3 center, double radius, float progress) {
        int inside = 3 + (int) (progress * 4.0F);
        for (int i = 0; i < inside; i++) {
            Vec3 p = randomInside(level, radius * 0.7D);
            spawn(level, TortoiseParticles.FIRE_CHARGE.get(),
                    center.x + p.x, center.y + p.y, center.z + p.z, 0.0D, 0.0D, 0.0D);
        }
        int shellCount = 2 + (int) (progress * 5.0F);
        shell(level, center, radius, shellCount, TortoiseParticles.FIRE_CHARGE.get(), level.random.nextLong());
    }

    private static Vec3 randomInside(ClientLevel level, double radius) {
        double x = level.random.nextGaussian() * radius * 0.45D;
        double y = level.random.nextGaussian() * radius * 0.45D;
        double z = level.random.nextGaussian() * radius * 0.45D;
        Vec3 v = new Vec3(x, y, z);
        double len = v.length();
        return len > radius ? v.scale(radius / len) : v;
    }

    /** 球面均匀分布：黄金角 + 逐点偏移，每 tick 不重样但覆盖稳定。 */
    private static void shell(ClientLevel level, Vec3 center, double radius, int count,
                              SimpleParticleType type, long seed) {
        for (int i = 0; i < count; i++) {
            double t = (seed + i) * 0.61803398875D + i * 0.37D;
            double y = 1.0D - 2.0D * ((t * 0.5D) % 1.0D);
            double r = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double a = 2.399963D * (seed + i);
            double ox = Math.cos(a) * r;
            double oz = Math.sin(a) * r;
            spawn(level, type, center.x + ox * radius, center.y + y * radius, center.z + oz * radius,
                    ox * 0.012D, 0.010D + level.random.nextDouble() * 0.010D, oz * 0.012D);
        }
    }

    private static void spawn(ClientLevel level, SimpleParticleType type,
                              double x, double y, double z, double vx, double vy, double vz) {
        // addAlwaysVisibleParticle：不受距离/数量限制裁剪，避免球体被削成零星火点。
        level.addAlwaysVisibleParticle(type, true, x, y, z, vx, vy, vz);
    }
}
