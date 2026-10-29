package com.tnc.tnc.boss.fire;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.tnc.tnc.magic.TNShockwaveEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 火山巨龟 —— 两阶段方块风 Boss。
 *
 * <p>设计口径（照接入说明）：
 * <ul>
 *   <li>同一个实体、同一个 UUID 切两阶段，只换模型 + 贴图 + 数值系数；</li>
 *   <li>阶段与动作分开存：{@code DATA_PHASE} 同步 + NBT，{@code action} 只活在服务端；</li>
 *   <li>攻击都在**固定 tick 结算一次**（{@link #ATTACK_HIT_TICK}），不靠客户端动画事件、不每 tick 结算；</li>
 *   <li>转阶段只触发一次，靠 {@code DATA_PHASE} 判断；**不往属性上叠乘**，二阶段只是换一个伤害系数
 *       （{@link #damageScale()}）⇒ 存档重载不会把伤害越滚越大；</li>
 *   <li>服务端管伤害 / 冷却 / 阶段判定，客户端只按 phase 选贴图与动画。</li>
 * </ul>
 *
 * <p>占地：模型宽 60 单位、高 44 单位、长 88 单位（1 单位 = 1/16 格）⇒ 约 3.75 × 2.75 × 5.5 格。
 */
public final class VolcanicTortoiseEntity extends Monster implements GeoEntity {
    /** 同一 Boss 的燃烧区域共用的受伤记录，避免重叠区域同时结算。 */
    private final java.util.Map<UUID, Integer> fieldHits = new java.util.HashMap<>();

    public boolean tryFieldHit(UUID target) {
        int now = this.tickCount;
        Integer last = this.fieldHits.get(target);
        if (last != null && now - last < VolcanicFireFieldEntity.SHARED_HIT_INTERVAL) {
            return false;
        }
        this.fieldHits.put(target, now);
        return true;
    }

    private static final org.slf4j.Logger SPIT_LOG = org.slf4j.LoggerFactory.getLogger("TN-C/spit");
    private static final org.slf4j.Logger SKILL_LOG = org.slf4j.LoggerFactory.getLogger("TN-C/skill");
    /** 一阶段满血；半血切二阶段。 */
    public static final float MAX_HP = 900.0F;
    public static final float PHASE_TWO_HEALTH_FRACTION = 0.5F;

    /** 碰撞箱（方块）。模型是长的，但 AABB 只能是方形，取宽度那一档。 */
    public static final float HITBOX_WIDTH = 5.4F;
    public static final float HITBOX_HEIGHT = 4.3F;
    /** 二阶段模型整体放到 1.2 倍（作者 2026-10-20 要求），碰撞箱跟着换。 */
    public static final float HITBOX_WIDTH_P2 = 6.6F;
    public static final float HITBOX_HEIGHT_P2 = 6.4F;

    /** 撕咬判定距离（格）。 */
    public static final double BITE_RANGE = 8.5D;
    /** 咬合扇区半角（度）：预警和伤害共用同一份几何。 */
    public static final double BITE_HALF_ANGLE_DEG = 35.0D;
    /** 嘴部锚点（格）：模型正前方距离 / 离地高度，两阶段分开标定。 */
    private static final double MOUTH_FORWARD_P1 = 5.3D;
    private static final double MOUTH_UP_P1 = 1.35D;
    private static final double MOUTH_FORWARD_P2 = 6.6D;
    private static final double MOUTH_UP_P2 = 0.70D;
    /** 攻击调试入口（-/tnc_bite、危险范围可视化），默认关闭。 */
    public static final boolean ATTACK_DEBUG = Boolean.getBoolean("tnc.tortoise.attack.debug");
    /** 重踏半径（格）：AI 的起手距离仍按这个值；实际伤害由扩散火环结算。 */
    public static final double SLAM_RADIUS = 9.5D;
    /** 扩散火环的最终半径（格）。 */
    public static final double SLAM_RING_RADIUS = 22.0D;
    /** 火环环带宽度（格）：伤害在 [半径-带宽/2, 半径+带宽/2] 之间结算。 */
    public static final double SLAM_RING_BAND = 2.0D;
    /** 脚下直接冲击半径（格）：与火环共用 castHits，同一次重踏只结算一次。 */
    private static final double SLAM_IMPACT_RADIUS = 3.0D;
    /** 目标与地表高差容差（格）：超过视为站高墙/坑里，火环够不到。 */
    private static final double SLAM_HEIGHT_TOLERANCE = 2.0D;
    /** 重踏震屏强度（度），0 = 关闭；用 -Dtnc.tortoise.slam.shake=3 开启。 */
    private static final float SLAM_SHAKE = parseFloatProperty("tnc.tortoise.slam.shake", 0.0F);
    /** 火环命中击退强度（原版 knockback 参数；内部会按目标的击退抗性衰减）。 */
    private static final double SLAM_KNOCKBACK = 0.6D;
    /** 二阶段火山弹的落点散布半径（格）。 */
    public static final double ERUPTION_SPREAD = 8.0D;

    private static final int PHASE_ONE = 1;
    private static final int PHASE_TWO = 2;

    /** 动作编号（只活在服务端；客户端只通过 triggerAnim 看动画）。 */
    private static final int ACTION_NONE = 0;
    private static final int ACTION_BITE = 1;
    private static final int ACTION_SLAM = 2;
    private static final int ACTION_TRANSITION = 3;
    private static final int ACTION_ERUPTION = 4;
    /** 技能 B：大地震波（只有地面起伏与击退，没有火环）。 */
    private static final int ACTION_QUAKE = 5;
    /** 技能 C（任务 5）：嘴前蓄力后喷吐一发火球。 */
    private static final int ACTION_SPIT = 6;
    /** 技能 D（任务 7）：二阶段熔岩之柱——前方长方形走廊预警 + 分批冲起的火柱。 */
    private static final int ACTION_PILLAR = 7;

    /** 预警时长（30–40 tick 之间）。 */
    public static final int PILLAR_WARN_TICKS = 36;
    /** 相邻两柱的间隔 tick。 */
    private static final int PILLAR_STAGGER = 4;
    /** 走廊内每根柱子的危险半径（与可见柱体主干部位对齐）。 */
    public static final double PILLAR_DANGER_RADIUS = 1.6D;
    /** 危险高度：视觉柱 15–19.5 格，但伤害只结算到 8 格，避免变成无限高伤墙。 */
    public static final double PILLAR_DANGER_HEIGHT = 8.0D;
    private static final float PILLAR_DAMAGE_SCALE = 0.85F;
    /** 从身体前缘外多远处开始算走廊。 */
    private static final double PILLAR_FRONT_OFFSET = 2.6D;
    private static final int PILLAR_COOLDOWN_TWO = 240;
    private static final int PILLAR_COOLDOWN_ONE = 320;

    // ------------------------------------------------- 任务 9：狂暴 / 虚弱 --
    /** 触发阈值：血量比例严格低于该值（默认 20%）。 */
    private static final float ENRAGE_THRESHOLD =
            parseFloatProperty("tnc.tortoise.enrage.threshold", 0.20F);
    /** 狂暴持续 tick（默认 200 = 10 秒）。 */
    private static final int ENRAGE_TICKS =
            (int) parseFloatProperty("tnc.tortoise.enrage.ticks", 200.0F);
    /** 狂暴期攻击伤害倍率。 */
    private static final float ENRAGE_DAMAGE_MULT =
            parseFloatProperty("tnc.tortoise.enrage.damage", 2.0F);
    /** 冷却倒计时倍速：0.5 = 冷却以两倍速度走完，技能间隔约减半。 */
    private static final float ENRAGE_COOLDOWN_SCALE =
            parseFloatProperty("tnc.tortoise.enrage.cooldown", 0.5F);
    /** 狂暴结束后的虚弱窗口（默认 160 tick = 8 秒）。 */
    private static final int WEAK_TICKS =
            (int) parseFloatProperty("tnc.tortoise.weak.ticks", 160.0F);
    /** 虚弱期受到伤害的倍率。 */
    private static final float WEAK_DAMAGE_TAKEN =
            parseFloatProperty("tnc.tortoise.weak.damage", 2.0F);

    public static final int STATE_NORMAL = 0;
    public static final int STATE_ENRAGED = 1;
    public static final int STATE_WEAK = 2;

    /** 喷吐：蓄力 24–32 tick，后段锁定目标朝向，随后发射。 */
    private static final int SPIT_CHARGE_MIN = 24;
    private static final int SPIT_CHARGE_MAX = 32;
    /** 锁定发生在蓄力的这个比例处（留出可辨识的锁定窗口）。 */
    private static final float SPIT_LOCK_FRACTION = 0.72F;
    /** 火球伤害系数：基础攻击力 × 该系数 × 阶段倍率。 */
    private static final float SPIT_DAMAGE_SCALE = 0.9F;
    /** 起手距离：太近用咬，太远不喷。 */
    private static final double SPIT_MIN_RANGE = 7.0D;
    private static final double SPIT_MAX_RANGE = 30.0D;

    /** 动作时长（tick）。 */
    private static final int BITE_TICKS = 20;
    private static final int SLAM_TICKS = 72; // 3.6s：24 tick 抬脚蓄力 / 30 tick 火环扩散 / 18 tick 收势
    private static final int TRANSITION_TICKS = TortoiseTransition.DURATION;
    private static final int ERUPTION_TICKS = 46;

    /** 命中结算发生在动作的第几 tick —— 对应动画里"砸到地"的那一帧。 */
    /** 咬合落在动画 0.50s 的闭嘴帧上（1.0s 动画 = 20 tick）。 */
    private static final int BITE_HIT_TICK = 10;
    /** 前脚砸地帧（1.2s），火环从这里开始扩散。 */
    private static final int SLAM_HIT_TICK = 24;
    /** 火环从中心推到 SLAM_RING_RADIUS 所需的 tick。 */
    /** 火环从中心推到最大半径的 tick 数（客户端网格也用它换算半径）。 */
    public static final int SLAM_RING_TICKS = 30;
    private static final int ERUPTION_HIT_TICK = 26;

    // ---------------------------------------------------------- 技能 B：大地震波 --
    /** 共用砸地帧，额外留出末圈地块归位时间。 */
    public static final int QUAKE_TICKS = 78;
    public static final int QUAKE_HIT_TICK = TortoiseQuake.WARNING_TICKS;
    /** 地面波从中心推到最大半径的 tick 数（比火环慢，方便看清地块依次抬起）。 */
    public static final int QUAKE_RING_TICKS = TortoiseQuake.TRAVEL_TICKS;
    /** 独立的最大半径，不与火环共用配置。 */
    public static final double QUAKE_RADIUS = TortoiseQuake.RADIUS;
    /** 波峰带宽（格）：只有这个宽度内的地块在抬升。 */
    public static final double QUAKE_BAND = 3.5D;
    private static final double QUAKE_HEIGHT_TOLERANCE = 2.0D;
    /** 首轮基础伤害为 0，只保留配置入口，后续按测试结果调整。 */
    private static final float QUAKE_DAMAGE = parseFloatProperty("tnc.tortoise.quake.damage", 0.0F);
    private static final double QUAKE_KNOCKBACK = parseFloatProperty("tnc.tortoise.quake.knockback", 0.85F);
    /** 命中即击飞；眩晕从同一刻计时100tick，落地不会重置。 */
    private static final double QUAKE_LIFT = parseFloatProperty("tnc.tortoise.quake.lift", 0.90F);

    private static final String ACTION_CONTROLLER = "action";

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    /** 重踏火环：服务端锁定的砸地时刻（gameTime，-1 表示当前没有火环）。 */
    private static final EntityDataAccessor<Integer> DATA_SLAM_START =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    /** 重踏火环：服务端锁定的砸地中心，火环展开后不再跟随 Boss 移动。 */
    private static final EntityDataAccessor<Float> DATA_SLAM_X =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SLAM_Y =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SLAM_Z =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);

    /** 大地震波：独立的施法编号（-1 = 当前没有震波），客户端据此创建地面波。 */
    private static final EntityDataAccessor<Integer> DATA_QUAKE_START =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    /** Elapsed cast age, independent of client/server world clock offsets. */
    private static final EntityDataAccessor<Integer> DATA_QUAKE_AGE =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_QUAKE_X =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUAKE_Y =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUAKE_Z =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUAKE_YAW =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    /** 喷吐蓄力：开始时刻（-1 = 当前没在蓄力）+ 总蓄力 tick。 */
    private static final EntityDataAccessor<Integer> DATA_SPIT_START =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPIT_CHARGE =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    /** 熔岩之柱：施法开始时刻（-1 = 当前没有该技能），柱位表由它作种子两端推导。 */
    private static final EntityDataAccessor<Integer> DATA_PILLAR_START =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PILLAR_X =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PILLAR_Y =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PILLAR_Z =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    /** 走廊朝向（锁定时刻的 yaw，之后不再跟随玩家旋转）。 */
    private static final EntityDataAccessor<Float> DATA_PILLAR_YAW =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    /** 狂暴状态：0 正常 / 1 狂暴 / 2 虚弱易伤（只同步状态，剩余时间由服务端自己算）。 */
    private static final EntityDataAccessor<Integer> DATA_ENRAGE_STATE =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);

    /** 与血条 UUID 对应的同步数据（COMPOUND_TAG 里放 Most/Least 两个 long）。 */
    private static final EntityDataAccessor<CompoundTag> DATA_BAR_ID =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** -1 = 非转换；统一时间轴让模型、动画、发光和特效在晚加入时仍能对齐。 */
    private static final EntityDataAccessor<Long> DATA_TRANSITION_START =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.LONG);

    private static final EntityDataAccessor<Integer> DATA_DEATH_AGE =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);
    private int serverDeathTicks = -1;
    private final TortoiseDeathClock clientDeathClock = new TortoiseDeathClock();
    private Vec3 deathAnchor = Vec3.ZERO;
    private float deathYaw;
    private boolean deathRemainsSpawned;

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int action = ACTION_NONE;
    /** 上一次真正出手的技能（用于避免连续两次大范围技能）。 */
    private int lastSkill = ACTION_NONE;
    private int actionTicks;
    private Vec3 transitionAnchor = Vec3.ZERO;
    private float transitionYaw;
    private boolean transitionPreviousNoAi;
    private boolean transitionPreviousNoGravity;
    private boolean transitionPreviousInvulnerable;
    /** 转阶段递增，已卸载的旧投射物/燃烧区回来后也不能继续结算。 */
    private int hazardEpoch;
    /** 砸地瞬间锁定的冲击中心：视觉与伤害共用同一个点与传播速度。 */
    private Vec3 slamCenter = Vec3.ZERO;
    /** 调试：把火环的最大半径画出来（仅 ATTACK_DEBUG 的 /tnc_slam true）。 */
    private int debugRingTicks;
    /** 大地震波：砸地瞬间锁定的中心，展开后不再跟随本体。 */
    private Vec3 quakeCenter = Vec3.ZERO;
    /** 震波命中记录：与火环的 castHits 完全分开。 */
    private final java.util.Set<java.util.UUID> quakeHits = new java.util.HashSet<>();
    /** 喷吐：本次蓄力的目标（按 id 存，目标丢失即放弃）、锁定点与冷却。 */
    private int spitCharge;
    private int spitLockTick;
    private int spitTargetId = -1;
    private Vec3 spitAim = Vec3.ZERO;
    private int spitCooldown;
    /** 熔岩之柱：走廊起点、锁定朝向、开始 tick 与冷却。 */
    private Vec3 pillarOrigin = Vec3.ZERO;
    private float pillarYaw;
    private int pillarStartTick = -1;
    private int pillarCooldown = PILLAR_COOLDOWN_TWO;
    private int attackCooldown = 40;
    private int slamCooldown = 120;
    /** 技能 B 的独立冷却，避免与火环紧接连放。 */
    private int quakeCooldown = 240;
    private int eruptionCooldown = 160;

    /** 狂暴：每场战斗只触发一次，触发后永久置位（存档保存）。 */
    private boolean enrageTriggered;
    /** 血量已跌破阈值但正在放技能/转阶段：等动作结束再进狂暴（转阶段优先）。 */
    private boolean enragePending;
    private int enrageTicks;
    private int weakTicks;

    // ---------------------------------------------------------- 火山喷发（任务 8）--
    /** 蓄势 tick 下限/上限。 */
    public static final int BARRAGE_CHARGE_MIN = 30;
    public static final int BARRAGE_CHARGE_MAX = 40;
    /** 三批、每批 7 颗，共 21 颗。 */
    public static final int BARRAGE_WAVES = 3;
    public static final int BARRAGE_PER_WAVE = 7;
    public static final int BARRAGE_WAVE_GAP = 16;
    /** 同一批里每颗之间的间隔（一颗颗喷出，避免 7 颗同时在空中共存）。 */
    public static final int BARRAGE_BALL_STAGGER = 2;
    /** 落区：内圈安全区 8 格，外缘 24 格。 */
    public static final double BARRAGE_INNER_RADIUS = 8.0D;
    public static final double BARRAGE_OUTER_RADIUS = 24.0D;
    public static final double BARRAGE_MIN_SPACING = 3.2D;
    /** 单颗伤害系数（发射时快照）。 */
    public static final float BARRAGE_DAMAGE_SCALE = 0.55F;
    /** 同一目标受击间隔与单次喷发上限。 */
    private static final int BARRAGE_HIT_INTERVAL = 10;
    private static final int BARRAGE_MAX_HITS = 8;

    private final java.util.Map<UUID, Integer> mortarLastHit = new java.util.HashMap<>();
    private final java.util.List<Vec3> barrageSpots = new java.util.ArrayList<>();
    private int mortarHits;
    private int barrageCharge;
    private int barrageBall;
    /** 本次喷发的形状参数：普通喷发用常量，狂暴登场的“大喷发”临时放大。 */
    private int barrageWaves = BARRAGE_WAVES;
    private int barragePerWave = BARRAGE_PER_WAVE;
    private int barrageWaveGap = BARRAGE_WAVE_GAP;
    private int barrageBallStagger = BARRAGE_BALL_STAGGER;
    private double barrageInner = BARRAGE_INNER_RADIUS;
    private double barrageOuter = BARRAGE_OUTER_RADIUS;
    private double barrageSpacing = BARRAGE_MIN_SPACING;
    private float barrageDamage = BARRAGE_DAMAGE_SCALE;
    /** 狂暴登场的大喷发标记：只在开始那一轮生效，结束后自动复位。 */
    private boolean megaEruption;

    /** 大喷发：5 批 × 12 颗 = 60 颗，落区外缘 34 格，比常规喷发大得多。 */
    private static final int MEGA_WAVES = 5;
    private static final int MEGA_PER_WAVE = 12;
    private static final int MEGA_WAVE_GAP = 10;
    private static final int MEGA_BALL_STAGGER = 1;
    private static final double MEGA_INNER_RADIUS = 5.0D;
    private static final double MEGA_OUTER_RADIUS = 34.0D;
    private static final double MEGA_MIN_SPACING = 2.4D;
    private static final float MEGA_DAMAGE_SCALE = 0.42F;
    private static final int MEGA_CHARGE_TICKS = 24;
    /** 二阶段火山弹的落点（服务端算好，倒计时结束时炸）。 */
    private final java.util.List<Vec3> pendingBlasts = new java.util.ArrayList<>();
    private int blastCountdown;

    /** 本次施法编号：同一次施法内只结算一次（重复判定/重入不会二次扣血）。 */
    private long castId;
    /** 施法开始时锁定的朝向；预警与伤害都用它，施法过程中不再跟随玩家。 */
    private float castYaw;
    private final java.util.Set<java.util.UUID> castHits = new java.util.HashSet<>();
    /** 调试用危险范围可视化的剩余 tick（仅 ATTACK_DEBUG 下生效）。 */
    private int debugOutlineTicks;

    public VolcanicTortoiseEntity(EntityType<? extends VolcanicTortoiseEntity> type, Level level) {
        super(type, level);
        this.xpReward = 420;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.16D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ------------------------------------------------------------------ 状态 --

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isPhaseTwo() {
        return this.phase() == PHASE_TWO;
    }

    public boolean isTransitioning() {
        return this.entityData.get(DATA_TRANSITION_START) >= 0L;
    }

    public float transitionAge(float partialTick) {
        return TortoiseTransition.age(this.entityData.get(DATA_TRANSITION_START),
                this.level().getGameTime(), partialTick);
    }

    public boolean isDyingAnimation() {
        return this.entityData.get(DATA_DEATH_AGE) >= 0
                // LivingEntity's constructor calls our setHealth before subclass fields initialize.
                || (this.level().isClientSide && this.clientDeathClock != null && this.clientDeathClock.started());
    }

    public float deathAge(float partialTick) {
        return this.level().isClientSide ? this.clientDeathClock == null ? -1 : this.clientDeathClock.age(this.tickCount, partialTick)
                : this.serverDeathTicks < 0 ? -1 : Math.min(TortoiseDeath.DURATION, this.serverDeathTicks + partialTick);
    }

    public boolean usesPhaseTwoModel() {
        return this.isTransitioning() ? TortoiseTransition.phaseTwo(this.transitionAge(0)) : this.isPhaseTwo();
    }

    public int hazardEpoch() {
        return this.hazardEpoch;
    }

    /** 二阶段伤害系数：不写进属性，重载不会叠乘；狂暴倍率也只在这里乘一次。 */
    public double damageScale() {
        double scale = this.isPhaseTwo() ? 1.35D : 1.0D;
        if (this.isEnraged()) {
            scale *= ENRAGE_DAMAGE_MULT;
        }
        return scale;
    }

    /** 0 正常 / 1 狂暴 / 2 虚弱易伤；客户端用它决定环境粒子与提示。 */
    public int enrageState() {
        return this.entityData.get(DATA_ENRAGE_STATE);
    }

    public boolean isEnraged() {
        return this.enrageState() == STATE_ENRAGED;
    }

    public boolean isWeakened() {
        return this.enrageState() == STATE_WEAK;
    }

    /** 服务端：剩余狂暴/虚弱 tick（调试与存档校验用）。 */
    public int enrageTicksLeft() {
        return Math.max(this.enrageTicks, this.weakTicks);
    }

    public boolean isBusy() {
        return this.action != ACTION_NONE;
    }

    /** 客户端渲染用：火环开始的 gameTime，-1 表示没有正在展开的火环。 */
    public int slamRingStart() {
        return this.entityData.get(DATA_SLAM_START);
    }

    /** 客户端渲染用：服务端锁定的砸地中心（与伤害共用同一个点）。 */
    public Vec3 slamRingCenter() {
        return new Vec3(this.entityData.get(DATA_SLAM_X), this.entityData.get(DATA_SLAM_Y),
                this.entityData.get(DATA_SLAM_Z));
    }

    /** 服务端：把本次火环的中心与开始时刻同步给客户端（只在砸地瞬间调用一次）。 */
    private void publishSlamRing(ServerLevel level) {
        this.entityData.set(DATA_SLAM_X, (float) this.slamCenter.x);
        this.entityData.set(DATA_SLAM_Y, (float) this.slamCenter.y);
        this.entityData.set(DATA_SLAM_Z, (float) this.slamCenter.z);
        this.entityData.set(DATA_SLAM_START, (int) level.getGameTime());
    }

    private void clearSlamRing() {
        this.entityData.set(DATA_SLAM_START, -1);
    }

    /** 客户端渲染用：大地震波的施法编号，-1 表示当前没有震波。 */
    public int quakeWaveStart() {
        return this.entityData.get(DATA_QUAKE_START);
    }

    public int quakeWaveAge() {
        return this.entityData.get(DATA_QUAKE_AGE);
    }

    /** 客户端渲染用：震波锁定的砸地中心。 */
    public Vec3 quakeWaveCenter() {
        return new Vec3(this.entityData.get(DATA_QUAKE_X), this.entityData.get(DATA_QUAKE_Y),
                this.entityData.get(DATA_QUAKE_Z));
    }

    public float quakeWaveYaw() {
        return this.entityData.get(DATA_QUAKE_YAW);
    }

    /** Lock warning and impact to one origin and direction at the start of the windup. */
    private void publishQuake() {
        this.entityData.set(DATA_QUAKE_X, (float) this.quakeCenter.x);
        this.entityData.set(DATA_QUAKE_Y, (float) this.quakeCenter.y);
        this.entityData.set(DATA_QUAKE_Z, (float) this.quakeCenter.z);
        this.entityData.set(DATA_QUAKE_YAW, this.castYaw);
        this.entityData.set(DATA_QUAKE_AGE, -QUAKE_HIT_TICK);
        this.entityData.set(DATA_QUAKE_START, (int) (this.castId & Integer.MAX_VALUE));
    }

    private void clearQuake() {
        this.entityData.set(DATA_QUAKE_START, -1);
        this.entityData.set(DATA_QUAKE_AGE, -1);
    }

    /** 客户端渲染用：喷吐蓄力的开始时刻（-1 表示没有蓄力）。 */
    public int spitChargeStart() {
        return this.entityData.get(DATA_SPIT_START);
    }

    /** 客户端渲染用：本次蓄力的总 tick 数。 */
    public int spitChargeTicks() {
        return this.entityData.get(DATA_SPIT_CHARGE);
    }

    private void publishSpit(ServerLevel level) {
        this.entityData.set(DATA_SPIT_CHARGE, this.spitCharge);
        this.entityData.set(DATA_SPIT_START, (int) level.getGameTime());
    }

    private void clearSpit() {
        this.entityData.set(DATA_SPIT_START, -1);
        this.spitTargetId = -1;
        this.spitAim = Vec3.ZERO;
    }

    /** 客户端：熔岩之柱开始时刻，-1 表示没有。 */
    public int pillarWaveStart() {
        return this.entityData.get(DATA_PILLAR_START);
    }

    /** 客户端：走廊起点（服务端锁定，之后不跟随本体）。 */
    public Vec3 pillarWaveOrigin() {
        return new Vec3(this.entityData.get(DATA_PILLAR_X), this.entityData.get(DATA_PILLAR_Y),
                this.entityData.get(DATA_PILLAR_Z));
    }

    /** 客户端：走廊朝向（锁定值）。 */
    public float pillarWaveYaw() {
        return this.entityData.get(DATA_PILLAR_YAW);
    }

    private void publishPillar(ServerLevel level) {
        this.entityData.set(DATA_PILLAR_X, (float) this.pillarOrigin.x);
        this.entityData.set(DATA_PILLAR_Y, (float) this.pillarOrigin.y);
        this.entityData.set(DATA_PILLAR_Z, (float) this.pillarOrigin.z);
        this.entityData.set(DATA_PILLAR_YAW, this.pillarYaw);
        this.entityData.set(DATA_PILLAR_START, this.pillarStartTick);
    }

    private void clearPillar() {
        this.entityData.set(DATA_PILLAR_START, -1);
    }

    /** 柱位在世界里的位置（服务端与客户端共用同一套推导）。 */
    public Vec3 pillarSpotCenter(PillarLayout.Spot spot) {
        return pillarSpot(this.pillarOrigin, this.pillarYaw, spot);
    }

    /** 走廊内的局部偏移转世界坐标：forward 沿锁定朝向，lateral 沿右侧。 */
    public static Vec3 pillarSpot(Vec3 origin, float yaw, PillarLayout.Spot spot) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return origin.add(forward.scale(spot.forward())).add(right.scale(spot.lateral()));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, PHASE_ONE);
        this.entityData.define(DATA_SLAM_START, -1);
        this.entityData.define(DATA_SLAM_X, 0.0F);
        this.entityData.define(DATA_SLAM_Y, 0.0F);
        this.entityData.define(DATA_SLAM_Z, 0.0F);
        this.entityData.define(DATA_QUAKE_START, -1);
        this.entityData.define(DATA_QUAKE_AGE, -1);
        this.entityData.define(DATA_QUAKE_X, 0.0F);
        this.entityData.define(DATA_QUAKE_Y, 0.0F);
        this.entityData.define(DATA_QUAKE_Z, 0.0F);
        this.entityData.define(DATA_QUAKE_YAW, 0.0F);
        this.entityData.define(DATA_SPIT_START, -1);
        this.entityData.define(DATA_SPIT_CHARGE, 0);
        this.entityData.define(DATA_PILLAR_START, -1);
        this.entityData.define(DATA_PILLAR_X, 0.0F);
        this.entityData.define(DATA_PILLAR_Y, 0.0F);
        this.entityData.define(DATA_PILLAR_Z, 0.0F);
        this.entityData.define(DATA_PILLAR_YAW, 0.0F);
        this.entityData.define(DATA_ENRAGE_STATE, STATE_NORMAL);
        this.entityData.define(DATA_BAR_ID, new CompoundTag());
        this.entityData.define(DATA_TRANSITION_START, -1L);
        this.entityData.define(DATA_DEATH_AGE, -1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DEATH_AGE.equals(key) && this.level().isClientSide && this.clientDeathClock != null) {
            this.clientDeathClock.receive(this.entityData.get(DATA_DEATH_AGE), this.tickCount);
        }
        if (DATA_PHASE.equals(key)) {
            this.refreshDimensions();
        }
    }

    /**
     * 血条 UUID 随实体同步：客户端用它把 Boss 血条与实体一一对应，不依赖名字/语言。
     * （1.20.1 的 ServerBossEvent 不能指定 UUID，所以把两边连起来只能走实体同步数据。）
     */
    public UUID bossBarUuid() {
        CompoundTag tag = this.entityData.get(DATA_BAR_ID);
        if (!tag.contains("Most") || !tag.contains("Least")) {
            return null;
        }
        return new UUID(tag.getLong("Most"), tag.getLong("Least"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TortoiseDeathAge", this.isDyingAnimation() ? Math.min(TortoiseDeath.DURATION - 1, (int) this.deathAge(0)) : -1);
        tag.putFloat("TortoiseDeathYaw", this.deathYaw);
        tag.putBoolean("TortoiseRemainsSpawned", this.deathRemainsSpawned);
        tag.putInt("TortoisePhase", this.phase());
        tag.putBoolean("TortoiseEnrageTriggered", this.enrageTriggered);
        tag.putInt("TortoiseEnrageTicks", this.enrageTicks);
        tag.putInt("TortoiseWeakTicks", this.weakTicks);
        tag.putBoolean("TortoiseEnragePending", this.enragePending);
        tag.putInt("TortoiseHazardEpoch", this.hazardEpoch);
        tag.putInt("TortoiseTransitionAge", this.isTransitioning()
                ? Math.min(TRANSITION_TICKS - 1, (int) this.transitionAge(0)) : -1);
        if (this.isTransitioning()) {
            tag.putFloat("TortoiseTransitionYaw", this.transitionYaw);
            tag.putBoolean("TortoiseTransitionNoAi", this.transitionPreviousNoAi);
            tag.putBoolean("TortoiseTransitionNoGravity", this.transitionPreviousNoGravity);
            tag.putBoolean("TortoiseTransitionInvulnerable", this.transitionPreviousInvulnerable);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE,
                tag.getInt("TortoisePhase") >= PHASE_TWO ? PHASE_TWO : PHASE_ONE);
        this.enrageTriggered = tag.getBoolean("TortoiseEnrageTriggered");
        this.enrageTicks = Math.max(0, tag.getInt("TortoiseEnrageTicks"));
        this.weakTicks = Math.max(0, tag.getInt("TortoiseWeakTicks"));
        this.enragePending = tag.getBoolean("TortoiseEnragePending");
        this.hazardEpoch = Math.max(0, tag.getInt("TortoiseHazardEpoch"));
        if (this.enrageTicks > 0) {
            this.entityData.set(DATA_ENRAGE_STATE, STATE_ENRAGED);
        } else if (this.weakTicks > 0) {
            this.entityData.set(DATA_ENRAGE_STATE, STATE_WEAK);
            this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, this.weakTicks, 0, false, true, true));
        } else {
            this.entityData.set(DATA_ENRAGE_STATE, STATE_NORMAL);
        }
        int deathAge = tag.contains("TortoiseDeathAge") ? tag.getInt("TortoiseDeathAge") : -1;
        if (deathAge >= 0) {
            this.serverDeathTicks = Math.min(TortoiseDeath.DURATION - 1, deathAge);
            this.entityData.set(DATA_DEATH_AGE, this.serverDeathTicks);
            this.deathAnchor = this.position();
            this.deathYaw = tag.getFloat("TortoiseDeathYaw");
            this.deathRemainsSpawned = tag.getBoolean("TortoiseRemainsSpawned");
            this.setNoAi(true);
            this.setNoGravity(true);
            this.bossEvent.setVisible(false);
            this.clearTransitionEffects();
        }
        // 属性由 LivingEntity 自己恢复，这里绝不重新乘一遍
        int age = tag.contains("TortoiseTransitionAge") ? tag.getInt("TortoiseTransitionAge") : -1;
        if (age >= 0 && deathAge < 0) {
            age = Math.min(TRANSITION_TICKS - 1, age);
            this.transitionAnchor = this.position();
            this.transitionYaw = tag.getFloat("TortoiseTransitionYaw");
            this.transitionPreviousNoAi = tag.getBoolean("TortoiseTransitionNoAi");
            this.transitionPreviousNoGravity = tag.getBoolean("TortoiseTransitionNoGravity");
            this.transitionPreviousInvulnerable = tag.getBoolean("TortoiseTransitionInvulnerable");
            this.entityData.set(DATA_TRANSITION_START, Math.max(0L, this.level().getGameTime() - age));
            this.action = ACTION_TRANSITION;
            this.actionTicks = age;
            this.setNoAi(true);
            this.setNoGravity(true);
            this.setInvulnerable(true);
            this.clearTransitionEffects();
        }
    }

    /** 虚弱窗口内受到的伤害翻倍；只作用于这只 Boss，且在 hurt 里只乘一次。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 包括虚空、创造玩家和 bypasses_invulnerability 的伤害，均不进入父类结算。
        if (this.isTransitioning() || this.isDyingAnimation()) return false;
        if (this.isWeakened() && amount > 0.0F) {
            amount *= WEAK_DAMAGE_TAKEN;
        }
        return super.hurt(source, amount);
    }

    /** 狂暴期间拒绝新增负面效果（有益效果照常），但不是无敌。 */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (this.isTransitioning() || this.isDyingAnimation()) return false;
        if (this.isEnraged() && !effect.getEffect().isBeneficial()) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return this.isTransitioning() || this.isDyingAnimation() || super.isInvulnerableTo(source);
    }

    @Override
    public void heal(float amount) {
        if (!this.isTransitioning() && !this.isDyingAnimation()) super.heal(amount);
    }

    @Override
    public void setHealth(float health) {
        if (!this.isTransitioning() && !this.isDyingAnimation()) super.setHealth(health);
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isTransitioning() || this.isDyingAnimation()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (this.action == ACTION_QUAKE) {
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            super.travel(new Vec3(0.0D, input.y, 0.0D));
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean isPushable() {
        return !this.isTransitioning() && !this.isDyingAnimation() && super.isPushable();
    }

    @Override
    public void push(double x, double y, double z) {
        if (!this.isTransitioning() && !this.isDyingAnimation()) super.push(x, y, z);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.isPhaseTwo()
                ? EntityDimensions.scalable(HITBOX_WIDTH_P2, HITBOX_HEIGHT_P2)
                : EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(4.2D, 3.6D, 4.2D);
    }

    // ------------------------------------------------------------------ AI ----

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new TortoiseCombatGoal());
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.55D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 自己走位 + 自己决定出哪一招；伤害仍在固定 tick 结算。 */
    private final class TortoiseCombatGoal extends Goal {
        private int repath;

        TortoiseCombatGoal() {
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (VolcanicTortoiseEntity.this.isTransitioning()) return false;
            LivingEntity target = VolcanicTortoiseEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            VolcanicTortoiseEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = VolcanicTortoiseEntity.this.getTarget();
            if (target == null) {
                return;
            }
            VolcanicTortoiseEntity self = VolcanicTortoiseEntity.this;
            if (self.action == ACTION_QUAKE) {
                self.getNavigation().stop();
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = self.distanceTo(target);
            if (--this.repath <= 0) {
                this.repath = 10;
                if (distance > VolcanicTortoiseEntity.BITE_RANGE * 0.8D) {
                    self.getNavigation().moveTo(target, 1.0D);
                } else {
                    self.getNavigation().stop();
                }
            }
            if (self.isBusy() || self.attackCooldown > 0) {
                return;
            }
            int pick = self.pickSkill(target, distance);
            if (pick == ACTION_NONE) {
                return;
            }
            self.startAction(pick);
            if (pick == ACTION_BITE) {
                self.triggerAnim(ACTION_CONTROLLER, "bite");
            } else if (pick == ACTION_SLAM || pick == ACTION_QUAKE || pick == ACTION_ERUPTION) {
                self.triggerAnim(ACTION_CONTROLLER, "slam");
            }
            // 喷吐与熔岩之柱不播攻击动画：前者靠嘴前凝聚粒子，后者要求本体保持待机/行走。
        }
    }

    /**
     * 按目标距离、冷却、视线与“上一招”做加权抽签。
     * <p>二阶段把新技能（熔岩之柱、火山喷发）的权重合计压在 65%–75%，
     * 旧技能（撕咬、火环、震波、喷吐）合计 25%–35%；连续两次大范围技能会被显著降权。
     * 总出招频率仍由 {@code attackCooldown} 控制，权重只决定“出哪一招”，不改变出手间隔。</p>
     */
    private int pickSkill(LivingEntity target, double distance) {
        boolean los = this.hasLineOfSight(target);
        boolean phaseTwo = this.isPhaseTwo();

        int[] ids = new int[6];
        double[] weights = new double[6];
        int count = 0;

        if (this.eruptionCooldown <= 0 && phaseTwo && distance > 8.0D && distance < 28.0D) {
            ids[count] = ACTION_ERUPTION;
            weights[count++] = 3.4D;
        }
        if (this.pillarCooldown <= 0 && phaseTwo && distance > 6.0D && distance < 22.0D && los) {
            ids[count] = ACTION_PILLAR;
            weights[count++] = 2.6D;
        }
        if (this.spitCooldown <= 0 && distance > SPIT_MIN_RANGE && distance < SPIT_MAX_RANGE && los) {
            ids[count] = ACTION_SPIT;
            weights[count++] = phaseTwo ? 0.90D : 0.95D;
        }
        if (this.slamCooldown <= 0 && distance < SLAM_RADIUS * 0.75D) {
            ids[count] = ACTION_SLAM;
            weights[count++] = phaseTwo ? 0.95D : 1.10D;
        }
        if (this.quakeCooldown <= 0 && distance < QUAKE_RADIUS * 0.45D) {
            ids[count] = ACTION_QUAKE;
            weights[count++] = 0.50D;
        }
        if (distance < BITE_RANGE) {
            ids[count] = ACTION_BITE;
            weights[count++] = phaseTwo ? 0.50D : 1.50D;
        }
        if (count == 0) {
            return ACTION_NONE;
        }

        // 不连续放同一种大范围技能：还有别的大招可选时，把重复的那一招压到 30%。
        boolean lastWasBig = isBigSkill(this.lastSkill);
        if (lastWasBig) {
            boolean otherBig = false;
            for (int i = 0; i < count; i++) {
                if (isBigSkill(ids[i]) && ids[i] != this.lastSkill) {
                    otherBig = true;
                    break;
                }
            }
            if (otherBig) {
                for (int i = 0; i < count; i++) {
                    if (ids[i] == this.lastSkill) {
                        weights[i] *= 0.30D;
                    }
                }
            }
        }

        double total = 0.0D;
        for (int i = 0; i < count; i++) {
            total += weights[i];
        }
        double roll = this.random.nextDouble() * total;
        int pick = ids[count - 1];
        for (int i = 0; i < count; i++) {
            roll -= weights[i];
            if (roll <= 0.0D) {
                pick = ids[i];
                break;
            }
        }

        if (Boolean.getBoolean("tnc.tortoise.attack.debug")) {
            StringBuilder sb = new StringBuilder("[TN-C Skill] phase=").append(this.phase())
                    .append(" dist=").append(String.format("%.1f", distance))
                    .append(" last=").append(this.lastSkill)
                    .append(" candidates=");
            for (int i = 0; i < count; i++) {
                sb.append(ids[i]).append(':').append(String.format("%.2f", weights[i])).append(' ');
            }
            sb.append("pick=").append(pick);
            SKILL_LOG.info(sb.toString());
        }
        return pick;
    }

    private static boolean isBigSkill(int action) {
        return action == ACTION_SLAM || action == ACTION_QUAKE
                || action == ACTION_ERUPTION || action == ACTION_PILLAR;
    }

    private void startAction(int next) {
        this.action = next;
        this.actionTicks = 0;
        this.lastSkill = next;
        // 每次施法一个编号 + 锁定朝向；本次施法内同一目标最多结算一次。
        this.castId++;
        this.castYaw = this.getYRot();
        this.castHits.clear();
        this.quakeHits.clear();
        this.clearSlamRing();
        this.clearQuake();
        this.clearSpit();
        this.clearPillar();
        this.mortarLastHit.clear();
        this.mortarHits = 0;
        this.barrageSpots.clear();
        this.barrageBall = 0;
        this.getNavigation().stop();
        if (next == ACTION_QUAKE) {
            this.quakeCenter = this.position();
            this.publishQuake();
        }
    }

    // ------------------------------------------------------ 狂暴 / 虚弱 --
    /** 每 tick 维护狂暴与虚弱计时；血量达到阈值时排队，等转阶段/当前技能结束再触发一次。 */
    private void tickEnrage(ServerLevel level) {
        if (this.enrageTicks > 0) {
            this.enrageTicks--;
            if (this.enrageTicks == 0) {
                this.beginWeak(level);
            }
        } else if (this.weakTicks > 0) {
            this.weakTicks--;
            if (this.weakTicks == 0) {
                this.endWeak();
            }
        }

        if (this.enrageTriggered) {
            return;
        }
        float health = this.getHealth();
        if (health <= 0.0F || (!this.enragePending && health > this.getMaxHealth() * ENRAGE_THRESHOLD)) {
            return;
        }
        // 转阶段优先：动作没结束（含 transition）就先排队，结束后的第一个空闲 tick 再进狂暴。
        if (this.action != ACTION_NONE) {
            this.enragePending = true;
            return;
        }
        this.beginEnrage(level);
    }

    /** 进入狂暴：清负面、置位、吼叫与火星脉冲；频率/伤害倍率由读取状态的地方生效。 */
    private void beginEnrage(ServerLevel level) {
        if (this.enrageTriggered) {
            return;
        }
        this.enrageTriggered = true;
        this.enragePending = false;
        this.enrageTicks = ENRAGE_TICKS;
        this.weakTicks = 0;
        this.entityData.set(DATA_ENRAGE_STATE, STATE_ENRAGED);
        for (MobEffectInstance instance : java.util.List.copyOf(this.getActiveEffects())) {
            if (!instance.getEffect().isBeneficial()) {
                this.removeEffect(instance.getEffect());
            }
        }
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 3.4F, 0.62F);
        level.sendParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + 2.0D, this.getZ(), 60, 2.4D, 1.2D, 2.4D, 0.05D);
        level.sendParticles(ParticleTypes.LAVA,
                this.getX(), this.getY() + 2.2D, this.getZ(), 24, 1.8D, 0.9D, 1.8D, 0.0D);
        // 狂暴登场：背部火山口直接来一轮超大喷发（5 批 × 12 颗、外缘 34 格）。
        this.startMegaEruption(level);
    }

    /** 狂暴登场的大喷发：放大喷发形状参数后，复用成熟的火山喷发流程。 */
    private void startMegaEruption(ServerLevel level) {
        this.barrageWaves = MEGA_WAVES;
        this.barragePerWave = MEGA_PER_WAVE;
        this.barrageWaveGap = MEGA_WAVE_GAP;
        this.barrageBallStagger = MEGA_BALL_STAGGER;
        this.barrageInner = MEGA_INNER_RADIUS;
        this.barrageOuter = MEGA_OUTER_RADIUS;
        this.barrageSpacing = MEGA_MIN_SPACING;
        this.barrageDamage = MEGA_DAMAGE_SCALE;
        this.megaEruption = true;
        this.startAction(ACTION_ERUPTION);
        this.triggerAnim(ACTION_CONTROLLER, "slam");
        level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 3.4F, 0.45F);
    }

    /** 狂暴结束：进入虚弱易伤窗口（Weakness + 受到伤害倍增）。 */
    private void beginWeak(ServerLevel level) {
        this.enrageTicks = 0;
        this.weakTicks = WEAK_TICKS;
        this.entityData.set(DATA_ENRAGE_STATE, STATE_WEAK);
        this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0, false, true, true));
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_HURT,
                SoundSource.HOSTILE, 2.6F, 0.55F);
        level.sendParticles(ParticleTypes.SMOKE,
                this.getX(), this.getY() + 1.6D, this.getZ(), 28, 2.0D, 0.8D, 2.0D, 0.01D);
    }

    private void endWeak() {
        this.weakTicks = 0;
        this.entityData.set(DATA_ENRAGE_STATE, STATE_NORMAL);
    }

    /** 调试：直接进入狂暴（不受血量限制），仍遵守每场一次。 */
    public void forceEnrage() {
        if (this.isTransitioning()) {
            this.enragePending = !this.enrageTriggered;
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            this.beginEnrage(level);
        }
    }

    /** 调试：直接进入虚弱易伤窗口。 */
    public void forceWeak() {
        if (this.isTransitioning()) return;
        if (this.level() instanceof ServerLevel level) {
            this.beginWeak(level);
        }
    }

    /** 调试：清掉狂暴/虚弱状态（不还原已触发的标记，避免重复触发）。 */
    public void clearEnrageState() {
        this.enrageTicks = 0;
        this.weakTicks = 0;
        this.enragePending = false;
        this.entityData.set(DATA_ENRAGE_STATE, STATE_NORMAL);
        this.removeEffect(MobEffects.WEAKNESS);
    }

    private void finishAction(int cooldown) {
        this.action = ACTION_NONE;
        this.actionTicks = 0;
        this.attackCooldown = cooldown;
        this.clearSlamRing();
        this.clearQuake();
        this.clearSpit();
        this.clearPillar();
    }

    // ------------------------------------------------------------------ tick --

    @Override
    public void aiStep() {
        if (this.level() instanceof ServerLevel server && this.isAlive() && !this.isDeadOrDying()
                && !this.isNoAi() && !this.isTransitioning() && !this.isPhaseTwo()
                && this.getHealth() <= this.getMaxHealth() * PHASE_TWO_HEALTH_FRACTION) {
            this.beginPhaseTwo(server);
        }
        super.aiStep();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(this.isAlive() && !this.isDeadOrDying());
        if (this.tickCount == 1) {
            // 血条 UUID 只在第一次服务端 tick 写一次，随后靠实体同步数据送到客户端。
            CompoundTag barTag = new CompoundTag();
            UUID barId = this.bossEvent.getId();
            barTag.putLong("Most", barId.getMostSignificantBits());
            barTag.putLong("Least", barId.getLeastSignificantBits());
            this.entityData.set(DATA_BAR_ID, barTag);
        }
        if (!this.isAlive() || this.isDeadOrDying()) {
            return;
        }
        if (this.isTransitioning()) {
            this.holdTransitionPosition();
            this.tickTransition(level);
            return;
        }
        if (this.isNoAi()) return;

        // 狂暴只加快“冷却倒计时”，不缩短动作时长与预警，保证每次技能仍有可躲避时间。
        int cooldownStep = this.isEnraged()
                ? Math.max(1, Math.round(1.0F / ENRAGE_COOLDOWN_SCALE))
                : 1;
        this.attackCooldown = Math.max(0, this.attackCooldown - cooldownStep);
        this.slamCooldown = Math.max(0, this.slamCooldown - cooldownStep);
        this.quakeCooldown = Math.max(0, this.quakeCooldown - cooldownStep);
        this.eruptionCooldown = Math.max(0, this.eruptionCooldown - cooldownStep);
        this.spitCooldown = Math.max(0, this.spitCooldown - cooldownStep);
        this.pillarCooldown = Math.max(0, this.pillarCooldown - cooldownStep);

        this.tickBlasts(level);
        this.tickEnrage(level);
        if (this.action == ACTION_NONE) {
            return;
        }
        this.actionTicks++;
        switch (this.action) {
            case ACTION_BITE -> this.tickBite(level);
            case ACTION_SLAM -> this.tickSlam(level);
            case ACTION_QUAKE -> this.tickQuake(level);
            case ACTION_ERUPTION -> this.tickEruption(level);
            case ACTION_SPIT -> this.tickSpit(level);
            case ACTION_PILLAR -> this.tickPillar(level);
            case ACTION_TRANSITION -> this.tickTransition(level);
            default -> {
            }
        }
    }

    /** 半血转二阶段：只触发一次，转完清无敌与冷却。 */
    private void beginPhaseTwo(ServerLevel level) {
        if (this.isPhaseTwo() || this.isTransitioning()) {
            return;
        }
        this.transitionAnchor = this.position();
        this.transitionYaw = this.getYRot();
        this.transitionPreviousNoAi = this.isNoAi();
        this.transitionPreviousNoGravity = this.isNoGravity();
        this.transitionPreviousInvulnerable = this.isInvulnerable();
        this.entityData.set(DATA_TRANSITION_START, level.getGameTime());
        this.startAction(ACTION_TRANSITION);
        this.hazardEpoch++;
        this.clearTransitionHazards(level);
        this.clearTransitionEffects();
        this.setInvulnerable(true);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.getNavigation().stop();
        this.holdTransitionPosition();
        if (!this.enrageTriggered && this.getHealth() <= this.getMaxHealth() * ENRAGE_THRESHOLD) {
            this.enragePending = true;
        }
        level.playSound(null, this.blockPosition(), SoundEvents.STONE_HIT, SoundSource.HOSTILE, 0.6F, 0.45F);
    }

    private void tickTransition(ServerLevel level) {
        this.actionTicks = (int) this.transitionAge(0);
        int age = this.actionTicks;
        this.clearTransitionEffects();
        if (age > 0 && age < TortoiseTransition.SILENCE_START && age % 12 == 0) {
            float volume = 0.3F + age / (float) TortoiseTransition.SILENCE_START;
            level.playSound(null, this.blockPosition(), SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, volume, 0.5F);
            level.playSound(null, this.blockPosition(), SoundEvents.STONE_HIT, SoundSource.HOSTILE, volume * 0.6F, 0.4F);
        }
        if (age >= TortoiseTransition.MODEL_SWAP && !this.isPhaseTwo()) {
            this.entityData.set(DATA_PHASE, PHASE_TWO);
            this.refreshDimensions();
        }
        if (age == TortoiseTransition.MODEL_SWAP) {
            level.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.4F, 0.7F);
            level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.55F);
            level.playSound(null, this.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 2.2F, 0.5F);
        }
        if (age == 90 || age == 112) {
            level.playSound(null, this.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        if (age == 118) {
            level.playSound(null, this.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 1.8F, 0.65F);
        }
        if (age == TortoiseTransition.REBUILD_END) {
            level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (age >= TRANSITION_TICKS) {
            this.entityData.set(DATA_PHASE, PHASE_TWO);
            this.endTransitionProtection();
            super.setHealth(Math.min(this.getHealth(), this.getMaxHealth()));
            this.finishAction(30);
            this.slamCooldown = 20;
            this.eruptionCooldown = 60;
        }
    }

    private void holdTransitionPosition() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.transitionAnchor.x, this.transitionAnchor.y, this.transitionAnchor.z);
        this.setYRot(this.transitionYaw);
        this.setYHeadRot(this.transitionYaw);
        this.yBodyRot = this.transitionYaw;
        this.setXRot(0.0F);
        this.setSpeed(0.0F);
        this.xxa = this.yya = this.zza = 0.0F;
    }

    private void clearTransitionEffects() {
        // 不允许可取消的 Remove 事件留下有益效果，也同步移除它们的属性修饰。
        for (MobEffectInstance effect : List.copyOf(this.getActiveEffects())) {
            MobEffectInstance removed = this.removeEffectNoUpdate(effect.getEffect());
            if (removed != null) this.onEffectRemoved(removed);
        }
    }

    private void endTransitionProtection() {
        this.entityData.set(DATA_TRANSITION_START, -1L);
        this.setInvulnerable(this.transitionPreviousInvulnerable);
        this.setNoAi(this.transitionPreviousNoAi);
        this.setNoGravity(this.transitionPreviousNoGravity);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void clearTransitionHazards(ServerLevel level) {
        this.pendingBlasts.clear();
        this.blastCountdown = 0;
        this.barrageSpots.clear();
        this.megaEruption = false;
        this.debugRingTicks = this.debugOutlineTicks = 0;
        this.fieldHits.clear();
        java.util.List<Entity> remove = new java.util.ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if ((entity instanceof VolcanicMortarEntity mortar && mortar.getOwner() == this)
                    || (entity instanceof VolcanicFireballEntity fireball && fireball.getOwner() == this)
                    || (entity instanceof VolcanicFireFieldEntity field && field.isOwnedBy(this.getUUID()))) {
                remove.add(entity);
            }
        }
        remove.forEach(Entity::discard);
    }

    public boolean forceTransition() {
        if (this.level() instanceof ServerLevel level && this.isAlive() && !this.isPhaseTwo() && !this.isTransitioning()) {
            this.beginPhaseTwo(level);
            return true;
        }
        return false;
    }

    private void tickBite(ServerLevel level) {
        if (ATTACK_DEBUG && this.debugOutlineTicks > 0) {
            this.debugOutlineTicks--;
            if (this.actionTicks % 4 == 1) {
                this.drawDangerOutline(level);
            }
        }
        // 命中时刻 = 动画 0.50s 的闭嘴帧（tick 10），不靠动画回调。
        if (this.actionTicks == BITE_HIT_TICK) {
            this.biteSweep(level);
            this.biteFx(level);
        }
        if (this.actionTicks >= BITE_TICKS) {
            this.finishAction(this.isPhaseTwo() ? 14 : 22);
        }
    }

    /** 咬合结算：几何以施法时锁定的 castYaw 为准，扇区内每个目标本次施法只吃一次。 */
    private void biteSweep(ServerLevel level) {
        Vec3 forward = horizontalForward(this.castYaw);
        double tanHalf = Math.tan(Math.toRadians(BITE_HALF_ANGLE_DEG));
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * this.damageScale());
        AABB box = new AABB(this.getX() - BITE_RANGE, this.getY() - 1.5D, this.getZ() - BITE_RANGE,
                this.getX() + BITE_RANGE, this.getY() + 5.0D, this.getZ() + BITE_RANGE);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, this::canHarm)) {
            Vec3 delta = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D)
                    .subtract(this.position().add(0.0D, 1.5D, 0.0D));
            double along = delta.x * forward.x + delta.z * forward.z;
            double lateral = Math.abs(delta.x * forward.z - delta.z * forward.x);
            if (along < 0.5D || along > BITE_RANGE + victim.getBbWidth() * 0.5D
                    || lateral > tanHalf * Math.max(along, 1.0D)) {
                continue;
            }
            if (!this.castHits.add(victim.getUUID())) {
                continue;
            }
            victim.hurt(this.damageSources().mobAttack(this), damage);
            victim.push(forward.x * 0.30D, 0.10D, forward.z * 0.30D);
        }
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.6F, 0.8F);
    }

    /** 咬合瞬间的橙色闪光 + 少量碎火星，位置跟随嘴部。 */
    private void biteFx(ServerLevel level) {
        Vec3 mouth = this.mouthPoint();
        level.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 8, 0.16D, 0.10D, 0.16D, 0.01D);
        level.sendParticles(ParticleTypes.LAVA, mouth.x, mouth.y, mouth.z, 3, 0.08D, 0.06D, 0.08D, 0.0D);
        level.playSound(null, net.minecraft.core.BlockPos.containing(mouth), SoundEvents.RAVAGER_ATTACK,
                SoundSource.HOSTILE, 2.0F, 1.30F);
    }

    // ------------------------------------------------------ 技能 D：熔岩之柱 --

    /** 任务 7：前方走廊预警 + 分批冲起的火柱；每根柱子冲起瞬间结算一次伤害。 */
    private void tickPillar(ServerLevel level) {
        if (this.actionTicks == 1) {
            this.pillarStartTick = (int) level.getGameTime();
            Vec3 forward = Vec3.directionFromRotation(0.0F, this.castYaw).scale(PILLAR_FRONT_OFFSET);
            this.pillarOrigin = new Vec3(this.getX() + forward.x, this.getY(), this.getZ() + forward.z);
            this.pillarYaw = this.castYaw;
            this.publishPillar(level);
            level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.HOSTILE, 2.2F, 0.55F);
        }

        List<PillarLayout.Spot> spots = PillarLayout.spots(this.pillarStartTick);
        int since = this.actionTicks - PILLAR_WARN_TICKS;
        if (since >= 0 && since % PILLAR_STAGGER == 0) {
            int index = since / PILLAR_STAGGER;
            if (index < spots.size()) {
                this.pillarImpact(level, this.pillarSpotCenter(spots.get(index)));
            }
        }

        int last = PILLAR_WARN_TICKS + (spots.size() - 1) * PILLAR_STAGGER;
        if (this.actionTicks >= last + 80) {
            int cooldown = this.isPhaseTwo() ? PILLAR_COOLDOWN_TWO : PILLAR_COOLDOWN_ONE;
            this.pillarCooldown = cooldown;
            this.finishAction(cooldown);
        }
    }

    /** 单根柱子的伤害：水平 1.6 格、垂直 8 格；同一次施法同一目标只吃一次主伤害。 */
    private void pillarImpact(ServerLevel level, Vec3 base) {
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * PILLAR_DAMAGE_SCALE * this.damageScale());
        AABB box = new AABB(base.x - PILLAR_DANGER_RADIUS, base.y - 0.8D, base.z - PILLAR_DANGER_RADIUS,
                base.x + PILLAR_DANGER_RADIUS, base.y + PILLAR_DANGER_HEIGHT, base.z + PILLAR_DANGER_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, this::canHarm)) {
            if (!this.castHits.add(victim.getUUID())) {
                continue;
            }
            double dx = victim.getX() - base.x;
            double dz = victim.getZ() - base.z;
            if (dx * dx + dz * dz > PILLAR_DANGER_RADIUS * PILLAR_DANGER_RADIUS) {
                continue;
            }
            if (victim.getY() > base.y + PILLAR_DANGER_HEIGHT
                    || victim.getY() + victim.getBbHeight() < base.y - 0.8D) {
                continue;
            }
            victim.hurt(this.damageSources().mobAttack(this), damage);
        }
        level.playSound(null, BlockPos.containing(base), SoundEvents.FIRECHARGE_USE,
                SoundSource.HOSTILE, 2.0F, 0.70F);
    }

    /** 调试/预览用：立即放一次熔岩之柱（不做阶段限制，方便验收）。 */
    public boolean forcePillar() {
        if (!this.isAlive() || this.isBusy()) {
            return false;
        }
        this.startAction(ACTION_PILLAR);
        return true;
    }

    private Vec3 mouthPoint() {
        boolean two = this.isPhaseTwo();
        Vec3 forward = horizontalForward(this.castYaw);
        double fwd = two ? MOUTH_FORWARD_P2 : MOUTH_FORWARD_P1;
        double up = two ? MOUTH_UP_P2 : MOUTH_UP_P1;
        return this.position().add(forward.scale(fwd)).add(0.0D, up, 0.0D);
    }

    private static Vec3 horizontalForward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    /** 调试：把本次咬合的扇区画出来（仅 ATTACK_DEBUG 下，由 /tnc_bite true 触发）。 */
    private void drawDangerOutline(ServerLevel level) {
        Vec3 center = this.position().add(0.0D, 0.25D, 0.0D);
        for (int i = 0; i <= 24; i++) {
            double angle = this.castYaw - BITE_HALF_ANGLE_DEG + (2.0D * BITE_HALF_ANGLE_DEG) * i / 24.0D;
            Vec3 p = center.add(horizontalForward((float) angle).scale(BITE_RANGE));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (int side = -1; side <= 1; side += 2) {
            double angle = this.castYaw + side * BITE_HALF_ANGLE_DEG;
            for (double r = 1.5D; r < BITE_RANGE; r += 1.5D) {
                Vec3 p = center.add(horizontalForward((float) angle).scale(r));
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 单技能触发入口（调试用）：立即开始一次撕咬，仍遵守死亡/忙碌限制。 */
    public boolean forceBite(LivingEntity target, boolean showRange) {
        if (!this.isAlive() || this.isDeadOrDying() || this.isBusy()) {
            return false;
        }
        if (target != null) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
            this.setTarget(target);
        }
        this.startAction(ACTION_BITE);
        this.triggerAnim(ACTION_CONTROLLER, "bite");
        this.debugOutlineTicks = showRange ? BITE_TICKS : 0;
        return true;
    }

    private void tickSlam(ServerLevel level) {
        if (this.actionTicks == SLAM_HIT_TICK) {
            this.slamCenter = this.position();
            this.publishSlamRing(level);
            this.groundSlamImpact(level);
        }
        int ringAge = this.actionTicks - SLAM_HIT_TICK;
        if (ringAge > 0 && ringAge <= SLAM_RING_TICKS) {
            this.tickSlamRing(level, ringAge);
        }
        if (this.debugRingTicks > 0) {
            if (this.actionTicks % 4 == 0) {
                this.debugRingOutline(level);
            }
            this.debugRingTicks--;
        }
        if (this.actionTicks >= SLAM_TICKS) {
            this.finishAction(this.isPhaseTwo() ? 40 : 60);
            this.slamCooldown = this.isPhaseTwo() ? 90 : 130;
        }
    }

    /**
     * 落地瞬间：短促闪光 + 少量尘土/火星 + 脚下直接冲击。
     *
     * <p>不生成常驻烟柱；脚下冲击与随后外扩的火环共用 {@link #castHits}，
     * 所以同一次重踏对同一目标只结算一次主伤害。
     */
    private void groundSlamImpact(ServerLevel level) {
        Vec3 center = this.slamCenter;
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.4F, 0.55F);
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.45D, center.z,
                2, 0.30D, 0.05D, 0.30D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.20D, center.z,
                24, 1.60D, 0.15D, 1.60D, 0.02D);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.25D, center.z,
                16, 1.10D, 0.15D, 1.10D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.30D, center.z,
                20, 1.20D, 0.10D, 1.20D, 0.03D);
        this.slamDamage(level, center, 0.0D, SLAM_IMPACT_RADIUS);
        if (SLAM_SHAKE > 0.0F) {
            TNShockwaveEntity wave = TNShockwaveEntity.blast(
                    level, center.add(0.0D, 0.2D, 0.0D), 0.8D, SLAM_SHAKE, this);
            if (wave != null) {
                wave.configurePush(false);
                wave.configureFlash(0.0F);
            }
        }
    }

    /**
     * 火环：伤害与视觉共用同一个半径/速度；贴地采样失败的点跳过，不加载区块。
     *
     * <p>客户端表现完全由专用粒子环负责（{@code SlamRingFx} / {@code SlamFlameParticle}），
     * 服务端这里不再额外撒 FLAME/LAVA——否则会在环带旁边留下与圆环无关的散火。
     */
    private void tickSlamRing(ServerLevel level, int age) {
        double radius = SLAM_RING_RADIUS * age / (double) SLAM_RING_TICKS;
        if (radius < 0.6D) {
            return;
        }
        this.slamDamage(level, this.slamCenter, radius - SLAM_RING_BAND * 0.5D, radius + SLAM_RING_BAND * 0.5D);
    }

    /** 伤害结算：环带内每人只吃一次；高差/遮挡不过的跳过，避免穿高墙打人。 */
    private void slamDamage(ServerLevel level, Vec3 center, double inner, double outer) {
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75D * this.damageScale());
        AABB box = new AABB(center.x - outer - 2.0D, center.y - 3.0D, center.z - outer - 2.0D,
                center.x + outer + 2.0D, center.y + 6.0D, center.z + outer + 2.0D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> this.canHarm(e))) {
            if (victim == this || this.castHits.contains(victim.getUUID())) {
                continue;
            }
            double dist = Math.hypot(victim.getX() - center.x, victim.getZ() - center.z);
            double half = victim.getBbWidth() * 0.5D;
            if (dist + half < inner || dist - half > outer) {
                continue;
            }
            if (!this.slamReaches(level, victim, center)) {
                continue;
            }
            if (!this.castHits.add(victim.getUUID())) {
                continue;
            }
            if (!victim.hurt(this.damageSources().mobAttack(this), damage)) {
                // 伤害被完全拒绝（同队/无敌/已死等）时不额外施加击退。
                continue;
            }
            // 击退统一走原版 knockback：方向 = 圆心 → 目标 的水平外推，强度会按击退抗性衰减。
            // 旧实现在这里额外 push 了一份水平+竖直速度，属于重复击退，已删除。
            Vec3 push = victim.position().subtract(center);
            double dx;
            double dz;
            if (push.horizontalDistanceSqr() > 1.0E-4D) {
                dx = push.x;
                dz = push.z;
            } else {
                // 目标正好站在圆心上：用固定方向兜底，避免零向量。
                dx = 1.0D;
                dz = 0.0D;
            }
            victim.knockback(SLAM_KNOCKBACK, dx, dz);
        }
    }

    /** 贴地判定：与地表高差不超过容差，且首领到目标有视线（高墙挡住就不结算）。 */
    private boolean slamReaches(ServerLevel level, LivingEntity victim, Vec3 center) {
        if (!level.hasChunkAt(victim.blockPosition())) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                victim.blockPosition().getX(), victim.blockPosition().getZ());
        if (Math.abs(victim.getY() - top) > SLAM_HEIGHT_TOLERANCE) {
            return false;
        }
        return this.hasLineOfSight(victim);
    }

    /** 调试：把火环最终半径画一圈（贴地采样，不加载区块）。 */
    private void debugRingOutline(ServerLevel level) {
        Vec3 center = this.slamCenter.equals(Vec3.ZERO) ? this.position() : this.slamCenter;
        int points = 96;
        for (int i = 0; i < points; i++) {
            double angle = 2.0D * Math.PI * i / points;
            double x = center.x + Math.cos(angle) * SLAM_RING_RADIUS;
            double z = center.z + Math.sin(angle) * SLAM_RING_RADIUS;
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(x, center.y, z))) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.core.BlockPos.containing(x, center.y, z).getX(),
                    net.minecraft.core.BlockPos.containing(x, center.y, z).getZ());
            level.sendParticles(ParticleTypes.END_ROD, x, top + 0.25D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 调试：立刻踏一次；仍遵守死亡/正在攻击/转阶段限制。 */
    public boolean forceSlam(boolean showRing) {
        if (this.isDeadOrDying() || this.action != ACTION_NONE) {
            return false;
        }
        this.startAction(ACTION_SLAM);
        this.triggerAnim(ACTION_CONTROLLER, "slam");
        this.debugRingTicks = showRing ? SLAM_TICKS : 0;
        return true;
    }

    // -------------------------------------------------- 技能 B：大地震波 --

    /**
     * 技能 B：双前脚砸地后，地面波向外传播。
     *
     * <p>与技能 A 完全分开：自己的施法编号（{@code DATA_QUAKE_START}）、自己的命中集合
     * {@link #quakeHits}、自己的冷却 {@link #quakeCooldown}；不产生火环粒子。
     */
    private void tickQuake(ServerLevel level) {
        this.getNavigation().stop();
        this.setYRot(this.castYaw);
        this.setYHeadRot(this.castYaw);
        this.yBodyRot = this.castYaw;
        if (this.actionTicks == QUAKE_HIT_TICK) this.groundQuakeImpact(level);
        int radiusAge = this.actionTicks - QUAKE_HIT_TICK;
        if (radiusAge % 2 == 0) {
            this.entityData.set(DATA_QUAKE_AGE, radiusAge);
        }
        if (radiusAge > 0 && radiusAge <= QUAKE_RING_TICKS) {
            double radius = TortoiseQuake.radius(radiusAge);
            double half = QUAKE_BAND * 0.5D;
            this.quakeHit(level, this.quakeCenter, radius - half, radius + half);
        }
        if (this.actionTicks >= QUAKE_TICKS) {
            this.finishAction(this.isPhaseTwo() ? 40 : 60);
            this.quakeCooldown = this.isPhaseTwo() ? 200 : 280;
            // 与火环错开：震波结束后至少再等一段时间才允许火环。
            this.slamCooldown = Math.max(this.slamCooldown, 60);
        }
    }

    /** 落地瞬间：只有尘土与低闷冲击，不生成火环、不撒火焰粒子。 */
    private void groundQuakeImpact(ServerLevel level) {
        Vec3 center = this.quakeCenter;
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.2F, 0.45F);
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3.0F, 0.45F);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.35D, center.z,
                1, 0.25D, 0.05D, 0.25D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.15D, center.z,
                18, 1.40D, 0.10D, 1.40D, 0.01D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.20D, center.z,
                30, 1.80D, 0.10D, 1.80D, 0.15D);
        if (SLAM_SHAKE > 0.0F) {
            TNShockwaveEntity wave = TNShockwaveEntity.blast(
                    level, center.add(0.0D, 0.2D, 0.0D), 0.9D, SLAM_SHAKE, this);
            if (wave != null) {
                wave.configurePush(false);
                wave.configureFlash(0.0F);
            }
        }
    }

    /**
     * 震波命中：基础伤害默认 0，每次施法命中一次，向外击飞并眩晕5秒。
     *
     * <p>不依赖 {@code hurt(0)} 的返回值（0 伤害会被原版判为无效），
     * 而是单独判断可攻击性、无敌与命中记录。
     */
    private void quakeHit(ServerLevel level, Vec3 center, double inner, double outer) {
        AABB box = new AABB(center.x - outer - 2.0D, center.y - 3.0D, center.z - outer - 2.0D,
                center.x + outer + 2.0D, center.y + 6.0D, center.z + outer + 2.0D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> this.canHarm(e))) {
            if (victim == this || this.quakeHits.contains(victim.getUUID())) {
                continue;
            }
            if (victim.isInvulnerableTo(this.damageSources().mobAttack(this))) {
                continue;
            }
            double dist = Math.hypot(victim.getX() - center.x, victim.getZ() - center.z);
            double half = victim.getBbWidth() * 0.5D;
            if (dist + half < inner || dist - half > outer) {
                continue;
            }
            if (!TortoiseQuake.contains(victim.getX()-center.x, victim.getZ()-center.z, this.castYaw, half)) {
                continue;
            }
            if (!this.quakeReaches(level, victim, center)) {
                continue;
            }
            if (!this.quakeHits.add(victim.getUUID())) {
                continue;
            }
            if (QUAKE_DAMAGE > 0.0F) {
                victim.hurt(this.damageSources().mobAttack(this), QUAKE_DAMAGE);
            }
            Vec3 push = victim.position().subtract(center);
            double dx;
            double dz;
            if (push.horizontalDistanceSqr() > 1.0E-4D) {
                dx = push.x;
                dz = push.z;
            } else {
                dx = 1.0D;
                dz = 0.0D;
            }
            victim.knockback(QUAKE_KNOCKBACK, -dx, -dz);
            TortoiseStun.apply(victim, QUAKE_LIFT);
        }
    }

    /** 震波贴地判定：与火环一致的高差容差 + 视线，避免隔墙推人。 */
    private boolean quakeReaches(ServerLevel level, LivingEntity victim, Vec3 center) {
        if (!level.hasChunkAt(victim.blockPosition())) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                victim.blockPosition().getX(), victim.blockPosition().getZ());
        if (Math.abs(victim.getY() - top) > QUAKE_HEIGHT_TOLERANCE) {
            return false;
        }
        return this.hasLineOfSight(victim);
    }

    /** 调试：立刻放一次大地震波（只震地面，不产生火环）。 */
    public boolean forceQuake() {
        if (this.isDeadOrDying() || this.action != ACTION_NONE) {
            return false;
        }
        this.startAction(ACTION_QUAKE);
        this.triggerAnim(ACTION_CONTROLLER, "slam");
        return true;
    }

    /** 火山喷发：火山口蓄势，随后分批抛射火球落向 Boss 周围；mega 版本用于狂暴登场。 */
    private void tickEruption(ServerLevel level) {
        if (this.actionTicks == 1) {
            if (!this.megaEruption) {
                this.barrageWaves = BARRAGE_WAVES;
                this.barragePerWave = BARRAGE_PER_WAVE;
                this.barrageWaveGap = BARRAGE_WAVE_GAP;
                this.barrageBallStagger = BARRAGE_BALL_STAGGER;
                this.barrageInner = BARRAGE_INNER_RADIUS;
                this.barrageOuter = BARRAGE_OUTER_RADIUS;
                this.barrageSpacing = BARRAGE_MIN_SPACING;
                this.barrageDamage = BARRAGE_DAMAGE_SCALE;
            }
            this.barrageCharge = this.megaEruption
                    ? MEGA_CHARGE_TICKS
                    : BARRAGE_CHARGE_MIN
                            + level.random.nextInt(BARRAGE_CHARGE_MAX - BARRAGE_CHARGE_MIN + 1);
            this.barrageBall = 0;
            this.barrageSpots.clear();
            this.mortarLastHit.clear();
            this.mortarHits = 0;
            this.pendingBlasts.clear();
            this.blastCountdown = 0;
            if (this.megaEruption) {
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR,
                        SoundSource.HOSTILE, 3.6F, 0.50F);
                level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                        SoundSource.HOSTILE, 3.0F, 0.45F);
            } else {
                level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                        SoundSource.HOSTILE, 2.2F, 0.6F);
            }
        }

        Vec3 crater = this.craterPoint();
        if (this.actionTicks <= this.barrageCharge) {
            float p = this.actionTicks / (float) this.barrageCharge;
            if (this.megaEruption) {
                // 大喷发：火山口整根火柱 + 浓烟，强度明显高于常规蓄势。
                level.sendParticles(ParticleTypes.FLAME, crater.x, crater.y + 0.4D, crater.z,
                        6 + (int) (p * 10.0F), 0.60D, 0.80D, 0.60D, 0.06D);
                level.sendParticles(ParticleTypes.LAVA, crater.x, crater.y, crater.z,
                        3 + (int) (p * 6.0F), 0.50D, 0.40D, 0.50D, 0.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, crater.x, crater.y + 1.5D, crater.z,
                        2 + (int) (p * 5.0F), 0.70D, 1.20D, 0.70D, 0.02D);
                level.sendParticles(TortoiseParticles.FIRE_MID.get(), crater.x, crater.y + 0.8D, crater.z,
                        6 + (int) (p * 8.0F), 0.55D, 0.90D, 0.55D, 0.03D);
            } else if (this.actionTicks % 2 == 0) {
                level.sendParticles(ParticleTypes.LAVA, crater.x, crater.y, crater.z,
                        1 + (int) (p * 3.0F), 0.35D, 0.10D, 0.35D, 0.0D);
                level.sendParticles(TortoiseParticles.FIRE_EDGE.get(), crater.x, crater.y + 0.15D, crater.z,
                        2 + (int) (p * 4.0F), 0.30D, 0.20D, 0.30D, 0.01D);
            }
        }

        int since = this.actionTicks - this.barrageCharge;
        int total = this.barrageWaves * this.barragePerWave;
        while (this.barrageBall < total) {
            int wave = this.barrageBall / this.barragePerWave;
            int index = this.barrageBall % this.barragePerWave;
            int at = wave * this.barrageWaveGap + index * this.barrageBallStagger;
            if (since < at) {
                break;
            }
            this.fireBarrageBall(level);
            this.barrageBall++;
        }
        if (this.barrageBall >= total && since > this.barrageWaves * this.barrageWaveGap + 40) {
            boolean mega = this.megaEruption;
            this.finishAction(mega ? 20 : 40);
            this.eruptionCooldown = mega ? 200 : (this.isPhaseTwo() ? 260 : 340);
            this.megaEruption = false;
        }
    }

    /** 发射一颗：一颗颗出膛，落点分散、彼此留出可走的空隙。 */
    private void fireBarrageBall(ServerLevel level) {
        Vec3 from = this.craterPoint();
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * this.barrageDamage * this.damageScale());
        Vec3 landing = this.pickLanding(level);
        if (landing != null) {
            int flight = 42 + level.random.nextInt(14);
            level.addFreshEntity(VolcanicMortarEntity.launch(level, this, from, landing, flight, damage, this.castId));
            this.barrageSpots.add(landing);
        }
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.1F, 1.5F);
    }

    /** 环形落区里挑一个点：内圈安全、外缘按本次喷发配置、与已有点至少隔开配置间距。 */
    private Vec3 pickLanding(ServerLevel level) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(level.random.nextDouble()
                    * (this.barrageOuter * this.barrageOuter
                    - this.barrageInner * this.barrageInner)
                    + this.barrageInner * this.barrageInner);
            double x = this.getX() + Math.cos(angle) * r;
            double z = this.getZ() + Math.sin(angle) * r;
            if (!level.hasChunkAt(BlockPos.containing(x, this.getY(), z))) {
                continue;
            }
            boolean crowded = false;
            for (Vec3 other : this.barrageSpots) {
                if (other.distanceToSqr(x, other.y, z) < this.barrageSpacing * this.barrageSpacing) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, 0, z).getX(), BlockPos.containing(x, 0, z).getZ()) - 1;
            return new Vec3(x, top + 1.0D, z);
        }
        return null;
    }

    /** 调试入口：立刻开始一次火山喷发（不要求阶段，仍遵守存活/忙碌限制）。 */
    public boolean forceEruption() {
        if (!this.isAlive() || this.isDeadOrDying() || this.isBusy()) {
            return false;
        }
        this.startAction(ACTION_ERUPTION);
        return true;
    }

    /** 火山口世界坐标：与客户端粒子定位使用同一组高度常数。 */
    public Vec3 craterPoint() {
        double height = this.usesPhaseTwoModel() ? 7.22D : 4.50D;
        return new Vec3(this.getX(), this.getY() + height, this.getZ());
    }

    /** 喷发伤害去重：同一次施法、同一目标 10 tick 内只吃一次，整场最多 8 次。 */
    public boolean tryMortarHit(UUID target, long cast) {
        if (cast != this.castId) {
            return false;
        }
        if (this.mortarHits >= BARRAGE_MAX_HITS) {
            return false;
        }
        Integer last = this.mortarLastHit.get(target);
        if (last != null && this.tickCount - last < BARRAGE_HIT_INTERVAL) {
            return false;
        }
        this.mortarLastHit.put(target, this.tickCount);
        this.mortarHits++;
        return true;
    }

    // ------------------------------------------------------------ 喷吐（任务 5）--

    private void tickSpit(ServerLevel level) {
        if (this.isDeadOrDying()) {
            this.clearSpit();
            this.finishAction(10);
            return;
        }
        if (this.actionTicks == 1) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                this.finishAction(20);
                return;
            }
            this.spitCharge = SPIT_CHARGE_MIN + level.random.nextInt(SPIT_CHARGE_MAX - SPIT_CHARGE_MIN + 1);
            this.spitLockTick = Math.max(1, Math.round(this.spitCharge * SPIT_LOCK_FRACTION));
            this.spitTargetId = target.getId();
            this.spitAim = Vec3.ZERO;
            this.publishSpit(level);
            level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.HOSTILE, 1.6F, 0.5F);
        }

        LivingEntity locked = this.spitTargetId >= 0 && level.getEntity(this.spitTargetId) instanceof LivingEntity l
                ? l : null;
        // 锁定前允许有限追踪；锁定后不再改朝向，留出可辨识的锁定窗口。
        if (this.actionTicks <= this.spitLockTick && locked != null && locked.isAlive()) {
            this.getLookControl().setLookAt(locked, 30.0F, 30.0F);
            if (this.actionTicks == this.spitLockTick) {
                this.spitAim = new Vec3(locked.getX(), locked.getY() + locked.getBbHeight() * 0.5D, locked.getZ());
                this.castYaw = yawTo(this.spitAim, this.position());
                this.setYRot(this.castYaw);
                this.setYHeadRot(this.castYaw);
                level.playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE,
                        SoundSource.HOSTILE, 1.1F, 1.5F);
            }
        }

        if (this.actionTicks >= this.spitCharge) {
            Vec3 from = this.mouthPoint();
            Vec3 aim = this.spitAim.equals(Vec3.ZERO) ? from.add(this.getLookAngle().scale(16.0D)) : this.spitAim;
            Vec3 dir = aim.subtract(from);
            if (dir.lengthSqr() < 1.0E-6D) {
                dir = this.getLookAngle();
            }
            Vec3 unit = dir.normalize();
            float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * SPIT_DAMAGE_SCALE * this.damageScale());
            // 生成点只沿水平朝向前移 1.55 格，与客户端蓄力中心用同一套公式（不再把俯仰角叠进去，否则会与蓄力位置错开）。
            Vec3 spawn = from.add(horizontalForward(this.castYaw).scale(1.55D));
            level.addFreshEntity(VolcanicFireballEntity.launch(level, this, spawn, unit, damage, this.castId));
            level.sendParticles(ParticleTypes.EXPLOSION, spawn.x, spawn.y, spawn.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (Boolean.getBoolean("tnc.tortoise.spit.debug")) {
                SPIT_LOG.info("[TN-C SpitAlign] cast={} mouth=({}, {}, {}) spawn=({}, {}, {}) yaw={}",
                        this.castId, from.x, from.y, from.z, spawn.x, spawn.y, spawn.z, this.castYaw);
            }
            level.playSound(null, this.blockPosition(), SoundEvents.GHAST_SHOOT,
                    SoundSource.HOSTILE, 2.2F, 0.65F);
            this.finishAction(30);
            this.spitCooldown = this.isPhaseTwo() ? 120 : 170;
        }
    }

    private static float yawTo(Vec3 target, Vec3 from) {
        return (float) Math.toDegrees(Math.atan2(-(target.x - from.x), target.z - from.z));
    }

    /** 调试入口：立刻开始一次喷吐（仍遵守存活/忙碌限制）。 */
    public boolean forceSpit(LivingEntity target) {
        if (!this.isAlive() || this.isDeadOrDying() || this.isBusy()) {
            return false;
        }
        if (target != null) {
            this.setTarget(target);
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        this.startAction(ACTION_SPIT);
        return true;
    }

    private void tickBlasts(ServerLevel level) {
        if (this.blastCountdown <= 0) {
            return;
        }
        if (--this.blastCountdown > 0) {
            return;
        }
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.55D * this.damageScale());
        for (Vec3 at : this.pendingBlasts) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.4D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.4D, at.z, 30, 1.2D, 0.5D, 1.2D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5D, at.z, 40, 1.0D, 0.6D, 1.0D, 0.03D);
            level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 2.0F, 0.7F);
            AABB box = new AABB(at.x - 2.5D, at.y - 2.0D, at.z - 2.5D,
                    at.x + 2.5D, at.y + 3.0D, at.z + 2.5D);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> this.canHarm(e))) {
                if (victim.position().distanceToSqr(at) <= 2.5D * 2.5D) {
                    victim.hurt(this.damageSources().mobAttack(this), damage);
                }
            }
        }
        this.pendingBlasts.clear();
    }

    private boolean canHarm(LivingEntity other) {
        return other != this && other.isAlive() && !other.isSpectator() && !other.isAlliedTo(this);
    }

    // ------------------------------------------------------------------ 血条 --

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        if (this.isDyingAnimation()) return;
        if (this.isTransitioning()) this.endTransitionProtection();
        super.die(source);
        if (!this.dead) return; // Respect a cancelled Forge death event.
        this.bossEvent.setVisible(false);
        this.bossEvent.removeAllPlayers();
        this.enrageTicks = this.weakTicks = 0;
        this.enragePending = this.megaEruption = false;
        this.entityData.set(DATA_ENRAGE_STATE, STATE_NORMAL);
        if (this.level() instanceof ServerLevel level) {
            this.beginDeathSequence(level);
        }
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == 60 && this.isDyingAnimation()) return; // The whole-body fade replaces the default central poof.
        super.handleEntityEvent(event);
        if (event == 3 && this.level().isClientSide) {
            // Process vanilla Health=0 first; otherwise the dying health guard would block it.
            this.clientDeathClock.startIfAbsent(this.tickCount);
        }
    }

    private void beginDeathSequence(ServerLevel level) {
        this.entityData.set(DATA_TRANSITION_START, -1L);
        this.serverDeathTicks = 0;
        this.entityData.set(DATA_DEATH_AGE, 0);
        this.bossEvent.setVisible(false);
        this.bossEvent.removeAllPlayers();
        this.deathAnchor = this.position();
        this.deathYaw = this.getYRot();
        this.startAction(ACTION_NONE);
        this.hazardEpoch++;
        this.clearTransitionHazards(level);
        this.clearTransitionEffects();
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setXRot(0);
    }

    @Override
    protected void tickDeath() {
        if (!this.isDyingAnimation()) {
            if (this.level() instanceof ServerLevel level) this.beginDeathSequence(level);
            else return; // Wait for the server's synced clock rather than removing the corpse early.
        }
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel level) {
            int age = this.serverDeathTicks = Math.min(TortoiseDeath.DURATION, this.serverDeathTicks + 1);
            this.deathTime = age;
            if (age % 2 == 0 || age == TortoiseDeath.DURATION) this.entityData.set(DATA_DEATH_AGE, age);
            this.getNavigation().stop();
            this.setPos(this.deathAnchor.x, this.deathAnchor.y, this.deathAnchor.z);
            this.setYRot(this.deathYaw);
            this.setYHeadRot(this.deathYaw);
            this.yBodyRot = this.deathYaw;
            if (age == 4) {
                level.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 0.65F);
            }
            if (age == 32 || age == 58) {
                level.playSound(null, this.blockPosition(), SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, 0.3F, 0.65F);
            }
            if (age >= TortoiseDeath.REMAINS_AT && !this.deathRemainsSpawned) {
                Vec3 ground = VolcanicFireFieldEntity.groundBelow(level, this.deathAnchor);
                if (ground == null) ground = this.deathAnchor;
                TortoiseRemainsEntity remains = new TortoiseRemainsEntity(VolcanicTortoiseRegistration.REMAINS, level);
                // A stable identity prevents a second mound when a partially saved death is resumed.
                remains.setUUID(UUID.nameUUIDFromBytes((this.getUUID() + ":remains").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                remains.moveTo(ground.x, ground.y, ground.z, this.deathYaw, 0);
                this.deathRemainsSpawned = level.getEntity(remains.getUUID()) != null || level.addFreshEntity(remains);
            }
            if (age >= TortoiseDeath.DURATION) {
                this.deathTime = 19;
                super.tickDeath(); // Retain vanilla KILLED removal and its entity event, exactly once.
            }
        }
    }

    @Override
    public Component getName() {
        return super.getName();
    }

    // ------------------------------------------------------------- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 8, state -> {
            if (this.isTransitioning() || this.isDeadOrDying()) return PlayState.STOP;
            if (state.isMoving()) {
                // 一阶段没有外翻岩甲骨骼，用不含 plate 通道的 walk；
                // 二阶段用 walk_p2：同一条承重步态 + 岩甲滞后 ±2°（滞后 0.15 秒，左右再错开 0.1 秒）。
                return state.setAndContinue(
                        RawAnimation.begin().thenLoop(this.isPhaseTwo() ? "walk_p2" : "walk"));
            }
            // 一阶段没有外翻岩甲骨骼，用不含 plate 通道的 idle；
            // 二阶段用 idle_p2，在 ±22° 基础外翻角上叠加 ±1° 的缓慢呼吸。
            return state.setAndContinue(
                    RawAnimation.begin().thenLoop(this.isPhaseTwo() ? "idle_p2" : "idle"));
        }));
        AnimationController<VolcanicTortoiseEntity> action = new AnimationController<>(
                this, ACTION_CONTROLLER, 2, state -> PlayState.STOP);
        action.triggerableAnim("bite", RawAnimation.begin().thenPlay("bite"));
        action.triggerableAnim("slam", RawAnimation.begin().thenPlay("slam"));
        controllers.add(action);
        controllers.add(new TortoiseTransitionController(this));
        controllers.add(new TortoiseDeathController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** 一阶段 / 二阶段的模型与贴图（GeckoLib 的 GeoModel 会按 phase 取）。 */
    public static ResourceLocation modelResource(boolean phaseTwo) {
        return ResourceLocation.fromNamespaceAndPath("tnc",
                "geo/entity/volcanic_tortoise_" + (phaseTwo ? "phase2" : "phase1") + ".geo.json");
    }

    public static ResourceLocation textureResource(boolean phaseTwo) {
        return ResourceLocation.fromNamespaceAndPath("tnc",
                "textures/entity/volcanic_tortoise_" + (phaseTwo ? "phase2" : "phase1") + ".png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/volcanic_tortoise.animation.json");
    }

    /** 读一个 float 系统属性，非法值回退到默认（重踏震屏等可调项用）。 */
    private static float parseFloatProperty(String key, float fallback) {
        try {
            return Float.parseFloat(System.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

