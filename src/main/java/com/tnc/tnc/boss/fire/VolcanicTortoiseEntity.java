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
    private static final int TRANSITION_TICKS = 68; // 3.4s 四拍（停步低头 / 下沉震动 / 岩甲掀开 / 口部喷发）
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
    /** 与火环共用砸地动作，所以时长与砸地帧一致。 */
    public static final int QUAKE_TICKS = 72;
    public static final int QUAKE_HIT_TICK = 24;
    /** 地面波从中心推到最大半径的 tick 数（比火环慢，方便看清地块依次抬起）。 */
    public static final int QUAKE_RING_TICKS = 40;
    /** 独立的最大半径，不与火环共用配置。 */
    public static final double QUAKE_RADIUS = 22.0D;
    /** 波峰带宽（格）：只有这个宽度内的地块在抬升。 */
    public static final double QUAKE_BAND = 3.5D;
    private static final double QUAKE_HEIGHT_TOLERANCE = 2.0D;
    /** 首轮基础伤害为 0，只保留配置入口，后续按测试结果调整。 */
    private static final float QUAKE_DAMAGE = parseFloatProperty("tnc.tortoise.quake.damage", 0.0F);
    private static final double QUAKE_KNOCKBACK = parseFloatProperty("tnc.tortoise.quake.knockback", 0.85F);
    /** 轻微上抛，避免只在地面平移。 */
    private static final double QUAKE_LIFT = parseFloatProperty("tnc.tortoise.quake.lift", 0.16F);

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
    private static final EntityDataAccessor<Float> DATA_QUAKE_X =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUAKE_Y =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUAKE_Z =
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

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int action = ACTION_NONE;
    private int actionTicks;
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

    /** 二阶段伤害系数：不写进属性，重载不会叠乘。 */
    public double damageScale() {
        return this.isPhaseTwo() ? 1.35D : 1.0D;
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

    /** 客户端渲染用：震波锁定的砸地中心。 */
    public Vec3 quakeWaveCenter() {
        return new Vec3(this.entityData.get(DATA_QUAKE_X), this.entityData.get(DATA_QUAKE_Y),
                this.entityData.get(DATA_QUAKE_Z));
    }

    /** 服务端：把震波中心与编号同步给客户端（只在砸地瞬间调用一次）。 */
    private void publishQuake(ServerLevel level) {
        this.entityData.set(DATA_QUAKE_X, (float) this.quakeCenter.x);
        this.entityData.set(DATA_QUAKE_Y, (float) this.quakeCenter.y);
        this.entityData.set(DATA_QUAKE_Z, (float) this.quakeCenter.z);
        this.entityData.set(DATA_QUAKE_START, (int) level.getGameTime());
    }

    private void clearQuake() {
        this.entityData.set(DATA_QUAKE_START, -1);
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
        this.entityData.define(DATA_QUAKE_X, 0.0F);
        this.entityData.define(DATA_QUAKE_Y, 0.0F);
        this.entityData.define(DATA_QUAKE_Z, 0.0F);
        this.entityData.define(DATA_SPIT_START, -1);
        this.entityData.define(DATA_SPIT_CHARGE, 0);
        this.entityData.define(DATA_PILLAR_START, -1);
        this.entityData.define(DATA_PILLAR_X, 0.0F);
        this.entityData.define(DATA_PILLAR_Y, 0.0F);
        this.entityData.define(DATA_PILLAR_Z, 0.0F);
        this.entityData.define(DATA_PILLAR_YAW, 0.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TortoisePhase", this.phase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE,
                tag.getInt("TortoisePhase") >= PHASE_TWO ? PHASE_TWO : PHASE_ONE);
        // 属性由 LivingEntity 自己恢复，这里绝不重新乘一遍
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
            if (self.eruptionCooldown <= 0 && distance > 8.0D && distance < 28.0D && self.isPhaseTwo()) {
                self.startAction(ACTION_ERUPTION);
                self.triggerAnim(ACTION_CONTROLLER, "slam");
                return;
            }
            // 中远距离：嘴前蓄力后喷吐一发火球（任务 5）。需要视线，避免隔墙喷。
            if (self.spitCooldown <= 0 && distance > SPIT_MIN_RANGE && distance < SPIT_MAX_RANGE
                    && self.hasLineOfSight(target)) {
                self.startAction(ACTION_SPIT);
                return;
            }
            // 二阶段中距离：前方走廊预警 + 分批火柱（任务 7）
            if (self.isPhaseTwo() && self.pillarCooldown <= 0
                    && distance > 6.0D && distance < 22.0D && self.hasLineOfSight(target)) {
                // 熔岩之柱不抬前腿：不放 slam 动作，本体保持待机/行走
                self.startAction(ACTION_PILLAR);
                return;
            }
            if (self.slamCooldown <= 0 && distance < VolcanicTortoiseEntity.SLAM_RADIUS * 0.75D) {
                self.startAction(ACTION_SLAM);
                self.triggerAnim(ACTION_CONTROLLER, "slam");
                return;
            }
            // 技能 B 用独立冷却；刚放完火环时 slamCooldown 会挡住连放，这里再加一道距离条件。
            if (self.quakeCooldown <= 0 && distance < VolcanicTortoiseEntity.QUAKE_RADIUS * 0.45D) {
                self.startAction(ACTION_QUAKE);
                self.triggerAnim(ACTION_CONTROLLER, "slam");
                return;
            }
            if (distance < VolcanicTortoiseEntity.BITE_RANGE) {
                self.startAction(ACTION_BITE);
                self.triggerAnim(ACTION_CONTROLLER, "bite");
            }
        }
    }

    private void startAction(int next) {
        this.action = next;
        this.actionTicks = 0;
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
        super.aiStep();

        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(this.isAlive() && !this.isDeadOrDying());
        if (!this.isAlive() || this.isDeadOrDying() || this.isNoAi()) {
            return;
        }

        if (!this.isPhaseTwo() && this.getHealth() <= this.getMaxHealth() * PHASE_TWO_HEALTH_FRACTION) {
            this.beginPhaseTwo(level);
        }

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.slamCooldown > 0) {
            this.slamCooldown--;
        }
        if (this.quakeCooldown > 0) {
            this.quakeCooldown--;
        }
        if (this.eruptionCooldown > 0) {
            this.eruptionCooldown--;
        }
        if (this.spitCooldown > 0) {
            this.spitCooldown--;
        }
        if (this.pillarCooldown > 0) {
            this.pillarCooldown--;
        }

        this.tickBlasts(level);
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
        if (this.isPhaseTwo()) {
            return;
        }
        this.entityData.set(DATA_PHASE, PHASE_TWO);
        this.refreshDimensions();
        this.startAction(ACTION_TRANSITION);
        this.setInvulnerable(true);
        this.getNavigation().stop();
        this.triggerAnim(ACTION_CONTROLLER, "transition");
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.55F);
        level.sendParticles(ParticleTypes.LAVA,
                this.getX(), this.getY() + 1.2D, this.getZ(), 40, 1.6D, 0.8D, 1.6D, 0.0D);
    }

    private void tickTransition(ServerLevel level) {
        if (this.actionTicks == 1 || this.actionTicks % 6 == 0) {
            level.sendParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY() + 1.0D, this.getZ(),
                    18, 1.8D, 0.6D, 1.8D, 0.02D);
        }
        if (this.actionTicks >= TRANSITION_TICKS) {
            this.setInvulnerable(false);
            this.finishAction(30);
            this.slamCooldown = 20;
            this.eruptionCooldown = 60;
            level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
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
        if (this.actionTicks == QUAKE_HIT_TICK) {
            this.quakeCenter = this.position();
            this.publishQuake(level);
            this.groundQuakeImpact(level);
        }
        int radiusAge = this.actionTicks - QUAKE_HIT_TICK;
        if (radiusAge > 0 && radiusAge <= QUAKE_RING_TICKS) {
            double radius = QUAKE_RADIUS * radiusAge / (double) QUAKE_RING_TICKS;
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
     * 震波命中：基础伤害默认 0，只做一次向外击退 + 轻微上抛。
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
            victim.knockback(QUAKE_KNOCKBACK, dx, dz);
            if (QUAKE_LIFT > 0.0D) {
                Vec3 motion = victim.getDeltaMovement();
                victim.setDeltaMovement(motion.x, Math.max(motion.y, QUAKE_LIFT), motion.z);
            }
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

    /** 二阶段火山喷发：火山口蓄势，随后 3 批共 21 颗抛射火球落向 Boss 周围。 */
    private void tickEruption(ServerLevel level) {
        if (this.actionTicks == 1) {
            this.barrageCharge = BARRAGE_CHARGE_MIN
                    + level.random.nextInt(BARRAGE_CHARGE_MAX - BARRAGE_CHARGE_MIN + 1);
            this.barrageBall = 0;
            this.barrageSpots.clear();
            this.mortarLastHit.clear();
            this.mortarHits = 0;
            this.pendingBlasts.clear();
            this.blastCountdown = 0;
            level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.2F, 0.6F);
        }

        Vec3 crater = this.craterPoint();
        if (this.actionTicks <= this.barrageCharge && this.actionTicks % 2 == 0) {
            // 蓄势：火山口内亮起来，越接近喷发越密
            float p = this.actionTicks / (float) this.barrageCharge;
            level.sendParticles(ParticleTypes.LAVA, crater.x, crater.y, crater.z,
                    1 + (int) (p * 3.0F), 0.35D, 0.10D, 0.35D, 0.0D);
            level.sendParticles(TortoiseParticles.FIRE_EDGE.get(), crater.x, crater.y + 0.15D, crater.z,
                    2 + (int) (p * 4.0F), 0.30D, 0.20D, 0.30D, 0.01D);
        }

        int since = this.actionTicks - this.barrageCharge;
        int total = BARRAGE_WAVES * BARRAGE_PER_WAVE;
        while (this.barrageBall < total) {
            int wave = this.barrageBall / BARRAGE_PER_WAVE;
            int index = this.barrageBall % BARRAGE_PER_WAVE;
            int at = wave * BARRAGE_WAVE_GAP + index * BARRAGE_BALL_STAGGER;
            if (since < at) {
                break;
            }
            this.fireBarrageBall(level);
            this.barrageBall++;
        }
        if (this.barrageBall >= total && since > BARRAGE_WAVES * BARRAGE_WAVE_GAP + 40) {
            this.finishAction(40);
            this.eruptionCooldown = this.isPhaseTwo() ? 260 : 340;
        }
    }

    /** 发射一颗：一颗颗出膛，落点分散、彼此留出可走的空隙。 */
    private void fireBarrageBall(ServerLevel level) {
        Vec3 from = this.craterPoint();
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * BARRAGE_DAMAGE_SCALE * this.damageScale());
        Vec3 landing = this.pickLanding(level);
        if (landing != null) {
            int flight = 42 + level.random.nextInt(14);
            level.addFreshEntity(VolcanicMortarEntity.launch(level, this, from, landing, flight, damage, this.castId));
            this.barrageSpots.add(landing);
        }
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.1F, 1.5F);
    }

    /** 环形落区里挑一个点：内圈安全、外缘 24 格、与已有点至少隔 3.2 格。 */
    private Vec3 pickLanding(ServerLevel level) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(level.random.nextDouble()
                    * (BARRAGE_OUTER_RADIUS * BARRAGE_OUTER_RADIUS
                    - BARRAGE_INNER_RADIUS * BARRAGE_INNER_RADIUS)
                    + BARRAGE_INNER_RADIUS * BARRAGE_INNER_RADIUS);
            double x = this.getX() + Math.cos(angle) * r;
            double z = this.getZ() + Math.sin(angle) * r;
            if (!level.hasChunkAt(BlockPos.containing(x, this.getY(), z))) {
                continue;
            }
            boolean crowded = false;
            for (Vec3 other : this.barrageSpots) {
                if (other.distanceToSqr(x, other.y, z) < BARRAGE_MIN_SPACING * BARRAGE_MIN_SPACING) {
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
        double height = this.isPhaseTwo() ? 7.22D : 4.50D;
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
            // 生成点前移 1.4 格：既不塞进头部，也不会一出生就撞到自己。
            Vec3 spawn = from.add(unit.scale(1.55D));
            level.addFreshEntity(VolcanicFireballEntity.launch(level, this, spawn, unit, damage, this.castId));
            level.sendParticles(ParticleTypes.EXPLOSION, spawn.x, spawn.y, spawn.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
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
        this.bossEvent.setVisible(false);
        this.bossEvent.removeAllPlayers();
        this.pendingBlasts.clear();
        this.blastCountdown = 0;
        // death 动画 1.8 秒 = 36 tick，但原版尸体 20 tick 就消失 ⇒ 把死亡计时往前推 16 tick，
        // 让倒地动作完整播完再移除（移除时机完全由 deathTime 驱动，不额外挂逻辑）。
        this.deathTime = -16;
        this.triggerAnim(ACTION_CONTROLLER, "death");
        super.die(source);
    }

    @Override
    public Component getName() {
        return super.getName();
    }

    // ------------------------------------------------------------- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 8, state -> {
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
        action.triggerableAnim("transition", RawAnimation.begin().thenPlay("transition"));
        action.triggerableAnim("death", RawAnimation.begin().thenPlay("death"));
        controllers.add(action);
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
