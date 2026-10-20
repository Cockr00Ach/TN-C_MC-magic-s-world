package com.tnc.tnc.world.wind;

import com.tnc.tnc.boss.GiantApproachGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
 * 翠岚武尊 / Wind Warden — the wind school's melee mage.
 *
 * <p>Wind is written as the element of "position matters more than strength"; this
 * boss is deliberately the heaviest thing in that family: three players tall,
 * emerald plate over a sculpted physique, and it closes to melee instead of
 * drifting away. Phase one conjures the wind wing sword
 * ({@code tnc:wind/arsenal/sword}); at half health it roars, the sword shatters
 * and it draws the spear that pierces wind ({@code spear}) instead.
 *
 * <p>Every damaging event lives in {@link WindWardenCombat} and is server-owned.
 * The client only reads {@link #wardenMove()} and {@link #phase()} to pick the
 * GeckoLib clip and to draw the conjured weapon.
 */
public final class WindWardenEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> WARDEN_MOVE = SynchedEntityData.defineId(
            WindWardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            WindWardenEntity.class, EntityDataSerializers.INT);

    private final WindWardenCombat combat = new WindWardenCombat(this);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.tnc.wind_warden"),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private int shiftTicks;

    public WindWardenEntity(EntityType<? extends WindWardenEntity> type, Level level) {
        super(type, level);
        xpReward = 1100;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1600.0D)
                .add(Attributes.ATTACK_DAMAGE, 26.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                // It is a duelist: the player cannot simply shove it around.
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(WARDEN_MOVE, WindWardenAttackRules.IDLE);
        entityData.define(DATA_PHASE, WindWardenAttackRules.PHASE_SWORD);
    }

    public int wardenMove() {
        return entityData.get(WARDEN_MOVE);
    }

    void setWardenMove(int move) {
        entityData.set(WARDEN_MOVE, move);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean isSpearPhase() {
        return phase() == WindWardenAttackRules.PHASE_SPEAR;
    }

    /** True while the phase-shift roar plays; the client hides the weapon then. */
    public boolean shifting() {
        return shiftTicks > 0;
    }

    public boolean busy() {
        return combat.busy();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Direct steering: a 5.4-block body must never run a normal A* search.
        goalSelector.addGoal(2, new GiantApproachGoal(this, 0.95D, () -> !busy() && shiftTicks == 0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 48.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            emitWindMotes();
            return;
        }
        bossBar.setName(getDisplayName());
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickPhaseShift()) {
            return;
        }
        combat.tick();
        LivingEntity watched = getTarget();
        if (watched == null) {
            watched = level().getNearestPlayer(this, 32.0D);
        }
        if (watched != null && hasLineOfSight(watched)) {
            getLookControl().setLookAt(watched, 20.0F, 15.0F);
        }
    }

    /**
     * Roar, shatter the sword, draw the spear, then hand control back to the AI.
     *
     * @return true while the transition owns the boss (normal AI is suspended)
     */
    private boolean tickPhaseShift() {
        if (shiftTicks > 0) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            shiftTicks--;
            return true;
        }
        if (!WindWardenAttackRules.shouldShift(phase(), getHealth(), getMaxHealth())) {
            return false;
        }
        shiftTicks = WindWardenAttackRules.PHASE_SHIFT_LENGTH;
        entityData.set(DATA_PHASE, WindWardenAttackRules.PHASE_SPEAR);
        combat.cancel();
        triggerAnim("action", "phase_shift");
        level().playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 3.0F, 1.35F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + getBbHeight() * 0.55,
                    getZ(), 48, 2.2, 2.2, 2.2, 0.35);
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5,
                    getZ(), 60, 2.6, 1.6, 2.6, 0.18);
        }
        // The shockwave pushes nearby players out so the phase swap is readable.
        for (LivingEntity nearby : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(9.0D), candidate -> candidate != this && candidate.isAlive()
                        && !candidate.isSpectator() && !isAlliedTo(candidate))) {
            Vec3 push = nearby.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D).normalize();
            nearby.push(push.x * 1.1D, 0.42D, push.z * 1.1D);
            nearby.hurtMarked = true;
        }
        return true;
    }

    private void emitWindMotes() {
        if (!isAlive() || tickCount % 3 != 0) {
            return;
        }
        double height = getBbHeight();
        level().addParticle(ParticleTypes.CLOUD,
                getRandomX(0.7D), getY() + random.nextDouble() * height, getRandomX(0.7D),
                0.0D, 0.012D, 0.0D);
        if (isSpearPhase() && tickCount % 9 == 0) {
            level().addParticle(ParticleTypes.END_ROD,
                    getX() + random.nextDouble() - 0.5D,
                    getY() + height * (0.45D + random.nextDouble() * 0.4D),
                    getZ() + random.nextDouble() - 0.5D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * All melee damage is driven by {@link WindWardenCombat}, so the generic
     * approach goal must not deal a second, untelegraphed hit.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(8.0D, 4.0D, 8.0D);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        combat.cancel();
        shiftTicks = 0;
        bossBar.removeAllPlayers();
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WardenPhase", phase());
        tag.putInt("WardenCycle", combat.cycle());
        tag.putInt("WardenCooldown", combat.cooldown());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_PHASE, Math.max(WindWardenAttackRules.PHASE_SWORD,
                Math.min(WindWardenAttackRules.PHASE_SPEAR, tag.getInt("WardenPhase"))));
        // A reload always starts from a safe stand-still: never resume mid-swing.
        setWardenMove(WindWardenAttackRules.IDLE);
        shiftTicks = 0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 6,
                state -> busy() || shiftTicks > 0 || !isAlive()
                        ? PlayState.STOP
                        : state.setAndContinue(RawAnimation.begin()
                                .thenLoop(state.isMoving() ? "walk" : "idle"))));
        AnimationController<WindWardenEntity> action = new AnimationController<>(this, "action", 1,
                state -> PlayState.STOP);
        for (String clip : new String[]{"slash_left", "slash_right", "overhead", "spin_sweep",
                "spear_thrust", "spear_rip", "spear_throw", "phase_shift"}) {
            action.triggerableAnim(clip, RawAnimation.begin().thenPlay(clip));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
