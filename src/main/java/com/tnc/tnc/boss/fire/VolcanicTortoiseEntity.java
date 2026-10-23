package com.tnc.tnc.boss.fire;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    /** 重踏半径（格）。 */
    public static final double SLAM_RADIUS = 9.5D;
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

    /** 动作时长（tick）。 */
    private static final int BITE_TICKS = 14;
    private static final int SLAM_TICKS = 26;
    private static final int TRANSITION_TICKS = 68; // 3.4s 四拍（停步低头 / 下沉震动 / 岩甲掀开 / 口部喷发）
    private static final int ERUPTION_TICKS = 46;

    /** 命中结算发生在动作的第几 tick —— 对应动画里"砸到地"的那一帧。 */
    private static final int BITE_HIT_TICK = 5;
    private static final int SLAM_HIT_TICK = 13;
    private static final int ERUPTION_HIT_TICK = 26;

    private static final String ACTION_CONTROLLER = "action";

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(VolcanicTortoiseEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int action = ACTION_NONE;
    private int actionTicks;
    private int attackCooldown = 40;
    private int slamCooldown = 120;
    private int eruptionCooldown = 160;
    /** 二阶段火山弹的落点（服务端算好，倒计时结束时炸）。 */
    private final java.util.List<Vec3> pendingBlasts = new java.util.ArrayList<>();
    private int blastCountdown;

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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, PHASE_ONE);
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
            if (self.eruptionCooldown <= 0 && distance < 18.0D && self.isPhaseTwo()) {
                self.startAction(ACTION_ERUPTION);
                self.triggerAnim(ACTION_CONTROLLER, "slam");
                return;
            }
            if (self.slamCooldown <= 0 && distance < VolcanicTortoiseEntity.SLAM_RADIUS * 0.75D) {
                self.startAction(ACTION_SLAM);
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
        this.getNavigation().stop();
    }

    private void finishAction(int cooldown) {
        this.action = ACTION_NONE;
        this.actionTicks = 0;
        this.attackCooldown = cooldown;
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
        if (this.eruptionCooldown > 0) {
            this.eruptionCooldown--;
        }

        this.tickBlasts(level);
        if (this.action == ACTION_NONE) {
            return;
        }
        this.actionTicks++;
        switch (this.action) {
            case ACTION_BITE -> this.tickBite(level);
            case ACTION_SLAM -> this.tickSlam(level);
            case ACTION_ERUPTION -> this.tickEruption(level);
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
        if (this.actionTicks == BITE_HIT_TICK) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive() && this.distanceTo(target) <= BITE_RANGE + 1.0D) {
                target.hurt(this.damageSources().mobAttack(this),
                        (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * this.damageScale()));
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK,
                        SoundSource.HOSTILE, 1.6F, 0.8F);
            }
        }
        if (this.actionTicks >= BITE_TICKS) {
            this.finishAction(this.isPhaseTwo() ? 14 : 22);
        }
    }

    private void tickSlam(ServerLevel level) {
        if (this.actionTicks == SLAM_HIT_TICK) {
            this.groundSlam(level);
        }
        if (this.actionTicks >= SLAM_TICKS) {
            this.finishAction(this.isPhaseTwo() ? 40 : 60);
            this.slamCooldown = this.isPhaseTwo() ? 90 : 130;
        }
    }

    /** 重踏：半径内所有非友方目标吃一次伤害并被掀起来一点。 */
    private void groundSlam(ServerLevel level) {
        Vec3 center = this.position();
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75D * this.damageScale());
        AABB box = new AABB(center.x - SLAM_RADIUS, center.y - 2.0D, center.z - SLAM_RADIUS,
                center.x + SLAM_RADIUS, center.y + 3.0D, center.z + SLAM_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> this.canHarm(e))) {
            victim.hurt(this.damageSources().mobAttack(this), damage);
            Vec3 push = victim.position().subtract(center);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(0.55D);
            }
            victim.push(push.x, 0.42D, push.z);
        }
        level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3.2F, 0.55F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y + 0.2D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 40; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = SLAM_RADIUS * Math.sqrt(level.getRandom().nextDouble());
            level.sendParticles(ParticleTypes.LAVA,
                    center.x + Math.cos(angle) * radius, center.y + 0.25D,
                    center.z + Math.sin(angle) * radius, 1, 0.0D, 0.05D, 0.0D, 0.0D);
        }
        for (int i = 0; i < 26; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = SLAM_RADIUS * 0.9D * Math.sqrt(level.getRandom().nextDouble());
            level.sendParticles(ParticleTypes.SMOKE,
                    center.x + Math.cos(angle) * radius, center.y + 0.3D,
                    center.z + Math.sin(angle) * radius, 1, 0.05D, 0.10D, 0.05D, 0.01D);
        }
    }

    /** 二阶段：先在目标周围画三圈预警，倒计时结束一起炸。 */
    private void tickEruption(ServerLevel level) {
        if (this.actionTicks == 1) {
            LivingEntity target = this.getTarget();
            Vec3 base = target != null ? target.position() : this.position().add(this.getLookAngle().scale(6.0D));
            this.pendingBlasts.clear();
            for (int i = 0; i < 3; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
                double radius = ERUPTION_SPREAD * Math.sqrt(level.getRandom().nextDouble());
                this.pendingBlasts.add(new Vec3(
                        base.x + Math.cos(angle) * radius, base.y, base.z + Math.sin(angle) * radius));
            }
            this.blastCountdown = ERUPTION_HIT_TICK;
            level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.6F, 0.55F);
        }
        // 预警环：落点周围冒火星，让人有时间走开
        if (this.actionTicks % 3 == 0) {
            for (Vec3 at : this.pendingBlasts) {
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2.0D * i / 6.0D + this.actionTicks * 0.2D;
                    level.sendParticles(ParticleTypes.FLAME,
                            at.x + Math.cos(angle) * 1.7D, at.y + 0.15D, at.z + Math.sin(angle) * 1.7D,
                            1, 0.0D, 0.02D, 0.0D, 0.0D);
                }
            }
        }
        if (this.actionTicks >= ERUPTION_TICKS) {
            this.pendingBlasts.clear();
            this.blastCountdown = 0;
            this.finishAction(50);
            this.eruptionCooldown = this.isPhaseTwo() ? 200 : 300;
        }
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
}
