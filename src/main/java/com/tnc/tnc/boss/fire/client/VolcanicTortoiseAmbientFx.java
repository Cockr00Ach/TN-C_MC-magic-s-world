package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
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

    /**
     * 小喷发调试入口开关：启动参数加 {@code -Dtnc.tortoise.eruption.debug=true} 才会注册
     * {@code /tnc_erupt} 客户端命令。命令只对 48 格内最近的一只火山巨龟触发一次正式小喷发，
     * 且仍然遵守存活 / 攻击 / 转阶段限制。默认关闭，正式构建里没有这个命令。
     */
    private static final boolean ERUPTION_DEBUG = Boolean.getBoolean("tnc.tortoise.eruption.debug");

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
        // 2026-10-22：常规火星初始上升速度整体下调 20%
        // （一阶段 0.090/0.200 → 0.072/0.160，二阶段 0.110/0.240 → 0.088/0.192）。
        // 数量、尺寸、颜色、出生位置、横向速度、寿命与烟的参数保持不变。
        // 环境小喷发走独立的 ERUPT_SPARK_UP_*，不会被这条减速影响。
        static final double SPARK_UP_MIN_P1 = 0.072D;
        static final double SPARK_UP_MAX_P1 = 0.160D;
        static final double SPARK_UP_MIN_P2 = 0.088D;
        static final double SPARK_UP_MAX_P2 = 0.192D;
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

        // ---- 无伤害环境小喷发 ---------------------------------------------

        static final boolean ERUPTION_ENABLED = true;
        /** 两次喷发的随机间隔（tick）：一阶段 12~20 秒，二阶段 7~12 秒。 */
        static final int ERUPT_INTERVAL_MIN_P1 = 12 * 20;
        static final int ERUPT_INTERVAL_MAX_P1 = 20 * 20;
        static final int ERUPT_INTERVAL_MIN_P2 = 7 * 20;
        static final int ERUPT_INTERVAL_MAX_P2 = 12 * 20;

        // 时间线：蓄势 8~10 + 喷出 6~8 + 收尾 8~12 = 22~30 tick（1.1~1.5 秒）。

        /** 蓄势：口内亮区由小到大、由暗橙到浅黄，并略微上抬。 */
        static final int ERUPT_CHARGE_MIN = 8;
        static final int ERUPT_CHARGE_MAX = 10;
        /** 喷出：主要火焰簇 + 火星，前 2~3 tick 释放约一半火星。 */
        static final int ERUPT_BURST_MIN = 6;
        static final int ERUPT_BURST_MAX = 8;
        /** 收尾：火焰快速回落、火星继续散开，不再补新火星。 */
        static final int ERUPT_TAIL_MIN = 8;
        static final int ERUPT_TAIL_MAX = 12;

        /** 一次喷发的额外火星总数。 */
        static final int ERUPT_SPARKS_MIN_P1 = 28;
        static final int ERUPT_SPARKS_MAX_P1 = 36;
        static final int ERUPT_SPARKS_MIN_P2 = 44;
        static final int ERUPT_SPARKS_MAX_P2 = 56;
        /** 火星前强后弱：第 i tick 的权重 = (burst - i)^FRONTLOAD。 */
        static final double ERUPT_SPARK_FRONTLOAD = 1.6D;

        /** 一次喷发的额外烟总数（保持克制，不用烟补体积）。 */
        static final int ERUPT_SMOKE_MIN_P1 = 2;
        static final int ERUPT_SMOKE_MAX_P1 = 3;
        static final int ERUPT_SMOKE_MIN_P2 = 3;
        static final int ERUPT_SMOKE_MAX_P2 = 4;
        /** 喷发烟的寿命（tick）。 */
        static final int ERUPT_SMOKE_LIFE_MIN = 10;
        static final int ERUPT_SMOKE_LIFE_MAX = 18;
        static final float ERUPT_SMOKE_SCALE_MIN = 0.35F;
        static final float ERUPT_SMOKE_SCALE_MAX = 0.55F;
        static final double ERUPT_SMOKE_RISE_MIN = 0.030D;
        static final double ERUPT_SMOKE_RISE_MAX = 0.070D;
        static final double ERUPT_SMOKE_SPREAD = 0.14D;

        // ---- 蓄势亮区 -----------------------------------------------------

        /** 蓄势亮区半径（格）：从口内一小点到铺开，p=0 时 start，p=1 时 end。 */
        static final double CHARGE_RADIUS_START = 0.10D;
        static final double CHARGE_RADIUS_END = 0.48D;
        /** 亮区在蓄势期间的上抬高度（格），仍在口沿以下。 */
        static final double CHARGE_RISE = 0.20D;
        static final float CHARGE_SCALE_START = 0.55F;
        static final float CHARGE_SCALE_END = 2.15F;
        static final int CHARGE_LIFE_MIN = 4;
        static final int CHARGE_LIFE_MAX = 7;
        /** 亮区颜色：起始暗橙，结束浅黄。 */
        static final float[] CHARGE_COLOR_START = {1.00F, 0.52F, 0.12F};
        static final float[] CHARGE_COLOR_END = {1.00F, 0.96F, 0.72F};
        static final int CHARGE_PARTICLES_MIN = 2;
        static final int CHARGE_PARTICLES_MAX = 7;

        // ---- 主要火焰簇 ---------------------------------------------------

        /** 火焰簇最高点到火山口中心的高度（格）：一阶段略高出口沿，二阶段更高更宽。 */
        static final double FLAME_HEIGHT_P1 = 1.55D;
        static final double FLAME_HEIGHT_P2 = 2.25D;
        /** 根部半径（格），顶部收拢到 FLAME_TOP_RADIUS。 */
        static final double FLAME_ROOT_RADIUS_P1 = 0.46D;
        static final double FLAME_ROOT_RADIUS_P2 = 0.56D;
        static final double FLAME_TOP_RADIUS = 0.14D;
        static final float FLAME_ROOT_SCALE = 1.75F;
        static final float FLAME_TOP_SCALE = 0.55F;
        /** 喷出最猛时每 tick 的火焰粒子数。 */
        static final int FLAME_PER_TICK_P1 = 7;
        static final int FLAME_PER_TICK_P2 = 10;
        static final int FLAME_LIFE_MIN = 5;
        static final int FLAME_LIFE_MAX = 10;
        static final double FLAME_RISE_MIN = 0.015D;
        static final double FLAME_RISE_MAX = 0.045D;
        /** 收尾阶段火焰簇的强度（相对满强度），快速回落。 */
        static final float FLAME_TAIL_STRENGTH = 0.30F;

        // ---- 喷发火星 -----------------------------------------------------

        /** 喷发火星：比常规火星更快，但寿命拉长，能看到“喷出—上升—散开”。 */
        static final double ERUPT_SPARK_UP_MIN_P1 = 0.26D;
        static final double ERUPT_SPARK_UP_MAX_P1 = 0.42D;
        static final double ERUPT_SPARK_UP_MIN_P2 = 0.30D;
        static final double ERUPT_SPARK_UP_MAX_P2 = 0.48D;
        static final double ERUPT_SPARK_OUTWARD_MIN = 0.045D;
        static final double ERUPT_SPARK_OUTWARD_MAX = 0.140D;
        static final double ERUPT_SPARK_SPREAD = 0.22D;
        static final float ERUPT_SPARK_SCALE_MIN = 0.45F;
        static final float ERUPT_SPARK_SCALE_MAX = 0.85F;
        /** 少量更大颗粒，形成层次。 */
        static final float ERUPT_SPARK_SCALE_BONUS = 0.55F;
        static final float ERUPT_SPARK_BONUS_CHANCE = 0.16F;
        static final int ERUPT_SPARK_LIFE_MIN = 16;
        static final int ERUPT_SPARK_LIFE_MAX = 26;
        /** 喷发期间常规粒子降到这个比例，结束后自然恢复。 */
        static final float ERUPT_REGULAR_SCALE = 0.20F;
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

        // ---- 小喷发调度（按实体保存，互不影响） -------------------------

        /** 下一次允许开始的 gameTime；0 表示还没排过。 */
        long nextEruptionAt;
        /** 本次喷发的开始 gameTime；-1 表示当前没有喷发。 */
        long eruptionStart = -1L;
        int eruptCharge;
        int eruptBurst;
        int eruptTail;
        int eruptSparksTotal;
        int eruptSparksLeft;
        int eruptSmokeTotal;
        int eruptSmokeLeft;
        /** 上一次 tick 的 gameTime，用来识别“离开又回来”的空档。 */
        long lastTickAt;
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

        if (CRATER_DEBUG) {
            // 调试：停掉正式粒子，只在火山口中心打标记，便于玩家确认定位。
            Vec3 at = new Vec3(boss.getX(), boss.getY() + (phaseTwo ? Tuning.CRATER_Y_P2 : Tuning.CRATER_Y_P1), boss.getZ());
            marker(level, at, now);
            logDebug(mc, boss, state, now, at, phaseTwo);
            return budget;
        }

        // 很长时间没 tick（切维度、重新加载、离开追踪范围再回来）不补发：重排一次间隔。
        if (state.lastTickAt != 0L && now - state.lastTickAt > 40L) {
            state.eruptionStart = -1L;
            state.nextEruptionAt = now + interval(level.random, phaseTwo);
        }
        state.lastTickAt = now;

        if (state.nextEruptionAt == 0L) {
            state.nextEruptionAt = now + interval(level.random, phaseTwo);
        }

        // ---- 小喷发调度：同一实体不重叠，攻击就顺延，绝不累计多次 ----------
        if (state.eruptionStart >= 0L) {
            if (attacking) {
                // 喷发中途开始攻击：停止后续生成，已生成的粒子自然消失。
                state.eruptionStart = -1L;
                state.nextEruptionAt = now + interval(level.random, phaseTwo);
            } else {
                budget = tickEruption(level, boss, state, now, phaseTwo, budget);
            }
        } else if (now >= state.nextEruptionAt && !attacking) {
            startEruption(level, state, now, phaseTwo);
            budget = tickEruption(level, boss, state, now, phaseTwo, budget);
        }
        boolean erupting = state.eruptionStart >= 0L;

        Vec3 vent = crater(boss, phaseTwo, level.random);

        // ---- 轻烟：小数累计，5/s 就是每 tick +0.25 -------------------------
        float rateScale = proximity * (attacking ? Tuning.ATTACK_RATE_SCALE : 1.0F);
        if (erupting) {
            // 喷发期间压低常规粒子，避免两套效果叠成浓烟。
            rateScale *= Tuning.ERUPT_REGULAR_SCALE;
        }
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

    /** 抽取下一次喷发间隔：一阶段 12~20 秒，二阶段 7~12 秒。 */
    private static int interval(RandomSource random, boolean phaseTwo) {
        int min = phaseTwo ? Tuning.ERUPT_INTERVAL_MIN_P2 : Tuning.ERUPT_INTERVAL_MIN_P1;
        int max = phaseTwo ? Tuning.ERUPT_INTERVAL_MAX_P2 : Tuning.ERUPT_INTERVAL_MAX_P1;
        return min + random.nextInt(max - min + 1);
    }

    /** 开始一次喷发：只随机一次节奏与总量，之后按 tick 分批放完。 */
    private static void startEruption(ClientLevel level, FxState state, long now, boolean phaseTwo) {
        RandomSource random = level.random;
        state.eruptionStart = now;
        state.eruptCharge = Tuning.ERUPT_CHARGE_MIN
                + random.nextInt(Tuning.ERUPT_CHARGE_MAX - Tuning.ERUPT_CHARGE_MIN + 1);
        state.eruptBurst = Tuning.ERUPT_BURST_MIN
                + random.nextInt(Tuning.ERUPT_BURST_MAX - Tuning.ERUPT_BURST_MIN + 1);
        state.eruptTail = Tuning.ERUPT_TAIL_MIN
                + random.nextInt(Tuning.ERUPT_TAIL_MAX - Tuning.ERUPT_TAIL_MIN + 1);
        int sparksMin = phaseTwo ? Tuning.ERUPT_SPARKS_MIN_P2 : Tuning.ERUPT_SPARKS_MIN_P1;
        int sparksMax = phaseTwo ? Tuning.ERUPT_SPARKS_MAX_P2 : Tuning.ERUPT_SPARKS_MAX_P1;
        state.eruptSparksTotal = sparksMin + random.nextInt(sparksMax - sparksMin + 1);
        state.eruptSparksLeft = state.eruptSparksTotal;
        int smokeMin = phaseTwo ? Tuning.ERUPT_SMOKE_MIN_P2 : Tuning.ERUPT_SMOKE_MIN_P1;
        int smokeMax = phaseTwo ? Tuning.ERUPT_SMOKE_MAX_P2 : Tuning.ERUPT_SMOKE_MAX_P1;
        state.eruptSmokeTotal = smokeMin + random.nextInt(smokeMax - smokeMin + 1);
        state.eruptSmokeLeft = state.eruptSmokeTotal;
    }

    /**
     * 推进一次喷发：蓄势（口内亮区由小到大）→ 喷出（火焰簇 + 前强后弱的火星）→ 收尾（火焰回落、烟补完）。
     * 粒子只在生成时给初速度，之后让它们自己走完寿命，不在结束那一 tick 清场。
     */
    private static int tickEruption(ClientLevel level, VolcanicTortoiseEntity boss, FxState state,
                                    long now, boolean phaseTwo, int budget) {
        int elapsed = (int) (now - state.eruptionStart);
        int charge = state.eruptCharge;
        int burst = state.eruptBurst;

        if (elapsed < charge) {
            state.skip = "erupt-charge";
            float progress = charge <= 1 ? 1.0F : elapsed / (float) (charge - 1);
            budget = chargeGlow(level, boss, phaseTwo, progress, budget);
            return budget;
        }

        int b = elapsed - charge;
        if (b < burst) {
            state.skip = "erupt-burst";
            float strength = 1.0F - 0.45F * (b / (float) Math.max(1, burst - 1));
            budget = flameCluster(level, boss, phaseTwo, strength, budget);

            int count = sparksForTick(state, b, burst);
            for (int i = 0; i < count && budget > 0; i++) {
                spawnEruptionSpark(level, crater(boss, phaseTwo, level.random), phaseTwo);
                budget--;
            }
            if (b == 0 && state.eruptSmokeLeft > 0) {
                spawnEruptionSmoke(level, crater(boss, phaseTwo, level.random), phaseTwo);
                state.eruptSmokeLeft--;
            }
            return budget;
        }

        // 收尾：火焰快速回落（前 3 tick 还有一点余焰），剩余烟每 3 tick 补一个，最后自然结束。
        int t = b - burst;
        boolean finished = t >= state.eruptTail;
        if (finished) {
            state.eruptionStart = -1L;
            state.nextEruptionAt = now + interval(level.random, phaseTwo);
            state.smokeAcc = 0.0F;
            state.sparkAcc = 0.0F;
            state.skip = "ok";
            return budget;
        }
        state.skip = "erupt-tail";
        if (t < 3) {
            float fade = Tuning.FLAME_TAIL_STRENGTH * (1.0F - t / 3.0F);
            budget = flameCluster(level, boss, phaseTwo, fade, budget);
        }
        if (state.eruptSmokeLeft > 0 && t % 3 == 0) {
            spawnEruptionSmoke(level, crater(boss, phaseTwo, level.random), phaseTwo);
            state.eruptSmokeLeft--;
        }
        return budget;
    }

    /** 前强后弱地分配火星：第 i tick 权重 (burst-i)^1.6，最后一 tick 把余数放完。 */
    private static int sparksForTick(FxState state, int b, int burst) {
        int left = state.eruptSparksLeft;
        if (left <= 0) {
            return 0;
        }
        if (b >= burst - 1) {
            state.eruptSparksLeft = 0;
            return left;
        }
        double sum = 0.0D;
        for (int i = b; i < burst; i++) {
            sum += Math.pow(burst - i, Tuning.ERUPT_SPARK_FRONTLOAD);
        }
        double weight = Math.pow(burst - b, Tuning.ERUPT_SPARK_FRONTLOAD);
        int count = (int) Math.round(left * weight / sum);
        count = Mth.clamp(count, b == 0 ? 1 : 0, left);
        state.eruptSparksLeft -= count;
        return count;
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

    /**
     * 蓄势：口内亮区随进度由小到大、由暗橙转浅黄，并略微上抬。
     * 不用光照，靠多颗短命 FLAME 在半透明贴图上的叠加来"变亮"；发光层本身已经是全亮，
     * 单纯加光照看不出差异。
     */
    private static int chargeGlow(ClientLevel level, VolcanicTortoiseEntity boss, boolean phaseTwo,
                                  float progress, int budget) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = level.random;
        float p = Mth.clamp(progress, 0.0F, 1.0F);
        int count = Tuning.CHARGE_PARTICLES_MIN
                + Math.round(p * (Tuning.CHARGE_PARTICLES_MAX - Tuning.CHARGE_PARTICLES_MIN));
        double radius = Tuning.CHARGE_RADIUS_START
                + (Tuning.CHARGE_RADIUS_END - Tuning.CHARGE_RADIUS_START) * p;
        float scale = (float) (Tuning.CHARGE_SCALE_START
                + (Tuning.CHARGE_SCALE_END - Tuning.CHARGE_SCALE_START) * p);
        float[] color = lerpColor(Tuning.CHARGE_COLOR_START, Tuning.CHARGE_COLOR_END, p);
        double baseY = boss.getY() + (phaseTwo ? Tuning.CRATER_Y_P2 : Tuning.CRATER_Y_P1)
                + p * Tuning.CHARGE_RISE;

        for (int i = 0; i < count && budget > 0; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(random.nextDouble()) * radius;
            double x = boss.getX() + Math.cos(angle) * r;
            double z = boss.getZ() + Math.sin(angle) * r;
            double y = baseY + (random.nextDouble() - 0.5D) * 0.05D;
            Particle particle = mc.particleEngine.createParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
            if (particle == null) {
                continue;
            }
            particle.setLifetime(Tuning.CHARGE_LIFE_MIN
                    + random.nextInt(Tuning.CHARGE_LIFE_MAX - Tuning.CHARGE_LIFE_MIN + 1));
            particle.scale(scale * (0.85F + random.nextFloat() * 0.30F));
            particle.setColor(color[0], color[1], color[2]);
            particle.setParticleSpeed(0.0D, 0.0D, 0.0D);
            budget--;
        }
        // 中心保留一点更亮的芯，蓄势后半段才出现。
        if (p > 0.35F && budget > 0) {
            Particle core = mc.particleEngine.createParticle(ParticleTypes.FLAME,
                    boss.getX(), baseY + 0.06D, boss.getZ(), 0.0D, 0.0D, 0.0D);
            if (core != null) {
                core.setLifetime(Tuning.CHARGE_LIFE_MIN + 2);
                core.scale(scale * 1.15F);
                core.setColor(1.0F, 1.0F, 0.92F);
                budget--;
            }
        }
        return budget;
    }

    /**
     * 主要火焰簇：根部在开口内、上方收拢的一簇火焰粒子。
     * 高度按阶段取 1.55 / 2.25 格（相对火山口中心），根部宽、顶部窄，强度随喷发进度衰减。
     */
    private static int flameCluster(ClientLevel level, VolcanicTortoiseEntity boss, boolean phaseTwo,
                                    float strength, int budget) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = level.random;
        float s = Mth.clamp(strength, 0.0F, 1.0F);
        if (s <= 0.0F) {
            return budget;
        }
        int perTick = phaseTwo ? Tuning.FLAME_PER_TICK_P2 : Tuning.FLAME_PER_TICK_P1;
        int count = Math.max(1, Math.round(perTick * s));
        double height = (phaseTwo ? Tuning.FLAME_HEIGHT_P2 : Tuning.FLAME_HEIGHT_P1)
                * (0.55D + 0.45D * s);
        double rootRadius = phaseTwo ? Tuning.FLAME_ROOT_RADIUS_P2 : Tuning.FLAME_ROOT_RADIUS_P1;
        double baseY = boss.getY() + (phaseTwo ? Tuning.CRATER_Y_P2 : Tuning.CRATER_Y_P1);

        for (int i = 0; i < count && budget > 0; i++) {
            // t=0 在根部，t=1 在顶部；平方根偏置让根部更密。
            double t = Math.pow(random.nextDouble(), 0.65D);
            double y = baseY + 0.02D + height * t;
            double radius = (rootRadius + (Tuning.FLAME_TOP_RADIUS - rootRadius) * t)
                    * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = boss.getX() + Math.cos(angle) * radius;
            double z = boss.getZ() + Math.sin(angle) * radius;
            float scale = (float) (Tuning.FLAME_ROOT_SCALE
                    + (Tuning.FLAME_TOP_SCALE - Tuning.FLAME_ROOT_SCALE) * t)
                    * (0.80F + random.nextFloat() * 0.40F);
            double vy = Tuning.FLAME_RISE_MIN
                    + random.nextDouble() * (Tuning.FLAME_RISE_MAX - Tuning.FLAME_RISE_MIN);
            Particle particle = mc.particleEngine.createParticle(ParticleTypes.FLAME, x, y, z, 0.0D, vy, 0.0D);
            if (particle == null) {
                continue;
            }
            particle.setLifetime(Tuning.FLAME_LIFE_MIN
                    + random.nextInt(Tuning.FLAME_LIFE_MAX - Tuning.FLAME_LIFE_MIN + 1));
            particle.scale(scale);
            // 根部橙红、顶部浅黄。
            particle.setColor(1.0F, (float) (0.55D + 0.37D * t), (float) (0.14D + 0.50D * t));
            particle.setParticleSpeed(0.0D, vy, 0.0D);
            budget--;
        }
        return budget;
    }

    /** 在两段颜色之间线性插值。 */
    private static float[] lerpColor(float[] from, float[] to, float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        return new float[]{
                from[0] + (to[0] - from[0]) * k,
                from[1] + (to[1] - from[1]) * k,
                from[2] + (to[2] - from[2]) * k
        };
    }

    /** 喷发火星：速度与高度明显高于常规火星，形成一次短促的“噗发”。 */
    private static void spawnEruptionSpark(ClientLevel level, Vec3 at, boolean phaseTwo) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = level.random;
        double x = at.x + (random.nextDouble() - 0.5D) * Tuning.ERUPT_SPARK_SPREAD;
        double z = at.z + (random.nextDouble() - 0.5D) * Tuning.ERUPT_SPARK_SPREAD;
        double y = at.y + 0.04D + random.nextDouble() * 0.10D;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double outward = Tuning.ERUPT_SPARK_OUTWARD_MIN
                + random.nextDouble() * (Tuning.ERUPT_SPARK_OUTWARD_MAX - Tuning.ERUPT_SPARK_OUTWARD_MIN);
        double vx = Math.cos(angle) * outward;
        double vz = Math.sin(angle) * outward;
        double upMin = phaseTwo ? Tuning.ERUPT_SPARK_UP_MIN_P2 : Tuning.ERUPT_SPARK_UP_MIN_P1;
        double upMax = phaseTwo ? Tuning.ERUPT_SPARK_UP_MAX_P2 : Tuning.ERUPT_SPARK_UP_MAX_P1;
        double vy = upMin + random.nextDouble() * (upMax - upMin);

        float[] color = random.nextFloat() < 0.25F
                ? new float[]{1.00F, 0.93F, 0.62F}
                : Tuning.SPARK_COLORS[random.nextInt(Tuning.SPARK_COLORS.length)];
        Particle particle = mc.particleEngine.createParticle(
                random.nextFloat() < 0.6F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        particle.setLifetime(Tuning.ERUPT_SPARK_LIFE_MIN
                + random.nextInt(Tuning.ERUPT_SPARK_LIFE_MAX - Tuning.ERUPT_SPARK_LIFE_MIN + 1));
        float scale = Tuning.ERUPT_SPARK_SCALE_MIN
                + random.nextFloat() * (Tuning.ERUPT_SPARK_SCALE_MAX - Tuning.ERUPT_SPARK_SCALE_MIN);
        if (random.nextFloat() < Tuning.ERUPT_SPARK_BONUS_CHANCE) {
            scale += Tuning.ERUPT_SPARK_SCALE_BONUS;
        }
        if (phaseTwo) {
            scale += 0.10F;
        }
        particle.scale(scale);
        particle.setColor(color[0], color[1], color[2]);
        particle.setParticleSpeed(vx, vy, vz);
    }

    /** 喷发附带的短命轻烟：少量、很快消失，不会长期挡住火山口。 */
    private static void spawnEruptionSmoke(ClientLevel level, Vec3 at, boolean phaseTwo) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = level.random;
        double x = at.x + (random.nextDouble() - 0.5D) * Tuning.ERUPT_SMOKE_SPREAD;
        double z = at.z + (random.nextDouble() - 0.5D) * Tuning.ERUPT_SMOKE_SPREAD;
        double y = at.y + 0.08D + random.nextDouble() * 0.10D;
        double vx = (random.nextDouble() - 0.5D) * 0.03D;
        double vz = (random.nextDouble() - 0.5D) * 0.03D;
        double vy = Tuning.ERUPT_SMOKE_RISE_MIN
                + random.nextDouble() * (Tuning.ERUPT_SMOKE_RISE_MAX - Tuning.ERUPT_SMOKE_RISE_MIN);
        Particle particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, x, y, z, vx, vy, vz);
        if (particle == null) {
            return;
        }
        particle.setLifetime(Tuning.ERUPT_SMOKE_LIFE_MIN
                + random.nextInt(Tuning.ERUPT_SMOKE_LIFE_MAX - Tuning.ERUPT_SMOKE_LIFE_MIN + 1));
        particle.scale(Tuning.ERUPT_SMOKE_SCALE_MIN
                + random.nextFloat() * (Tuning.ERUPT_SMOKE_SCALE_MAX - Tuning.ERUPT_SMOKE_SCALE_MIN));
        particle.setColor(0.46F, 0.43F, 0.40F);
        particle.setParticleSpeed(vx, vy, vz);
    }

    // ========================================================= 调试入口 ==

    /** 调试命令只注册在客户端，并且需要 {@code -Dtnc.tortoise.eruption.debug=true}。 */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (!ERUPTION_DEBUG) {
            return;
        }
        event.getDispatcher().register(Commands.literal("tnc_erupt").executes(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return 0;
            }
            VolcanicTortoiseEntity nearest = null;
            double best = 48.0D * 48.0D;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof VolcanicTortoiseEntity boss && !boss.isRemoved()) {
                    double d = mc.player.distanceToSqr(boss);
                    if (d <= best) {
                        best = d;
                        nearest = boss;
                    }
                }
            }
            if (nearest == null) {
                context.getSource().sendFailure(Component.literal("48 格内没有火山巨龟。"));
                return 0;
            }
            VolcanicTortoiseEntity target = nearest;
            String refused = forceEruption(target);
            if (refused == null) {
                context.getSource().sendSuccess(
                        () -> Component.literal("已触发一次小喷发：" + target.getName().getString()), false);
                return 1;
            }
            context.getSource().sendFailure(Component.literal("没有触发：" + refused));
            return 0;
        }));
    }

    /** 手动触发一次正式小喷发；返回 null 表示已触发，否则返回拒绝原因。 */
    private static String forceEruption(VolcanicTortoiseEntity boss) {
        if (!boss.isAlive() || boss.isDeadOrDying()) {
            return "实体已死亡";
        }
        if (!(boss.level() instanceof ClientLevel level)) {
            return "不在客户端世界";
        }
        long now = level.getGameTime();
        FxState state = STATES.computeIfAbsent(boss, ignored -> new FxState());
        state.phase = boss.phase();
        if (now < state.resumeAt) {
            return "正在转阶段";
        }
        if (actionPlaying(boss)) {
            return "正在攻击";
        }
        if (state.eruptionStart >= 0L) {
            return "上一次喷发还没结束";
        }
        startEruption(level, state, now, boss.isPhaseTwo());
        return null;
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
