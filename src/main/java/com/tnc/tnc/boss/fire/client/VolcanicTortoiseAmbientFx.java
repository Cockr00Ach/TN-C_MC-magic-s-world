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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
 * 火山巨龟的火山口轻烟与裂隙火星。
 *
 * <p>只有两种表现：壳顶火山口持续冒出的细烟，和壳面主要裂隙间或崩出的火星。
 * 不做小喷发、不改动画、不加伤害、不动地形。
 *
 * <p>全部在客户端生成，按 client tick 生成，距离越远越稀。
 *
 * <p><b>坐标系</b>：GeckoLib 的骨骼矩阵来自实体渲染的 poseStack，vanilla 在渲染实体前
 * 已经把坐标平移了 -camera，所以矩阵是<b>相机相对</b>的。写回世界坐标时必须加上
 * 渲染当帧的相机位置（{@link Anchor#cameraPos}），否则粒子会落在离 Boss 很远的地方
 * ——这正是 2026-10-22 首次实机“完全看不到”的原因。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VolcanicTortoiseAmbientFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("TN-C/tortoise-ambient");

    /**
     * 临时调试开关：启动参数加 {@code -Dtnc.tortoise.ambient.debug=true} 才会生效。
     * 打开后会在 Boss 包围盒顶部固定位置强制生成肉眼可见的原版烟/火，并每秒打一行日志；
     * 默认关闭，正式构建不受影响。验证完成后按计划整段删除。
     */
    private static final boolean DEBUG = Boolean.getBoolean("tnc.tortoise.ambient.debug");

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

        // ---- 生成频率（每秒个数，按 20 tick/s 折算成每 tick 的小数累计） ---

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
        /** 火星来源：火山口为主，裂隙少量。 */
        static final float SPARK_FISSURE_CHANCE = 0.30F;

        // ---- 状态抑制 -----------------------------------------------------

        /** 攻击动作期间保留的比例（降频但不完全停）。 */
        static final float ATTACK_RATE_SCALE = 0.35F;
        /** 转阶段后暂停多少 tick（68 tick 动画 + 余韵）；只在阶段变化那一次设置。 */
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
    /** 每只 Boss 的频率累计器、调试计数、阶段观察。 */
    private static final Map<VolcanicTortoiseEntity, FxState> STATES = new WeakHashMap<>();

    private static final class Anchor {
        /** 骨骼矩阵：相机相对坐标（vanilla 实体渲染已经平移 -camera）。 */
        Matrix4f shell;
        Matrix4f whorl;
        /** 抓取该帧的相机世界坐标，用来把相机相对矩阵还原成世界坐标。 */
        Vec3 cameraPos;
        int entityTick;
    }

    private static final class FxState {
        int phase = -1;
        long resumeAt;
        float smokeAcc;
        float sparkAcc;
        int smokeThisSecond;
        int sparkThisSecond;
        long secondMark;
        int updates;
        String skip = "ok";
        long debugUntil;
    }

    private VolcanicTortoiseAmbientFx() {
    }

    // ==================================================== 渲染层调用的钩子 ==

    /**
     * 由 {@link VolcanicTortoiseAnchorLayer} 在对应骨骼的 poseStack 上调用。
     *
     * @param boneName "shell" 或 "whorl"
     * @param pose     相机相对的骨骼矩阵（已含实体位置/朝向/缩放/骨骼动画）
     */
    static void captureBone(VolcanicTortoiseEntity entity, String boneName, PoseStack pose) {
        Anchor anchor = ANCHORS.computeIfAbsent(entity, ignored -> new Anchor());
        Matrix4f copy = new Matrix4f(pose.last().pose());
        if ("whorl".equals(boneName)) {
            anchor.whorl = copy;
        } else if ("shell".equals(boneName)) {
            anchor.shell = copy;
        }
        // 相机位置必须和矩阵同一帧抓取，否则快速移动时会有偏移。
        anchor.cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
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

        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }

        // 只处理最近的几个 Boss；排序保证预算优先给离玩家最近的那只。
        List<VolcanicTortoiseEntity> bosses = new ArrayList<>();
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity boss && !boss.isRemoved()
                    && mc.player.distanceToSqr(boss) <= Tuning.MAX_DISTANCE * Tuning.MAX_DISTANCE) {
                bosses.add(boss);
            }
        }
        if (bosses.isEmpty()) {
            return;
        }
        bosses.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
        int count = Math.min(Tuning.MAX_BOSSES, bosses.size());

        int budget = budget(mc);
        for (int i = 0; i < count; i++) {
            VolcanicTortoiseEntity boss = bosses.get(i);
            FxState state = STATES.computeIfAbsent(boss, ignored -> new FxState());
            state.updates++;
            budget = tickBoss(mc, boss, state, now, budget);
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

    private static int tickBoss(Minecraft mc, VolcanicTortoiseEntity boss, FxState state,
                                long now, int budget) {
        Level level = mc.level;
        boolean phaseTwo = boss.isPhaseTwo();

        // ---- 阶段观察：只在阶段变化的那一 tick 设置一次暂停 -----------------
        if (state.phase != boss.phase()) {
            if (state.phase != -1) {
                state.resumeAt = now + Tuning.TRANSITION_PAUSE_TICKS;
            }
            state.phase = boss.phase();
            state.smokeAcc = level.random.nextFloat();
            state.sparkAcc = level.random.nextFloat();
        }

        // ---- 逐项判定跳过原因（日志用，不改变逻辑） -------------------------
        Anchor anchor = ANCHORS.get(boss);
        double distance = Math.sqrt(mc.player.distanceToSqr(boss));
        float proximity = proximity(distance);
        boolean dead = !boss.isAlive() || boss.isDeadOrDying();
        boolean paused = now < state.resumeAt;
        boolean attacking = actionPlaying(boss);

        if (dead) {
            state.skip = "dead";
            ANCHORS.remove(boss);
            return budget;
        }
        if (anchor == null || anchor.whorl == null || anchor.shell == null || anchor.cameraPos == null) {
            state.skip = "no-anchor";
            debugTick(mc, boss, state, now, null, null);
            return budget;
        }
        if (boss.tickCount - anchor.entityTick > 4) {
            state.skip = "stale-anchor";
            debugTick(mc, boss, state, now, null, null);
            return budget;
        }
        if (proximity <= 0.0F) {
            state.skip = "distance";
            debugTick(mc, boss, state, now, null, null);
            return budget;
        }
        if (paused) {
            state.skip = "transition-pause";
            debugTick(mc, boss, state, now, null, craterWorld(anchor, phaseTwo));
            return budget;
        }
        state.skip = attacking ? "attack" : "ok";
        float stateScale = attacking ? Tuning.ATTACK_RATE_SCALE : 1.0F;

        // ---- 世界坐标（相机相对矩阵 + 相机位置） ----------------------------
        Vec3 crater = craterWorld(anchor, phaseTwo);

        // ---- 调试：固定世界位置强制生成，绕过距离/预算/动作抑制 -------------
        if (DEBUG) {
            debugFixedPosition(mc, boss, state, now);
        }

        float rateScale = proximity * stateScale;

        // ---- 轻烟：小数累计，5/s 就是每 tick +0.25，不会取整成 0 -------------
        float smokeRate = (phaseTwo ? Tuning.SMOKE_PER_SECOND_P2 : Tuning.SMOKE_PER_SECOND_P1) * rateScale;
        state.smokeAcc += smokeRate / 20.0F;
        while (state.smokeAcc >= 1.0F && budget > 0) {
            spawnSmoke(level, crater);
            state.smokeAcc -= 1.0F;
            state.smokeThisSecond++;
            budget--;
        }
        if (state.smokeAcc > 4.0F) {
            state.smokeAcc = 4.0F; // 预算不够时不要无限积压
        }

        // ---- 火星：火山口为主、裂隙少量 -------------------------------------
        float sparkRate = (phaseTwo ? Tuning.SPARK_PER_SECOND_P2 : Tuning.SPARK_PER_SECOND_P1) * rateScale;
        state.sparkAcc += sparkRate / 20.0F;
        while (state.sparkAcc >= 1.0F && budget > 0) {
            Vec3 at = crater;
            if (level.random.nextFloat() < Tuning.SPARK_FISSURE_CHANCE) {
                float[][] fissures = Tuning.FISSURES_MODEL[phaseTwo ? 1 : 0];
                at = transform(anchor, anchor.shell, fissures[level.random.nextInt(fissures.length)]);
            }
            spawnSpark(level, at);
            state.sparkAcc -= 1.0F;
            state.sparkThisSecond++;
            budget--;
        }
        if (state.sparkAcc > 4.0F) {
            state.sparkAcc = 4.0F;
        }

        debugTick(mc, boss, state, now, crater, crater);
        return budget;
    }

    /** 火山口世界坐标：相机相对矩阵变换后加上抓取时的相机位置。 */
    private static Vec3 craterWorld(Anchor anchor, boolean phaseTwo) {
        return transform(anchor, anchor.whorl, Tuning.CRATER_MODEL[phaseTwo ? 1 : 0]);
    }

    /**
     * 模型单位 -> 世界坐标。
     * 矩阵是相机相对的（vanilla 渲染实体前平移了 -camera），所以必须加回相机世界坐标。
     */
    private static Vec3 transform(Anchor anchor, Matrix4f matrix, float[] modelUnits) {
        Vector3f point = matrix.transformPosition(new Vector3f(
                modelUnits[0] / 16.0F, modelUnits[1] / 16.0F, modelUnits[2] / 16.0F));
        return anchor.cameraPos.add(point.x, point.y, point.z);
    }

    // =========================================================== 粒子生成 ==

    private static void spawnSmoke(Level level, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        double x = at.x + (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_SPREAD;
        double z = at.z + (level.random.nextDouble() - 0.5D) * Tuning.SMOKE_SPREAD;
        double y = at.y + 0.10D + level.random.nextDouble() * 0.08D;
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

    // ====================================================== 临时调试（可删） ==

    /**
     * 调试第一阶段：在 Boss 包围盒顶部上方约 1 格的世界坐标，用原版烟/火强制生成，
     * 绕过距离、预算、动作抑制，持续约 5 秒。只用于判断“客户端到底能不能出粒子”。
     */
    private static void debugFixedPosition(Minecraft mc, VolcanicTortoiseEntity boss, FxState state, long now) {
        AABB box = boss.getBoundingBox();
        if (state.debugUntil == 0L) {
            state.debugUntil = now + 100L; // 5 秒
        }
        if (now >= state.debugUntil) {
            return;
        }
        Vec3 at = new Vec3((box.minX + box.maxX) * 0.5D, box.maxY + 1.0D, (box.minZ + box.maxZ) * 0.5D);
        for (int i = 0; i < 2; i++) {
            Particle p = mc.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    at.x + (mc.level.random.nextDouble() - 0.5D) * 0.4D,
                    at.y + mc.level.random.nextDouble() * 0.2D,
                    at.z + (mc.level.random.nextDouble() - 0.5D) * 0.4D,
                    0.0D, 0.02D, 0.0D);
            if (p != null) {
                p.setLifetime(40);
                p.scale(1.2F);
            }
            Particle f = mc.particleEngine.createParticle(ParticleTypes.FLAME,
                    at.x, at.y + 0.2D, at.z, 0.0D, 0.03D, 0.0D);
            if (f != null) {
                f.setLifetime(20);
                f.scale(0.9F);
            }
        }
        state.smokeThisSecond += 2;
    }

    /** 每秒一行，不逐粒子/逐帧刷日志。 */
    private static void debugTick(Minecraft mc, VolcanicTortoiseEntity boss, FxState state,
                                  long now, Vec3 crater, Vec3 ignored) {
        if (!DEBUG) {
            return;
        }
        if (state.secondMark == 0L) {
            state.secondMark = now;
        }
        if (now - state.secondMark < 20L) {
            return;
        }
        Anchor anchor = ANCHORS.get(boss);
        String anchorText = anchor == null ? "-" : fmt(craterWorld(anchor, boss.isPhaseTwo()));
        LOGGER.info("TN-C/tortoise-ambient: id={} phase={} updates={} anchor={} cam={} smoke/s={} spark/s={} skip={}",
                boss.getId(), boss.phase(), state.updates, anchorText,
                anchor == null ? "-" : fmt(anchor.cameraPos),
                state.smokeThisSecond, state.sparkThisSecond, state.skip);
        state.smokeThisSecond = 0;
        state.sparkThisSecond = 0;
        state.secondMark = now;
    }

    private static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", v.x, v.y, v.z);
    }
}
