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

    /**
     * ★ 法阵**样式**（作者 2026-10-01："专门为光魔法画一个法阵，要纯白色的"）：
     * 0 = 原来的雷系阵（{@code magic_circle.png} ✓），1 = 光系阵（{@code light_circle.png} ✓），
     * 2 = 暗色阵（{@code dark_circle.png} ✓）—— 给<b>黑雾链</b>当"领域的边界"用 ✓。
     *
     * <p>暗色阵和另外两张的用法不一样：它不是"施法时脚下的装饰"，而是
     * <b>雾的边界线</b> —— 半径 = 雾半径、时长 = 雾时长、位置每 tick 跟着雾走 ✓
     * （由 {@link DarkFogCloudEntity} 托管，见那边的 {@code claimBoundaryCircle}）。
     */
    public static final int STYLE_EARTH = 4;
    public static final int STYLE_WIND = 5;
    public static final int STYLE_STORM = 0;
    public static final int STYLE_LIGHT = 1;
    public static final int STYLE_DARK = 2;
    /** Airborne boss seal, independent of fog boundary ownership. */
    public static final int STYLE_DARK_RITUAL = 6;
    /**
     * ★ <b>暗系黑夜之手链的黑阵</b>（作者 2026-10-10：「给暗魔法专门做一个**黑色的**魔法阵」）✓
     * —— 贴图是 {@code textures/entity/black_circle.png}（近黑＋极暗血红线条，生成器
     * {@code tools/gen_black_circle.py}）✓。
     *
     * <p>它和 {@link #STYLE_DARK} 的区别：那个是**黑雾的边界线**（偏紫、可读性优先）；
     * 这个是**手穿过去的门**（压暗、氛围优先）✓。
     */
    public static final int STYLE_DARK_HAND = 3;

    protected static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(TNMagicCircleEntity.class, EntityDataSerializers.INT);

    /**
     * ★ <b>竖着的阵</b>（作者 2026-10-10：「手伸出去是穿过好几个**竖着的**魔法阵的感觉，从 t3 开始」）✓
     *
     * <p>0 = 平铺在地面（原来的行为，**黑雾链的边界线仍然走这条** ✓ 向后兼容 ✓）；
     * 1 = 立起来、面朝 {@link #facingDegrees()} ✓ —— 手沿弹道飞过去时**穿过**它 ✓。
     */
    protected static final EntityDataAccessor<Integer> DATA_UPRIGHT =
            SynchedEntityData.defineId(TNMagicCircleEntity.class, EntityDataSerializers.INT);

    /**
     * 竖立时的朝向：**直接就是渲染器喂给 {@code Axis.YP.rotationDegrees(...)} 的度数** ✓。
     *
     * <p>为什么不复用实体自己的 yaw ✗：MC 的 yaw 约定（0 = +Z、90° = −X）与
     * {@code Axis.YP} 的旋转方向不是一回事，隔一层换算很容易左右反 ✗。
     * 这里由生成方按 {@code atan2(dx, dz)}（弹道方向）算好、同步成整数度 ✓ ——
     * 渲染器拿到就能用，零歧义 ✓（而且阵是旋转对称的 ⇒ 就算差 180° 也看不出来 ✓）。
     */
    protected static final EntityDataAccessor<Integer> DATA_FACING =
            SynchedEntityData.defineId(TNMagicCircleEntity.class, EntityDataSerializers.INT);

    public TNMagicCircleEntity(EntityType<? extends TNMagicCircleEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** 生成后调用一次：半径（格）与存在时长（tick）。 */
    public void configure(double radius, int lifeTicks) {
        this.configure(radius, lifeTicks, STYLE_STORM);
    }

    /** 生成后调用一次：半径（格）、存在时长（tick）、样式 ✓。 */
    public void configure(double radius, int lifeTicks, int style) {
        this.configure(radius, lifeTicks, style, false, 0.0F);
    }

    /**
     * 全参数版 ✓：`upright` = 立起来（手穿过去），`facingDegrees` = 立起来时面朝哪边 ✓。
     *
     * @param facingDegrees 直接给 {@code Axis.YP.rotationDegrees} 用的度数（见 {@link #DATA_FACING}）
     */
    public void configure(double radius, int lifeTicks, int style, boolean upright, float facingDegrees) {
        this.entityData.set(DATA_RADIUS, (int) Math.round(radius * 10.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.entityData.set(DATA_STYLE, style);
        this.entityData.set(DATA_UPRIGHT, upright ? 1 : 0);
        this.entityData.set(DATA_FACING, Math.round(facingDegrees));
    }

    /** 是竖着的阵吗（手穿过去的那种 ✓）。 */
    public boolean upright() {
        return this.entityData.get(DATA_UPRIGHT) == 1;
    }

    /** 竖立时的朝向度数（直接喂渲染器的 YP 旋转 ✓）。 */
    public float facingDegrees() {
        return this.entityData.get(DATA_FACING);
    }

    /** 样式（0 雷 / 1 光 ✓）。 */
    public int style() {
        return this.entityData.get(DATA_STYLE);
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
        this.entityData.define(DATA_STYLE, STYLE_STORM);
        this.entityData.define(DATA_UPRIGHT, 0);
        this.entityData.define(DATA_FACING, 0);
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
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return upright()?super.getBoundingBoxForCulling().inflate(radius())
                :super.getBoundingBoxForCulling().inflate(radius(),.2,radius());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double distance=128+radius();
        return distanceSqr < distance*distance;
    }
}
