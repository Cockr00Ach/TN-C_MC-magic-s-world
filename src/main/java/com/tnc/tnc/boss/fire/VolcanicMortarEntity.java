package com.tnc.tnc.boss.fire;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 二阶段火山喷发的抛射火球（任务 8）。
 *
 * <p>与喷吐火球不同，这一颗走抛物线：服务端在发射瞬间就算好了落点、飞行时长与
 * 预计落地时刻，并把这些数据同步给客户端做落点预警；落点预警和真实爆炸使用
 * 同一份服务端数据，客户端不自己猜。
 *
 * <p>规则：
 * <ul>
 *   <li>爆炸半径 2.5 格，每颗只结算一次；</li>
 *   <li>同一目标在一次喷发里受击有间隔与次数上限（由 Boss 统一记录）；</li>
 *   <li>飞行途中提前撞到新出现的方块/墙体：无伤害消散，不做“没预警的爆炸”；</li>
 *   <li>不破坏地形、不点火。</li>
 * </ul>
 */
public final class VolcanicMortarEntity extends Projectile {
    private static final org.slf4j.Logger FIELD_LOG = org.slf4j.LoggerFactory.getLogger("TN-C/firefield");

    /** 单颗爆炸半径（格）。 */
    public static final double EXPLOSION_RADIUS = 2.5D;
    /** 自定义重力（格/tick²）。 */
    private static final double GRAVITY = 0.05D;

    private static final EntityDataAccessor<Float> DATA_LAND_X =
            SynchedEntityData.defineId(VolcanicMortarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LAND_Y =
            SynchedEntityData.defineId(VolcanicMortarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LAND_Z =
            SynchedEntityData.defineId(VolcanicMortarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT =
            SynchedEntityData.defineId(VolcanicMortarEntity.class, EntityDataSerializers.INT);

    private final Set<UUID> hitOnce = new HashSet<>();
    private float damage = 6.0F;
    private long castId;
    /** 服务端专用：落地时刻（gameTime），用于提前/延后判定。 */
    private int landY = -1;

    public VolcanicMortarEntity(EntityType<? extends VolcanicMortarEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 发射一颗抛射火球。
     *
     * @param from   火山口位置
     * @param target 服务端选定的落点（已取地表高度）
     * @param flight 飞行 tick 数（同时用作预警时长）
     */
    public static VolcanicMortarEntity launch(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 target,
                                              int flight, float damage, long castId) {
        VolcanicMortarEntity ball = new VolcanicMortarEntity(VolcanicTortoiseRegistration.MORTAR, level);
        ball.setOwner(owner);
        ball.setPos(from.x, from.y, from.z);
        ball.damage = damage;
        ball.castId = castId;
        ball.landY = (int) Math.floor(target.y);

        double t = Math.max(1, flight);
        double dx = target.x - from.x;
        double dz = target.z - from.z;
        double dy = target.y - from.y;
        double vy = (dy + 0.5D * GRAVITY * t * t) / t;
        ball.setDeltaMovement(dx / t, vy, dz / t);
        ball.setYRot((float) Math.toDegrees(Math.atan2(dx, dz)));

        ball.entityData.set(DATA_LAND_X, (float) target.x);
        ball.entityData.set(DATA_LAND_Y, (float) target.y);
        ball.entityData.set(DATA_LAND_Z, (float) target.z);
        ball.entityData.set(DATA_FLIGHT, flight);
        return ball;
    }

    public Vec3 landing() {
        return new Vec3(this.entityData.get(DATA_LAND_X), this.entityData.get(DATA_LAND_Y),
                this.entityData.get(DATA_LAND_Z));
    }

    public int flightTicks() {
        return this.entityData.get(DATA_FLIGHT);
    }

    public float damage() {
        return this.damage;
    }

    public long castId() {
        return this.castId;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_LAND_X, 0.0F);
        this.entityData.define(DATA_LAND_Y, 0.0F);
        this.entityData.define(DATA_LAND_Z, 0.0F);
        this.entityData.define(DATA_FLIGHT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(motion);

        // 提前撞到方块：说明轨迹上出现了新障碍，这一颗无伤害消散（预警随实体一起消失）。
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean earlyHit = hit.getType() != HitResult.Type.MISS
                && hit.getLocation().y > this.landing().y + 0.75D;

        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(motion.x, motion.y - GRAVITY, motion.z);

        // 到达落点高度（或飞过预计时长）就引爆；落点本身在服务端已确定。
        boolean landed = this.getY() <= this.landing().y + 0.15D
                || this.tickCount >= this.flightTicks();
        if (earlyHit) {
            this.fizzle();
            return;
        }
        if (landed) {
            this.impact();
        }
        if (this.tickCount > this.flightTicks() + 40) {
            this.discard();
        }
    }

    /** 途中受阻：只做少量火星，不结算任何伤害。 */
    private void fizzle() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(TortoiseParticles.FIRE_MID.get(), this.getX(), this.getY(), this.getZ(),
                    6, 0.25D, 0.25D, 0.25D, 0.02D);
        }
        this.discard();
    }

    private void impact() {
        Vec3 at = this.landing();
        this.hitOnce.add(this.getUUID());
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(TortoiseParticles.FIRE_CORE.get(), at.x, at.y + 0.25D, at.z, 12, 0.18D, 0.18D, 0.18D, 0.03D);
            level.sendParticles(TortoiseParticles.FIRE_MID.get(), at.x, at.y + 0.35D, at.z, 22, 0.40D, 0.35D, 0.40D, 0.06D);
            level.sendParticles(TortoiseParticles.FIRE_EDGE.get(), at.x, at.y + 0.45D, at.z, 16, 0.65D, 0.55D, 0.65D, 0.10D);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3D, at.z, 5, 0.45D, 0.35D, 0.45D, 0.0D);
            level.playSound(null, net.minecraft.core.BlockPos.containing(at),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.35F);
            // 有效落地就在实际支撑面上留下燃烧区；若下方没有合法支撑面（空中）就不生成。
            VolcanicFireFieldEntity field = VolcanicFireFieldEntity.spawn(level, at,
                    this.damage * 0.30F, this.castId,
                    this.getOwner() == null ? null : this.getOwner().getUUID(),
                    VolcanicFireFieldEntity.MORTAR_RADIUS,
                    VolcanicFireFieldEntity.MORTAR_DURATION,
                    VolcanicFireFieldEntity.MORTAR_HIT_INTERVAL);
            if (Boolean.getBoolean("tnc.tortoise.firefield.debug")) {
                FIELD_LOG.info("[TN-C FireField] mortar impact at=({}, {}, {}) created={} reason={}",
                        at.x, at.y, at.z, field != null, field == null ? "no-support-surface" : "ok");
            }

            AABB area = new AABB(at, at).inflate(EXPLOSION_RADIUS);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, this::canHitEntity)) {
                if (victim.position().distanceTo(at) > EXPLOSION_RADIUS) {
                    continue;
                }
                if (this.getOwner() instanceof VolcanicTortoiseEntity boss
                        && !boss.tryMortarHit(victim.getUUID(), this.castId)) {
                    continue;
                }
                this.hitOnce.add(victim.getUUID());
                if (this.getOwner() instanceof LivingEntity owner) {
                    victim.hurt(this.damageSources().mobProjectile(this, owner), this.damage);
                } else {
                    victim.hurt(this.damageSources().magic(), this.damage);
                }
            }
        }
        this.discard();
    }

    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
            return false;
        }
        if (living == this.getOwner() || living.isAlliedTo(this.getOwner())) {
            return false;
        }
        return !this.hitOnce.contains(living.getUUID());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 6400.0D;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("MortarDamage", this.damage);
        tag.putLong("MortarCastId", this.castId);
        tag.putDouble("MortarLandX", this.landing().x);
        tag.putDouble("MortarLandY", this.landing().y);
        tag.putDouble("MortarLandZ", this.landing().z);
        tag.putInt("MortarFlight", this.flightTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("MortarDamage");
        this.castId = tag.getLong("MortarCastId");
        this.entityData.set(DATA_LAND_X, (float) tag.getDouble("MortarLandX"));
        this.entityData.set(DATA_LAND_Y, (float) tag.getDouble("MortarLandY"));
        this.entityData.set(DATA_LAND_Z, (float) tag.getDouble("MortarLandZ"));
        this.entityData.set(DATA_FLIGHT, tag.getInt("MortarFlight"));
        this.landY = (int) Math.floor(this.landing().y);
    }
}
