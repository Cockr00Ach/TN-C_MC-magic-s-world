package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * ★ <b>飞出去的风刃</b>（龙卷风甩出来的那一片，2026-10-10）✓
 *
 * <p>作者要的是「龙卷风……<b>持续向外发射风刃</b>」✓ —— 所以这是一个**自己的投射物实体**，
 * 不走引擎那条投射物路（引擎的投射物必须挂在一次施法上 ✗，而龙卷风是持续生成的，
 * 没有"那一次施法"可用 ✗）。
 *
 * <h2>为什么不用引擎的投射物</h2>
 * 引擎的 {@code SpellProjectile} 每发都要一个 spell 上下文 ＋ 一次 release 流程；
 * 龙卷风每 14 tick 就要吐 3~7 发、连吐十几秒 ⇒ 那条路又重又绕 ✗。
 * 本仓已有自己的投射物先例（火系的 {@code TNFireBoltEntity}、水系的 {@code TNWaterBoltEntity}）✓，
 * 这里就是同一个做法 ✓。
 *
 * <h2>模型</h2>
 * 用的就是**月牙**（{@code tnc:projectile/windblade} ✓，和 t1/t2 飞出去的那片同一个模型 ✓）
 * —— 作者要的"风刃"就是它 ✓，龙卷风只是换了个发射方式 ✓。
 *
 * <h2>命中</h2>
 * 直线飞 ＋ 每 tick 查一次碰撞盒（{@link #hitRadius()}）✓；
 * 打到人 ⇒ 伤害 ＋ 往外推一点（风刃是"斩"出去的 ✓）⇒ 自己消失 ✓；
 * 撞到方块 ⇒ 消失 ✓（不穿墙 ✗）；飞满 {@link #MAX_LIFE} tick ⇒ 消失 ✓。
 */
public class TNWindBladeEntity extends Entity {

    /** 飞行的方向（单位向量）✓ —— 同步给客户端，渲染朝向用。 */
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(TNWindBladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(TNWindBladeEntity.class, EntityDataSerializers.FLOAT);

    /** 自转速度（度/tick）✓ —— "斩击在飞的时候是转的"。 */
    private static final EntityDataAccessor<Float> DATA_SPIN_RATE =
            SynchedEntityData.defineId(TNWindBladeEntity.class, EntityDataSerializers.FLOAT);

    /** 当前自转角 ✓。 */
    private static final EntityDataAccessor<Float> DATA_SPIN =
            SynchedEntityData.defineId(TNWindBladeEntity.class, EntityDataSerializers.FLOAT);

    /** 这一发的基础伤害 ✓。 */
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(TNWindBladeEntity.class, EntityDataSerializers.FLOAT);

    /**
     * 这一发的**尺寸倍率** ✓（1.0 = 模型原大小）—— 作者 2026-10-10 给的逐档表：
     * t3/t4/t5 = <b>5 / 10 / 20 倍</b> ✓。
     *
     * <p>为什么要同步：渲染（{@code TNWindBladeRenderer}）和剔除盒子都在客户端读它 ✓
     * ⇒ 两边读同一个数，永远不会"画多大"和"判定多大"各说各话 ✗。
     */
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(TNWindBladeEntity.class, EntityDataSerializers.FLOAT);

    /** 命中判定半径的**基准** ✓（1 倍时的值 —— 月牙很薄，给宽一点才打得到 ✗ 不然老是"穿过去"）。 */
    private static final double BASE_HIT_RADIUS = 1.1D;

    /**
     * 命中半径的**上限** ✓。
     *
     * <p>★ 为什么不跟着尺寸**线性**放大 ✗：20 倍时月牙跨度约 <b>23.6 格</b>，
     * 线性放大就是 **22 格的判定球** —— 风刃**一出生就够得到龙卷风旁边的怪** ⇒
     * 当场命中、当场消失，**根本飞不出去** ✗（"持续向外发射风刃"就废了 ✗）。
     * 所以按 <b>√尺寸</b> 放大（面积直觉）再封顶 ✓：5/10/20 倍 → 约 2.5 / 3.5 / 4.9 格 ✓。
     */
    private static final double MAX_HIT_RADIUS = 5.0D;

    /**
     * 最长活多久（tick）✓ —— 0.85 格/tick × 40 ≈ 34 格，够飞出视野 ✓。
     *
     * <p>⚠️ 大尺寸的刃要**多飞一会儿** ✗：它出生点离风眼只有 0.9 格，而判定半径
     * 20 倍时是 5 格 ⇒ 一出生就可能够到旁边的怪。给足 {@code MAX_LIFE} 才不至于
     * "刚出门就没了" ✓（真正的保护是 {@link #MAX_HIT_RADIUS} 的封顶 ✓）。
     */
    private static final int MAX_LIFE = 40;

    /**
     * 渲染缩放的**基准** ✓ —— 月牙模型本身最大跨度 1.179 格 ⇒ 1 倍 ≈ 1.2 格宽 ✓。
     *
     * <p>口径与黑夜之手那条链一致 ✓：<b>作者说的"倍"就是 {@code SIZE_MULTIPLIER}</b>，
     * 实际渲染缩放 = {@code BASE_SCALE × 倍率} ✓（见 {@code TNNightGripEntity}）。
     */
    private static final double BASE_SCALE = 1.0D;

    private UUID ownerId;

    public TNWindBladeEntity(EntityType<? extends TNWindBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_YAW, 0.0F);
        this.entityData.define(DATA_PITCH, 0.0F);
        this.entityData.define(DATA_SPIN_RATE, 20.0F);
        this.entityData.define(DATA_SPIN, 0.0F);
        this.entityData.define(DATA_DAMAGE, 5.0F);
        this.entityData.define(DATA_SIZE, 1.0F);
    }

    /**
     * 发射一片风刃 ✓。
     *
     * @param pos    出发点
     * @param dir    方向（会被归一化 ✓）
     * @param speed  速度（格/tick）
     * @param damage 命中伤害
     * @param owner  发射者（可以 null ✓）
     * @param size   尺寸倍率（1.0 = 原大小 ✓；作者逐档表 t3/t4/t5 = 5/10/20 ✓）
     * @return 生成失败（实体类型没注册）时返回 null ✓
     */
    public static TNWindBladeEntity shoot(ServerLevel level, Vec3 pos, Vec3 dir, double speed,
                                          float damage, LivingEntity owner, double size) {
        if (TNOrbEntities.WIND_BLADE == null || !TNOrbEntities.WIND_BLADE.isPresent()) {
            return null;
        }
        TNWindBladeEntity blade = TNOrbEntities.WIND_BLADE.get().create(level);
        if (blade == null) {
            return null;
        }
        Vec3 unit = dir.normalize();
        blade.ownerId = owner == null ? null : owner.getUUID();
        blade.setPos(pos.x, pos.y, pos.z);
        blade.setDeltaMovement(unit.scale(speed));
        blade.entityData.set(DATA_DAMAGE, damage);
        blade.entityData.set(DATA_SIZE, (float) size);
        // 朝向：先 yaw 再 pitch（和引擎投射物同一套算法 ✓）
        blade.entityData.set(DATA_YAW, (float) Math.toDegrees(Math.atan2(unit.x, unit.z)));
        blade.entityData.set(DATA_PITCH, (float) -Math.toDegrees(Math.asin(unit.y)));
        level.addFreshEntity(blade);
        // One compact launch burst; ongoing trails remain client-side and bounded.
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,pos.x,pos.y,pos.z,
                8,.20,.20,.20,.055);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,pos.x,pos.y,pos.z,
                1,.10,.10,.10,0);
        return blade;
    }

    @Override
    public void tick() {
        super.tick();

        // Spin follows local visual age. Advancing a synced angle on both sides caused corrections
        // to fight the client animation and made the blade appear to turn discontinuously.

        if (level().isClientSide()) {
            // ⚠️ **客户端绝对不能再自己挪实体** ✗（作者 2026-10-10：
            //     「风刃应该是发射出去的啊，什么叫跟着龙卷风一直在那里转动」✗）
            //
            //     这里原来有一句 `setPos(getX() + getDeltaMovement()...)` ——
            //     抛射物的位置由**服务端**算好、每 tick 同步过来
            //     （见 TNOrbEntities.WIND_BLADE 的 updateInterval(1)），客户端只负责插值 ✓。
            //     两边各挪一次 ⇒ **客户端的外推和服务端的同步互相打架** ✗
            //     ⇒ 画面上就是"一顿一顿 / 来回弹"，看着像**卡在龙卷风旁边转** ✗。
            //
            //     火系的 TNFireBoltEntity 2026-10-05 已经踩过**同一个坑**并删掉了同一句 ✓
            //     （那句注释还在那里），本文件当时漏改 ✗ ⇒ 现在对齐 ✓。
            //
            //     客户端只留"好看"：给飞出去的刃补一条尾迹，让"射出去"看得见 ✓
            if (tickCount % 2 == 0 && getDeltaMovement().lengthSqr() > 0.01D) {
                Vec3 direction=getDeltaMovement().normalize();
                Vec3 side=direction.cross(new Vec3(0,1,0));
                if(side.lengthSqr()<1E-6)side=new Vec3(1,0,0);
                side=side.normalize();
                double width=Math.min(1.5,.18+sizeMultiplier()*.06);
                for(int i=-1;i<=1;i+=2) {
                    Vec3 at=position().subtract(direction.scale(.55)).add(side.scale(width*i));
                    level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            at.x,at.y,at.z,-direction.x*.025,.012,-direction.z*.025);
                }
            }
            return;
        }

        // 飞
        Vec3 motion = getDeltaMovement();
        Vec3 next = position().add(motion);
        setPos(next.x, next.y, next.z);

        // 撞方块 ⇒ 散（不穿墙 ✗）
        if (level().getBlockState(blockPosition()).isSolid()) {
            burst();
            discard();
            return;
        }

        // 打到人 ⇒ 伤害 ＋ 往外推 ＋ 散
        LivingEntity target = firstHit();
        if (target != null) {
            LivingEntity owner = owner();
            target.hurt(damageSources().indirectMagic(this, owner), this.entityData.get(DATA_DAMAGE));
            Vec3 push = motion.normalize().scale(0.35D);
            target.push(push.x, 0.12D, push.z);
            target.hurtMarked = true;
            burst();
            discard();
            return;
        }

        if (tickCount > MAX_LIFE) {
            discard();
        }
    }

    /** 这一 tick 撞到的第一个人（排除发射者与旁观者模式 ✓）。 */
    private LivingEntity firstHit() {
        LivingEntity owner = owner();
        double r = hitRadius();
        List<LivingEntity> hits = level().getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - r, getY() - r, getZ() - r,
                        getX() + r, getY() + r, getZ() + r),
                e -> e.isAlive() && e != owner && !e.isSpectator());
        return hits.isEmpty() ? null : hits.get(0);
    }

    private void burst() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    getX(), getY(), getZ(), 3, 0.2D, 0.2D, 0.2D, 0.0D);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    getX(), getY(), getZ(), 6, 0.25D, 0.25D, 0.25D, 0.05D);
        }
    }

    // ---------------- 渲染要读的 ----------------

    public float bladeYaw() {
        return this.entityData.get(DATA_YAW);
    }

    public float bladePitch() {
        return this.entityData.get(DATA_PITCH);
    }

    public float spin() {
        return visualSpin(0);
    }

    public float visualSpin(float partialTick) {
        return ((tickCount+partialTick)*this.entityData.get(DATA_SPIN_RATE))%360F;
    }

    public void setSpinRate(float rate) {
        this.entityData.set(DATA_SPIN_RATE, rate);
    }

    /** 尺寸倍率（1.0 = 原大小 ✓）。 */
    public float sizeMultiplier() {
        return this.entityData.get(DATA_SIZE);
    }

    /** 渲染缩放 = 基准 × 倍率 ✓（渲染器读这个，和判定半径同源 ✓）。 */
    public double sizeScale() {
        return BASE_SCALE * sizeMultiplier();
    }

    /**
     * 命中判定半径 ✓ —— 按 <b>√倍率</b> 放大并封顶（理由见 {@link #MAX_HIT_RADIUS} ✗）。
     */
    public double hitRadius() {
        return hitRadiusFor(sizeMultiplier());
    }

    /**
     * 静态版的命中半径 ✓ —— 给**发射方**用 ✓。
     *
     * <p>为什么要给 ✗：{@link TNTornadoEntity} 必须把风刃生在**这个半径之外** ✗，
     * 否则 t3 那种"判定半径比漩涡外壁还大"的情况会让风刃**刚出生就命中、当场消失** ✗
     * —— 表现就是"刃根本没射出去" ✗。
     */
    public static double hitRadiusFor(double sizeMultiplier) {
        return Math.min(MAX_HIT_RADIUS, BASE_HIT_RADIUS * Math.sqrt(sizeMultiplier));
    }

    /** 发射者（可能已下线 ⇒ null ✓）。 */
    public LivingEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity found = server.getEntity(ownerId);
        return found instanceof LivingEntity living ? living : null;
    }

    // ---------------- 存盘 / 不吃伤害 ----------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.tickCount = tag.getInt("Age");
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.tickCount);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * 视锥剔除用的包围盒：<b>按尺寸放大</b> ✓。
     *
     * <p>为什么必须给 ✗：t5 的月牙跨度约 <b>23.6 格</b>，而本实体碰撞箱只有 1 格 ⇒
     * 原版按碰撞箱剔除 ⇒ 风刃**中心一出屏就整片消失** ✗（黑夜之手同款坑 ✓）。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(sizeScale() * 0.6D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }
}
