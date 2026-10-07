package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>射线链 t4「炎魔龙之怒」</b>（作者 2026-10-05 设计）✓
 *
 * <h2>作者原话</h2>
 * <p>「在目标脚下生成一个持续 8s 的巨大的法阵，法阵的东南西北各生起 4 个黑曜石巨柱，
 * 柱子上方漂浮着红黄色的圆球（这里尽量模拟出太阳的感觉），圆球附近有火焰粒子飞舞，
 * 效果是对该法阵内的生物产生持续伤害，表现为从圆球中发出一条光线，类似激光一样，
 * 给予生物焚身2效果5秒，但生物离开法阵范围或者死亡时圆球会切换目标，
 * 允许多个光线攻击同个生物，法术结束后四个圆球同时爆炸，
 * 每个圆球爆炸伤害是光线伤害×2，并带有视角震动」✓
 *
 * <h2>怎么落的</h2>
 * <ul>
 *   <li><b>4 个柱子 + 4 个圆球是纯视觉</b> ✓ —— 作者明确「不改地形」✗，
 *       所以它们由渲染器画（{@code TNFlameDemonRenderer} ✓），**一根真方块都不放** ✓</li>
 *   <li><b>4 条光线各自独立索敌</b> ✓ —— 4 个 {@code DATA_ORB_n} 同步字段分别记目标实体 id，
 *       客户端渲染器照着画激光 ✓（所以"多束打同一个生物"天然成立 ✓：4 条都锁到同一只就行 ✓）</li>
 *   <li><b>目标跑了/死了就换</b> ✓ —— 每 tick 校验：不在阵内或已死 ⇒ 重新在阵内找最近的一只 ✓</li>
 *   <li><b>伤害是持续跳伤</b> ✓ —— 每 {@link FireSpellRules#T4_BEAM_INTERVAL_TICKS} tick 跳一次，
 *       单次伤害 = 系数 × 基础伤害 × 火法强 ÷ {@link FireSpellRules#T4_BEAM_DIVISOR}
 *       ⇒ 8 秒共 16 跳，**总量正好 = 系数 × 基础伤害 × 火法强** ✓（和别档同一口径 ✓）</li>
 *   <li><b>收场 4 球同时爆炸</b> ✓ —— 每球伤害 = 单次光线伤害 × 2 ✓ + 震屏 ✓</li>
 * </ul>
 */
public class TNFlameDemonField extends Entity {

    /** 半径 × 100（同步整数 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(TNFlameDemonField.class, EntityDataSerializers.INT);

    /** 存在时长（tick ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNFlameDemonField.class, EntityDataSerializers.INT);

    /** 施法者 id（判"不打自己人"要用 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_CASTER =
            SynchedEntityData.defineId(TNFlameDemonField.class, EntityDataSerializers.INT);

    /** 画面震动强度（度 ×100 ✓，收场那一下用 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SHAKE =
            SynchedEntityData.defineId(TNFlameDemonField.class, EntityDataSerializers.INT);

    /** 4 个圆球各自锁定的目标实体 id（-1 = 没锁 ✓）。 */
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Integer>[] DATA_ORB = new EntityDataAccessor[4];

    static {
        for (int i = 0; i < 4; i++) {
            DATA_ORB[i] = SynchedEntityData.defineId(TNFlameDemonField.class, EntityDataSerializers.INT);
        }
    }

    /** 单次光线伤害（只在服务端算 ✓）。 */
    private float beamDamage;

    /** 收场爆炸是否已经做过（只做一次 ✓）。 */
    private boolean collapsed;

    public TNFlameDemonField(EntityType<? extends TNFlameDemonField> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** 生成后调一次：多大、活多久、单次光线多疼、震多狠、谁放的 ✓。 */
    public void configure(Entity caster, double radius, int lifeTicks, float beamDamage, double shake) {
        this.entityData.set(DATA_RADIUS, (int) Math.round(radius * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.entityData.set(DATA_CASTER, caster == null ? -1 : caster.getId());
        this.entityData.set(DATA_SHAKE, (int) Math.round(shake * 100.0D));
        for (int i = 0; i < 4; i++) {
            this.entityData.set(DATA_ORB[i], -1);
        }
        this.beamDamage = beamDamage;
    }

    public double radius() {
        return this.entityData.get(DATA_RADIUS) / 100.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    public double shake() {
        return this.entityData.get(DATA_SHAKE) / 100.0D;
    }

    public int casterId() {
        return this.entityData.get(DATA_CASTER);
    }

    /** 第 i 个圆球锁定的目标 id（-1 = 没锁 ✓）—— 渲染器照着画激光 ✓。 */
    public int orbTargetId(int i) {
        return this.entityData.get(DATA_ORB[i]);
    }

    /** 4 个圆球的世界坐标（渲染器与判伤共用 ✓）。 */
    public Vec3 orbPosition(int i) {
        double r = this.radius() * FireSpellRules.T4_ORB_RING;
        double angle = Math.PI * 0.5D * i;                  // 东南西北 ✓
        double h = FireSpellRules.T4_ORB_HEIGHT;
        return new Vec3(this.getX() + Math.cos(angle) * r, this.getY() + h,
                this.getZ() + Math.sin(angle) * r);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 1000);
        this.entityData.define(DATA_LIFE, 160);
        this.entityData.define(DATA_CASTER, -1);
        this.entityData.define(DATA_SHAKE, 0);
        for (int i = 0; i < 4; i++) {
            this.entityData.define(DATA_ORB[i], -1);
        }
    }

    @Override
    public void tick() {
        // ★ 作者 2026-10-05：「柱顶的光球不要用光滑的几何体，用**粒子球**」✓
        //   ⇒ 渲染器里那 3 片正交圆盘已删 ✗；这里每 tick 在 orbPosition 撒一层染色粒子壳 ✓
        //     服务端发 ⇒ 所有玩家可见 ✓；粒子寿命会把球"糊"成一个会呼吸的火球 ✓
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (int i = 0; i < 4; i++) {
                net.minecraft.world.phys.Vec3 o = orbPosition(i);
                for (int p = 0; p < ORB_PARTICLES; p++) {
                    // 球面附近随机取点（半径 0.55~0.85 ⇒ 球壳 ✓，中心也补几颗 ⇒ 有芯 ✓）
                    double theta = Math.random() * Math.PI * 2.0D;
                    double phi = Math.acos(2.0D * Math.random() - 1.0D);
                    double rr = 0.55D + Math.random() * 0.30D;
                    double dx = Math.sin(phi) * Math.cos(theta) * rr;
                    double dy = Math.cos(phi) * rr;
                    double dz = Math.sin(phi) * Math.sin(theta) * rr;
                    // 取色：靠外偏红、靠内偏白黄（和火系色板一致 ✓）
                    double shade = (rr - 0.55D) / 0.30D * 0.85D;
                    fireDust(sl, o.x + dx, o.y + dy, o.z + dz, shade, 1.5F + (float) (1.0D - shade));
                }
            }
        }
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        // 施法者没了 ⇒ 法阵也收掉（免得原地留一堆激光 ✓）
        if (!(server.getEntity(this.casterId()) instanceof LivingEntity caster)
                || !caster.isAlive() || this.tickCount > this.life()) {
            collapse(server);
            this.discard();
            return;
        }

        // 每条光线各自索敌 + 跳伤 ✓
        boolean tickDamage = this.tickCount % FireSpellRules.T4_BEAM_INTERVAL_TICKS == 0;
        for (int i = 0; i < 4; i++) {
            LivingEntity target = this.resolveOrbTarget(server, caster, i);
            this.entityData.set(DATA_ORB[i], target == null ? -1 : target.getId());
            if (target != null && tickDamage) {
                this.beamHit(server, caster, target);
            }
        }

        // 圆球附近飞舞的火焰粒子（纯表现，客户端也能做，但放服务端更省事 ✓）
        for (int i = 0; i < 4; i++) {
            Vec3 at = this.orbPosition(i);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, 0.55D, 0.55D, 0.55D, 0.02D);
            server.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 3, 0.7D, 0.7D, 0.7D, 0.03D);
        }
    }

    /**
     * 第 i 个圆球现在该打谁 ✓
     *
     * <p>已锁的目标只要**还在阵内且活着**就继续打 ✓；
     * 否则在阵内重新找最近的一只 ✓（作者：「生物离开法阵范围或者死亡时圆球会切换目标」✓）。
     */
    private LivingEntity resolveOrbTarget(ServerLevel server, LivingEntity caster, int i) {
        int id = this.entityData.get(DATA_ORB[i]);
        if (id >= 0 && server.getEntity(id) instanceof LivingEntity current
                && current.isAlive() && this.inside(current)
                && FireSpellRules.hittable(caster, current)) {
            return current;
        }
        return this.nearestInside(server, caster);
    }

    /** 阵内最近的、能打中的生物 ✓。 */
    private LivingEntity nearestInside(ServerLevel server, LivingEntity caster) {
        double r = this.radius();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(this.getX() - r, this.getY() - 2.0D, this.getZ() - r,
                        this.getX() + r, this.getY() + FireSpellRules.T4_ORB_HEIGHT + 2.0D, this.getZ() + r),
                t -> FireSpellRules.hittable(caster, t))) {
            if (!this.inside(candidate)) {
                continue;
            }
            double d = candidate.distanceToSqr(this.getX(), this.getY(), this.getZ());
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return best;
    }

    /** 在不在阵内（水平距离 ≤ 半径，且高度别跑太远 ✓）。 */
    public boolean inside(LivingEntity entity) {
        double dx = entity.getX() - this.getX();
        double dz = entity.getZ() - this.getZ();
        double dy = entity.getY() - this.getY();
        return dx * dx + dz * dz <= this.radius() * this.radius()
                && dy > -3.0D && dy < FireSpellRules.T4_ORB_HEIGHT + 4.0D;
    }

    /** 一次光线跳伤 + 挂焚身 II ✓。 */
    private void beamHit(ServerLevel server, LivingEntity caster, LivingEntity target) {
        target.invulnerableTime = 0;                        // 别被无敌帧吃掉 ✓
        target.hurt(server.damageSources().indirectMagic(this, caster), this.beamDamage);
        // 焚身 II 级（作者定 t4~t5 都是 II 级 ✓）——只有玩家施法才算得出基数 ✓
        if (caster instanceof ServerPlayer player) {
            TNScorch.apply(target, player, this.beamDamage, true);
        }
    }

    /** 收场：4 个圆球**同时**爆炸 ✓，每球伤害 = 单次光线伤害 × 2 ✓ + 震屏 ✓。 */
    private void collapse(ServerLevel server) {
        if (this.collapsed) {
            return;
        }
        this.collapsed = true;
        LivingEntity caster = server.getEntity(this.casterId()) instanceof LivingEntity living ? living : null;
        float blast = this.beamDamage * FireSpellRules.T4_ORB_BLAST_MULTIPLIER;
        double r = FireSpellRules.T4_ORB_BLAST_RADIUS;
        for (int i = 0; i < 4; i++) {
            Vec3 at = this.orbPosition(i);
            for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                    FireSpellRules.uprightArea(at, r, r), t -> FireSpellRules.hittable(caster, t))) {
                victim.invulnerableTime = 0;
                victim.hurt(server.damageSources().indirectMagic(this, caster), blast);
            }
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, r * 0.5D, r * 0.5D, r * 0.5D, 0.28D);
            server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 24, r * 0.4D, r * 0.4D, r * 0.4D, 0.18D);
        }
        // 四个球是"同时"炸 ⇒ 声音和震屏只来一次就够了（四次叠起来会爆音 ✗）
        Vec3 center = this.position();
        server.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 4.0F, 0.9F);
        TNShockwaveEntity.blast(server, center, 3.0D + (float) this.shake() * 0.6D,
                (float) this.shake(), caster, 0.0F);
    }

    /** 供命令/自检用：这个法阵是不是还有活着的目标（渲染器/测试可以用 ✓）。 */
    public List<Integer> orbTargetIds() {
        List<Integer> ids = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            ids.add(this.entityData.get(DATA_ORB[i]));
        }
        return ids;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 法阵是"法术效果"，不做过图/存档（和冲击波同一个约定 ✓）
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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
        return distanceSqr < 160.0D * 160.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = this.radius() + 2.0D;
        return new AABB(this.getX() - r, this.getY() - 2.0D, this.getZ() - r,
                this.getX() + r, this.getY() + FireSpellRules.T4_ORB_HEIGHT + 4.0D, this.getZ() + r);
    }

    /** 造一个：由 {@link TNFireRays} 在施法瞬间放 ✓。 */
    public static TNFlameDemonField spawn(ServerLevel level, ServerPlayer caster, double radius,
                                          int lifeTicks, float beamDamage, double shake,
                                          Vec3 at) {
        TNFlameDemonField field = new TNFlameDemonField(TNOrbEntities.FLAME_DEMON_FIELD.get(), level);
        field.setPos(at);
        field.configure(caster, radius, lifeTicks, beamDamage, shake);
        level.addFreshEntity(field);
        return field;
    }

    /** 柱顶光球的**粒子数**（每颗每 tick ✓）。 */
    private static final int ORB_PARTICLES = 14;

    /** 撒一颗火系染色粒子（同 PixelFlame 色板 ✓）。 */
    private static void fireDust(net.minecraft.server.level.ServerLevel sl,
                                 double x, double y, double z, double shade, float scale) {
        float r;
        float g;
        float b;
        if (shade < 0.18D) {
            r = 1.00F; g = 0.96F; b = 0.80F;          // 白热芯 ✓
        } else if (shade < 0.42D) {
            r = 1.00F; g = 0.78F; b = 0.30F;          // 亮黄 ✓
        } else if (shade < 0.68D) {
            r = 1.00F; g = 0.52F; b = 0.12F;          // 橙 ✓
        } else {
            r = 0.86F; g = 0.24F; b = 0.06F;          // 深橙红 ✓
        }
        sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(r, g, b), scale),
                x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
    }
}