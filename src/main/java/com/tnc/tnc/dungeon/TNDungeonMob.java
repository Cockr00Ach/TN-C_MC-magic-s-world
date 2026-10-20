package com.tnc.tnc.dungeon;

import com.tnc.tnc.dungeon.DungeonSpecies.Kind;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 12 只地下城小怪的**公共基类**：GeckoLib 动画 + 通用 AI + 按 {@link Kind} 分派的攻击结算。
 *
 * <p>分工：数值与"打什么"在 {@link DungeonSpecies}，这里只负责"怎么打"。
 * 每只怪的行为差异只有三处，都能一眼看到：
 * <ol>
 *   <li>{@link DungeonSpecies.Kind} 决定门槛/前摇/后摇/冷却；</li>
 *   <li>{@link #performImpact()} 里一个 switch 决定命中瞬间发生什么；</li>
 *   <li>动画片段由生成器按同一组 tick 出关键帧（idle/walk/attack/special/death）。</li>
 * </ol>
 *
 * <p>★ 血量/伤害是**基础值**：地下城框架（Library of Exile）会按层数给怪物挂词缀与缩放，
 * 我们不自带"层数"概念，省得两套倍率打架。
 */
public class TNDungeonMob extends Monster implements GeoEntity {

    /** 过热/定身等"非攻击状态"，客户端按它挑 special 动画。 */
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(TNDungeonMob.class, EntityDataSerializers.INT);
    /** 精英个头（×100，和暗系召唤物同一套写法）。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNDungeonMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ELITE =
            SynchedEntityData.defineId(TNDungeonMob.class, EntityDataSerializers.BOOLEAN);

    /** 0 正常 / 1 过热 / 2 定身。 */
    private static final int STATE_NORMAL = 0;
    private static final int STATE_OVERHEAT = 1;
    private static final int STATE_STUN = 2;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SPECIAL = RawAnimation.begin().thenLoop("special");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("death");

    /** 过热后停机 tick 数（= 4 秒的破防窗口）。 */
    private static final int OVERHEAT_TICKS = 80;
    /** 冲撞撞墙后的自晕 tick 数。 */
    private static final int STUN_TICKS = 50;
    /** 召唤物的默认存续时间。 */
    private static final int SUMMON_LIFE = 400;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int attackCooldown;
    private int windup;
    private int recover;
    private int overheat;
    private int stun;
    private int chargeTicks;
    private int heat;
    private int lifeTicks;
    private boolean eliteRolled;
    private UUID ownerId;

    public TNDungeonMob(EntityType<? extends TNDungeonMob> type, Level level) {
        super(type, level);
        DungeonSpecies s = species();
        this.xpReward = s == null ? 5 : s.xp;
    }

    // ------------------------------------------------------------------ 物种
    public DungeonSpecies species() {
        return DungeonSpecies.of(this.getType());
    }

    public Kind kind() {
        DungeonSpecies s = species();
        return s == null ? Kind.CHARGE : s.kind;
    }

    /** 注册表里的属性表（实体类型 → 属性，必须在 MOD 总线交上去）。 */
    public static AttributeSupplier.Builder createAttributes(DungeonSpecies species) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, species.health)
                .add(Attributes.ATTACK_DAMAGE, species.attackDamage)
                .add(Attributes.MOVEMENT_SPEED, species.speed)
                .add(Attributes.ARMOR, species.armor)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, species.kind == Kind.OVERHEAT ? 0.6D : 0.0D);
    }

    // ------------------------------------------------------------------ 数据同步
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, STATE_NORMAL);
        this.entityData.define(DATA_SCALE, 100);
        this.entityData.define(DATA_ELITE, false);
    }

    public int stateKey() {
        return this.entityData.get(DATA_STATE);
    }

    private void setStateKey(int key) {
        this.entityData.set(DATA_STATE, key);
    }

    public boolean isElite() {
        return this.entityData.get(DATA_ELITE);
    }

    public void setElite(boolean value) {
        this.entityData.set(DATA_ELITE, value);
        this.entityData.set(DATA_SCALE, value ? 125 : 100);
        if (value) {
            var hp = getAttribute(Attributes.MAX_HEALTH);
            var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
            if (hp != null) {
                hp.setBaseValue(hp.getBaseValue() * 2.5D);
            }
            if (dmg != null) {
                dmg.setBaseValue(dmg.getBaseValue() * 1.35D);
            }
            setHealth(getMaxHealth());
            refreshDimensions();
        }
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale((float) scale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(Math.max(0.0D, scale() - 1.0D) * 2.0D);
    }

    /** 召唤物：给个主人和解散时间（由召唤者写，存档要带上）。 */
    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    public UUID owner() {
        return this.ownerId;
    }

    public void setLifetime(int ticks) {
        this.lifeTicks = Math.max(0, ticks);
    }

    // ------------------------------------------------------------------ AI
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DungeonGoals.Attack(this));
        this.goalSelector.addGoal(2, new DungeonGoals.Approach(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    // ------------------------------------------------------------------ 攻击时序
    public boolean canStartAttack() {
        return attackCooldown <= 0 && windup <= 0 && recover <= 0
                && overheat <= 0 && stun <= 0 && chargeTicks <= 0;
    }

    /**
     * 起手：动画与 tick 一起开始。{@code windup} 到点才真的出伤害，
     * 这段时间玩家看得到"它在抬手"，所以能被躲开 —— 这是刻意的。
     */
    public void beginAttack() {
        Kind k = kind();
        this.windup = k.windup;
        this.recover = k.recover;
        getNavigation().stop();
        LivingEntity target = getTarget();
        if (target != null) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        triggerAnim("action", k.anim);
    }

    public boolean isMidAttack() {
        return windup > 0 || recover > 0;
    }

    /** 由 goal 每 tick 驱动（只在前摇/后摇期间被调用）。 */
    public void tickAttack() {
        if (windup > 0) {
            windup--;
            if (windup == 0) {
                performImpact();
            }
        } else if (recover > 0) {
            recover--;
            if (recover == 0) {
                attackCooldown = kind().cooldown;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (overheat > 0) {
            overheat--;
            if (overheat == 0) {
                setStateKey(STATE_NORMAL);
                this.entityData.set(DATA_STATE, STATE_NORMAL);
            }
        }
        if (stun > 0) {
            stun--;
            if (stun == 0) {
                setStateKey(STATE_NORMAL);
            }
        }
        if (chargeTicks > 0) {
            chargeTicks--;
            // 撞墙自晕：冲撞型怪的弱点，也是"把野猪引到墙角"这个打法的由来。
            if (this.horizontalCollision) {
                chargeTicks = 0;
                stun = STUN_TICKS;
                setStateKey(STATE_STUN);
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                this.level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND,
                        SoundSource.HOSTILE, 1.0F, 0.7F);
            }
        }
        if (lifeTicks > 0) {
            lifeTicks--;
            if (lifeTicks == 0) {
                discard();
            }
        }
        rollEliteOnce();
        visibleParticles();
    }

    /** 地下城维度里第一次 tick 掷一次精英骰（12%）。 */
    private void rollEliteOnce() {
        if (eliteRolled) {
            return;
        }
        eliteRolled = true;
        ResourceLocation dim = this.level().dimension().location();
        boolean inDungeon = dim.getNamespace().equals("dungeon_realm") || dim.getPath().contains("dungeon");
        if (inDungeon && this.random.nextFloat() < 0.12F && ownerId == null) {
            setElite(true);
        }
    }

    private void visibleParticles() {
        Kind k = kind();
        if (k == Kind.OVERHEAT && overheat > 0 && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.7D, getZ(),
                    3, 0.35D, 0.3D, 0.35D, 0.01D);
        }
        if (isElite() && this.tickCount % 8 == 0 && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    2, 0.3D, 0.5D, 0.3D, 0.0D);
        }
    }

    // ------------------------------------------------------------------ 命中结算
    /** 前摇结束的那一刻发生什么 —— 12 种攻击方式的全部差异就在这一个 switch 里。 */
    protected void performImpact() {
        LivingEntity target = getTarget();
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (kind()) {
            case CHARGE -> impactCharge(target, damage);
            case HOOK -> impactHook(target, damage);
            case POUNCE -> impactPounce(target, damage);
            case VOLLEY -> impactVolley(damage);
            case POT -> impactPot(damage);
            case BEAM -> impactBeam(damage);
            case BURROW -> impactBurrow(target, damage);
            case BIND -> impactBind(target, damage);
            case FROST -> impactFrost(damage);
            case SUMMON -> impactSummon(damage);
            case OVERHEAT -> impactOverheat(target, damage);
            case DETONATE -> impactDetonate(damage);
            case WHIRL -> impactWhirl(damage);
            case GRAB -> impactGrab(damage);
            default -> {
            }
        }
    }

    private void impactCharge(LivingEntity target, float damage) {
        Vec3 dir;
        if (target != null) {
            dir = target.position().subtract(position());
        } else {
            dir = getLookAngle();
        }
        dir = new Vec3(dir.x, 0.0D, dir.z);
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = getLookAngle();
        }
        dir = dir.normalize();
        setDeltaMovement(dir.x * 0.85D, getDeltaMovement().y, dir.z * 0.85D);
        chargeTicks = 10;
        for (LivingEntity victim : cone(3.0D, 70.0D)) {
            hurtOther(victim, damage);
            victim.setDeltaMovement(victim.getDeltaMovement().add(dir.scale(0.55D)).add(0.0D, 0.3D, 0.0D));
        }
    }

    private void impactHook(LivingEntity target, float damage) {
        if (target == null || distanceToSqr(target) > kind().reachSqr() * 1.6D) {
            return;
        }
        Vec3 pull = position().subtract(target.position());
        pull = new Vec3(pull.x, 0.0D, pull.z);
        if (pull.lengthSqr() > 1.0E-4D) {
            target.setDeltaMovement(pull.normalize().scale(0.95D).add(0.0D, 0.35D, 0.0D));
        }
        hurtOther(target, damage * 0.6F);
        burst(ParticleTypes.CRIT, 10);
    }

    private void impactPounce(LivingEntity target, float damage) {
        Vec3 dir = target == null ? getLookAngle()
                : target.position().subtract(position()).normalize();
        setDeltaMovement(dir.x * 0.6D, 0.45D, dir.z * 0.6D);
        for (LivingEntity victim : cone(3.0D, 80.0D)) {
            hurtOther(victim, damage * 0.9F);
        }
    }

    private void impactVolley(float damage) {
        for (int i = -1; i <= 1; i++) {
            // 第三发是"补射"：更疼、更快，配合装填动画的尾段。
            boolean finisher = i == 1;
            DungeonBoltEntity bolt = DungeonBoltEntity.of(this.level(), this, DungeonBoltEntity.Bolt.ARROW,
                    damage * (finisher ? 0.9F : 0.55F));
            bolt.shootFromRotation(this, getXRot(), getYRot() + i * 3.0F, 0.0F,
                    finisher ? 2.0F : 1.6F, finisher ? 0.2F : 1.0F);
            this.level().addFreshEntity(bolt);
        }
        this.level().playSound(null, blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void impactPot(float damage) {
        DungeonBoltEntity bolt = DungeonBoltEntity.of(this.level(), this, DungeonBoltEntity.Bolt.POT, damage);
        bolt.shootFromRotation(this, getXRot() - 18.0F, getYRot(), 0.0F, 1.05F, 0.5F);
        this.level().addFreshEntity(bolt);
    }

    private void impactBeam(float damage) {
        Vec3 start = getEyePosition();
        Vec3 end = start.add(getLookAngle().scale(kind().reach));
        BlockHitResult blockHit = this.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        AABB sweep = new AABB(start, stop).inflate(1.0D);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, sweep, this::isEnemy)) {
            if (distanceToLine(start, stop, victim.getBoundingBox().getCenter()) <= 1.0D) {
                hurtOther(victim, damage);
                victim.setSecondsOnFire(2);
            }
        }
        if (this.level() instanceof ServerLevel sl) {
            Vec3 step = stop.subtract(start).normalize().scale(0.5D);
            for (int i = 0; i < (int) (start.distanceTo(stop) * 2.0D); i++) {
                Vec3 p = start.add(step.scale(i));
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        this.level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2F, 1.6F);
    }

    private void impactBurrow(LivingEntity target, float damage) {
        if (target == null) {
            return;
        }
        Vec3 behind = target.position().subtract(target.getLookAngle().scale(1.8D));
        BlockPos pos = BlockPos.containing(behind.x, target.getY(), behind.z);
        burst(ParticleTypes.POOF, 18);
        teleportTo(pos.getX() + 0.5D, target.getY(), pos.getZ() + 0.5D);
        burst(ParticleTypes.CRIT, 14);
        hurtOther(target, damage);
        this.level().playSound(null, blockPosition(), SoundEvents.SILVERFISH_HURT, SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    private void impactBind(LivingEntity target, float damage) {
        if (target == null) {
            return;
        }
        hurtOther(target, damage * 0.4F);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4), this);
        target.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel sl) {
            Vec3 mid = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mid.x, mid.y, mid.z, 16, 0.4D, 0.6D, 0.4D, 0.01D);
        }
        this.level().playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    private void impactFrost(float damage) {
        for (LivingEntity victim : cone(5.0D, 60.0D)) {
            hurtOther(victim, damage);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2), this);
            victim.setTicksFrozen(Math.min(200, victim.getTicksFrozen() + 100));
        }
        burst(ParticleTypes.SNOWFLAKE, 24);
        this.level().playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 1.4F);
    }

    private void impactSummon(float damage) {
        LivingEntity target = getTarget();
        if (target == null) {
            return;
        }
        // 场上同主人的傀儡最多 4 只，否则一只僧能刷满整层。
        int owned = this.level().getEntitiesOfClass(TNDungeonMob.class,
                getBoundingBox().inflate(24.0D), m -> getUUID().equals(m.owner())).size();
        if (owned >= 4) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            TNDungeonMob minion = DungeonSpecies.GRAVE_GRUB.type().get().create(this.level());
            if (minion == null) {
                continue;
            }
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = 2.0D + this.random.nextDouble();
            minion.moveTo(getX() + Math.cos(angle) * radius, getY(), getZ() + Math.sin(angle) * radius,
                    this.random.nextFloat() * 360.0F, 0.0F);
            minion.setOwner(getUUID());
            minion.setLifetime(SUMMON_LIFE);
            minion.setTarget(target);
            minion.setPersistenceRequired();
            this.level().addFreshEntity(minion);
        }
        burst(ParticleTypes.FLAME, 20);
        this.level().playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.9F);
    }

    private void impactOverheat(LivingEntity target, float damage) {
        for (LivingEntity victim : cone(3.2D, 60.0D)) {
            hurtOther(victim, damage);
        }
        burst(ParticleTypes.LARGE_SMOKE, 8);
        this.level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.0F, 0.8F);
        heat++;
        if (heat >= 4) {
            heat = 0;
            overheat = OVERHEAT_TICKS;
            setStateKey(STATE_OVERHEAT);
            this.level().playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.6F);
        }
    }

    /**
     * 六条触手在全伸展时的尖端坐标（模型空间，单位=格）。
     *
     * <p>数字来自 `tools/gen_dungeon_mobs.py` 的 grab 片段：触手绕 X +30°、向前平移
     * 36 像素（2.25 格）、缩放 1.2，取每条触手最外侧那块方块的外角。
     * 改动画就必须同步这里，否则"看着够到却没伤害"。
     */
    private static final double[][] TENTACLE_TIPS = {
            {0.74D, 0.00D, -2.86D}, {0.74D, 0.00D, -2.55D}, {0.74D, 0.00D, -2.24D},
            {-0.74D, 0.00D, -2.86D}, {-0.74D, 0.00D, -2.55D}, {-0.74D, 0.00D, -2.24D},
    };

    /**
     * 兽人横扫：360° 无死角。
     *
     * <p>不用 {@link #cone} —— 锥形有朝向，横扫的卖点就是"绕后也没用"。
     */
    private void impactWhirl(float damage) {
        AABB box = getBoundingBox().inflate(4.5D);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemy)) {
            hurtOther(victim, damage);
            Vec3 push = victim.position().subtract(position());
            push = new Vec3(push.x, 0.0D, push.z);
            if (push.lengthSqr() > 1.0E-4D) {
                victim.setDeltaMovement(push.normalize().scale(0.85D).add(0.0D, 0.4D, 0.0D));
            }
        }
        burst(ParticleTypes.SWEEP_ATTACK, 12);
        this.level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    /**
     * 触手攻击：六条触手的**尖端**各自结算,不是"以怪为中心的一个圈"。
     *
     * <p>尖端坐标来自生成器的 attack 片段(触手绕 X 转 30°、前伸 36 像素、缩放 1.2),
     * 所以判定点和画面上看到的手是对齐的:够不着就是够不着,不用玩家去猜。
     *
     * <p>同一条触手可以同时碰到多个目标;被几条同时缠上时伤害只算一次
     * (原版 hurt 有 10t 无敌帧,叠加也不会生效,不如直接按一次算),但都会被拽过来。
     */
    private void impactGrab(float damage) {
        double yaw = Math.toRadians(getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        java.util.Set<LivingEntity> caught = new java.util.LinkedHashSet<>();
        int lashing = 0;
        for (double[] tip : TENTACLE_TIPS) {
            // 模型空间(x 右、z 负=正前) → 世界:模型是朝 -z 建模的,渲染时按 yaw 转过来
            double wx = getX() - cos * tip[0] + sin * tip[2];
            double wz = getZ() - sin * tip[0] - cos * tip[2];
            // 判定盒只给 0.75 格余量：触手尖碰到才算，不然又变成"以触手为圆心的足球场"
            AABB tipBox = new AABB(wx - 0.75D, getY() - 1.0D, wz - 0.75D,
                    wx + 0.75D, getY() + 2.5D, wz + 0.75D);
            boolean touched = false;
            for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, tipBox, this::isEnemy)) {
                caught.add(victim);
                touched = true;
            }
            if (touched) {
                lashing++;
            }
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SQUID_INK, wx, getY() + 0.2D, wz, 3, 0.15D, 0.15D, 0.15D, 0.0D);
            }
        }
        if (caught.isEmpty()) {
            return;
        }
        for (LivingEntity victim : caught) {
            hurtOther(victim, damage);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3), this);
            Vec3 pull = position().subtract(victim.position());
            pull = new Vec3(pull.x, 0.0D, pull.z);
            if (pull.lengthSqr() > 1.0E-4D) {
                victim.setDeltaMovement(pull.normalize().scale(0.85D).add(0.0D, 0.15D, 0.0D));
            }
        }
        this.level().playSound(null, blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE,
                1.0F + 0.1F * lashing, 0.6F);
    }

    private void impactDetonate(float damage) {
        AABB box = getBoundingBox().inflate(3.2D);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemy)) {
            hurtOther(victim, damage * 1.4F);
            victim.setSecondsOnFire(3);
        }
        burst(ParticleTypes.EXPLOSION_EMITTER, 1);
        burst(ParticleTypes.LAVA, 24);
        this.level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.8F);
        kill();
    }

    // ------------------------------------------------------------------ 工具
    /**
     * 锥形/AoE 命中的敌我判定。
     *
     * <p>★ 这里**不能只认 {@code Monster}** ✗ —— 那会把村民、铁傀儡漏掉
     * （地城里"关着村民的房间"是常见房间，怪走进去却打不到人，等于白给）。
     * 这个 bug 是 GameTest 抓出来的：靶子用村民时冲撞与自爆都不掉血。
     *
     * <p>刻意**不打**的两类：同类小怪（不然一群怪自己打自己），以及其它被动动物。
     */
    private boolean isEnemy(LivingEntity e) {
        if (e == this || !e.isAlive()) {
            return false;
        }
        if (e instanceof Player p) {
            return !p.isCreative() && !p.isSpectator();
        }
        if (e instanceof TNDungeonMob) {
            return false;
        }
        return e instanceof Monster
                || e instanceof IronGolem
                || e instanceof net.minecraft.world.entity.npc.AbstractVillager;
    }

    /** 前方 {@code range} 格、半角 {@code halfAngle} 度内的敌人。 */
    private List<LivingEntity> cone(double range, double halfAngle) {
        Vec3 look = getLookAngle();
        List<LivingEntity> out = new java.util.ArrayList<>();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(range), this::isEnemy)) {
            Vec3 to = e.position().subtract(position());
            if (to.lengthSqr() > range * range || to.lengthSqr() < 1.0E-4D) {
                continue;
            }
            double cos = look.dot(to.normalize());
            if (cos >= Math.cos(Math.toRadians(halfAngle))) {
                out.add(e);
            }
        }
        return out;
    }

    private void hurtOther(LivingEntity victim, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        victim.hurt(damageSources().mobAttack(this), amount);
    }

    private void burst(net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(particle, getX(), getY() + getBbHeight() * 0.5D, getZ(), count,
                    0.4D, 0.4D, 0.4D, 0.02D);
        }
    }

    private static double distanceToLine(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len = ab.length();
        if (len < 1.0E-4D) {
            return a.distanceTo(p);
        }
        double t = Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / (len * len)));
        return a.add(ab.scale(t)).distanceTo(p);
    }

    // ------------------------------------------------------------------ 受伤 / 掉落
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 过热停机期间是**破防窗口**：打它翻倍，这是"打节奏"的核心奖励。
        if (overheat > 0) {
            amount *= 2.0F;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source,
                                       int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        Kind k = kind();
        if (k == Kind.VOLLEY) {
            spawnAtLocation(new ItemStack(Items.BONE, 1 + this.random.nextInt(2)));
            spawnAtLocation(new ItemStack(Items.ARROW, 2 + this.random.nextInt(4)));
        } else if (k == Kind.OVERHEAT) {
            spawnAtLocation(new ItemStack(Items.IRON_INGOT, 1));
            spawnAtLocation(new ItemStack(Items.IRON_NUGGET, 2 + this.random.nextInt(4)));
        } else if (k == Kind.POT || k == Kind.SUMMON) {
            spawnAtLocation(new ItemStack(Items.CHARCOAL, 1 + this.random.nextInt(3)));
        } else if (k == Kind.FROST) {
            spawnAtLocation(new ItemStack(Items.PACKED_ICE, 1 + this.random.nextInt(2)));
        } else if (k == Kind.BIND) {
            spawnAtLocation(new ItemStack(Items.STRING, 1 + this.random.nextInt(3)));
        } else if (k == Kind.DETONATE) {
            spawnAtLocation(new ItemStack(Items.ROTTEN_FLESH, 1 + this.random.nextInt(2)));
        } else {
            spawnAtLocation(new ItemStack(Items.ROTTEN_FLESH, 1 + this.random.nextInt(2)));
        }
        if (isElite()) {
            spawnAtLocation(new ItemStack(Items.IRON_NUGGET, 3 + this.random.nextInt(4)));
        }
        if (this.random.nextFloat() < 0.18F + looting * 0.05F) {
            spawnAtLocation(new ItemStack(Items.GOLD_NUGGET, 1 + this.random.nextInt(2)));
        }
    }

    // ------------------------------------------------------------------ 存读档
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TncElite", isElite());
        tag.putInt("TncHeat", heat);
        tag.putInt("TncLife", lifeTicks);
        if (ownerId != null) {
            tag.putUUID("TncOwner", ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TncElite")) {
            setElite(tag.getBoolean("TncElite"));
        }
        heat = tag.getInt("TncHeat");
        lifeTicks = tag.getInt("TncLife");
        if (tag.hasUUID("TncOwner")) {
            ownerId = tag.getUUID("TncOwner");
        }
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "state", 4, this::stateAnim));
        // 攻击是**一次性**触发片段：播完自然回落到 state 控制器，不会卡在攻击姿势。
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP));
    }

    private PlayState stateAnim(AnimationState<TNDungeonMob> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (stateKey() != STATE_NORMAL) {
            return state.setAndContinue(SPECIAL);
        }
        if (state.isMoving()) {
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
