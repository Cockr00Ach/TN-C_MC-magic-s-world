package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * 火山巨龟的火山口轻烟与裂隙火星。
 *
 * <p>只有两种表现：壳顶火山口持续冒出的细烟，和壳面主要裂隙间或崩出的火星。
 * 不做小喷发、不改动画、不加伤害、不动地形。
 *
 * <p>全部在客户端生成：
 * <ul>
 *   <li>服务端不发包，附近有几个玩家都不会被放大；</li>
 *   <li>按 client tick 生成，帧率高低不改变频率；</li>
 *   <li>距离越远越稀，超过 {@link Tuning#MAX_DISTANCE} 完全不生成。</li>
 * </ul>
 *
 * <p>位置不是写死的实体坐标，而是渲染时从 {@code shell}/{@code whorl} 骨骼矩阵取出的
 * 当前火山口/裂隙世界坐标（见 {@link VolcanicTortoiseAnchorLayer}）。因此阶段、朝向、
 * 缩放、攻击与转阶段的骨骼动作都会自然跟随。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VolcanicTortoiseAmbientFx {

    // ============================================================ 可调参数 ==
    // 想调浓淡/快慢，只改这一块即可；下面所有逻辑都从这里取值。

    /** 所有数值集中在这里：单位、频率、寿命、颜色、锚点。 */
    static final class Tuning {
        private Tuning() {
        }

        // ---- 距离与预算 ---------------------------------------------------

        /** 超过这个距离（格）完全不生成。 */
        static final double MAX_DISTANCE = 56.0D;
        /** 这个距离内按 100% 生成，往外线性衰减到 0。 */
        static final double FULL_DISTANCE = 24.0D;
        /** 同时处理的 Boss 上限，防止有人刷一堆。 */
        static final int MAX_BOSSES = 3;
        /** 每个 client tick 的全局粒子预算；帧率再高也不会超。 */
        static final int MAX_PARTICLES_PER_TICK = 8;

        // ---- 生成频率（每秒个数，按 20 tick/s 折算成每 tick 概率） ---------

        /** 一阶段：轻烟 5/s、火星 8/s。 */
        static final float SMOKE_PER_SECOND_P1 = 5.0F;
        static final float SPARK_PER_SECOND_P1 = 8.0F;
        /** 二阶段：轻烟 8/s、火星 13/s，明显更躁动。 */
        static final float SMOKE_PER_SECOND_P2 = 8.0F;
        static final float SPARK_PER_SECOND_P2 = 13.0F;

        // ---- 轻烟 ---------------------------------------------------------

        /** 寿命 1.0~1.5 秒（20~30 tick），短命，不在壳顶堆成一片。 */
        static final int SMOKE_LIFE_MIN = 20;
        static final int SMOKE_LIFE_MAX = 30;
        /** 颗粒小：0.45~0.75 倍。 */
        static final float SMOKE_SCALE_MIN = 0.45F;
        static final float SMOKE_SCALE_MAX = 0.75F;
        /** 灰褐色，和岩甲区分但不过分发黑。 */
        static final float SMOKE_RED = 0.50F;
        static final float SMOKE_GREEN = 0.48F;
        static final float SMOKE_BLUE = 0.45F;
        /** 上升速度与横向漂移（格/tick）。 */
        static final double SMOKE_RISE_MIN = 0.018D;
        static final double SMOKE_RISE_MAX = 0.040D;
        static final double SMOKE_DRIFT = 0.014D;
        /** 生成点的水平抖动（格），保持细而集中。 */
        static final double SMOKE_SPREAD = 0.10D;

        // ---- 火星 ---------------------------------------------------------

        /** 火星寿命 0.4~0.7 秒，短促。 */
        static final int SPARK_LIFE_MIN = 8;
        static final int SPARK_LIFE_MAX = 14;
        static final float SPARK_SCALE_MIN = 0.30F;
        static final float SPARK_SCALE_MAX = 0.55F;
        /** 橙黄色，关光影也看得见（原版粒子，不依赖 shader）。 */
        static final float SPARK_RED = 1.0F;
        static final float SPARK_GREEN_MIN = 0.58F;
        static final float SPARK_GREEN_MAX = 0.74F;
        static final float SPARK_BLUE = 0.12F;
        /** 火星初速：向上为主，带一点径向散开。 */
        static final double SPARK_UP_MIN = 0.070D;
        static final double SPARK_UP_MAX = 0.150D;
        static final double SPARK_RADIAL_MIN = 0.015D;
        static final double SPARK_RADIAL_MAX = 0.045D;
        /** 火星生成点抖动（格）。 */
        static final double SPARK_SPREAD = 0.12D;
        /** 火星里从裂隙出来的比例，其余从火山口。 */
        static final float SPARK_FISSURE_CHANCE = 0.70F;

        // ---- 状态抑制 -----------------------------------------------------

        /** 攻击动作期间保留的比例（降频但不完全停）。 */
        static final float ATTACK_RATE_SCALE = 0.35F;
        /** 转阶段后暂停多少 tick（68 tick 动画 + 余韵）。 */
        static final int TRANSITION_PAUSE_TICKS = 80;

        // ---- 锚点（模型单位，1 格 = 16 单位） -----------------------------

        /**
         * 火山口：取 whorl 骨骼局部坐标，位于壳顶中心，不朝向头部，
         * 因此烟柱从背甲上方升起，不会糊住头。
         */
        static final float[][] CRATER_MODEL = {
                {-2.5F, 75.8F, -0.5F},   // phase 1
                {1.1F, 115.0F, 1.1F}     // phase 2
        };

        /** 主要裂隙：取 shell 骨骼局部坐标，只放壳体外表面几个固定点。 */
        static final float[][][] FISSURES_MODEL = {
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
    }

    // ============================================================ 内部状态 ==

    /** 渲染时写入、tick 时读取；WeakHashMap 不阻止实体卸载。 */
    private static final Map<VolcanicTortoiseEntity, Anchor> ANCHORS = new WeakHashMap<>();
    /** 转阶段暂停计时：记录上次看到的阶段与解除暂停的时间。 */
    private static final Map<VolcanicTortoiseEntity, PhaseWatch> PHASES = new WeakHashMap<>();

    private static final class Anchor {
        Matrix4f shell;
        Matrix4f whorl;
        int entityTick;
    }

    private static final class PhaseWatch {
        int phase = -1;
        long resumeAt;
    }

    private VolcanicTortoiseAmbientFx() {
    }

    // ==================================================== 渲染层调用的钩子 ==

    /**
     * 由 {@link VolcanicTortoiseAnchorLayer} 在对应骨骼的 poseStack 上调用。
     *
     * @param boneName "shell" 或 "whorl"
     * @param pose     已包含实体位置、朝向、缩放与骨骼旋转的矩阵
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
        Level level = mc.level;
        long now = level.getGameTime();

        // 只处理最近的几个 Boss；排序保证预算优先给离玩家最近的那只。
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }
        List<VolcanicTortoiseEntity> bosses = new ArrayList<>();
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && boss.isAlive() && !boss.isRemoved()) {
                if (mc.player.distanceToSqr(boss) <= Tuning.MAX_DISTANCE * Tuning.MAX_DISTANCE) {
                    bosses.add(boss);
                }
            }
        }
        if (bosses.isEmpty()) {
            return;
        }
        bosses.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
        int count = Math.min(Tuning.MAX_BOSSES, bosses.size());

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
        return Tuning.MAX_PARTICLES_PER_TICK;
    }

    private static float proximity(double distance) {
        if (distance <= Tuning.FULL_DISTANCE) {
            return 1.0F;
        }
        double t = (distance - Tuning.FULL_DISTANCE) / (Tuning.MAX_DISTANCE - Tuning.FULL_DISTANCE);
        return Mth.clamp(1.0F - (float) t, 0.0F, 1.0F);
    }

    private static int tickBoss(Minecraft mc, VolcanicTortoiseEntity boss, Anchor anchor,
                                long now, float proximity, int budget) {
        // 死亡/移除：不再冒烟冒火，并把残留锚点清掉。
        if (!boss.isAlive() || boss.isDeadOrDying()) {
            ANCHORS.remove(boss);
            PHASES.remove(boss);
            return budget;
        }

        float stateScale = stateScale(boss, now);
        if (stateScale <= 0.0F) {
            return budget;
        }

        boolean phaseTwo = boss.isPhaseTwo();
        Level level = mc.level;
        float rateScale = proximity * stateScale;

        Vec3 crater = transform(anchor.whorl, Tuning.CRATER_MODEL[phaseTwo ? 1 : 0]);

        // 轻烟：只从壳顶火山口，持续、细、短命。
        float smokeRate = (phaseTwo ? Tuning.SMOKE_PER_SECOND_P2 : Tuning.SMOKE_PER_SECOND_P1) * rateScale;
        if (budget > 0 && level.random.nextFloat() < smokeRate / 20.0F) {
            spawnSmoke(level, crater);
            budget--;
        }

        // 火星：七成从裂隙、三成从火山口，间歇崩出。
        float sparkRate = (phaseTwo ? Tuning.SPARK_PER_SECOND_P2 : Tuning.SPARK_PER_SECOND_P1) * rateScale;
        if (budget > 0 && level.random.nextFloat() < sparkRate / 20.0F) {
            Vec3 at = crater;
            if (level.random.nextFloat() < Tuning.SPARK_FISSURE_CHANCE) {
                float[][] fissures = Tuning.FISSURES_MODEL[phaseTwo ? 1 : 0];
                at = transform(anchor.shell, fissures[level.random.nextInt(fissures.length)]);
            }
            spawnSpark(level, at);
            budget--;
        }

        return budget;
    }

    /**
     * 动作状态对生成频率的抑制：
     * 死亡 0（已在上层拦掉）、转阶段 0（暂停）、普通攻击 0.35（降频）、其余 1。
     */
    private static float stateScale(VolcanicTortoiseEntity boss, long now) {
        PhaseWatch watch = PHASES.computeIfAbsent(boss, ignored -> new PhaseWatch());
        if (watch.phase != boss.phase()) {
            if (watch.phase != -1) {
                watch.resumeAt = now + Tuning.TRANSITION_PAUSE_TICKS;
            }
            watch.phase = boss.phase();
        }
        if (now < watch.resumeAt) {
            return 0.0F;
        }
        return actionPlaying(boss) ? Tuning.ATTACK_RATE_SCALE : 1.0F;
    }

    /** 模型单位 -> 世界坐标；矩阵已包含实体朝向、位置、缩放和骨骼动画。 */
    private static Vec3 transform(Matrix4f matrix, float[] modelUnits) {
        Vector3f point = matrix.transformPosition(new Vector3f(
                modelUnits[0] / 16.0F, modelUnits[1] / 16.0F, modelUnits[2] / 16.0F));
        return new Vec3(point.x, point.y, point.z);
    }

    // =========================================================== 粒子生成 ==

    private static void spawnSmoke(Level level, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_SPREAD;
        double z = at.z + (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_SPREAD;
        double y = at.y + 0.10D + level.random.nextDouble() * 0.08D;
        // 先给一个很小的横向初速，粒子自身还会继续上浮，形成轻微横移的烟柱。
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

    private static void spawnSpark(Level level, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * Tuning.SPARK_SPREAD;
        double z = at.z + (level.random.nextDouble() - 0.5D) * Tuning.SPARK_SPREAD;
        double y = at.y + 0.05D + level.random.nextDouble() * 0.10D;
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double radial = Tuning.SPARK_RADIAL_MIN
                + level.random.nextDouble() * (Tuning.SPARK_RADIAL_MAX - Tuning.SPARK_RADIAL_MIN);
        double vx = Math.cos(angle) * radial;
        double vz = Math.sin(angle) * radial;
        double vy = Tuning.SPARK_UP_MIN
                + level.random.nextDouble() * (Tuning.SPARK_UP_MAX - Tuning.SPARK_UP_MIN);
        Particle particle = mc.particleEngine.createParticle(ParticleTypes.SMALL_FLAME, x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        particle.setLifetime(Tuning.SPARK_LIFE_MIN
                + level.random.nextInt(Tuning.SPARK_LIFE_MAX - Tuning.SPARK_LIFE_MIN + 1));
        particle.scale(Tuning.SPARK_SCALE_MIN
                + level.random.nextFloat() * (Tuning.SPARK_SCALE_MAX - Tuning.SPARK_SCALE_MIN));
        particle.setColor(Tuning.SPARK_RED,
                Tuning.SPARK_GREEN_MIN + level.random.nextFloat()
                        * (Tuning.SPARK_GREEN_MAX - Tuning.SPARK_GREEN_MIN),
                Tuning.SPARK_BLUE);
        particle.setParticleSpeed(vx, vy, vz);
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
}
