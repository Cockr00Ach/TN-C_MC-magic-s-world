package com.tnc.tnc.boss.fire;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 火球落地后留下的火焰区域：原地持续冒火与黑烟，并对范围内目标周期性造成伤害。
 *
 * <p>只在服务端结算伤害；客户端靠 {@code FireFieldFx} 补粒子，实体本身不画任何模型。
 * 每次命中生成一个，生命结束后自动移除。
 */
public final class VolcanicFireFieldEntity extends Entity {

    public static final double RADIUS = 3.0D;
    public static final int DURATION = 260;
    /** 同一目标两次灼烧的最小间隔。 */
    public static final int HIT_INTERVAL = 20;

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(VolcanicFireFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_END_TICK =
            SynchedEntityData.defineId(VolcanicFireFieldEntity.class, EntityDataSerializers.INT);

    private final Map<UUID, Integer> lastHit = new HashMap<>();
    private float damage = 2.0F;
    private long castId;

    public VolcanicFireFieldEntity(EntityType<? extends VolcanicFireFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static VolcanicFireFieldEntity spawn(ServerLevel level, Vec3 at, float damage, long castId) {
        VolcanicFireFieldEntity field = new VolcanicFireFieldEntity(VolcanicTortoiseRegistration.FIRE_FIELD, level);
        field.setPos(at.x, at.y, at.z);
        field.damage = damage;
        field.castId = castId;
        field.getEntityData().set(DATA_RADIUS, (float) RADIUS);
        field.getEntityData().set(DATA_END_TICK, field.tickCount + DURATION);
        level.addFreshEntity(field);
        return field;
    }

    public float radius() {
        return this.getEntityData().get(DATA_RADIUS);
    }

    public float damageAmount() {
        return this.damage;
    }

    public long castId() {
        return this.castId;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_RADIUS, (float) RADIUS);
        this.getEntityData().define(DATA_END_TICK, DURATION);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        int end = this.getEntityData().get(DATA_END_TICK);
        if (this.tickCount >= end) {
            this.discard();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        double r = this.radius();
        // 服务端直接发粒子：即使客户端没同步到这个实体，余火也能看见。
        if (this.tickCount % 4 == 0) {
            level.sendParticles(TortoiseParticles.FIRE_MID.get(), this.getX(), this.getY() + 0.25D, this.getZ(),
                    4, r * 0.55D, 0.15D, r * 0.55D, 0.01D);
        }
        if (this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(),
                    2, r * 0.45D, 0.25D, r * 0.45D, 0.01D);
        }
        if (this.tickCount % HIT_INTERVAL != 0) {
            return;
        }
        Vec3 center = this.position();
        AABB area = new AABB(center, center).inflate(r, 2.0D, r);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == this.getVehicle() || victim.isSpectator()) {
                continue;
            }
            if (victim.position().distanceToSqr(center) > r * r) {
                continue;
            }
            Integer last = this.lastHit.get(victim.getUUID());
            if (last != null && this.tickCount - last < HIT_INTERVAL) {
                continue;
            }
            this.lastHit.put(victim.getUUID(), this.tickCount);
            DamageSource source = level.damageSources().inFire();
            victim.hurt(source, this.damage);
            victim.setSecondsOnFire(3);
        }
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.2D, center.z, 3, 0.4D, 0.2D, 0.4D, 0.01D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.damage = tag.getFloat("FieldDamage");
        this.castId = tag.getLong("FieldCastId");
        if (tag.contains("FieldEnd")) {
            this.getEntityData().set(DATA_END_TICK, tag.getInt("FieldEnd"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("FieldDamage", this.damage);
        tag.putLong("FieldCastId", this.castId);
        tag.putInt("FieldEnd", this.getEntityData().get(DATA_END_TICK));
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }
}
