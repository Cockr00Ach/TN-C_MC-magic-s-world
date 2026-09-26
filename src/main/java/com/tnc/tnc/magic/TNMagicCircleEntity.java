package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * <b>地上的魔法阵</b> —— 释放雷球链的<b>传说级（tier 4）与神级（tier 5）</b>时在脚下留下 ✓
 * （作者 2026-09-22："可以绘制一个魔法阵吗，在释放雷球的传说级和神级魔法时地上留下魔法阵"）。
 *
 * <h2>为什么用实体而不是粒子</h2>
 * 粒子撒出来的圆圈永远是"一圈点" ✗；这个实体把 {@code textures/entity/magic_circle.png}
 * （128×128 透明背景，符文/六芒/双环，见 {@code tools/gen_magic_circle.ps1}）当**贴地的一张面片**画 ✓
 * —— 才像真的魔法阵。它没有碰撞、不可选中、不写进存档（纯表现 ✓）。
 *
 * <h2>同步</h2>
 * 客户端只需要三件事：多大、活多久、什么形态 ✓ —— 走 {@link SynchedEntityData}；
 * 年龄用原版就有的 {@code tickCount}（两端都在涨 ✓），所以不用自己同步。
 */
public class TNMagicCircleEntity extends Entity {

    /** 默认存在时长（tick）：8 秒。 */
    public static final int DEFAULT_LIFE = 160;

    /** 半径 × 10（同步用整数） */
    protected static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(TNMagicCircleEntity.class, EntityDataSerializers.INT);
    /** 存在时长（tick） */
    protected static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNMagicCircleEntity.class, EntityDataSerializers.INT);

    public TNMagicCircleEntity(EntityType<? extends TNMagicCircleEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** 生成后调用一次：半径（格）与存在时长（tick）。 */
    public void configure(double radius, int lifeTicks) {
        this.entityData.set(DATA_RADIUS, (int) Math.round(radius * 10.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
    }

    public double radius() {
        return this.entityData.get(DATA_RADIUS) / 10.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 30);
        this.entityData.define(DATA_LIFE, DEFAULT_LIFE);
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
        // 纯表现实体，不存档 ✓
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
        return distanceSqr < 16384.0D;
    }
}
