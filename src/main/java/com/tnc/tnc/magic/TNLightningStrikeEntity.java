package com.tnc.tnc.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 一道<b>从天而降</b>的落雷（纯表现）。
 *
 * <h2>为什么要有它</h2>
 * 作者 2026-09-27："这主链的雷场雷暴雷击用的都是啥，我不是给你闪电的模型了吗？
 * 做成跟**天打五雷轰一样的雷劈的方式**不就好了" ＋ "你这像是在怪身上爆开的，我要**从天上劈下去**啊"。
 *
 * <p>所以它**不是**站在目标身上闪一下 ✗ —— 它在目标**正上方 24 格**生成，
 * 每 tick 落 {@link #FALL_SPEED} 格砸下来 ✓，落下时一路撒电花（拖尾 ✓），
 * 到底后再亮 {@link #life()} tick 就消失 ✓；渲染器把作者的 {@code flash} 模型
 * **底端**画在实体位置上，于是看上去就是"闪电从天上劈到敌人身上" ✓✓。
 *
 * <p>伤害不在这里结算 ✗ —— 机制层在生成它的时候就已经扣血了（见 {@code TnSpellMechanics.strikeEnemy}），
 * 这个实体只负责"看起来是雷劈"。
 */
public class TNLightningStrikeEntity extends Entity {

    /** 每 tick 下落多少格（24 格高 ⇒ 约 4 tick 落地 ✓ 够快，看得出是"劈"下来 ✓）。 */
    private static final double FALL_SPEED = 6.0D;
    /** 生成高度（格）—— 作者要"从天上来" ✓ */
    public static final double FALL_HEIGHT = 24.0D;

    /** scale x 100（同步给客户端 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);
    /** 落地后还亮多少 tick ✓ */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);

    /** 落点高度（只有服务端要用 ✓ 不用同步）。 */
    private double fallTo;
    /** 落地那一 tick（-1 = 还没落地 ✓）。 */
    private int landedTick = -1;

    public TNLightningStrikeEntity(EntityType<? extends TNLightningStrikeEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * 生成后立刻调用一次。
     *
     * @param scale    闪电大小（倍数）
     * @param lifeTicks 落地后还亮多少 tick
     * @param fallToY  落到哪个高度（＝目标脚下 ✓）
     */
    public void configure(double scale, int lifeTicks, double fallToY) {
        this.entityData.set(DATA_SCALE, (int) Math.round(scale * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.fallTo = fallToY;
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }


    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 450);
        this.entityData.define(DATA_LIFE, 4);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.tickCount == 1) {
            // 生成在目标正上方（生成时给的是落点坐标 ✓）
            this.setPos(this.getX(), this.fallTo + FALL_HEIGHT, this.getZ());
            return;
        }
        if (this.getY() > this.fallTo) {
            double next = Math.max(this.fallTo, this.getY() - FALL_SPEED);
            // 下落拖尾：一路撒电花 —— "从天劈下来"的观感主要靠它 ✓
            ServerLevel level = (ServerLevel) this.level();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), next + 0.5D, this.getZ(),
                    8, 0.35D, 0.9D, 0.35D, 0.5D);
            level.sendParticles(ParticleTypes.WITCH, this.getX(), next + 0.5D, this.getZ(),
                    3, 0.3D, 0.8D, 0.3D, 0.3D);
            this.setPos(this.getX(), next, this.getZ());
            return;
        }
        if (this.landedTick < 0) {
            this.landedTick = this.tickCount;
            // 落地那一下：炸开一圈 ✓
            ServerLevel level = (ServerLevel) this.level();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.4D, this.getZ(),
                    45, 0.6D, 0.5D, 0.6D, 1.2D);
            level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 0.4D, this.getZ(),
                    25, 0.5D, 0.4D, 0.5D, 0.9D);
            level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 0.8D, this.getZ(),
                    2, 0.1D, 0.1D, 0.1D, 0.0D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
        } else if (this.tickCount > this.landedTick + this.life()) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 纯表现，不存档 ✓
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 同上
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
