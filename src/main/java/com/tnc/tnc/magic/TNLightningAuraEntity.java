package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * <b>绕在施法者身上的一圈雷笼</b>（纯表现实体）。
 *
 * <h2>为什么要单独一个实体</h2>
 * 作者 2026-11-05："类似蛛网一样的雷网特效可以丰富我们的法阵还有自身周围的特效" ✓。
 * 法阵贴在地上（{@link TNMagicCircleEntity}），而"自身周围"要跟着人走 ——
 * 已有的落雷实体是**钉在落点**的 ✗，跟随神是**模型** ✗，都不适合。
 * 所以新开一个极轻的实体：没有任何碰撞箱、不参与伤害、只同步 5 个数，
 * 每 tick 把自己挪到主人脚下 ✓（和跟随神同一条既成做法：实体自己跟随，机制层只负责补 ✓）。
 *
 * <p>几何全在 {@code TNLightningAuraRenderer} 里现画（竖弧 ＋ 横环 ＋ 斜拉弧），
 * 这里只负责"跟着谁、多大、什么颜色、活多久"✓。
 */
public class TNLightningAuraEntity extends Entity {

    /** 雷暴：会随 15 tick 一轮的落雷一起"鼓"一下（渲染器按 tick 取脉冲）✓ */
    public static final int STYLE_STORM = 0;
    /** 雷场：一直在放电，脉冲更快更碎 ✓ */
    public static final int STYLE_FIELD = 1;

    private static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(TNLightningAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEIGHT =
            SynchedEntityData.defineId(TNLightningAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightningAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_THEME =
            SynchedEntityData.defineId(TNLightningAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(TNLightningAuraEntity.class, EntityDataSerializers.INT);

    /** 跟随谁（只在服务端有值 ✓）。 */
    private UUID owner;
    /** 找不到主人时最多再活多少 tick（防止掉线残留 ✓）。 */
    private int orphanTicks = 0;

    public TNLightningAuraEntity(EntityType<? extends TNLightningAuraEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * @param radius   半径（格）—— 4~5 格：第一人称站在里面也看得到四周 ✓
     * @param height   高度（格）
     * @param lifeTicks 活多少 tick（和 buff 时长对齐 ✓）
     * @param theme    {@code LightningTheme} 主题
     * @param style    {@link #STYLE_STORM} / {@link #STYLE_FIELD}
     */
    public void configure(double radius, double height, int lifeTicks, int theme, int style) {
        this.entityData.set(DATA_RADIUS, (int) Math.round(radius * 100.0D));
        this.entityData.set(DATA_HEIGHT, (int) Math.round(height * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.entityData.set(DATA_THEME, Math.max(0, theme));
        this.entityData.set(DATA_STYLE, style);
    }

    /** 绑到主人身上（之后每 tick 自己跟 ✓）。 */
    public void bind(LivingEntity owner) {
        this.owner = owner == null ? null : owner.getUUID();
        if (owner != null) {
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
        }
    }

    public double radius() {
        return this.entityData.get(DATA_RADIUS) / 100.0D;
    }

    public double height() {
        return this.entityData.get(DATA_HEIGHT) / 100.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    public int theme() {
        return this.entityData.get(DATA_THEME);
    }

    public int style() {
        return this.entityData.get(DATA_STYLE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 430);
        this.entityData.define(DATA_HEIGHT, 480);
        this.entityData.define(DATA_LIFE, 120);
        this.entityData.define(DATA_THEME, 2);
        this.entityData.define(DATA_STYLE, STYLE_STORM);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.tickCount > life()) {
            this.discard();
            return;
        }
        // ★ 跟随主人：每 tick 直接贴上去（不做插值）—— 玩家跑/飞都不会被甩下 ✓；
        //   位置更新会走正常的实体同步包，客户端每 tick 一次 ✓。
        Entity found = this.owner == null ? null : ((ServerLevel) this.level()).getEntity(this.owner);
        if (!(found instanceof LivingEntity living) || living.isRemoved()) {
            if (++this.orphanTicks > 10) {
                this.discard();          // 主人没了（掉线/死亡/换维度）⇒ 自己消失 ✓
            }
            return;
        }
        this.orphanTicks = 0;
        this.setPos(living.getX(), living.getY(), living.getZ());
    }

    /**
     * 剔除盒：实体本身只有 0.1 格，但几何有 5 格宽 / 5 格高 ✓ ——
     * 不覆盖的话，站在雷笼里往上看/往下看时整个实体会被视锥裁掉 ✗（落雷那边踩过同款坑）。
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        double r = Math.max(radius() + 1.0D, 2.0D);
        return super.getBoundingBoxForCulling().inflate(r, height() + 1.0D, r);
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 32768.0D;
    }
}
