package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A single lightning strike bolt standing at a position (visual only).
 *
 * <p>Author 2026-09-27: "the primary chain field / storm / strike must strike WITH the
 * lightning model I gave you, same as the heavenly thunder". So instead of drawing an
 * arc out of particles, the mechanic layer spawns this entity on the target and the
 * client renders the author's {@code tnc:projectile/flash} block model - the exact same
 * model the heavenly thunder projectile uses, via the same proven CustomModels path
 * (see TNLightningStrikeRenderer).
 *
 * <p>Damage is NOT dealt here: the mechanic layer already hit the target
 * (see TnSpellMechanics.strikeEnemy). This entity exists purely so the hit looks like a
 * real bolt coming down.
 */
public class TNLightningStrikeEntity extends Entity {

    /** scale x 100 (integer so it can be synced). */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);
    /** how many ticks the bolt stays (a strike is instantaneous; 6-8 ticks reads best). */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);

    public TNLightningStrikeEntity(EntityType<? extends TNLightningStrikeEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** Called right after spawning: bolt size and how long it stays. */
    public void configure(double scale, int lifeTicks) {
        this.entityData.set(DATA_SCALE, (int) Math.round(scale * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 300);
        this.entityData.define(DATA_LIFE, 7);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > this.life()) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // pure visual, never saved
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // same
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 32768.0D;
    }
}
