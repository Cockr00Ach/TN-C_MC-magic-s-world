package com.tnc.tnc.world.ink;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Transient synchronized phantom: pursuit, long wing, giant dive, or ground ritual. */
public final class InkCranePhantomEntity extends Entity implements GeoEntity {
    public static final int PURSUIT = 0, LONG_WINGS = 1, GIANT_DIVE = 2, RITUAL = 3, DRAKE_CHARGE = 4;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(InkCranePhantomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(InkCranePhantomEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> FINISHED = SynchedEntityData.defineId(InkCranePhantomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> CHARGE_START = SynchedEntityData.defineId(InkCranePhantomEntity.class, EntityDataSerializers.LONG);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId, targetId;
    private Vec3 origin = Vec3.ZERO, destination = Vec3.ZERO;
    private final Set<UUID> struck = new HashSet<>();
    private final Deque<Vec3> trail = new ArrayDeque<>();
    private int fadeAt = Integer.MAX_VALUE, lerpSteps;
    private int lastSweepPass = -1;
    private Vec3 lerpTarget = Vec3.ZERO;

    public InkCranePhantomEntity(EntityType<? extends InkCranePhantomEntity> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }

    public static InkCranePhantomEntity spawn(InkCraneBossEntity owner, LivingEntity victim,
                                              int mode, Vec3 position, Vec3 motionOrDestination) {
        InkCranePhantomEntity effect = InkBossRegistration.INK_CRANE_PHANTOM.create(owner.level());
        if (effect == null) return null;
        effect.entityData.set(MODE, mode);
        effect.entityData.set(START, owner.level().getGameTime());
        effect.entityData.set(CHARGE_START, owner.level().getGameTime());
        effect.ownerId = owner.getUUID(); effect.targetId = victim.getUUID();
        effect.origin = position; effect.destination = motionOrDestination;
        effect.moveTo(position.x, position.y, position.z, owner.getYRot(), 0);
        if (mode == PURSUIT || mode == DRAKE_CHARGE) {
            effect.setDeltaMovement(motionOrDestination);
            effect.faceVelocity();
        }
        owner.level().addFreshEntity(effect);
        return effect;
    }

    public int mode() { return entityData.get(MODE); }
    public int age() { return (int) Math.max(0, level().getGameTime() - entityData.get(START)); }
    public boolean finished() { return entityData.get(FINISHED); }
    public int chargeAge() { return entityData.get(CHARGE_START) < 0 ? -1
            : (int) Math.max(0, level().getGameTime() - entityData.get(CHARGE_START)); }
    public void waitForLanding() { entityData.set(CHARGE_START, -1L); }
    public void startRitualCharge() { entityData.set(CHARGE_START, level().getGameTime()); }
    public Deque<Vec3> trail() { return trail; }
    public boolean ownedBy(Entity entity) { return entity.getUUID().equals(ownerId); }

    @Override protected void defineSynchedData() {
        entityData.define(MODE, PURSUIT); entityData.define(START, 0L); entityData.define(FINISHED, false);
        entityData.define(CHARGE_START, 0L);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(position().lerp(lerpTarget, 1.0 / lerpSteps--));
            } else if (!finished() && (mode() == PURSUIT || mode() == DRAKE_CHARGE)) setPos(position().add(getDeltaMovement()));
            if (mode() == PURSUIT || mode() == GIANT_DIVE || mode() == DRAKE_CHARGE) {
                trail.addFirst(position());
                while (trail.size() > 45) trail.removeLast();
            }
            return;
        }
        InkCraneBossEntity owner = owner();
        if (owner == null || !owner.isAlive()) {
            // A harmless finished painting may outlive its caster; never repeat damage.
            if(mode()!=RITUAL || chargeAge()<InkCraneAttackRules.BALL_EXPLODE
                    || chargeAge()>=InkCraneAttackRules.RITUAL_LIFE) discard();
            return;
        }
        int t = age();
        if (mode() == PURSUIT || mode() == DRAKE_CHARGE) pursuit(owner, t);
        else if (mode() == LONG_WINGS) sweep(owner, t);
        else if (mode() == GIANT_DIVE) dive(owner, t);
        else ritual(owner, t);
    }

    private InkCraneBossEntity owner() {
        Entity entity = ownerId == null ? null : ((ServerLevel) level()).getEntity(ownerId);
        return entity instanceof InkCraneBossEntity crane ? crane : null;
    }

    private boolean canHit(InkCraneBossEntity owner, Entity candidate) {
        return candidate instanceof LivingEntity living && living.isAlive() && candidate != owner
                && !owner.isAlliedTo(candidate) && !candidate.isSpectator();
    }

    private void pursuit(InkCraneBossEntity owner, int t) {
        if (finished()) { if (t >= fadeAt + 15) discard(); return; }
        Entity victim = targetId == null ? null : ((ServerLevel) level()).getEntity(targetId);
        if (victim == null || !victim.isAlive() || distanceToSqr(victim) > 80 * 80) {
            victim = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48),
                    e -> canHit(owner, e) && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()))
                    .stream().filter(e -> level().clip(new ClipContext(position(), e.getEyePosition(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS)
                    .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            targetId = victim == null ? null : victim.getUUID();
        }
        boolean drake = mode() == DRAKE_CHARGE;
        double speed = drake ? 1.3 : 0.95;
        if (victim != null && victim.isAlive()) {
            Vec3 desired = victim.position().add(0, victim.getBbHeight() * 0.6, 0)
                    .subtract(position()).normalize().scale(speed);
            setDeltaMovement(getDeltaMovement().lerp(desired, drake ? .10 : .18).normalize().scale(speed));
        }
        Vec3 from = position(), to = from.add(getDeltaMovement());
        var block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level(), this, from, limit,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(drake ? 3.5 : 0.65), e -> canHit(owner, e),
                drake ? 3.0F : 0.4F);
        if (hit != null) {
            hit.getEntity().hurt(damageSources().mobAttack(owner), drake ? 30.0F : 10.0F);
            if (drake && hit.getEntity() instanceof LivingEntity struck) {
                var seal = spawn(owner, struck, RITUAL, struck.position(), Vec3.ZERO);
                playSound(SoundEvents.GENERIC_EXPLODE, 2, 0.6F);
            }
            setPos(hit.getLocation()); finish(t);
        } else {
            setPos(limit);
            if (block.getType() != HitResult.Type.MISS || t >= 110) finish(t);
        }
        faceVelocity();
    }

    private void faceVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 0.0001) return;
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())));
    }

    private void sweep(InkCraneBossEntity owner, int t) {
        if (owner.inkMove() != InkCraneAttackRules.SWEEP) { discard(); return; }
        Vec3 forward = Vec3.directionFromRotation(0, owner.getYRot());
        setPos(owner.position().add(forward.scale(-10)).add(0, 32.3, 0));
        setYRot(owner.getYRot());
        int castAge = t + InkCraneAttackRules.ROAR_TICKS;
        int pass = InkCraneAttackRules.sweepPass(castAge);
        if (pass >= 0) {
            if (lastSweepPass != pass) { struck.clear(); lastSweepPass = pass; }
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(InkCraneAttackRules.SWEEP_REACH, 1.5, InkCraneAttackRules.SWEEP_REACH), e -> canHit(owner, e))) {
                Vec3 delta = victim.position().subtract(owner.position());
                if (InkCraneAttackRules.inSweep(delta.x, delta.z, forward.x, forward.z)
                        && owner.hasLineOfSight(victim) && struck.add(victim.getUUID())) {
                    victim.hurt(damageSources().mobAttack(owner), 28.0F);
                    victim.knockback(1.4, -delta.x, -delta.z);
                }
            }
        }
        if (castAge >= InkCraneAttackRules.duration(InkCraneAttackRules.SWEEP)) discard();
    }

    private void dive(InkCraneBossEntity owner, int t) {
        double progress = Math.min(1, t / (double) InkCraneAttackRules.DIVE_TICKS);
        setPos(origin.lerp(destination.add(0, 68, 0), progress * progress));
        if (t == InkCraneAttackRules.DIVE_TICKS) {
            hurtArea(owner, destination.add(0, 1, 0), 6.0, 32);
            playSound(SoundEvents.GENERIC_EXPLODE, 2.8F, 0.65F);
        }
        if (t >= InkCraneAttackRules.DIVE_TICKS + 8) discard();
    }

    private void ritual(InkCraneBossEntity owner, int t) {
        t = chargeAge();
        if (t < 0) {
            if (owner.inkMove() != InkCraneAttackRules.SKY || age() > 100) discard();
            return;
        }
        if (t == InkCraneAttackRules.BALL_EXPLODE) {
            hurtArea(owner, position().add(0, 2, 0), InkCraneAttackRules.BLAST_RADIUS, 38);
            playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
        }
        if(t>=InkCraneAttackRules.DIVE_TICKS && t<InkCraneAttackRules.BALL_EXPLODE) pullNearby(owner,t);
        if (t >= InkCraneAttackRules.RITUAL_LIFE) discard();
    }

    private void pullNearby(InkCraneBossEntity owner,int age) {
        Vec3 center=position().add(0,1,0);
        for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(InkCraneAttackRules.PULL_RADIUS,18,InkCraneAttackRules.PULL_RADIUS),
                e->canHit(owner,e) && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()))) {
            Vec3 delta=center.subtract(victim.position()).multiply(1,0,1);
            double distance=delta.length();
            if(distance<.9 || distance>=InkCraneAttackRules.PULL_RADIUS)continue;
            if(level().clip(new ClipContext(center,victim.getEyePosition(),ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            Vec3 direction=delta.scale(1/distance),velocity=victim.getDeltaMovement();
            double force=InkCraneAttackRules.pullAcceleration(age,distance,velocity.dot(direction));
            if(force<=0)continue;
            victim.setDeltaMovement(velocity.add(direction.scale(force)));
            victim.hasImpulse=true;victim.hurtMarked=true;
        }
    }

    private void hurtArea(InkCraneBossEntity owner, Vec3 center, double radius, float damage) {
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius), e -> canHit(owner, e))) {
            if (victim.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius
                    && level().clip(new ClipContext(center, victim.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
                victim.hurt(damageSources().mobAttack(owner), damage);
            }
        }
    }

    private void finish(int t) { entityData.set(FINISHED, true); fadeAt = t; setDeltaMovement(Vec3.ZERO); }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpTarget = new Vec3(x, y, z); lerpSteps = teleport ? 1 : Math.max(1, steps);
        setYRot(yaw); setXRot(pitch);
    }
    @Override public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(mode() == GIANT_DIVE || mode() == DRAKE_CHARGE
                || mode() == LONG_WINGS ? 170 : mode() == PURSUIT ? 80 : 128);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        return mode()==RITUAL ? distance<256*256 : super.shouldRenderAtSqrDistance(distance);
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(
                        mode() == DRAKE_CHARGE ? "idle" : mode() == LONG_WINGS ? "avatar_sweep" : "phantom_flight"))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
