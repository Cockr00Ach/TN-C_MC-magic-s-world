package com.tnc.tnc.boss.fire;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 火法 Boss 的喷吐火球（任务 5）。
 *
 * <p>规则：
 * <ul>
 *   <li>服务端权威：运动、命中、爆炸与伤害全在服务端算，客户端只画；</li>
 *   <li>直线飞行、发射后不追踪；发射瞬间方向由服务端锁定；</li>
 *   <li>扫掠碰撞：每 tick 用「线段 × 包围盒」检测，高速也不会穿墙/穿人；</li>
 *   <li>忽略施法者本体，避免出生瞬间自撞；</li>
 *   <li>不破坏地形、不点火，只有粒子与音效；</li>
 *   <li>直接命中与 3 格爆炸对同一目标只结算一次伤害。</li>
 * </ul>
 */
public final class VolcanicFireballEntity extends Projectile {

    /** 命中爆炸半径（格）。 */
    public static final double EXPLOSION_RADIUS = 3.0D;
    /** 飞行速度（格/tick）。 */
    public static final double SPEED = 1.35D;
    /** 最长存活 tick，防止打空后永远飞。 */
    private static final int MAX_LIFE = 100;

    private final Set<UUID> hitOnce = new HashSet<>();
    private float damage = 6.0F;
    private long castId;

    public VolcanicFireballEntity(EntityType<? extends VolcanicFireballEntity> type, Level level) {
        super(type, level);
    }

    public static VolcanicFireballEntity launch(ServerLevel level, LivingEntity owner, Vec3 from,
                                                Vec3 direction, float damage, long castId) {
        VolcanicFireballEntity ball = new VolcanicFireballEntity(VolcanicTortoiseRegistration.FIREBALL, level);
        ball.setOwner(owner);
        ball.setPos(from.x, from.y, from.z);
        ball.damage = damage;
        ball.castId = castId;
        Vec3 dir = direction.normalize();
        ball.setDeltaMovement(dir.scale(SPEED));
        ball.setYRot((float) (Math.toDegrees(Math.atan2(dir.x, dir.z))));
        ball.setXRot((float) Math.toDegrees(Math.asin(dir.y)));
        return ball;
    }

    public float damage() {
        return this.damage;
    }

    public long castId() {
        return this.castId;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientTrail();
            return;
        }
        if (this.tickCount > MAX_LIFE) {
            this.discard();
            return;
        }

        Vec3 from = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 to = from.add(motion);

        // 1) 方块：沿线段取第一个碰撞点。
        BlockHitResult blockHit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();

        // 2) 实体：扫掠包围盒，取离起点最近的一个（跳过施法者与已经命中过的）。
        EntityHitResult entityHit = this.findEntityHit(from, end);
        if (entityHit != null) {
            end = entityHit.getLocation();
        }

        if (entityHit != null || blockHit.getType() != HitResult.Type.MISS) {
            this.setPos(end.x, end.y, end.z);
            this.explode(end, entityHit == null ? null : entityHit.getEntity());
            return;
        }
        this.setPos(end.x, end.y, end.z);
    }

    private EntityHitResult findEntityHit(Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(1.2D);
        EntityHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : this.level().getEntities(this, sweep, this::canHitEntity)) {
            AABB box = entity.getBoundingBox().inflate(0.35D);
            var clip = box.clip(from, to);
            if (clip.isEmpty()) {
                continue;
            }
            double dist = from.distanceToSqr(clip.get());
            if (dist < bestDist) {
                bestDist = dist;
                best = new EntityHitResult(entity, clip.get());
            }
        }
        return best;
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

    /** 命中或到期：短促核心破裂 + 火焰冲击 + 少量余烬；3 格范围伤害，同一目标只算一次。 */
    private void explode(Vec3 at, Entity direct) {
        this.hitOnce.add(this.getUUID());
        if (this.level() instanceof ServerLevel level) {
            // 主体破裂：核心向内收、外缘向外扩，短促一下；不铺大烟。
            level.sendParticles(TortoiseParticles.FIRE_CORE.get(), at.x, at.y, at.z, 10, 0.22D, 0.22D, 0.22D, 0.05D);
            level.sendParticles(TortoiseParticles.FIRE_MID.get(), at.x, at.y, at.z, 24, 0.55D, 0.5D, 0.55D, 0.10D);
            level.sendParticles(TortoiseParticles.FIRE_EDGE.get(), at.x, at.y, at.z, 18, 0.95D, 0.8D, 0.95D, 0.16D);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.2D, 0.2D, 0.2D, 0.0D);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.6D, 0.5D, 0.6D, 0.0D);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.2D, at.z, 4, 0.5D, 0.35D, 0.5D, 0.01D);
            level.playSound(null, net.minecraft.core.BlockPos.containing(at),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6F, 1.15F);
            level.playSound(null, net.minecraft.core.BlockPos.containing(at),
                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.7F);

            if (direct instanceof LivingEntity living && this.canHitEntity(living)) {
                this.damageOnce(living);
                level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() * 0.5D,
                        living.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.1D);
            }
            AABB area = new AABB(at, at).inflate(EXPLOSION_RADIUS);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (victim.position().distanceTo(at) <= EXPLOSION_RADIUS) {
                    this.damageOnce(victim);
                }
            }
        }
        this.discard();
    }

    private void damageOnce(LivingEntity victim) {
        if (!this.canHitEntity(victim)) {
            return;
        }
        this.hitOnce.add(victim.getUUID());
        if (this.getOwner() instanceof LivingEntity owner) {
            victim.hurt(this.damageSources().mobProjectile(this, owner), this.damage);
        } else {
            victim.hurt(this.damageSources().magic(), this.damage);
        }
    }

    private void clientTrail() {
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.FLAME,
                    this.getX() + (this.random.nextDouble() - 0.5D) * 0.5D,
                    this.getY() + (this.random.nextDouble() - 0.5D) * 0.5D,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * 0.5D,
                    0.0D, 0.01D, 0.0D);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("FireballDamage", this.damage);
        tag.putLong("FireballCastId", this.castId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("FireballDamage");
        this.castId = tag.getLong("FireballCastId");
    }
}
