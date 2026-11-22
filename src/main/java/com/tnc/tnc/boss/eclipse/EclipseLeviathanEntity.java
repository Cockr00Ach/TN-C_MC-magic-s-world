package com.tnc.tnc.boss.eclipse;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Short-lived black-sea pursuer. The server owns motion, action timing and the damaging mouth sweep. */
public final class EclipseLeviathanEntity extends PathfinderMob {
    public enum Phase {
        EMERGE(40), PATROL(0), WINDUP(32), LUNGE(14), RECOVER(36), RETREAT(48);

        private final int duration;
        Phase(int duration) { this.duration = duration; }
    }

    private static final Phase[] PHASES = Phase.values();
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(
            EclipseLeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(
            EclipseLeviathanEntity.class, EntityDataSerializers.LONG);
    public static final float MODEL_SCALE = 3.4F;
    private static final double HEAD_OFFSET = 18.0 / 16.0 * MODEL_SCALE;
    private static final double BITE_RADIUS = 2.9 * MODEL_SCALE;
    private static final double PATROL_Y = EclipseHunt.SEA_SURFACE + .2;
    private static final double ATTACK_RANGE = 28;
    private static final int LOST_TARGET_GRACE = 20;
    private static final int ACTIVE_LIFETIME = 20 * 90;
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.tnc.eclipse_leviathan"), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> struck = new HashSet<>();
    private final Set<ServerPlayer> trackers = new HashSet<>();
    private UUID quarry;
    private int lostTargetTicks;
    private double phaseHeight;
    private Vec3 lungeStep = Vec3.ZERO;
    private long expiresAt = Long.MAX_VALUE;

    public EclipseLeviathanEntity(EntityType<? extends EclipseLeviathanEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 360).add(Attributes.ARMOR, 12)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, EclipseHunt.TRACK_RANGE)
                .add(Attributes.ATTACK_DAMAGE, 14);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, Phase.EMERGE.ordinal());
        entityData.define(PHASE_START, 0L);
    }

    public Phase phase() { return PHASES[entityData.get(PHASE)]; }
    public long phaseStartTick() { return entityData.get(PHASE_START); }
    public int phaseDuration() { return phase().duration; }
    public float phaseAge(float partialTick) {
        return Math.max(0, level().getGameTime() - phaseStartTick() + partialTick);
    }

    long expiresAt() { return expiresAt; }

    void emergeAt(Vec3 at, ServerPlayer target) {
        setPos(at);
        face(target.position(), 360);
        yRotO = getYRot();
        yBodyRotO = yBodyRot;
        quarry = target.getUUID();
        expiresAt = level().getGameTime() + ACTIVE_LIFETIME + Phase.RETREAT.duration;
        enter(Phase.EMERGE);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3, .58F);
    }

    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public void travel(Vec3 input) {}

    @Override public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - 52, getY() - 18, getZ() - 52, getX() + 52, getY() + 18, getZ() + 52);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        trackers.add(player);
        updateBar(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        trackers.remove(player);
        bossBar.removePlayer(player);
    }

    @Override public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        trackers.clear();
        super.remove(reason);
    }

    private void updateBar(ServerPlayer player) {
        if (distanceToSqr(player) < 56 * 56) bossBar.addPlayer(player);
        else bossBar.removePlayer(player);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sea)) return;
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (!isAlive()) return;
        if (tickCount % 10 == 0) trackers.forEach(this::updateBar);
        if (phase() == Phase.RETREAT) {
            retreat(sea, phaseAge(0));
            return;
        }
        if (sea.getGameTime() >= expiresAt - Phase.RETREAT.duration) {
            enter(Phase.RETREAT);
            return;
        }

        ServerPlayer target = quarry(sea);
        if (target == null) {
            if (++lostTargetTicks >= LOST_TARGET_GRACE) enter(Phase.RETREAT);
            else if (phase() == Phase.WINDUP || phase() == Phase.LUNGE) enter(Phase.RECOVER);
            return;
        }
        lostTargetTicks = 0;
        float age = phaseAge(0);
        switch (phase()) {
            case EMERGE -> emerge(sea, target, age);
            case PATROL -> patrol(sea, target);
            case WINDUP -> windup(sea, target, age);
            case LUNGE -> lunge(sea, age);
            case RECOVER -> recover(sea, age);
            case RETREAT -> throw new IllegalStateException("Retreat already handled");
        }
    }

    private void enter(Phase next) {
        entityData.set(PHASE, next.ordinal());
        entityData.set(PHASE_START, level().getGameTime());
        phaseHeight = getY();
    }

    private ServerPlayer quarry(ServerLevel sea) {
        if (quarry != null && sea.getPlayerByUUID(quarry) instanceof ServerPlayer player && canChase(player)) {
            return player;
        }
        if (phase() == Phase.WINDUP || phase() == Phase.LUNGE) return null;
        ServerPlayer closest = null;
        double distance = EclipseHunt.TRACK_RANGE * EclipseHunt.TRACK_RANGE;
        for (ServerPlayer candidate : sea.players()) {
            double toCandidate = distanceToSqr(candidate);
            if (toCandidate < distance && canChase(candidate)) {
                closest = candidate;
                distance = toCandidate;
            }
        }
        quarry = closest == null ? null : closest.getUUID();
        return closest;
    }

    private boolean canChase(ServerPlayer candidate) {
        return distanceToSqr(candidate) <= EclipseHunt.TRACK_RANGE * EclipseHunt.TRACK_RANGE
                && EclipseHunt.huntable(candidate);
    }

    private void emerge(ServerLevel sea, ServerPlayer target, float age) {
        face(target.position(), 4);
        if (!moveInSea(sea, position().with(Direction.Axis.Y, Mth.lerp(ease(age), phaseHeight, PATROL_Y)))) {
            enter(Phase.RETREAT);
            return;
        }
        if (age == 32) {
            playSound(SoundEvents.GENERIC_SPLASH, 3, .65F);
            sea.sendParticles(ParticleTypes.SPLASH, getX(), EclipseHunt.SEA_SURFACE, getZ(), 35, 5, .7, 5, .3);
        }
        if (age >= phaseDuration()) enter(Phase.PATROL);
    }

    private void patrol(ServerLevel sea, ServerPlayer target) {
        float turn = face(target.position(), 4);
        double horizontal = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (horizontal <= ATTACK_RANGE && Math.abs(turn) < 12) {
            enter(Phase.WINDUP);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.4F, .62F);
            return;
        }
        if (horizontal > ATTACK_RANGE) {
            Vec3 next = position().add(forward().scale(.68));
            double y = getY() + Mth.clamp(PATROL_Y - getY(), -.12, .12);
            moveInSea(sea, next.with(Direction.Axis.Y, y));
        }
    }

    private void windup(ServerLevel sea, ServerPlayer target, float age) {
        // Lock aim early enough that a swimmer can dodge sideways during the final tell.
        if (age < 16) {
            face(target.position(), 5);
            Vec3 aim = target.getBoundingBox().getCenter().subtract(mouth());
            double vertical = Mth.clamp(aim.y / Math.max(4, Math.hypot(aim.x, aim.z)), -.4, .14);
            lungeStep = forward().add(0, vertical, 0).normalize().scale(2.0);
        }
        if ((int) age % 5 == 0) {
            Vec3 mouth = mouth();
            sea.sendParticles(ParticleTypes.REVERSE_PORTAL, mouth.x, mouth.y, mouth.z, 20, 3.7, 2.7, 3.7, .25);
        }
        if (age >= phaseDuration()) {
            struck.clear();
            enter(Phase.LUNGE);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.8F, .72F);
        }
    }

    private void lunge(ServerLevel sea, float age) {
        Vec3 before = mouth();
        Vec3 next = position().add(lungeStep);
        if (age >= phaseDuration() || !moveInSea(sea, next.with(Direction.Axis.Y, Mth.clamp(next.y, 40, 50.5)))) {
            enter(Phase.RECOVER);
            return;
        }
        Vec3 after = mouth();
        for (ServerPlayer player : sea.players()) {
            if (!canChase(player) || !sweepHits(before, after, player.getBoundingBox()) || !struck.add(player.getUUID())) continue;
            if (player.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                Vec3 forward = forward();
                player.knockback(1.3, -forward.x, -forward.z);
            }
        }
    }

    static boolean sweepHits(Vec3 from, Vec3 to, AABB victim) {
        Vec3 center = victim.getCenter();
        Vec3 path = to.subtract(from);
        double progress = path.lengthSqr() == 0 ? 0 : Mth.clamp(center.subtract(from).dot(path) / path.lengthSqr(), 0, 1);
        double radius = BITE_RADIUS + victim.getXsize() * .5;
        return center.distanceToSqr(from.add(path.scale(progress))) <= radius * radius;
    }

    private void recover(ServerLevel sea, float age) {
        double y = getY() + Mth.clamp(PATROL_Y - getY(), -.1, .1);
        Vec3 next = position().add(forward().scale(.32 * (1 - ease(age))));
        moveInSea(sea, next.with(Direction.Axis.Y, y));
        if (age >= phaseDuration()) enter(Phase.PATROL);
    }

    private void retreat(ServerLevel sea, float age) {
        if (age >= phaseDuration()) {
            discard();
            return;
        }
        moveInSea(sea, position().with(Direction.Axis.Y, Mth.lerp(ease(age), phaseHeight, EclipseHunt.DEEP_ENTRY_Y)));
    }

    private double ease(float age) {
        double t = Mth.clamp(age / phaseDuration(), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private boolean moveInSea(ServerLevel sea, Vec3 at) {
        if (!EclipseHunt.room(sea, this, at)) return false;
        setPos(at);
        return true;
    }

    private Vec3 mouth() { return position().add(forward().scale(HEAD_OFFSET)); }
    private Vec3 forward() { return Vec3.directionFromRotation(0, getYRot()); }

    private float face(Vec3 point, float maxTurn) {
        Vec3 direction = point.subtract(position());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float turn = Mth.wrapDegrees(targetYaw - getYRot());
        setYRot(getYRot() + Mth.clamp(turn, -maxTurn, maxTurn));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        return turn;
    }
}
