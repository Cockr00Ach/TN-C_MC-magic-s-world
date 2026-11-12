package com.tnc.tnc.magic.water;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * <b>一道水波</b>（水球链 t4/t5 齐射里的那一发 ✓ 作者 2026-11-09：金闪闪那种"法阵里射出来"）。
 *
 * <p>和老的 {@link TNWaterSpellEntity} 完全是两个东西 ✗：
 * <ul>
 *   <li>老的是一根<b>持续 3~12 秒</b>的钻掘光柱（自己走地形、每 10 tick 结算一次 ✗）；</li>
 *   <li>这个是<b>一闪而过</b>的一道水波 ✓（作者："不用原来那样持久，就跟光线一样就行了"✓）——
 *       活 {@link WaterGateRules#lanceTicks(int)} tick、<b>只结算一次</b>伤害、不碰地形 ✓。</li>
 * </ul>
 *
 * <p>位置/朝向/长度在生成时定死（法阵已经算好了"从哪射、射向谁"✓），服务端第一 tick 结算伤害，
 * 之后纯粹等渲染器把它淡出 ✓。
 */
public final class TNWaterLanceEntity extends Entity {

    private static final EntityDataAccessor<Integer> TIER =
            SynchedEntityData.defineId(TNWaterLanceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GIANT =
            SynchedEntityData.defineId(TNWaterLanceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(TNWaterLanceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(TNWaterLanceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH =
            SynchedEntityData.defineId(TNWaterLanceEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private boolean struck;

    public TNWaterLanceEntity(EntityType<? extends TNWaterLanceEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    /**
     * 从 {@code from} 朝 {@code aim} 射一道水波。
     *
     * @param giant t5 收尾那道"巨大水波"（更粗、伤害 ×2 ✓）
     * @return 生成成功返回实体（调用方一般不用管 ✓）
     */
    public static TNWaterLanceEntity fire(ServerLevel level, LivingEntity caster, int tier,
                                          boolean giant, Vec3 from, Vec3 aim) {
        TNWaterLanceEntity lance = TNOrbEntities.WATER_LANCE.get().create(level);
        if (lance == null) {
            return null;
        }
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1.0E-4D) {
            return null;
        }
        double length = Math.min(WaterGateRules.LANCE_RANGE, dir.length());
        lance.owner = caster.getUUID();
        lance.setPos(from);
        lance.entityData.set(TIER, tier);
        lance.entityData.set(GIANT, giant);
        lance.entityData.set(LENGTH, (float)length);
        lance.entityData.set(YAW, (float)Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        lance.entityData.set(PITCH, (float)-Math.toDegrees(Math.asin(dir.normalize().y)));
        level.addFreshEntity(lance);
        return lance;
    }

    public int tier() { return entityData.get(TIER); }

    public boolean giant() { return entityData.get(GIANT); }

    public float length() { return entityData.get(LENGTH); }

    public Vec3 direction() {
        return Vec3.directionFromRotation(entityData.get(PITCH), entityData.get(YAW));
    }

    /** 这一道水波多粗（格 ✓ 巨大版更粗）。 */
    public double radius() {
        return giant() ? WaterGateRules.giantRadius(tier()) : WaterGateRules.lanceRadius(tier());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TIER, 4);
        entityData.define(GIANT, false);
        entityData.define(LENGTH, 0F);
        entityData.define(YAW, 0F);
        entityData.define(PITCH, 0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel)level();
        LivingEntity caster = WaterSpellOwners.resolve(level, owner);
        if (!struck) {
            struck = true;
            float power = caster == null ? 1.0F : WaterSpellRules.power(caster);
            strike(level, caster, position(), direction(), length(), radius(),
                    (giant() ? WaterGateRules.giantDamage(tier()) : WaterGateRules.lanceDamage(tier()))
                            * power);
            level.playSound(null, blockPosition(), giant() ? SoundEvents.CONDUIT_ACTIVATE
                    : SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.1F, giant() ? 0.55F : 1.35F);
        }
        if (tickCount > WaterGateRules.lanceTicks(tier())) {
            discard();
        }
    }

    /**
     * 一次结算：沿光束扫一遍，够得着谁就打谁 ✓。
     *
     * <p>和老的持续光柱不同 ✗：这里<b>只扫一次</b>（"跟光线一样"✓），所以判定用
     * "点到线段的距离 ≤ 水波半径 + 目标半宽"这条老公式（{@code WaterSpellRules.segmentDistanceSquared} ✓），
     * 但**不要求视线** ✗ —— 水波是一闪而过的，被自己面前的碎屑挡一下就整发白打太难用了 ✗。
     */
    private void strike(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir,
                        double length, double radius, float damage) {
        if (length <= 0.05D || caster == null) {
            return;
        }
        Vec3 end = from.add(dir.scale(length));
        AABB box = new AABB(from, end).inflate(radius + 1.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!WaterSpellRules.enemy(caster, target)) {
                continue;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            double along = center.subtract(from).dot(dir);
            double reach = radius + target.getBbWidth() * 0.5D;
            if (along < -reach || along > length + reach) {
                continue;
            }
            if (WaterSpellRules.segmentDistanceSquared(center, from, dir, length) <= reach * reach) {
                target.hurt(level.damageSources().indirectMagic(this, caster), damage);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();      // 一闪而过的表现实体，存档里没有它 ✓
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position().add(direction().scale(Math.max(1.0D, length()))))
                .inflate(radius() + 1.0D);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
