package com.tnc.tnc.boss.fire;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 火球落地后留下的贴地燃烧区域：地面持续冒火苗与黑烟，并对范围内目标周期性造成伤害。
 *
 * <p>只在服务端结算伤害；粒子由服务端广播（{@code sendParticles}）+ 客户端 {@code FireFieldFx}
 * 叠加，因此即使客户端没有同步到这个实体，火焰根部也看得见。实体本身不画任何模型。
 */
public final class VolcanicFireFieldEntity extends Entity {

    /** 喷吐火球留下的余火：范围大、时间长。 */
    public static final double RADIUS = 3.0D;
    public static final int DURATION = 260;
    public static final int HIT_INTERVAL = 20;

    /** 喷发弹幕的落点燃烧区：范围小、时间短。 */
    public static final double MORTAR_RADIUS = 1.75D;
    public static final int MORTAR_DURATION = 100;
    public static final int MORTAR_HIT_INTERVAL = 10;

    /** 同一 Boss 的多个区域之间共用的受伤间隔。 */
    public static final int SHARED_HIT_INTERVAL = 10;

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(VolcanicFireFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_END_TICK =
            SynchedEntityData.defineId(VolcanicFireFieldEntity.class, EntityDataSerializers.INT);

    private final Map<UUID, Integer> lastHit = new HashMap<>();
    private float damage = 2.0F;
    private long castId;
    private int hitInterval = HIT_INTERVAL;
    private UUID ownerId;
    /** 支撑面高度（服务端用，落地后不再随地形变化）。 */
    private double groundY;

    public VolcanicFireFieldEntity(EntityType<? extends VolcanicFireFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 在给定落点创建燃烧区。位置会自动吸附到下方最近的支撑面（台阶/半砖/坡地都按真实碰撞形状取）。
     *
     * @return 创建成功返回实体；下方找不到支撑面（比如在空中爆炸）时返回 {@code null}，不生成。
     */
    public static VolcanicFireFieldEntity spawn(ServerLevel level, Vec3 at, float damage, long castId,
                                                UUID ownerId, double radius, int duration, int hitInterval) {
        Vec3 ground = groundBelow(level, at);
        if (ground == null) {
            return null;
        }
        VolcanicFireFieldEntity field = new VolcanicFireFieldEntity(VolcanicTortoiseRegistration.FIRE_FIELD, level);
        field.setPos(ground.x, ground.y, ground.z);
        field.damage = damage;
        field.castId = castId;
        field.ownerId = ownerId;
        field.hitInterval = Math.max(1, hitInterval);
        field.groundY = ground.y;
        field.getEntityData().set(DATA_RADIUS, (float) radius);
        field.getEntityData().set(DATA_END_TICK, field.tickCount + Math.max(1, duration));
        level.addFreshEntity(field);
        return field;
    }

    /**
     * 从落点上方往下找支撑面：命中点在方块碰撞形状的顶面，负高度、台阶、半砖都按实际形状处理。
     * 找不到（落入虚空或脚下无碰撞体）返回 {@code null}。
     */
    public static Vec3 groundBelow(ServerLevel level, Vec3 at) {
        Vec3 from = at.add(0.0D, 1.0D, 0.0D);
        Vec3 to = at.add(0.0D, -6.0D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, null));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        // 只接受“从上方踩到的面”，避免贴到墙面上。
        if (hit.getDirection() != Direction.UP) {
            return null;
        }
        return hit.getLocation();
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

    public double groundY() {
        return this.groundY;
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
        // 服务端直接发粒子：即使客户端没同步到这个实体，贴地火苗也能看见。
        if (this.tickCount % 2 == 0) {
            level.sendParticles(TortoiseParticles.FIRE_MID.get(), this.getX(), this.getY() + 0.06D, this.getZ(),
                    3, r * 0.55D, 0.05D, r * 0.55D, 0.01D);
        }
        if (this.tickCount % 4 == 0) {
            level.sendParticles(TortoiseParticles.FIRE_CORE.get(), this.getX(), this.getY() + 0.10D, this.getZ(),
                    1, r * 0.25D, 0.02D, r * 0.25D, 0.004D);
        }
        if (this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.55D, this.getZ(),
                    2, r * 0.40D, 0.20D, r * 0.40D, 0.01D);
        }
        if (this.tickCount % this.hitInterval != 0) {
            return;
        }
        Vec3 center = this.position();
        AABB area = new AABB(center, center).inflate(r, 2.5D, r);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim.isSpectator()) {
                continue;
            }
            if (victim.position().distanceToSqr(center) > r * r) {
                continue;
            }
            // 垂直限制：只烧贴地这一层，站在上层楼板或从高空飞过不受影响。
            if (victim.getY() < this.groundY - 1.0D || victim.getY() > this.groundY + 2.5D) {
                continue;
            }
            if (!this.canHitNow(victim)) {
                continue;
            }
            this.lastHit.put(victim.getUUID(), this.tickCount);
            DamageSource source = level.damageSources().inFire();
            victim.hurt(source, this.damage);
            victim.setSecondsOnFire(2);
        }
    }

    /** 同一 Boss 的多个区域共用 10 tick 间隔，避免重叠区域同时结算。 */
    private boolean canHitNow(LivingEntity victim) {
        if (this.level() instanceof ServerLevel server
                && this.ownerId != null
                && server.getEntity(this.ownerId) instanceof VolcanicTortoiseEntity boss) {
            return boss.tryFieldHit(victim.getUUID());
        }
        Integer last = this.lastHit.get(victim.getUUID());
        return last == null || this.tickCount - last >= this.hitInterval;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.damage = tag.getFloat("FieldDamage");
        this.castId = tag.getLong("FieldCastId");
        this.hitInterval = Math.max(1, tag.getInt("FieldInterval"));
        this.groundY = tag.getDouble("FieldGroundY");
        if (tag.hasUUID("FieldOwner")) {
            this.ownerId = tag.getUUID("FieldOwner");
        }
        if (tag.contains("FieldEnd")) {
            this.getEntityData().set(DATA_END_TICK, tag.getInt("FieldEnd"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("FieldDamage", this.damage);
        tag.putLong("FieldCastId", this.castId);
        tag.putInt("FieldInterval", this.hitInterval);
        tag.putDouble("FieldGroundY", this.groundY);
        if (this.ownerId != null) {
            tag.putUUID("FieldOwner", this.ownerId);
        }
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
