package com.tnc.tnc.dungeon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 地下城远程怪的弹药：弩箭（{@link Bolt#ARROW}）与火罐（{@link Bolt#POT}）。
 *
 * <p>挂 {@link ItemSupplier} 而不是自己画一张弹体贴图 —— 客户端可以直接用原版
 * {@code ThrownItemRenderer} 画"一支箭 / 一罐火"，省掉一整套模型与渲染器，
 * 而且玩家一眼就知道飞过来的是什么 ✓。
 *
 * <p>★ 伤害写死在弹体上（{@code damage}），**不读弓的蓄力** —— 这些怪没有弓，
 * 伤害由 {@link TNDungeonMob#performImpact()} 按物种数值给。
 */
public class DungeonBoltEntity extends AbstractArrow implements ItemSupplier {

    public enum Bolt {
        /** 弩箭：单体、快。 */
        ARROW,
        /** 火罐：命中后炸开一小片火。 */
        POT
    }

    private static final EntityDataAccessor<Byte> DATA_KIND =
            SynchedEntityData.defineId(DungeonBoltEntity.class, EntityDataSerializers.BYTE);

    private float damage = 4.0F;
    private boolean exploded;

    public DungeonBoltEntity(EntityType<? extends DungeonBoltEntity> type, Level level) {
        super(type, level);
    }

    /** 统一的构造入口：位置放在主人眼睛下方，避免弹体从脚底钻地。 */
    public static DungeonBoltEntity of(Level level, LivingEntity owner, Bolt bolt, float damage) {
        DungeonBoltEntity entity = new DungeonBoltEntity(DungeonMobRegistration.BOLT.get(), level);
        entity.setOwner(owner);
        entity.setPos(owner.getX(), owner.getEyeY() - 0.25D, owner.getZ());
        entity.entityData.set(DATA_KIND, (byte) bolt.ordinal());
        entity.damage = damage;
        entity.setBaseDamage(damage);
        return entity;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_KIND, (byte) Bolt.ARROW.ordinal());
    }

    public Bolt bolt() {
        return Bolt.values()[this.entityData.get(DATA_KIND)];
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(bolt() == Bolt.POT ? Items.FIRE_CHARGE : Items.ARROW);
    }

    /** 弹体是怪物的攻击，玩家不能捡（捡走一根弩箭就没意义，还污染背包）。 */
    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (this.level().isClientSide()) {
            return;
        }
        if (target instanceof LivingEntity living) {
            LivingEntity owner = getOwner() instanceof LivingEntity living2 ? living2 : null;
            living.hurt(owner == null ? damageSources().magic()
                    : damageSources().mobProjectile(this, owner), damage);
        }
        if (bolt() == Bolt.POT) {
            detonate();
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide() && bolt() == Bolt.POT) {
            detonate();
            discard();
        }
    }

    /** 火罐的落点效果：小范围伤害 + 点燃，半径刻意小（3 格）好让玩家有走位空间。 */
    private void detonate() {
        if (exploded) {
            return;
        }
        exploded = true;
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(3.0D), e -> e != getOwner() && e instanceof LivingEntity)) {
            victim.hurt(damageSources().indirectMagic(this, getOwner()), damage * 0.8F);
            victim.setSecondsOnFire(4);
        }
        server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 26, 1.2D, 0.4D, 1.2D, 0.02D);
        server.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 10, 1.0D, 0.2D, 1.0D, 0.0D);
        this.level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2F, 0.8F);
    }
}
