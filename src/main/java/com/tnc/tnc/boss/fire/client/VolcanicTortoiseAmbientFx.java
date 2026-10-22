package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 火山巨龟的火山口轻烟与火星。
 *
 * <p>发射点在模型自身中轴上、火山口开口内部（内壁熔岩面略上方），按阶段取不同高度，
 * 水平只做小范围抖动。火山口在中轴上，所以不需要朝向参与；也不使用骨骼矩阵，
 * 避免“相机相对 / 骨骼 pivot”两种坐标空间混用带来的错位。
 *
 * <p>只有两种表现：壳顶火山口持续冒出的细烟，和火星（火山口为主，外壳裂隙少量）。
 * 裂隙点位是模型局部坐标，按身体朝向旋转后贴在壳体表面，不用渲染矩阵。
 * 不做小喷发、不改动画、不加伤害、不动地形、不点燃方块。
 *
 * <p>全部在客户端生成，按 client tick 稳定步进，距离越远越稀。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VolcanicTortoiseAmbientFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/tortoise-ambient");

    /**
     * 定位调试开关：启动参数加 {@code -Dtnc.tortoise.crater.debug=true} 才生效。
     * 打开后停止轻烟与火星，只在计算出的火山口中心打一个醒目的原版标记，并每秒输出一行坐标日志。
     * 默认关闭，正式构建不受影响。
     */
    private static final boolean CRATER_DEBUG = Boolean.getBoolean("tnc.tortoise.crater.debug");

    // ============================================================ 可调参数 ==
    // 所有数值集中在这里；调浓淡、快慢、大小、颜色只改这一块。

    static final class Tuning {
        private Tuning() {
        }

        // ---- 距离与预算 ---------------------------------------------------

        /** 超过这个距离（格）完全不生成。 */
        static final double MAX_DISTANCE = 56.0D;
        /** 这个距离内满额生成，往外线性衰减到 0。 */
        static final double FULL_DISTANCE = 24.0D;
        /** 同时处理的 Boss 上限。 */
        static final int MAX_BOSSES = 3;
        /** 每个 client tick 的全局粒子预算（“全部”档）。 */
        static final int MAX_PARTICLES_PER_TICK = 12;

        // ---- 发射点（模型单位，1 格 = 16 单位） -----------------------------

        /** 火山口内熔岩面上方一点：一阶段 / 二阶段。 */
        static final double CRATER_Y_P1 = 72.0D / 16.0D;   // 4.50 格
        static final double CRATER_Y_P2 = 115.5D / 16.0D;  // 7.22 格
        /** 发射点水平抖动半径（格），保持在开口内部。 */
        static final double CRATER_JITTER = 0.30D;

        // ---- 轻烟（本轮不改数量，只跟随新发射点） ---------------------------

        static final float SMOKE_PER_SECOND_P1 = 5.0F;
        static final float SMOKE_PER_SECOND_P2 = 8.0F;
        static final int SMOKE_LIFE_MIN = 20;
        static final int SMOKE_LIFE_MAX = 30;
        static final float SMOKE_SCALE_MIN = 0.45F;
        static final float SMOKE_SCALE_MAX = 0.75F;
        static final float SMOKE_RED = 0.50F;
        static final float SMOKE_GREEN = 0.48F;
        static final float SMOKE_BLUE = 0.45F;
        static final double SMOKE_RISE_MIN = 0.018D;
        static final double SMOKE_RISE_MAX = 0.040D;
        static final double SMOKE_DRIFT = 0.014D;
        static final double SMOKE_SPREAD = 0.10D;

        // ---- 火星（本轮增强，全部从火山口出） -------------------------------

        static final float SPARK_PER_SECOND_P1 = 22.0F;
        static final float SPARK_PER_SECOND_P2 = 33.0F;
        static final int SPARK_LIFE_MIN_P1 = 16;
        static final int SPARK_LIFE_MAX_P1 = 24;
        static final int SPARK_LIFE_MIN_P2 = 18;
        static final int SPARK_LIFE_MAX_P2 = 28;
        /** 在上一版 0.30~0.55 的基础上放大约 1.6~1.8 倍。 */
        static final float SPARK_SCALE_MIN = 0.50F;
        static final float SPARK_SCALE_MAX = 0.95F;
        static final float SPARK_SCALE_P2_BONUS = 0.35F;
        static final double SPARK_UP_MIN_P1 = 0.090D;
        static final double SPARK_UP_MAX_P1 = 0.200D;
        static final double SPARK_UP_MIN_P2 = 0.110D;
        static final double SPARK_UP_MAX_P2 = 0.240D;
        static final double SPARK_RADIAL_MIN = 0.020D;
        static final double SPARK_RADIAL_MAX = 0.060D;
        static final double SPARK_SPREAD = 0.26D;
        /** 三元色：橙红 / 橙黄 / 浅黄高亮。 */
        static final float[][] SPARK_COLORS = {
                {1.00F, 0.38F, 0.06F},
                {1.00F, 0.62F, 0.12F},
                {1.00F, 0.86F, 0.40F}
        };

        // ---- 裂隙火星 -----------------------------------------------------

        /** 打开裂隙火星。点位见 {@link #FISSURES_P1} / {@link #FISSURES_P2}，都贴在外壳表面。 */
        static final boolean FISSURE_SPARKS_ENABLED = true;
        /** 火星里有多大比例从裂隙出，其余从火山口出。 */
        static final float FISSURE_SPARK_SHARE = 0.30F;
        /** 裂隙火星离表面很近，散开要小，避免钻进石头里。 */
        static final double FISSURE_SPREAD = 0.05D;
        static final double FISSURE_OUTWARD_MIN = 0.030D;
        static final double FISSURE_OUTWARD_MAX = 0.090D;

        // ---- 状态抑制 -----------------------------------------------------

        static final float ATTACK_RATE_SCALE = 0.35F;
        /** 转阶段后停多少 tick；只在阶段变化那一次设置。 */
        static final int TRANSITION_PAUSE_TICKS = 80;
    }

    /**
     * 一处裂隙发射点，用实体局部坐标表示（单位：格）。
     *
     * <p>x 向右、y 从脚下起、z 向模型背面（模型正脸朝 -z）。这里是「模型原始坐标」，
     * 不是骨骼 pivot、也不是相机空间——运行时按 entity 的 {@code yBodyRot} 绕 Y 轴旋转后加到实体坐标上，
     * 所以巨龟转身时裂隙跟着转，不需要任何渲染矩阵。
     *
     * <p>这些点是从 geo 里外壳（shell/whorl/body）方块的朝外面中心算出来的：取面中心后再沿法线外移 0.6 单位
     * （0.0375 格），并逐个验证过不落在任何方块内部（含腿、头、颈），因此不会浮空、也不会埋进石头。
     */
    private record Fissure(double x, double y, double z) {
    }

    /** 一阶段外壳侧面的裂隙点（高度 2.78~3.03 格）。 */
    private static final Fissure[] FISSURES_P1 = {
            new Fissure(1.9125D, 2.7812D, -1.4375D),
            new Fissure(-1.7875D, 2.8125D, 1.2500D),
            new Fissure(-1.0375D, 3.0325D, -1.6250D),
            new Fissure(1.7875D, 2.8125D, 1.2500D),
            new Fissure(0.0000D, 2.8125D, 1.9125D)
    };

    /** 二阶段外壳侧面的裂隙点（高度 5.25~5.85 格）。 */
    private static final Fissure[] FISSURES_P2 = {
            new Fissure(-2.2125D, 5.3125D, -0.5735D),
            new Fissure(1.9125D, 5.4000D, -0.4563D),
            new Fissure(-0.2000D, 5.8500D, 2.0375D),
            new Fissure(-1.6125D, 5.3593D, 0.8297D),
            new Fissure(1.1125D, 5.2515D, -1.3875D),
            new Fissure(-1.0750D, 5.3593D, 1.3985D)
    };

    private static final class FxState {
        int phase = -1;
        long resumeAt;
        float smokeAcc;
        float sparkAcc;
        int smokeThisSecond;
        int sparkThisSecond;
        long secondMark;
        String skip = "ok";
    }

    private static final Map<VolcanicTortoiseEntity, FxState> STATES = new WeakHashMap<>();

    private VolcanicTortoiseAmbientFx() {
    }

    // ============================================================== 主 tick ==

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;

        List<VolcanicTortoiseEntity> bosses = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && !boss.isRemoved()
                    && mc.player.distanceToSqr(boss) <= Tuning.MAX_DISTANCE * Tuning.MAX_DISTANCE) {
                bosses.add(boss);
            }
        }
        if (bosses.isEmpty()) {
            return;
        }
        bosses.sort(Comparator.comparingDouble(mc.player::distanceToSqr));

        int budget = particleBudget(mc);
        int count = Math.min(Tuning.MAX_BOSSES, bosses.size());
        for (int i = 0; i < count; i++) {
            VolcanicTortoiseEntity boss = bosses.get(i);
            FxState state = STATES.computeIfAbsent(boss, ignored -> new FxState());
            budget = tickBoss(mc, level, boss, state, level.getGameTime(), budget);
        }
    }

    private static int particleBudget(Minecraft mc) {
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            return 3;
        }
        if (status == ParticleStatus.DECREASED) {
            return 8;
        }
        return Tuning.MAX_PARTICLES_PER_TICK;
    }

    private static float proximity(double distance) {
        if (distance <= Tuning.FULL_DISTANCE) {
            return 1.0F;
        }
        double t = (distance - Tuning.FULL_DISTANCE) / (Tuning.MAX_DISTANCE - Tuning.FULL_DISTANCE);
        return Mth.clamp(1.0F - (float) t, 0.0F, 1.0F);
    }

    /** 火山口中心世界坐标：实体脚下 + 阶段高度，水平在开口内小范围抖动。 */
    private static Vec3 crater(VolcanicTortoiseEntity boss, boolean phaseTwo, RandomSource random) {
        double y = boss.getY() + (phaseTwo ? Tuning.CRATER_Y_P2 : Tuning.CRATER_Y_P1);
        double dx = (random.nextDouble() - 0.5D) * 2.0D * Tuning.CRATER_JITTER;
        double dz = (random.nextDouble() - 0.5D) * 2.0D * Tuning.CRATER_JITTER;
        return new Vec3(boss.getX() + dx, y, boss.getZ() + dz);
    }

    private static int tickBoss(Minecraft mc, ClientLevel level, VolcanicTortoiseEntity boss,
                                FxState state, long now, int budget) {
        boolean phaseTwo = boss.isPhaseTwo();
        double distance = Math.sqrt(mc.player.distanceToSqr(boss));
        float proximity = proximity(distance);

        // 阶段变化：只在这一 tick 设一次暂停，之后不再重置。
        boolean phaseChanged = state.phase != boss.phase();
        if (phaseChanged) {
            if (state.phase != -1) {
                state.resumeAt = now + Tuning.TRANSITION_PAUSE_TICKS;
            }
            state.phase = boss.phase();
            state.smokeAcc = level.random.nextFloat();
            state.sparkAcc = level.random.nextFloat();
        }

        if (!boss.isAlive() || boss.isDeadOrDying()) {
            state.skip = "dead";
            return budget;
        }
        if (proximity <= 0.0F) {
            state.skip = "distance";
            return budget;
        }
        if (now < state.resumeAt) {
            state.skip = "transition-pause";
            return budget;
        }
        boolean attacking = actionPlaying(boss);
        state.skip = attacking ? "attack" : "ok";
        float rateScale = proximity * (attacking ? Tuning.ATTACK_RATE_SCALE : 1.0F);

        if (CRATER_DEBUG) {
            // 调试：停掉正式粒子，只在火山口中心打标记，便于玩家确认定位。
            Vec3 at = new Vec3(boss.getX(), boss.getY() + (phaseTwo ? Tuning.CRATER_Y_P2 : Tuning.CRATER_Y_P1), boss.getZ());
            marker(level, at, now);
            logDebug(mc, boss, state, now, at, phaseTwo);
            return budget;
        }

        Vec3 vent = crater(boss, phaseTwo, level.random);

        // ---- 轻烟：小数累计，5/s 就是每 tick +0.25 -------------------------
        float smokeRate = (phaseTwo ? Tuning.SMOKE_PER_SECOND_P2 : Tuning.SMOKE_PER_SECOND_P1) * rateScale;
        state.smokeAcc += smokeRate / 20.0F;
        while (state.smokeAcc >= 1.0F && budget > 0) {
            spawnSmoke(level, vent);
            state.smokeAcc -= 1.0F;
            state.smokeThisSecond++;
            budget--;
        }
        if (state.smokeAcc > 4.0F) {
            state.smokeAcc = 4.0F;
        }

        // ---- 火星：火山口为主，裂隙少量 =====================================
        float sparkRate = (phaseTwo ? Tuning.SPARK_PER_SECOND_P2 : Tuning.SPARK_PER_SECOND_P1) * rateScale;
        state.sparkAcc += sparkRate / 20.0F;
        while (state.sparkAcc >= 1.0F && budget > 0) {
            boolean fissure = Tuning.FISSURE_SPARKS_ENABLED
                    && level.random.nextFloat() < Tuning.FISSURE_SPARK_SHARE;
            if (fissure) {
                spawnFissureSpark(level, boss, phaseTwo);
            } else {
                spawnSpark(level, vent, phaseTwo);
            }
            state.sparkAcc -= 1.0F;
            state.sparkThisSecond++;
            budget--;
        }
        if (state.sparkAcc > 4.0F) {
            state.sparkAcc = 4.0F;
        }

        logDebug(mc, boss, state, now, vent, phaseTwo);
        return budget;
    }

    // =========================================================== 粒子生成 ==

    private static void spawnSmoke(Level level, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_SPREAD;
        double z = at.z + (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_SPREAD;
        double y = at.y + 0.06D + level.random.nextDouble() * 0.06D;
        double vx = (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_DRIFT;
        double vz = (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_DRIFT;
        double vy = Tuning.SMOKE_RISE_MIN
                + level.random.nextDouble() * (Tuning.SMOKE_RISE_MAX - Tuning.SMOKE_RISE_MIN);
        Particle particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        particle.setLifetime(Tuning.SMOKE_LIFE_MIN
                + level.random.nextInt(Tuning.SMOKE_LIFE_MAX - Tuning.SMOKE_LIFE_MIN + 1));
        particle.scale(Tuning.SMOKE_SCALE_MIN
                + level.random.nextFloat() * (Tuning.SMOKE_SCALE_MAX - Tuning.SMOKE_SCALE_MIN));
        particle.setColor(Tuning.SMOKE_RED, Tuning.SMOKE_GREEN, Tuning.SMOKE_BLUE);
        particle.setParticleSpeed(vx, vy, vz);
    }

    private static void spawnSpark(Level level, Vec3 at, boolean phaseTwo) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * Tuning.SPARK_SPREAD;
        double z = at.z + (level.random.nextDouble() - 0.5D) * Tuning.SPARK_SPREAD;
        double y = at.y + 0.04D + level.random.nextDouble() * 0.10D;
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double radial = Tuning.SPARK_RADIAL_MIN
                + level.random.nextDouble() * (Tuning.SPARK_RADIAL_MAX - Tuning.SPARK_RADIAL_MIN);
        double vx = Math.cos(angle) * radial;
        double vz = Math.sin(angle) * radial;
        double upMin = phaseTwo ? Tuning.SPARK_UP_MIN_P2 : Tuning.SPARK_UP_MIN_P1;
        double upMax = phaseTwo ? Tuning.SPARK_UP_MAX_P2 : Tuning.SPARK_UP_MAX_P1;
        double vy = upMin + level.random.nextDouble() * (upMax - upMin);

        float[] color = Tuning.SPARK_COLORS[level.random.nextInt(Tuning.SPARK_COLORS.length)];
        Particle particle = mc.particleEngine.createParticle(
                level.random.nextFloat() < 0.25F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        int lifeMin = phaseTwo ? Tuning.SPARK_LIFE_MIN_P2 : Tuning.SPARK_LIFE_MIN_P1;
        int lifeMax = phaseTwo ? Tuning.SPARK_LIFE_MAX_P2 : Tuning.SPARK_LIFE_MAX_P1;
        particle.setLifetime(lifeMin + level.random.nextInt(lifeMax - lifeMin + 1));
        float scale = Tuning.SPARK_SCALE_MIN
                + level.random.nextFloat() * (Tuning.SPARK_SCALE_MAX - Tuning.SPARK_SCALE_MIN);
        if (phaseTwo && level.random.nextFloat() < 0.3F) {
            scale += Tuning.SPARK_SCALE_P2_BONUS;
        }
        particle.scale(scale);
        particle.setColor(color[0], color[1], color[2]);
        particle.setParticleSpeed(vx, vy, vz);
    }

    /**
     * 裂隙火星：从外壳表面的固定点位冒出，向外、向上飞。
     * 点位是实体局部坐标，按 {@code yBodyRot} 旋转后加到实体位置上，所以巨龟转身/移动时不会脱离壳体。
     */
    private static void spawnFissureSpark(Level level, VolcanicTortoiseEntity boss, boolean phaseTwo) {
        Fissure[] points = phaseTwo ? FISSURES_P2 : FISSURES_P1;
        Fissure point = points[level.random.nextInt(points.length)];
        float yaw = boss.yBodyRot;

        double[] local = rotateYaw(point.x(), point.z(), yaw);
        double x = boss.getX() + local[0] + (level.random.nextDouble() - 0.5D) * Tuning.FISSURE_SPREAD;
        double z = boss.getZ() + local[1] + (level.random.nextDouble() - 0.5D) * Tuning.FISSURE_SPREAD;
        double y = boss.getY() + point.y() + 0.02D + level.random.nextDouble() * 0.04D;

        // 朝外方向：取点位在壳体上的径向，随身体一起转。
        double radius = Math.sqrt(point.x() * point.x() + point.z() * point.z());
        double[] outwardDir = rotateYaw(point.x() / radius, point.z() / radius, yaw);
        double outward = Tuning.FISSURE_OUTWARD_MIN
                + level.random.nextDouble() * (Tuning.FISSURE_OUTWARD_MAX - Tuning.FISSURE_OUTWARD_MIN);
        double vx = outwardDir[0] * outward;
        double vz = outwardDir[1] * outward;
        double upMin = (phaseTwo ? Tuning.SPARK_UP_MIN_P2 : Tuning.SPARK_UP_MIN_P1) * 0.7D;
        double upMax = (phaseTwo ? Tuning.SPARK_UP_MAX_P2 : Tuning.SPARK_UP_MAX_P1) * 0.7D;
        double vy = upMin + level.random.nextDouble() * (upMax - upMin);

        float[] color = Tuning.SPARK_COLORS[level.random.nextInt(Tuning.SPARK_COLORS.length)];
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(
                level.random.nextFloat() < 0.3F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        int lifeMin = phaseTwo ? Tuning.SPARK_LIFE_MIN_P2 : Tuning.SPARK_LIFE_MIN_P1;
        int lifeMax = phaseTwo ? Tuning.SPARK_LIFE_MAX_P2 : Tuning.SPARK_LIFE_MAX_P1;
        particle.setLifetime(lifeMin + level.random.nextInt(lifeMax - lifeMin + 1));
        float scale = Tuning.SPARK_SCALE_MIN
                + level.random.nextFloat() * (Tuning.SPARK_SCALE_MAX - Tuning.SPARK_SCALE_MIN);
        if (phaseTwo && level.random.nextFloat() < 0.3F) {
            scale += Tuning.SPARK_SCALE_P2_BONUS;
        }
        particle.scale(scale);
        particle.setColor(color[0], color[1], color[2]);
        particle.setParticleSpeed(vx, vy, vz);
    }

    /**
     * 把模型局部 (x, z) 按实体身体朝向转成世界水平偏移。
     * 与 {@code LivingEntityRenderer} 的 {@code 180° - yaw} 约定一致：yaw=0 时模型 -z（正脸）朝世界 +z。
     */
    private static double[] rotateYaw(double x, double z, float yawDegrees) {
        double angle = (180.0D - yawDegrees) * (Math.PI / 180.0D);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{x * cos + z * sin, -x * sin + z * cos};
    }

    private static void marker(Level level, Vec3 at, long now) {
        Minecraft mc = Minecraft.getInstance();
        // 白色柱状标记，白天也看得清；中心再放一颗火焰确认落点。
        mc.particleEngine.createParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
        mc.particleEngine.createParticle(ParticleTypes.END_ROD, at.x, at.y + 0.6D, at.z, 0.0D, 0.0D, 0.0D);
        mc.particleEngine.createParticle(ParticleTypes.FLAME, at.x, at.y + 0.05D, at.z, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.5D * i;
            mc.particleEngine.createParticle(ParticleTypes.END_ROD,
                    at.x + Math.cos(a) * 0.4D, at.y, at.z + Math.sin(a) * 0.4D, 0.0D, 0.0D, 0.0D);
        }
    }

    // =========================================================== 动作检测 ==

    private static boolean actionPlaying(VolcanicTortoiseEntity boss) {
        AnimatableManager<?> manager = boss.getAnimatableInstanceCache().getManagerForId(boss.getId());
        if (manager == null) {
            return false;
        }
        AnimationController<?> action = manager.getAnimationControllers().get("action");
        return action != null && action.isPlayingTriggeredAnimation();
    }

    // ============================================================== 日志 ==

    private static void logDebug(Minecraft mc, VolcanicTortoiseEntity boss, FxState state,
                                 long now, Vec3 vent, boolean phaseTwo) {
        if (!CRATER_DEBUG) {
            return;
        }
        if (state.secondMark == 0L) {
            state.secondMark = now;
        }
        if (now - state.secondMark < 20L) {
            return;
        }
        LOGGER.info("TN-C/tortoise-crater: id={} phase={} boss={} crater={} dist={} skip={}",
                boss.getId(), boss.phase(), fmt(new Vec3(boss.getX(), boss.getY(), boss.getZ())),
                fmt(vent), String.format(Locale.ROOT, "%.1f", Math.sqrt(mc.player.distanceToSqr(boss))),
                state.skip);
        state.secondMark = now;
    }

    private static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", v.x, v.y, v.z);
    }
}
