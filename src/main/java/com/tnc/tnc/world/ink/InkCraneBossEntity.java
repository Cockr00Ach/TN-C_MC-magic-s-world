package com.tnc.tnc.world.ink;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Tall ink crane with folded wing arms and eleven independently rigged tail plumes. */
public final class InkCraneBossEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> INK_MOVE = SynchedEntityData.defineId(
            InkCraneBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SKY_SHROUD = SynchedEntityData.defineId(
            InkCraneBossEntity.class, EntityDataSerializers.BOOLEAN);
    private final InkCraneAttacks attacks = new InkCraneAttacks(this);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.tnc.ink_crane_boss"),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private int wingStrike;

    public InkCraneBossEntity(EntityType<? extends InkCraneBossEntity> type, Level level) {
        super(type, level);
        xpReward = 900;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1100.0)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(INK_MOVE, 0); entityData.define(SKY_SHROUD, false);
    }
    public boolean skyShroudVisible() { return entityData.get(SKY_SHROUD); }
    void setSkyShroudVisible(boolean visible) { entityData.set(SKY_SHROUD,visible); }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return skyShroudVisible()?super.getBoundingBoxForCulling().inflate(20):super.getBoundingBoxForCulling();
    }
    public int inkMove() { return entityData.get(INK_MOVE); }
    void setInkMove(int move) { entityData.set(INK_MOVE, move); }
    public boolean startInkAttack(int move, LivingEntity target) { return attacks.begin(move, target); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, true) {
            @Override public boolean canUse() { return inkMove() == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return inkMove() == 0 && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new RandomStrollGoal(this, 0.78) {
            @Override public boolean canUse() { return inkMove() == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return inkMove() == 0 && super.canContinueToUse(); }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        if(!level().isClientSide&&InkEncounter.introducing(this)){setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);return;}
        super.aiStep();
        if (level().isClientSide) {
            emitInkLight();
        } else {
            bossBar.setName(getDisplayName());
            bossBar.setProgress(getHealth() / getMaxHealth());
            attacks.tick();
            LivingEntity watched = getTarget();
            if (watched == null) watched = level().getNearestPlayer(this, 24.0);
            if (watched != null && hasLineOfSight(watched)) {
                getLookControl().setLookAt(watched, 12.0F, 8.0F);
            }
        }
    }

    private void emitInkLight() {
        if (random.nextInt(2) == 0) {
            level().addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    getRandomX(0.65), getY() + 5.6 + random.nextDouble() * 5.6,
                    getRandomZ(1.15), 0.0, 0.012, 0.0);
        }
        // Tail and action trails are emitted at the actual rendered bone positions.
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (inkMove() != 0) return false;
        boolean hit = super.doHurtTarget(target);
        if (!hit || level().isClientSide) return hit;

        wingStrike++;
        String animation = wingStrike % 3 == 0
                ? "wing_double"
                : (wingStrike % 2 == 0 ? "wing_slash_right" : "wing_slash_left");
        triggerAnim("action", animation);

        float sweepDamage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.45F;
        for (LivingEntity nearby : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(3.25, 1.0, 3.25), candidate ->
                        candidate != this && candidate != target && candidate.isAlive() && !isAlliedTo(candidate))) {
            nearby.hurt(damageSources().mobAttack(this), sweepDamage);
        }
        return true;
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public void remove(RemovalReason reason) { InkRestraints.release(this); bossBar.removeAllPlayers(); super.remove(reason); }
    @Override public void die(DamageSource source) { attacks.cancel(); bossBar.removeAllPlayers(); super.die(source); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); attacks.saveRecovery(tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("InkCraneAirborne")) {
            setPos(tag.getDouble("InkCraneHomeX"), tag.getDouble("InkCraneHomeY"), tag.getDouble("InkCraneHomeZ"));
            setNoGravity(tag.getBoolean("InkCranePreviousGravity")); fallDistance = 0;
        }
        setInkMove(0);
        setSkyShroudVisible(false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 8,
                state -> inkMove() != 0 ? PlayState.STOP : state.setAndContinue(RawAnimation.begin().thenLoop(
                        state.isMoving() ? "walk" : "idle"))));
        AnimationController<InkCraneBossEntity> action = new AnimationController<>(this, "action", 1,
                state -> PlayState.STOP);
        action.triggerableAnim("wing_slash_left", RawAnimation.begin().thenPlay("wing_slash_left"));
        action.triggerableAnim("wing_slash_right", RawAnimation.begin().thenPlay("wing_slash_right"));
        action.triggerableAnim("wing_double", RawAnimation.begin().thenPlay("wing_double"));
        action.triggerableAnim("ink_flap", RawAnimation.begin().thenPlay("ink_flap"));
        action.triggerableAnim("spectral_sweep", RawAnimation.begin().thenPlay("spectral_sweep"));
        action.triggerableAnim("ink_roar", RawAnimation.begin().thenPlay("ink_roar"));
        action.triggerableAnim("sky_ascent", RawAnimation.begin().thenPlay("sky_ascent"));
        action.triggerableAnim("sky_dive", RawAnimation.begin().thenPlay("sky_dive"));
        action.triggerableAnim("sky_land", RawAnimation.begin().thenPlay("sky_land"));
        controllers.add(action);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
