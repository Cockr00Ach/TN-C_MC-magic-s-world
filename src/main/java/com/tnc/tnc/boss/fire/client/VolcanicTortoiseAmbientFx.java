package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 火山巨龟的环境表现：火山口轻烟、裂隙火星、以及偶尔一次的“小喷发”。
 *
 * <p>全部在客户端生成：服务端不发包、不加伤害、不改地形，所以：
 * <ul>
 *   <li>粒子只在本地出现，附近有几个玩家都不会被放大；</li>
 *   <li>按 client tick 生成，帧率高低不会改变生成频率；</li>
 *   <li>距离越远生成越少，超过 {@link #MAX_DISTANCE} 直接不生成。</li>
 * </ul>
 *
 * <p>位置不是写死的实体坐标，而是渲染时从 {@code shell}/{@code whorl} 骨骼矩阵里
 * 取出的“当前火山口/裂隙”世界坐标，见 {@link VolcanicTortoiseAnchorLayer}。
 * 这样阶段、朝向、缩放、待机/行走/攻击动画都会自然跟随。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VolcanicTortoiseAmbientFx {
    // ------------------------------------------------------------ 可调参数 --

    /** 超过这个距离（格）完全不生成。 */
    private static final double MAX_DISTANCE = 64.0D;
    /** 这个距离以内按 100% 生成，往外线性/台阶式减少。 */
    private static final double FULL_DISTANCE = 28.0D;
    /** 同时处理的 Boss 上限，防止有人刷一堆。 */
    private static final int MAX_BOSSES = 3;
    /** 每个 client tick 的全局粒子预算；帧率再高也不会超。 */
    private static final int MAX_PARTICLES_PER_TICK = 6;

    /** 一阶段 / 二阶段的烟、火星间隔（tick）。二阶段稍活跃。 */
    private static final int SMOKE_INTERVAL_P1 = 9;
    private static final int SMOKE_INTERVAL_P2 = 6;
    private static final int SPARK_INTERVAL_P1 = 7;
    private static final int SPARK_INTERVAL_P2 = 5;

    /** 小喷发间隔（tick）：一阶段 13~23 秒，二阶段 8~14 秒。 */
    private static final int BURST_DELAY_MIN_P1 = 260;
    private static final int BURST_DELAY_MAX_P1 = 460;
    private static final int BURST_DELAY_MIN_P2 = 160;
    private static final int BURST_DELAY_MAX_P2 = 280;
    /** 小喷发持续时间：14 tick ≈ 0.7 秒，短促。 */
    private static final int BURST_TICKS = 14;
    /** 攻击/转阶段结束后，至少再等这么久才允许下一次小喷发。 */
    private static final int BURST_RETRY_DELAY = 60;

    /** 烟的生命周期（tick）与大小；故意短，避免长期糊住火山口。 */
    private static final int SMOKE_LIFE_MIN = 18;
    private static final int SMOKE_LIFE_MAX = 30;
    private static final float SMOKE_SCALE_MIN = 0.55F;
    private static final float SMOKE_SCALE_MAX = 0.95F;
    /** 火星生命周期与大小。 */
    private static final int SPARK_LIFE_MIN = 9;
    private static final int SPARK_LIFE_MAX = 17;
    private static final float SPARK_SCALE_MIN = 0.40F;
    private static final float SPARK_SCALE_MAX = 0.72F;

    /**
     * 火山口在 whorl 骨骼局部坐标里的位置（模型单位，1 格 = 16 单位）。
     * 一阶段壳顶约 y=71~78；二阶段壳顶约 y=114。z 取壳体中心，不朝头部。
     */
    private static final float[][] CRATER_MODEL = {
            {-2.5F, 75.8F, -0.5F},   // phase 1
            {1.1F, 115.0F, 1.1F}     // phase 2
    };

    /**
     * 主要裂隙在 shell 骨骼局部坐标里的位置（模型单位）。
     * 只取壳体外表面的几个点，数量少、位置固定，方便在游戏里微调。
     */
    private static final float[][][] FISSURES_MODEL = {
            { // phase 1
                    {26.0F, 46.0F, -22.0F},
                    {-30.0F, 40.0F, 18.0F},
                    {18.0F, 60.0F, 8.0F},
                    {-16.0F, 56.0F, -28.0F}
            },
            { // phase 2
                    {34.0F, 60.0F, -22.0F},
                    {-38.0F, 62.0F, 12.0F},
                    {16.0F, 80.0F, 28.0F},
                    {-14.0F, 96.0F, -18.0F}
            }
    };

    // ------------------------------------------------------------ 内部状态 --

    /** 渲染时写入，tick 时读取；WeakHashMap 不会阻止实体卸载。 */
    private static final Map<VolcanicTortoiseEntity, Anchor> ANCHORS = new WeakHashMap<>();
    /** 小喷发的本地计时器。 */
    private static final Map<VolcanicTortoiseEntity, Burst> BURSTS = new WeakHashMap<>();

    private static final class Anchor {
        Matrix4f shell;
        Matrix4f whorl;
        int entityTick;
    }

    private static final class Burst {
        long nextAt;
        int ticks;
        int phase;
    }

    private VolcanicTortoiseAmbientFx() {
    }

    // ------------------------------------------------------- 渲染层调用的钩子 --

    /**
     * 由 {@link VolcanicTortoiseAnchorLayer} 在对应骨骼的 poseStack 上调用。
     *
     * @param boneName "shell" 或 "whorl"
     * @param pose     已经包含实体朝向、缩放、骨骼旋转的矩阵
     */
    static void captureBone(VolcanicTortoiseEntity entity, String boneName, PoseStack pose) {
        Anchor anchor = ANCHORS.computeIfAbsent(entity, ignored -> new Anchor());
        Matrix4f copy = new Matrix4f(pose.last().pose());
        if ("whorl".equals(boneName)) {
            anchor.whorl = copy;
        } else if ("shell".equals(boneName)) {
            anchor.shell = copy;
        }
        anchor.entityTick = entity.tickCount;
    }

    // --------------------------------------------------------------- 主 tick --

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        Level level = mc.level;
        long now = level.getGameTime();

        // 只处理最近的几个 Boss；排序保证预算优先给离玩家最近的那只。
        List<VolcanicTortoiseEntity> bosses = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && boss.isAlive() && !boss.isRemoved()) {
                if (mc.player.distanceToSqr(boss) <= MAX_DISTANCE * MAX_DISTANCE) {
                    bosses.add(boss);
                }
            }
        }
        if (bosses.isEmpty()) {
            return;
        }
        bosses.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
        int count = Math.min(MAX_BOSSES, bosses.size());

        int budget = budget(mc);
        for (int i = 0; i < count && budget > 0; i++) {
            VolcanicTortoiseEntity boss = bosses.get(i);
            Anchor anchor = ANCHORS.get(boss);
            if (anchor == null || anchor.whorl == null || anchor.shell == null
                    || boss.tickCount - anchor.entityTick > 4) {
                continue;
            }
            double distance = Math.sqrt(mc.player.distanceToSqr(boss));
            float proximity = proximity(distance);
            if (proximity <= 0.0F) {
                continue;
            }
            budget = tickBoss(mc, boss, anchor, now, proximity, budget);
        }
    }

    private static int budget(Minecraft mc) {
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            return 2;
        }
        if (status == ParticleStatus.DECREASED) {
            return 4;
        }
        return MAX_PARTICLES_PER_TICK;
    }

    private static float proximity(double distance) {
        if (distance <= FULL_DISTANCE) {
            return 1.0F;
        }
        double t = (distance - FULL_DISTANCE) / (MAX_DISTANCE - FULL_DISTANCE);
        return Mth.clamp(1.0F - (float) t, 0.0F, 1.0F);
    }

    private static int tickBoss(Minecraft mc, VolcanicTortoiseEntity boss, Anchor anchor,
                                long now, float proximity, int budget) {
        boolean phaseTwo = boss.isPhaseTwo();
        long id = boss.getId();
        Level level = mc.level;
        Burst burst = BURSTS.computeIfAbsent(boss, ignored -> new Burst());

        // 阶段切换：清掉旧计时，等转阶段动画播完再重新排。
        if (burst.phase != boss.phase()) {
            burst.phase = boss.phase();
            burst.ticks = 0;
            burst.nextAt = now + 80L;
        }
        if (burst.nextAt == 0L) {
            burst.nextAt = now + initialBurstDelay(level, phaseTwo);
        }

        boolean actionPlaying = actionPlaying(boss);
        boolean burstActive = burst.ticks > 0;
        if (burstActive && (actionPlaying || !boss.isAlive() || boss.isDeadOrDying())) {
            burst.ticks = 0;
            burst.nextAt = now + BURST_RETRY_DELAY;
            burstActive = false;
        }

        if (!burstActive && now >= burst.nextAt) {
            if (actionPlaying || !boss.isAlive() || boss.isDeadOrDying()) {
                burst.nextAt = now + BURST_RETRY_DELAY;
            } else {
                burst.ticks = BURST_TICKS;
                burstActive = true;
            }
        }

        Vec3 crater = transform(anchor.whorl, CRATER_MODEL[phaseTwo ? 1 : 0]);
        Vec3[] fissures = fissures(anchor.shell, phaseTwo);

        // 持续的轻烟：每个 Boss 独立错开，避免同一帧一起冒。
        int smokeInterval = phaseTwo ? SMOKE_INTERVAL_P2 : SMOKE_INTERVAL_P1;
        if (budget > 0 && (now + id) % smokeInterval == 0
                && level.random.nextFloat() < proximity) {
            spawnSmoke(level, crater, 0.09F, false);
            budget--;
        }

        // 稀疏火星：大多数从火山口，少数从裂隙。
        int sparkInterval = phaseTwo ? SPARK_INTERVAL_P2 : SPARK_INTERVAL_P1;
        if (budget > 0 && (now + id * 3L) % sparkInterval == 0
                && level.random.nextFloat() < proximity) {
            Vec3 at = level.random.nextFloat() < 0.28F && fissures.length > 0
                    ? fissures[level.random.nextInt(fissures.length)]
                    : crater;
            spawnSpark(level, at, 0.18F, 1.0F);
            budget--;
        }

        // 小喷发：短促、主要是火星，少量烟；没有伤害/爆炸/地形变化。
        if (burstActive) {
            int tick = BURST_TICKS - burst.ticks;
            int sparks = tick < 4 ? 2 : 1;
            for (int i = 0; i < sparks && budget > 0; i++) {
                spawnSpark(level, crater, 0.55F, 1.8F);
                budget--;
            }
            if (budget > 0 && tick % 3 == 0) {
                spawnSmoke(level, crater, 0.16F, true);
                budget--;
            }
            // 喷发时让最近的一条裂隙也跟着冒一点。
            if (budget > 0 && fissures.length > 0 && tick % 2 == 0) {
                spawnSpark(level, fissures[level.random.nextInt(fissures.length)], 0.12F, 1.2F);
                budget--;
            }
            burst.ticks--;
            if (burst.ticks <= 0) {
                burst.nextAt = now + nextBurstDelay(level, phaseTwo);
            }
        }

        return budget;
    }

    private static Vec3[] fissures(Matrix4f shell, boolean phaseTwo) {
        float[][] model = FISSURES_MODEL[phaseTwo ? 1 : 0];
        Vec3[] out = new Vec3[model.length];
        for (int i = 0; i < model.length; i++) {
            out[i] = transform(shell, model[i]);
        }
        return out;
    }

    /** 模型单位 -> 世界坐标；矩阵已经包含实体朝向、位置、缩放和骨骼动画。 */
    private static Vec3 transform(Matrix4f matrix, float[] modelUnits) {
        Vector3f point = matrix.transformPosition(new Vector3f(
                modelUnits[0] / 16.0F, modelUnits[1] / 16.0F, modelUnits[2] / 16.0F));
        return new Vec3(point.x, point.y, point.z);
    }

    // ------------------------------------------------------------- 粒子生成 --

    private static void spawnSmoke(Level level, Vec3 at, double spread, boolean burst) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * spread;
        double z = at.z + (level.random.nextDouble() - 0.5D) * spread;
        double y = at.y + 0.12D + level.random.nextDouble() * 0.10D;
        double vx = (level.random.nextDouble() - 0.5D) * 0.012D;
        double vz = (level.random.nextDouble() - 0.5D) * 0.012D;
        double vy = 0.022D + level.random.nextDouble() * 0.030D;
        if (burst) {
            vy *= 1.6D;
            x += (level.random.nextDouble() - 0.5D) * 0.35D;
            z += (level.random.nextDouble() - 0.5D) * 0.35D;
        }
        Particle particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        particle.setLifetime(SMOKE_LIFE_MIN + level.random.nextInt(SMOKE_LIFE_MAX - SMOKE_LIFE_MIN + 1));
        particle.scale(SMOKE_SCALE_MIN + level.random.nextFloat() * (SMOKE_SCALE_MAX - SMOKE_SCALE_MIN));
        particle.setColor(0.52F, 0.50F, 0.47F);
        particle.setParticleSpeed(vx, vy, vz);
    }

    private static void spawnSpark(Level level, Vec3 at, double spread, float speed) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * spread;
        double z = at.z + (level.random.nextDouble() - 0.5D) * spread;
        double y = at.y + 0.06D + level.random.nextDouble() * 0.12D;
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double radial = (0.020D + level.random.nextDouble() * 0.045D) * speed;
        double vx = Math.cos(angle) * radial;
        double vz = Math.sin(angle) * radial;
        double vy = (0.075D + level.random.nextDouble() * 0.105D) * speed;
        Particle particle = mc.particleEngine.createParticle(ParticleTypes.SMALL_FLAME, x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        particle.setLifetime(SPARK_LIFE_MIN + level.random.nextInt(SPARK_LIFE_MAX - SPARK_LIFE_MIN + 1));
        particle.scale(SPARK_SCALE_MIN + level.random.nextFloat() * (SPARK_SCALE_MAX - SPARK_SCALE_MIN));
        // 橙黄色，关闭光影时也仍然可见（原版粒子，不依赖 shader）。
        particle.setColor(1.0F, 0.62F + level.random.nextFloat() * 0.16F, 0.12F);
        particle.setParticleSpeed(vx, vy, vz);
    }

    // ------------------------------------------------------------- 喷发计时 --

    private static int initialBurstDelay(Level level, boolean phaseTwo) {
        return phaseTwo
                ? BURST_DELAY_MIN_P2 + level.random.nextInt(BURST_DELAY_MAX_P2 - BURST_DELAY_MIN_P2 + 1)
                : BURST_DELAY_MIN_P1 + level.random.nextInt(BURST_DELAY_MAX_P1 - BURST_DELAY_MIN_P1 + 1);
    }

    private static int nextBurstDelay(Level level, boolean phaseTwo) {
        return initialBurstDelay(level, phaseTwo);
    }

    private static boolean actionPlaying(VolcanicTortoiseEntity boss) {
        AnimatableManager<?> manager = boss.getAnimatableInstanceCache().getManagerForId(boss.getId());
        if (manager == null) {
            return false;
        }
        AnimationController<?> action = manager.getAnimationControllers().get("action");
        return action != null && action.isPlayingTriggeredAnimation();
    }
}
