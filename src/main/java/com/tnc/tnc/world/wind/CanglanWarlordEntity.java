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

/** Wind war-god: corporeal duel, ten linked echoes, then a fivefold phantom after the first defeat. */
public final class CanglanWarlordEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_MOVE = SynchedEntityData.defineId(
            CanglanWarlordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            CanglanWarlordEntity.class, EntityDataSerializers.INT);

    private final CanglanWarlordCombat combat = new CanglanWarlordCombat(this);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.tnc.canglan_warlord"),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    /** Ticks left in the phase-shift entrance; while positive the AI is suspended. */
    private int shiftTicks;
    /** Ticks left in the spawn rise; the boss cannot attack before it finishes. */
    private int spawnTicks = CanglanWarlordAttackRules.SPAWN_LENGTH;
    private int roarCooldown;
    private boolean spawned;

    public CanglanWarlordEntity(EntityType<? extends CanglanWarlordEntity> type, Level level) {
        super(type, level);
        xpReward = 1200;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1200.0D)
                .add(Attributes.ATTACK_DAMAGE, 28.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                // Wind is light on its feet but still a six-block wall of plate:
                // knockback barely moves it, and only the player's own weight does.
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MOVE, CanglanWarlordAttackRules.IDLE);
        entityData.define(DATA_PHASE, CanglanWarlordAttackRules.PHASE_SWORD);
    }

    /** Synced so the client can play the matching action clip. */
    public int canglanMove() {
        return entityData.get(DATA_MOVE);
    }

    void setCanglanMove(int move) {
        entityData.set(DATA_MOVE, move);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    /** True once the sword has been replaced by the spear. */
    public boolean isSpearPhase() {
        return phase() == CanglanWarlordAttackRules.PHASE_SPEAR;
    }

    /** The wind arsenal form the renderer must draw in the right hand right now. */
    public int weaponForm() {
        return CanglanWarlordAttackRules.formFor(phase());
    }

    public float battleScale() { return CanglanWarGodRules.scale(phase()); }
    public int anchorCount() { return combat.anchorCount(); }
    CanglanWarlordCombat battle() { return combat; }
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(entityData.get(DATA_PHASE) == 2 ? 5F : 1F);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PHASE.equals(key)) refreshDimensions();
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && shiftTicks > 0) return false;
        if (!level().isClientSide && amount > 0 && combat.dodge(source)) return false;
        return super.hurt(source, amount);
    }

    /** Called by the roar so the buff cannot be stacked by spam. */
    void setRoarCooldown(int ticks) {
        roarCooldown = Math.max(roarCooldown, ticks);
    }

    public boolean busy() {
        return canglanMove() != 0 || combat.busy();
    }

    /** True while the phase-shift roar owns the boss. */
    public boolean shifting() {
        return shiftTicks > 0;
    }

    /** True while the boss is still rising out of the spawn pose. */
    public boolean rising() {
        return spawnTicks > 0;
    }
    boolean canApproach() { return !busy() && shiftTicks == 0 && spawnTicks == 0; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Direct steering: a 6.4-block body must never run an A* search across the arena.
        goalSelector.addGoal(2, new GiantApproachGoal(this, 0.95D,
                this::canApproach));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 48.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // The spawn rise ticks down on both sides so the client's idle controller
        // knows to stay quiet for exactly as long as the server does.
        if (spawnTicks > 0) {
            spawnTicks--;
            if (level().isClientSide) {
                return;
            }
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            if (!spawned) {
                startSpawnSequence();
            }
            return;
        }
        if (level().isClientSide) {
            emitGale();
            return;
        }
        if (!isAlive()) {
            return;
        }
        bossBar.setName(phase()==2 ? getDisplayName().copy().append(" · 风神虚像") : getDisplayName());
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickPhaseShift()) {
            return;
        }
        if (roarCooldown > 0) {
            roarCooldown--;
        }
        tickGaleRoar();
        combat.tick();
        LivingEntity watched = getTarget();
        if (watched == null) {
            watched = level().getNearestPlayer(this, 32.0D);
        }
        if (watched != null && hasLineOfSight(watched)) {
            getLookControl().setLookAt(watched, 30.0F, 20.0F);
        }
    }

    /** The arrival animation: kneeling in the crater, helm down, then rising. */
    private void startSpawnSequence() {
        spawned = true;
        triggerAnim("action", "spawn");
        level().playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.HOSTILE, 3.0F, 0.55F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + 0.6D, getZ(), 70, 2.2D, 0.4D, 2.2D, 0.10D);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    getX(), getY() + 1.2D, getZ(), 22, 2.0D, 0.3D, 2.0D, 0.02D);
            server.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 0.5D, getZ(), 30, 1.6D, 0.3D, 1.6D, 0.10D);
        }
    }

    /**
     * At half health the warlord's crest whips up, it roars, and the arsenal blade
     * in its fist changes from the sword to the spear. There is no mesh swap in the
     * model: the phase is synced and the renderer draws the other arsenal form.
     *
     * @return true while the transition owns the boss
     */
    private boolean tickPhaseShift() {
        if (shiftTicks > 0) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            shiftTicks--;
            if (shiftTicks == 0) setCanglanMove(0);
            return true;
        }
        return false;
    }

    private boolean ascend() {
        spawnTicks = 0;
        spawned = true;
        shiftTicks = CanglanWarlordAttackRules.PHASE_SHIFT_LENGTH;
        entityData.set(DATA_PHASE, CanglanWarlordAttackRules.PHASE_SPEAR);
        combat.cleanup();
        setCanglanMove(13);
        setHealth(getMaxHealth());
        refreshDimensions();
        triggerAnim("action", "phase_shift");
        level().playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 3.4F, 0.82F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    getX(), getY() + getBbHeight() * 0.55D, getZ(), 80, 2.6D, 2.0D, 2.6D, 0.02D);
            server.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + getBbHeight() * 0.45D, getZ(), 70, 3.0D, 1.6D, 3.0D, 0.18D);
            server.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + getBbHeight() * 0.6D, getZ(), 40, 2.2D, 1.4D, 2.2D, 0.20D);
        }
        for (LivingEntity nearby : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(11.0D), candidate -> candidate != this
                        && candidate.isAlive() && !candidate.isSpectator() && !isAlliedTo(candidate))) {
            Vec3 push = nearby.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 0.01D) {
                push = push.normalize();
                nearby.push(push.x * 1.25D, 0.5D, push.z * 1.25D);
                nearby.hurtMarked = true;
            }
        }
        return true;
    }

    /** One gale roar per roar cooldown, never on top of another move. */
    private void tickGaleRoar() {
        if (combat.busy() || spawnTicks > 0 || shiftTicks > 0) {
            return;
        }
        if (!CanglanWarlordAttackRules.shouldRoar(phase(), getHealth(), getMaxHealth(), roarCooldown)) {
            return;
        }
        if (combat.beginGaleRoar()) {
            roarCooldown = CanglanWarlordAttackRules.GALE_ROAR_COOLDOWN;
        }
    }

    /** Mist trails off the plate; in the spear phase the mask and crest stream wind. */
    private void emitGale() {
        if (!isAlive()) {
            return;
        }
        double height = getBbHeight();
        if (tickCount % 4 == 0) {
            level().addParticle(ParticleTypes.CLOUD,
                    getRandomX(0.85D), getY() + random.nextDouble() * height, getRandomZ(0.85D),
                    0.0D, 0.015D, 0.0D);
        }
        if (tickCount % 6 == 0) {
            level().addParticle(ParticleTypes.END_ROD,
                    getRandomX(0.6D), getY() + random.nextDouble() * height * 0.95D,
                    getRandomZ(0.6D), 0.0D, 0.02D, 0.0D);
        }
        if (isSpearPhase() && tickCount % 3 == 0) {
            double eyeY = getY() + height * 0.855D;
            level().addParticle(ParticleTypes.SWEEP_ATTACK,
                    getX() + random.nextGaussian() * 0.28D, eyeY,
                    getZ() + random.nextGaussian() * 0.28D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * All melee damage comes from {@link CanglanWarlordCombat}, so the shared approach
     * goal must not land an extra untelegraphed hit.
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
        // The cape, banner, buckler and the drawn blade all reach outside the hitbox.
        return super.getBoundingBoxForCulling().inflate(10.0D, 5.0D, 10.0D);
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
        if (!level().isClientSide) combat.cleanup();
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        if (phase() == 1 && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) { ascend(); return; }
        combat.cleanup();
        shiftTicks = 0;
        spawnTicks = 0;
        bossBar.removeAllPlayers();
        triggerAnim("action", "death");
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CanglanWarlordPhase", phase());
        tag.putInt("CanglanWarlordCycle", combat.cycle());
        tag.putInt("CanglanWarlordCooldown", combat.cooldown());
        tag.putInt("CanglanWarlordRoar", roarCooldown);
        tag.putBoolean("CanglanWarlordSpawned", spawned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_PHASE, Math.max(CanglanWarlordAttackRules.PHASE_SWORD,
                Math.min(CanglanWarlordAttackRules.PHASE_SPEAR, tag.getInt("CanglanWarlordPhase"))));
        refreshDimensions();
        // A reload always resumes from a safe stand-still, never mid-swing.
        setCanglanMove(CanglanWarlordAttackRules.IDLE);
        shiftTicks = 0;
        spawnTicks = 0;
        spawned = tag.getBoolean("CanglanWarlordSpawned");
        roarCooldown = Math.max(0, tag.getInt("CanglanWarlordRoar"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 6,
                state -> busy() || shiftTicks > 0 || spawnTicks > 0 || !isAlive()
                        ? PlayState.STOP
                        : state.setAndContinue(RawAnimation.begin()
                                .thenLoop((state.isMoving() || walkAnimation.speed() > .02F) ? "walk" : phase()==2 ? "phantom_idle" : "idle"))));
        AnimationController<CanglanWarlordEntity> action = new AnimationController<>(this, "action", 1,
                state -> PlayState.STOP);
        for (String clip : new String[]{"cleave_left", "cleave_right", "overhead", "thrust",
                "sweep", "spear_throw", "shield_bash", "war_cry", "phase_shift", "spawn",
                "death", "cyclone", "sky_arsenal", "gale_cut", "wind_step"}) {
            action.triggerableAnim(clip, RawAnimation.begin().thenPlay(clip));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
