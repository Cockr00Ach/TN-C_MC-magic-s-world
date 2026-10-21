package com.tnc.tnc.world.wind;

import com.tnc.tnc.magic.TNWindBladeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One committed move at a time, exactly like the ink crane and the stone lancer:
 * the boss never free-forms its attacks, so every swing is telegraphed, and an
 * interrupted boss always leaves the world in a clean state.
 *
 * <p>All damage is server-owned. The client only reads {@link WindWardenEntity#wardenMove()}
 * to play the matching GeckoLib clip and to draw the conjured weapon.
 */
final class WindWardenCombat {
    private final WindWardenEntity boss;
    private int move;
    private int age;
    private int cooldown = 40;
    private int cycle;
    private LivingEntity target;
    private Vec3 lungeFrom;
    private Vec3 lungeTo;
    /** Keys of "target uuid : hit window" already damaged by the current move. */
    private final Set<String> struck = new HashSet<>();

    WindWardenCombat(WindWardenEntity boss) {
        this.boss = boss;
    }

    boolean busy() {
        return move != WindWardenAttackRules.IDLE;
    }

    int cooldown() {
        return cooldown;
    }

    int cycle() {
        return cycle;
    }

    boolean begin(int requested, LivingEntity victim) {
        if (busy() || !boss.isAlive() || victim == null || !victim.isAlive()
                || victim.level() != boss.level()
                || requested == WindWardenAttackRules.IDLE) {
            return false;
        }
        move = requested;
        age = 0;
        target = victim;
        struck.clear();
        lungeFrom = null;
        lungeTo = null;
        boss.getNavigation().stop();
        boss.setWardenMove(move);
        boss.getLookControl().setLookAt(victim, 30.0F, 30.0F);
        boss.triggerAnim("action", animationFor(move));
        boss.level().playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.6F, move >= WindWardenAttackRules.SPEAR_THRUST ? 0.7F : 1.0F);
        if (move == WindWardenAttackRules.LUNGE) {
            Vec3 direction = victim.position().subtract(boss.position()).multiply(1, 0, 1).normalize();
            lungeFrom = boss.position();
            lungeTo = boss.position().add(direction.scale(Math.min(9.0, boss.distanceTo(victim))));
        }
        return true;
    }

    /** Called once per server AI tick. */
    void tick() {
        if (!busy()) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            LivingEntity victim = boss.getTarget();
            if (victim != null && victim.isAlive() && boss.distanceToSqr(victim) < 64.0 * 64.0
                    && boss.hasLineOfSight(victim)) {
                int next = WindWardenAttackRules.choose(boss.phase(), cycle,
                        Math.sqrt(boss.distanceToSqr(victim)));
                if (begin(next, victim)) {
                    cycle++;
                }
            }
            return;
        }
        if (!boss.isAlive() || target == null || !target.isAlive()
                || target.level() != boss.level()) {
            cancel();
            return;
        }
        boss.getLookControl().setLookAt(target, 25.0F, 20.0F);
        if (move == WindWardenAttackRules.LUNGE) {
            driveLunge();
        } else {
            boss.getNavigation().stop();
        }
        if (move == WindWardenAttackRules.BLADE_WAVE && WindWardenAttackRules.waveAt(age)) {
            fireWave();
        }
        if (move == WindWardenAttackRules.SPEAR_THROW && age == WindWardenAttackRules.THROW_RELEASE) {
            throwSpear();
        }
        if (WindWardenAttackRules.connects(move, age)) {
            strike();
        }
        if (++age >= WindWardenAttackRules.duration(move)) {
            finish();
        }
    }

    private void driveLunge() {
        if (lungeFrom == null || lungeTo == null) {
            return;
        }
        double t = Math.min(1.0, age / (double) WindWardenAttackRules.LUNGE_HIT);
        double eased = t * t * (3.0 - 2.0 * t);
        Vec3 next = lungeFrom.lerp(lungeTo, eased);
        // A lunge never tunnels through terrain: stop at the first collision.
        var hit = boss.level().clip(new net.minecraft.world.level.ClipContext(
                boss.position().add(0, boss.getBbHeight() * 0.5, 0),
                next.add(0, boss.getBbHeight() * 0.5, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, boss));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            next = hit.getLocation().subtract(0, boss.getBbHeight() * 0.5, 0);
        }
        boss.setPos(next.x, boss.getY(), next.z);
        boss.setDeltaMovement(Vec3.ZERO);
    }

    private void strike() {
        double reach = WindWardenAttackRules.reach(move);
        double cone = Math.cos(Math.toRadians(WindWardenAttackRules.coneDegrees(move)));
        Vec3 forward = Vec3.directionFromRotation(0.0F, boss.getYRot());
        // Multi-hit moves get one hit per window, so a sweep cannot double-dip.
        String window = String.valueOf(age);
        float damage = (float) boss.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * WindWardenAttackRules.damageFactor(move);
        List<LivingEntity> victims = boss.level().getEntitiesOfClass(LivingEntity.class,
                boss.getBoundingBox().inflate(reach), candidate -> candidate != boss
                        && candidate.isAlive() && !candidate.isSpectator()
                        && !boss.isAlliedTo(candidate));
        for (LivingEntity victim : victims) {
            if (!struck.add(victim.getUUID() + ":" + window)) {
                continue;
            }
            double dx = victim.getX() - boss.getX();
            double dz = victim.getZ() - boss.getZ();
            double distance = Math.hypot(dx, dz);
            if (distance > reach) {
                continue;
            }
            if (distance > 0.01 && (dx * forward.x + dz * forward.z) / distance < cone) {
                continue;
            }
            if (!boss.hasLineOfSight(victim)) {
                continue;
            }
            victim.hurt(move == WindWardenAttackRules.CYCLONE
                    ? boss.level().damageSources().mobAttack(boss)
                    : boss.level().damageSources().mobAttack(boss), damage);
            Vec3 push = new Vec3(dx, 0, dz).normalize().scale(
                    move == WindWardenAttackRules.SPEAR_THRUST ? 0.05 : 0.22);
            victim.push(push.x, move == WindWardenAttackRules.OVERHEAD ? 0.35 : 0.08, push.z);
            victim.hurtMarked = true;
            if (boss.level() instanceof ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        4, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    private void fireWave() {
        if (!(boss.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0)
                .subtract(boss.position().add(0, boss.getBbHeight() * 0.62, 0)).normalize();
        float damage = (float) boss.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * WindWardenAttackRules.WAVE_DAMAGE;
        TNWindBladeEntity blade = TNWindBladeEntity.shoot(server,
                boss.position().add(0, boss.getBbHeight() * 0.62, 0).add(aim.scale(1.6)),
                aim, 1.05D, damage, boss, 2.6D);
        if (blade != null) {
            blade.setSpinRate(26.0F);
        }
        boss.level().playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.1F, 1.5F);
    }

    /** The spear leaves the hand and becomes a real, dodgeable projectile. */
    private void throwSpear() {
        if (!(boss.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0)
                .subtract(boss.position().add(0, boss.getBbHeight() * 0.62, 0)).normalize();
        float damage = (float) boss.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * WindWardenAttackRules.THROW_DAMAGE;
        TNWindBladeEntity spear = TNWindBladeEntity.shoot(server,
                boss.position().add(0, boss.getBbHeight() * 0.62, 0).add(aim.scale(1.8)),
                aim, 1.9D, damage, boss, 4.2D);
        if (spear != null) {
            spear.setSpinRate(0.0F);
        }
        boss.level().playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.HOSTILE, 1.8F, 0.8F);
    }

    private void finish() {
        boolean wasSweep = move == WindWardenAttackRules.SPEAR_SWEEP
                || move == WindWardenAttackRules.CYCLONE;
        reset();
        cooldown = WindWardenAttackRules.recovery(boss.phase()) + (wasSweep ? 6 : 0);
    }

    /** Interruption, death and save-reload all funnel through here. */
    void cancel() {
        reset();
        cooldown = Math.max(20, cooldown);
    }

    private void reset() {
        move = WindWardenAttackRules.IDLE;
        age = 0;
        target = null;
        lungeFrom = null;
        lungeTo = null;
        struck.clear();
        boss.setWardenMove(WindWardenAttackRules.IDLE);
    }

    static String animationFor(int move) {
        return switch (move) {
            case WindWardenAttackRules.SLASH_LEFT -> "slash_left";
            case WindWardenAttackRules.SLASH_RIGHT, WindWardenAttackRules.LUNGE -> "slash_right";
            case WindWardenAttackRules.OVERHEAD -> "overhead";
            case WindWardenAttackRules.BLADE_WAVE -> "slash_left";
            case WindWardenAttackRules.SPEAR_THRUST -> "spear_thrust";
            case WindWardenAttackRules.SPEAR_SWEEP, WindWardenAttackRules.CYCLONE -> "spin_sweep";
            case WindWardenAttackRules.SPEAR_THROW -> "spear_throw";
            default -> "idle";
        };
    }
}
