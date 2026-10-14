package com.tnc.tnc.boss;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
 * 圣渊利维坦：深海兽形水系首领。深蓝鳞甲和外露獠牙负责“凶兽”，蓝金圣甲、
 * 发光额冠和脊柱符文负责“神圣”。它能在陆地完成验收，也能水下呼吸。
 */
public final class TNWaterLeviathanEntity extends Monster implements GeoEntity {
    public static final float MAX_HP = 4200.0F;
    public static final float HITBOX_WIDTH = 4.8F;
    public static final float HITBOX_HEIGHT = 4.1F;

    private static final int PHASE_ONE = 1;
    private static final int PHASE_TWO = 2;
    private static final int ACTION_NONE = 0;
    private static final int ACTION_BREATH = 1;
    private static final int ACTION_TAIL = 2;
    private static final String ACTION_CONTROLLER = "action";

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int pendingAction = ACTION_NONE;
    private int actionTicks;
    private int attackCooldown = 70;

    public TNWaterLeviathanEntity(EntityType<? extends TNWaterLeviathanEntity> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.xpReward = 1200;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        AmphibiousPathNavigation navigation = new AmphibiousPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.FOLLOW_RANGE, 56.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, PHASE_ONE);
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isPhaseTwo() {
        return this.phase() >= PHASE_TWO;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LeviathanPhase", this.phase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE,
                tag.getInt("LeviathanPhase") >= PHASE_TWO ? PHASE_TWO : PHASE_ONE);
        // Attributes are already restored by LivingEntity; never multiply them again on reload.
        this.pendingAction = ACTION_NONE;
        this.attackCooldown = 70;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.72D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 56.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
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
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(7.0D, 3.0D, 7.0D);
    }

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
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide()) {
            this.triggerAnim(ACTION_CONTROLLER, "bite");
        }
        return hit;
    }

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
        if (!this.isAlive() || this.isDeadOrDying() || this.isNoAi()) return;

        if (!this.isPhaseTwo() && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.enterPhaseTwo(level);
        }
        this.sacredAura(level);
        this.tickSpecialAttack(level);
    }

    private void enterPhaseTwo(ServerLevel level) {
        this.entityData.set(DATA_PHASE, PHASE_TWO);
        this.pendingAction = ACTION_NONE;
        this.attackCooldown = 50;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.18D);
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * 1.25D);

        this.triggerAnim(ACTION_CONTROLLER, "roar");
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.5F, 0.72F);
        level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 1.5D, this.getZ(),
                220, 4.8D, 2.2D, 4.8D, 0.24D);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 3.4D, this.getZ(),
                110, 3.2D, 1.8D, 3.2D, 0.12D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(72.0D))) {
            player.displayClientMessage(Component.literal(
                    "§3[圣渊] §b利维坦的蓝金圣甲正在苏醒"), false);
        }
    }

    private void sacredAura(ServerLevel level) {
        int interval = this.isPhaseTwo() ? 3 : 7;
        if (this.tickCount % interval != 0) return;
        int count = this.isPhaseTwo() ? 7 : 3;
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.4D + this.random.nextDouble() * 3.8D;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + 0.4D + this.random.nextDouble() * 4.0D;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.BUBBLE_POP,
                    x, y, z, 1, 0.03D, 0.05D, 0.03D, 0.01D);
        }
    }

    private void tickSpecialAttack(ServerLevel level) {
        if (this.pendingAction != ACTION_NONE) {
            if (--this.actionTicks <= 0) {
                if (this.pendingAction == ACTION_BREATH) {
                    this.releaseTidalBreath(level);
                } else {
                    this.releaseTailSweep(level);
                }
                this.pendingAction = ACTION_NONE;
                this.attackCooldown = this.isPhaseTwo() ? 42 : 68;
            }
            return;
        }

        if (this.getTarget() == null || !this.getTarget().isAlive()
                || !this.hasLineOfSight(this.getTarget()) || --this.attackCooldown > 0) return;
        double distance = this.distanceTo(this.getTarget());
        if (distance <= 8.5D && this.random.nextBoolean()) {
            this.pendingAction = ACTION_TAIL;
            this.actionTicks = 18;
            this.triggerAnim(ACTION_CONTROLLER, "tail");
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.HOSTILE, 2.3F, 0.72F);
        } else {
            this.pendingAction = ACTION_BREATH;
            this.actionTicks = 30;
            this.triggerAnim(ACTION_CONTROLLER, "breath");
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 2.5F, 0.58F);
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.6D, this.getZ(),
                    28, 1.1D, 0.65D, 1.1D, 0.04D);
        }
    }

    /** Twenty-block blue-gold cone. The long telegraph gives players time to dodge sideways. */
    private void releaseTidalBreath(ServerLevel level) {
        Vec3 origin = this.getEyePosition();
        Vec3 forward = this.getLookAngle().normalize();
        float damage = this.isPhaseTwo() ? 38.0F : 28.0F;
        for (net.minecraft.world.entity.LivingEntity victim : level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, this.getBoundingBox().inflate(22.0D),
                this::canStrike)) {
            Vec3 offset = victim.getEyePosition().subtract(origin);
            double distance = offset.length();
            if (distance <= 20.0D && distance > 0.1D && offset.normalize().dot(forward) >= 0.68D
                    && this.hasLineOfSight(victim)) {
                victim.hurt(this.damageSources().mobAttack(this), damage);
                victim.push(forward.x * 1.2D, 0.35D, forward.z * 1.2D);
            }
        }
        for (int step = 1; step <= 20; step++) {
            Vec3 p = origin.add(forward.scale(step));
            double spread = 0.10D + step * 0.055D;
            level.sendParticles(step % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SPLASH,
                    p.x, p.y, p.z, 8, spread, spread, spread, 0.08D);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.6F, 0.82F);
    }

    /** Close-range 360-degree tail sweep with a visible ring and strong horizontal knockback. */
    private void releaseTailSweep(ServerLevel level) {
        float damage = this.isPhaseTwo() ? 34.0F : 25.0F;
        for (net.minecraft.world.entity.LivingEntity victim : level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, this.getBoundingBox().inflate(8.0D),
                this::canStrike)) {
            Vec3 away = victim.position().subtract(this.position());
            if (away.horizontalDistanceSqr() <= 64.0D && Math.abs(away.y) <= 4.0D
                    && this.hasLineOfSight(victim)) {
                victim.hurt(this.damageSources().mobAttack(this), damage);
                Vec3 push = away.lengthSqr() < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
                victim.push(push.x * 2.0D, 0.55D, push.z * 2.0D);
            }
        }
        for (int degree = 0; degree < 360; degree += 10) {
            double angle = Math.toRadians(degree);
            level.sendParticles(degree % 30 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SPLASH,
                    this.getX() + Math.cos(angle) * 7.0D, this.getY() + 0.45D,
                    this.getZ() + Math.sin(angle) * 7.0D, 2, 0.12D, 0.08D, 0.12D, 0.03D);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 2.2F, 1.15F);
    }

    private boolean canStrike(net.minecraft.world.entity.LivingEntity victim) {
        return victim != this && victim.isAlive() && !this.isAlliedTo(victim)
                && !(victim instanceof TNWaterLeviathanEntity)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            if (state.isMoving() || this.isInWater()) {
                state.getController().setAnimationSpeed(this.isInWater() ? 1.2F : 0.82F);
                return state.setAndContinue(RawAnimation.begin().thenLoop("swim"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
        AnimationController<TNWaterLeviathanEntity> action = new AnimationController<>(
                this, ACTION_CONTROLLER, 2, state -> PlayState.STOP);
        action.triggerableAnim("bite", RawAnimation.begin().thenPlay("bite"));
        action.triggerableAnim("roar", RawAnimation.begin().thenPlay("roar"));
        action.triggerableAnim("breath", RawAnimation.begin().thenPlay("breath"));
        action.triggerableAnim("tail", RawAnimation.begin().thenPlay("tail_sweep"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/water_leviathan.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/water_leviathan_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/water_leviathan.animation.json");
    }
}
