package com.tnc.tnc.world.ink;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One committed move at a time; interruption always restores flight state. */
final class InkCraneAttacks {
    private final InkCraneBossEntity boss;
    private int move, age, nextMove = 1, cooldown = 70;
    private LivingEntity target;
    private Vec3 home;
    private Vec3 landing;
    private InkCranePhantomEntity landingSeal;
    private boolean landed;
    private boolean previousGravity;

    InkCraneAttacks(InkCraneBossEntity boss) { this.boss = boss; }

    boolean begin(int requested, LivingEntity victim) {
        if (move != 0 || !boss.isAlive() || victim == null || !victim.isAlive()
                || victim.level() != boss.level() || requested < 1 || requested > 4) return false;
        move = requested; age = 0; target = victim; home = boss.position();
        landing = null; landingSeal = null; landed = false;
        previousGravity = boss.isNoGravity();
        boss.setInkMove(move);
        boss.getNavigation().stop();
        if (move == InkCraneAttackRules.SKY) {
            boss.setNoGravity(true);boss.setSkyShroudVisible(true);
        }
        boss.triggerAnim("action", move == 2 ? "ink_roar" : move == 3 ? "sky_ascent" : "ink_flap");
        boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.8F, 0.55F);
        return true;
    }

    void tick() {
        if (move == 0) {
            if (cooldown > 0) { cooldown--; return; }
            LivingEntity victim = boss.getTarget();
            if (victim != null && boss.distanceToSqr(victim) < 64 * 64 && boss.hasLineOfSight(victim)
                    && begin(nextMove, victim)) nextMove = nextMove % 4 + 1;
            return;
        }
        if (!boss.isAlive() || target == null || !target.isAlive() || target.level() != boss.level()) {
            cancel(); return;
        }
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.getLookControl().setLookAt(target, 15, 10);
        if (move == InkCraneAttackRules.SWEEP) {
            if (age == 14) boss.playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 1.35F);
            if (age == InkCraneAttackRules.ROAR_TICKS) {
                Vec3 behind = Vec3.directionFromRotation(0, boss.getYRot()).scale(-10);
                InkCranePhantomEntity.spawn(boss, target, InkCranePhantomEntity.LONG_WINGS,
                        boss.position().add(behind).add(0, 32.3, 0), Vec3.ZERO);
                boss.playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.8F, 1.5F);
            }
            if (age == InkCraneAttackRules.ROAR_TICKS + InkCraneAttackRules.AVATAR_FORM_TICKS)
                boss.triggerAnim("action", "spectral_sweep");
        }
        if (move == InkCraneAttackRules.BARRAGE && age > 0 && age < 83 && age % 22 == 0) {
            boss.triggerAnim("action", "ink_flap");
        }
        if (move == InkCraneAttackRules.BARRAGE && InkCraneAttackRules.firesAt(age)) {
            Vec3 origin = boss.position().add(0, 6.46, 0);
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
            InkCranePhantomEntity.spawn(boss, target, InkCranePhantomEntity.PURSUIT,
                    origin, aim.subtract(origin).normalize().scale(0.95));
            boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 0.75F);
        }
        if (move == InkCraneAttackRules.SKY) sky();
        if (move == InkCraneAttackRules.DRAKE && age == 24) {
            Vec3 origin = boss.position().add(0, 7, 0);
            Vec3 direction = target.position().add(0, 1, 0).subtract(origin).normalize().scale(1.3);
            InkCranePhantomEntity.spawn(boss, target, InkCranePhantomEntity.DRAKE_CHARGE, origin, direction);
            boss.playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.6F);
        }
        if (++age >= InkCraneAttackRules.duration(move)) cancel();
    }

    private void sky() {
        Vec3 hover = home.add(0, 32, 0);
        Vec3 position = hover;
        if (age < InkCraneAttackRules.RISE_TICKS) {
            double t = age / (double) InkCraneAttackRules.RISE_TICKS;
            position = home.lerp(hover, t * t * (3 - 2 * t));
        } else if (landing != null && age >= InkCraneAttackRules.SKY_RELEASE) {
            double t = Math.min(1, (age - InkCraneAttackRules.SKY_RELEASE)
                    / (double) InkCraneAttackRules.DIVE_TICKS);
            position = hover.lerp(landing, t * t);
            if (t >= 1 && !landed) {
                landed = true;
                // Same-frame order: arrive, remove the attached shadow, impact, then gather ink.
                boss.setSkyShroudVisible(false);
                skyImpact(landing);
                if (landingSeal != null && !landingSeal.isRemoved()) landingSeal.startRitualCharge();
                boss.triggerAnim("action", "sky_land");
                boss.setNoGravity(previousGravity);
            }
        }
        boss.setPos(position); boss.fallDistance = 0;
        if (age < 50 && age % 15 == 0) {
            boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 2, 0.5F);
        }
        if (age == InkCraneAttackRules.SKY_RELEASE) {
            // The seal commits to this spot. It never follows an escaping player.
            // Find actual ground, including when the target is jumping or flying.
            net.minecraft.core.BlockPos ground = target.blockPosition();
            while (ground.getY() > boss.level().getMinBuildHeight()
                    && boss.level().getBlockState(ground.below()).getCollisionShape(boss.level(), ground.below()).isEmpty())
                ground = ground.below();
            Vec3 floor = new Vec3(target.getX(), ground.getY(), target.getZ());
            landing = floor;
            landingSeal = InkCranePhantomEntity.spawn(boss, target, InkCranePhantomEntity.RITUAL, floor, Vec3.ZERO);
            if (landingSeal != null) landingSeal.waitForLanding();
            boss.triggerAnim("action", "sky_dive");
        }
    }

    private void skyImpact(Vec3 landing) {
        Vec3 center=landing.add(0,1,0);
        for(LivingEntity victim:boss.level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center,center).inflate(6),e->e!=boss && e.isAlive()
                        && !e.isSpectator() && !boss.isAlliedTo(e))) {
            if(victim.getBoundingBox().getCenter().distanceToSqr(center)<=36
                    && boss.level().clip(new net.minecraft.world.level.ClipContext(center,victim.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,boss)).getType()==net.minecraft.world.phys.HitResult.Type.MISS)
                victim.hurt(boss.damageSources().mobAttack(boss),32);
        }
        boss.playSound(SoundEvents.GENERIC_EXPLODE,2.8F,.65F);
    }

    void cancel() {
        boss.setSkyShroudVisible(false);
        if (move == InkCraneAttackRules.SKY) {
            if (landed && landing != null) boss.setPos(landing);
            else if (home != null) boss.setPos(home);
            if (!landed && landingSeal != null) landingSeal.discard();
            boss.setNoGravity(previousGravity); boss.fallDistance = 0;
        }
        boss.setDeltaMovement(Vec3.ZERO);
        move = 0; target = null; home = null; landing = null; landingSeal = null; cooldown = 75;
        boss.setInkMove(0);
    }

    void saveRecovery(CompoundTag tag) {
        if (move != InkCraneAttackRules.SKY || home == null) return;
        tag.putBoolean("InkCraneAirborne", true);
        tag.putBoolean("InkCranePreviousGravity", previousGravity);
        Vec3 recovery = landed && landing != null ? landing : home;
        tag.putDouble("InkCraneHomeX", recovery.x); tag.putDouble("InkCraneHomeY", recovery.y);
        tag.putDouble("InkCraneHomeZ", recovery.z);
    }
}
