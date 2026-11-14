package com.tnc.tnc.boss.earth;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 土柱：土系 Boss 的交互玩法里，从场地中心升起的那根<b>有血量的石柱</b>（2026-11-15 作者设计）。
 *
 * <p>玩家要在 15 秒内打碎它；碎了他会从地里出来停手 10 秒，打不碎他就飞上 30 格放地爆天星。
 * 它不会动、不会攻击，只会挨打（和衍的雷锲同一套思路）。
 */
public class TNEarthPillarEntity extends Entity {

    /** 石柱血量：六个人围着打大约 8~12 秒。 */
    public static final float PILLAR_HEALTH = 220.0F;

    private static final EntityDataAccessor<Float> HEALTH =
            SynchedEntityData.defineId(TNEarthPillarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DONE =
            SynchedEntityData.defineId(TNEarthPillarEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID owner;

    public TNEarthPillarEntity(EntityType<? extends TNEarthPillarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(HEALTH, PILLAR_HEALTH);
        entityData.define(DONE, false);
    }

    public static TNEarthPillarEntity spawn(ServerLevel level, StoneLancerEntity boss, double x, double y, double z) {
        TNEarthPillarEntity pillar = new TNEarthPillarEntity(TNOrbEntities.EARTH_PILLAR.get(), level);
        pillar.setPos(x, y, z);
        pillar.owner = boss.getUUID();
        level.addFreshEntity(pillar);
        level.sendParticles(ParticleTypes.POOF, x, y + 1, z, 30, 0.8, 1.2, 0.8, 0.05);
        level.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.6F, 0.7F);
        return pillar;
    }

    public float health() {
        return entityData.get(HEALTH);
    }

    public boolean done() {
        return entityData.get(DONE);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || done() || amount <= 0.0F) {
            return false;
        }
        float left = health() - amount;
        entityData.set(HEALTH, Math.max(0.0F, left));
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 10, 0.4, 0.6, 0.4, 0.2);
            level.playSound(null, blockPosition(), SoundEvents.STONE_HIT, SoundSource.HOSTILE, 1.0F, 0.9F);
            if (left <= 0.0F) {
                entityData.set(DONE, true);
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 3, 0.4, 0.5, 0.4, 0.0);
                level.playSound(null, blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.8F, 0.6F);
                if (owner != null) {
                    EarthPillarTrial.onPillarDestroyed(level, owner);
                }
                discard();
            }
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (tickCount % 5 == 0 && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1.6, getZ(),
                    2, 0.25, 0.3, 0.25, 0.01);
        }
        if (tickCount > 400) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(HEALTH, Math.max(1.0F, tag.getFloat("Health")));
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Health", health());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }
}
