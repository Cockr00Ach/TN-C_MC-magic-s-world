package com.tnc.tnc.boss;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 雷锲：衍召唤出来插在地上的六枚雷楔（2026-11-15 作者设计的交互玩法）。
 *
 * <p>它不是普通怪物 —— 不会动、不会攻击，只会<b>挨打</b>：
 * 15 秒内六枚全碎 ⇒ 衍遭一次雷劈、掉 10% 血；打不掉 ⇒ 衍把雷锲全收回去、
 * 给周围所有玩家落雷。存在期间衍<b>无敌</b>（见 {@link YanNailPuzzle#isActive}）。
 *
 * <p>血量由 {@link #NAIL_HEALTH} 决定，玩家一发火球就该碎的那种量级，
 * 六枚加起来正好是"15 秒内要打完"的节奏。
 */
public class TNThunderNailEntity extends Entity {

    /** 每枚雷锲的血量。 */
    public static final float NAIL_HEALTH = 24.0F;

    private static final EntityDataAccessor<Float> HEALTH =
            SynchedEntityData.defineId(TNThunderNailEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DONE =
            SynchedEntityData.defineId(TNThunderNailEntity.class, EntityDataSerializers.BOOLEAN);

    /** 属于哪个衍（用来通知谜题管理器）。 */
    private UUID owner;

    public TNThunderNailEntity(EntityType<? extends TNThunderNailEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(HEALTH, NAIL_HEALTH);
        entityData.define(DONE, false);
    }

    public static TNThunderNailEntity spawn(ServerLevel level, YanDarkBossEntity boss, Vec3 at) {
        TNThunderNailEntity nail = new TNThunderNailEntity(TNOrbEntities.THUNDER_NAIL.get(), level);
        nail.setPos(at.x, at.y, at.z);
        nail.owner = boss.getUUID();
        level.addFreshEntity(nail);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 18, 0.3, 0.5, 0.3, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.HOSTILE, 0.7F, 1.5F);
        return nail;
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
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.9, getZ(),
                    12, 0.35, 0.45, 0.35, 0.2);
            level.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.HOSTILE, 0.6F, 1.9F);
            if (left <= 0.0F) {
                shatter(level);
            }
        }
        return true;
    }

    private void shatter(ServerLevel level) {
        entityData.set(DONE, true);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 2, 0.2, 0.3, 0.2, 0.0);
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.8, getZ(), 2, 0.1, 0.2, 0.1, 0.0);
        level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2F, 1.2F);
        if (owner != null) {
            YanNailPuzzle.onNailDestroyed(level, owner);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        // 悬在地面上方缓慢自转 + 冒电火花（让玩家一眼看见"这是要打掉的东西"）
        if (tickCount % 4 == 0 && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(),
                    2, 0.18, 0.25, 0.18, 0.02);
        }
        if (tickCount > 400) {
            discard();                       // 保险：谜题没结算就自己消失，别留垃圾实体
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
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
