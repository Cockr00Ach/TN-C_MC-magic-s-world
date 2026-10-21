package com.tnc.tnc.world.wind;

import com.tnc.tnc.magic.TNWindBladeEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One committed move at a time, exactly like the wind warden and the stone lancer.
 *
 * <p>The warlord's two phases use two different weapons, so the same state machine
 * drives a sword duel early and a spear duel late: the thrust lunges, the sweep
 * spins, and the spear throw lets the whole spear leave the hand as a real
 * projectile ({@link TNWindBladeEntity}) that the player can dodge.
 *
 * <p>Every point of damage is server-owned and gated by
 * {@link CanglanWarlordAttackRules#connects(int, int)}, so nothing can double-dip inside
 * one window and the client never decides a hit. An interrupted boss (death,
 * reload, dimension change) always leaves the world in a clean state.
 */
final class CanglanWarlordCombat {
    private final CanglanWarlordEntity boss;
    private int move;
    private int age;
    private int cooldown = 45;
    private int cycle;
    private LivingEntity target;
    private Vec3 dashFrom;
    private Vec3 dashTo;
    /** Keys of "target uuid : hit window" already damaged by the current move. */
    private final Set<String> struck = new HashSet<>();

    CanglanWarlordCombat(CanglanWarlordEntity boss) {
        this.boss = boss;
    }

    boolean busy() {
        return move != CanglanWarlordAttackRules.IDLE;
    }

    int cooldown() {
        return cooldown;
    }

    int cycle() {
        return cycle;
    }

    int move() {
        return move;
    }

    int age() {
        return age;
    }

    boolean begin(int requested, LivingEntity victim) {
        if (busy() || !boss.isAlive() || victim == null || !victim.isAlive()
                || victim.level() != boss.level() || requested == CanglanWarlordAttackRules.IDLE) {
            return false;
        }
        move = requested;
        age = 0;
        target = victim;
        struck.clear();
        dashFrom = null;
        dashTo = null;
        boss.getNavigation().stop();
        boss.setCanglanMove(move);
        boss.getLookControl().setLookAt(victim, 40.0F, 30.0F);
        boss.triggerAnim("action", CanglanWarlordAttackRules.animationFor(move));
        boss.level().playSound(null, boss.blockPosition(), windupSound(move),
                SoundSource.HOSTILE, 2.2F, windupPitch(move));
        if (move == CanglanWarlordAttackRules.THRUST) {
            Vec3 direction = victim.position().subtract(boss.position())
                    .multiply(1.0D, 0.0D, 1.0D);
            if (direction.lengthSqr() > 0.01D) {
                direction = direction.normalize();
                dashFrom = boss.position();
                dashTo = boss.position().add(direction.scale(Math.min(
                        CanglanWarlordAttackRules.THRUST_DASH, boss.distanceTo(victim))));
            }
        }
        return true;
    }

    /** Starts the gale roar: the phase-two entrance and the late-fight self buff. */
    boolean beginGaleRoar() {
        if (busy() || !boss.isAlive()) {
            return false;
        }
        LivingEntity victim = boss.getTarget();
        if (victim == null || !victim.isAlive() || victim.level() != boss.level()) {
            return false;
        }
        return begin(CanglanWarlordAttackRules.GALE_ROAR, victim);
    }

    /** Called once per server AI tick. */
    void tick() {
        if (!busy()) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            LivingEntity victim = boss.getTarget();
            if (victim != null && victim.isAlive()
                    && boss.distanceToSqr(victim) < (double) CanglanWarlordAttackRules.ENGAGE_RANGE
                    * CanglanWarlordAttackRules.ENGAGE_RANGE
                    && boss.hasLineOfSight(victim)) {
                int next = CanglanWarlordAttackRules.choose(boss.phase(), cycle,
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
        boss.getLookControl().setLookAt(target, 30.0F, 20.0F);
        if (move == CanglanWarlordAttackRules.THRUST) {
            driveDash();
        } else {
            boss.getNavigation().stop();
        }
        if (move == CanglanWarlordAttackRules.GALE_ROAR
                && age == CanglanWarlordAttackRules.GALE_ROAR_HIT) {
            roar();
        }
        if (move == CanglanWarlordAttackRules.SPEAR_THROW
                && age == CanglanWarlordAttackRules.SPEAR_THROW_HIT) {
            throwSpear();
        }
        if (CanglanWarlordAttackRules.connects(move, age)) {
            strike();
        }
        if (++age >= CanglanWarlordAttackRules.duration(move)) {
            finish();
        }
    }

    private void driveDash() {
        if (dashFrom == null || dashTo == null) {
            return;
        }
        double t = Math.min(1.0D, age / (double) CanglanWarlordAttackRules.THRUST_HIT);
        double eased = t * t * (3.0D - 2.0D * t);
        Vec3 next = dashFrom.lerp(dashTo, eased);
        // A 6-block boss must never tunnel through a wall to finish its attack.
        var hit = boss.level().clip(new ClipContext(
                boss.position().add(0.0D, boss.getBbHeight() * 0.5D, 0.0D),
                next.add(0.0D, boss.getBbHeight() * 0.5D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        if (hit.getType() != HitResult.Type.MISS) {
            next = hit.getLocation().subtract(0.0D, boss.getBbHeight() * 0.5D, 0.0D);
        }
        boss.setPos(next.x, boss.getY(), next.z);
        boss.setDeltaMovement(Vec3.ZERO);
    }

    private void strike() {
        double reach = CanglanWarlordAttackRules.reach(move);
        double cone = Math.cos(Math.toRadians(CanglanWarlordAttackRules.coneDegrees(move)));
        Vec3 forward = Vec3.directionFromRotation(0.0F, boss.getYRot());
        String window = String.valueOf(age);
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * CanglanWarlordAttackRules.damageFactor(move, boss.phase());
        double knockback = CanglanWarlordAttackRules.knockback(move);
        double launch = CanglanWarlordAttackRules.launch(move);
        double chestY = boss.getY() + boss.getBbHeight() * 0.55D;
        List<LivingEntity> victims = boss.level().getEntitiesOfClass(LivingEntity.class,
                boss.getBoundingBox().inflate(reach, CanglanWarlordAttackRules.VERTICAL_REACH, reach),
                candidate -> candidate != boss && candidate.isAlive()
                        && !candidate.isSpectator() && !boss.isAlliedTo(candidate));
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
            if (Math.abs(victim.getY() + victim.getBbHeight() * 0.5D - chestY)
                    > CanglanWarlordAttackRules.VERTICAL_REACH) {
                continue;
            }
            if (distance > 0.01D && (dx * forward.x + dz * forward.z) / distance < cone) {
                continue;
            }
            if (!boss.hasLineOfSight(victim)) {
                continue;
            }
            victim.hurt(boss.level().damageSources().mobAttack(boss), damage);
            if (distance > 0.01D) {
                Vec3 push = new Vec3(dx, 0.0D, dz).normalize().scale(knockback);
                victim.push(push.x, launch, push.z);
                victim.hurtMarked = true;
            }
            if (boss.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55D,
                        victim.getZ(), 8, 0.4D, 0.4D, 0.4D, 0.02D);
                server.sendParticles(ParticleTypes.CLOUD,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55D,
                        victim.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.08D);
                server.sendParticles(ParticleTypes.END_ROD,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.55D,
                        victim.getZ(), 4, 0.25D, 0.25D, 0.25D, 0.10D);
            }
        }
    }

    /**
     * The roar peak: a ring of wind leaves the boss. It buffs the warlord (speed
     * more than strength -- wind is about reach and tempo) and shoves everything
     * standing in range, then starts its own cooldown.
     */
    private void roar() {
        boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                CanglanWarlordAttackRules.GALE_ROAR_BUFF_LENGTH, 1));
        boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                CanglanWarlordAttackRules.GALE_ROAR_BUFF_LENGTH, 0));
        boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                CanglanWarlordAttackRules.GALE_ROAR_BUFF_LENGTH / 2, 0));
        boss.setRoarCooldown(CanglanWarlordAttackRules.GALE_ROAR_COOLDOWN);
        boolean spear = boss.phase() == CanglanWarlordAttackRules.PHASE_SPEAR;
        boss.level().playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 3.0F, spear ? 0.62F : 0.86F);
        if (boss.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD,
                    boss.getX(), boss.getY() + boss.getBbHeight() * 0.55D, boss.getZ(),
                    70, 2.6D, 1.0D, 2.6D, 0.22D);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    boss.getX(), boss.getY() + boss.getBbHeight() * 0.5D, boss.getZ(),
                    26, 3.2D, 0.4D, 3.2D, 0.02D);
            server.sendParticles(ParticleTypes.END_ROD,
                    boss.getX(), boss.getY() + boss.getBbHeight() * 0.7D, boss.getZ(),
                    18, 1.4D, 0.8D, 1.4D, 0.12D);
        }
    }

    /**
     * The spear leaves the hand. Same projectile the wind warden and the player's
     * own arsenal spells use, so it inherits their damage falloff and hit rules.
     */
    private void throwSpear() {
        if (!(boss.level() instanceof ServerLevel server) || target == null) {
            return;
        }
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(boss.position().add(0.0D, boss.getBbHeight() * 0.62D, 0.0D))
                .normalize();
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * CanglanWarlordAttackRules.damageFactor(CanglanWarlordAttackRules.SPEAR_THROW,
                        boss.phase());
        TNWindBladeEntity spear = TNWindBladeEntity.shoot(server,
                boss.position().add(0.0D, boss.getBbHeight() * 0.62D, 0.0D).add(aim.scale(2.2D)),
                aim, CanglanWarlordAttackRules.SPEAR_THROW_SPEED, damage, boss,
                CanglanWarlordAttackRules.SPEAR_THROW_SIZE);
        if (spear != null) {
            spear.setSpinRate(0.0F);
        }
        boss.level().playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    private void finish() {
        boolean heavy = move == CanglanWarlordAttackRules.SWEEP
                || move == CanglanWarlordAttackRules.GALE_ROAR
                || move == CanglanWarlordAttackRules.OVERHEAD
                || move == CanglanWarlordAttackRules.SPEAR_THROW;
        reset();
        cooldown = CanglanWarlordAttackRules.recovery(boss.phase()) + (heavy ? 8 : 0);
    }

    /** Interruption, death and save-reload all funnel through here. */
    void cancel() {
        reset();
        cooldown = Math.max(20, cooldown);
    }

    private void reset() {
        move = CanglanWarlordAttackRules.IDLE;
        age = 0;
        target = null;
        dashFrom = null;
        dashTo = null;
        struck.clear();
        boss.setCanglanMove(CanglanWarlordAttackRules.IDLE);
    }

    private static net.minecraft.sounds.SoundEvent windupSound(int move) {
        return switch (move) {
            case CanglanWarlordAttackRules.OVERHEAD -> SoundEvents.ANVIL_LAND;
            case CanglanWarlordAttackRules.SHIELD_BASH -> SoundEvents.SHIELD_BLOCK;
            case CanglanWarlordAttackRules.SPEAR_THROW -> SoundEvents.TRIDENT_THROW;
            case CanglanWarlordAttackRules.GALE_ROAR -> SoundEvents.ENDER_DRAGON_GROWL;
            default -> SoundEvents.IRON_GOLEM_ATTACK;
        };
    }

    private static float windupPitch(int move) {
        return switch (move) {
            case CanglanWarlordAttackRules.OVERHEAD -> 0.55F;
            case CanglanWarlordAttackRules.SPEAR_THROW -> 1.25F;
            case CanglanWarlordAttackRules.GALE_ROAR -> 0.9F;
            default -> 0.7F;
        };
    }
}
