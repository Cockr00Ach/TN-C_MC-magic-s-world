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
                    // ⚠️ 作者 2026-10-05：「柱子上的粒子球**大点**」✓ ⇒ 球壳半径 0.55~0.85 → 0.9~1.45 ✓
                    double rr = 0.90D + Math.random() * 0.55D;
                    double dx = Math.sin(phi) * Math.cos(theta) * rr;
                    double dy = Math.cos(phi) * rr;
                    double dz = Math.sin(phi) * Math.sin(theta) * rr;
                    // 取色：靠外偏红、靠内偏白黄（和火系色板一致 ✓）
                    double shade = (rr - 0.90D) / 0.55D * 0.85D;
                    // 粒子本身也做大 ✓（原来 1.5~2.5 ⇒ 现在 2.4~3.4 ✓）
                    fireDust(sl, o.x + dx, o.y + dy, o.z + dz, shade, 2.4F + (float) (1.0D - shade));
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

        // ★ 作者 2026-10-05：「**在法阵中加上粒子效果让场景更壮观**」✓
        //   三样叠起来 ✓：① 贴地一圈缓慢旋转的余烬环 ✓
        //              ② 从地面往上飘的火星柱 ✓
        //              ③ 阵心一根会"呼吸"的火柱 ✓
        {
            double rr = this.radius();
            double cx = this.getX();
            double cy = this.getY();
            double cz = this.getZ();
            // ① 贴地余烬环：沿圆周取样，相位随 tickCount 转 ✓
            for (int k = 0; k < SIGIL_RING_POINTS; k++) {
                double a = this.tickCount * 0.02D + k * Math.PI * 2.0D / SIGIL_RING_POINTS;
                double px = cx + Math.cos(a) * rr * 0.98D;
                double pz = cz + Math.sin(a) * rr * 0.98D;
                fireDust(server, px, cy + 0.15D + Math.random() * 0.25D, pz,
                        0.62D + Math.random() * 0.30D, 1.6F);
            }
            // ② 从地面往上飘的火星（随机位置 ✓）
            for (int k = 0; k < SIGIL_EMBER_POINTS; k++) {
                double a = Math.random() * Math.PI * 2.0D;
                double d = Math.sqrt(Math.random()) * rr * 0.95D;
                double px = cx + Math.cos(a) * d;
                double pz = cz + Math.sin(a) * d;
                fireDust(server, px, cy + Math.random() * 2.2D, pz,
                        0.30D + Math.random() * 0.55D, 1.4F);
            }
            // ③ 阵心那根"呼吸"火柱 ✓
            double pulse = 1.0D + 0.18D * Math.sin(this.tickCount * 0.22D);
            for (int k = 0; k < 6; k++) {
                double py = cy + 0.3D + k * 0.9D * pulse;
                double off = 0.55D * (1.0D - k / 7.0D);
                fireDust(server, cx + (Math.random() * 2.0D - 1.0D) * off, py,
                        cz + (Math.random() * 2.0D - 1.0D) * off,
                        k * 0.10D, 1.8F);
            }
        }

        // ★ 作者 2026-10-05：「发射出来的射线改为**粒子流**」✓
        //   ⇒ 不再由渲染器画两层激光管 ✗（光滑几何体 ✗），改成**服务端沿 orb→目标 撒粒子** ✓
        //     每个采样点一颗 ✓；隔点取"白热芯/橙红外壳"⇒ 有流动感 ✓
        //   ⚠️ 顺带把原来那圈"圆球附近飞舞的火焰粒子"也并进来 ✗（同一条循环里做 ✓）
        for (int i = 0; i < 4; i++) {
            Vec3 at = this.orbPosition(i);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, 0.55D, 0.55D, 0.55D, 0.02D);
            server.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 3, 0.7D, 0.7D, 0.7D, 0.03D);

            int id = this.orbTargetId(i);
            if (id < 0) {
                continue;                                  // 这条线这一 tick 没锁到东西 ✓
            }
            if (!(server.getEntity(id) instanceof LivingEntity target)) {
                continue;
            }
            Vec3 to = target.getPosition(1.0F).add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 delta = to.subtract(at);
            int steps = (int) Math.max(2.0D, delta.length() * 1.2D);
            for (int s = 0; s <= steps; s++) {
                Vec3 p = at.add(delta.scale(s / (double) steps));
                // 隔点交替：白热芯 ↔ 橙红外壳 ⇒ 粒子流"在跑"的观感 ✓
                boolean core = ((this.tickCount + s) & 1) == 0;
                fireDust(server, p.x, p.y, p.z, core ? 0.05D : 0.70D, core ? 1.9F : 1.5F);
            }
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

    /**
     * 阵内最近的、能打中的生物 ✓ —— <b>优先敌对生物</b> ✓
     *
     * <p>⚠️ 作者 2026-10-05：「**优先攻击敌对生物**」✓
     * ⇒ 先把敌对的分出来（{@code Enemy} 标记的怪 ✓ + 玩家 ✓ —— 这是战斗 mod ✗），
     * 只在敌对里挑最近的 ✓；一只敌对都没有时，才退回去打中立/被动生物 ✓
     */
    private LivingEntity nearestInside(ServerLevel server, LivingEntity caster) {
        double r = this.radius();
        LivingEntity best = null;              // 非敌对里最近的 ✓
        LivingEntity bestHostile = null;       // 敌对里最近的 ✓
        double bestDist = Double.MAX_VALUE;
        double bestHostileDist = Double.MAX_VALUE;
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(this.getX() - r, this.getY() - 2.0D, this.getZ() - r,
                        this.getX() + r, this.getY() + FireSpellRules.T4_ORB_HEIGHT + 2.0D, this.getZ() + r),
                t -> FireSpellRules.hittable(caster, t))) {
            if (!this.inside(candidate)) {
                continue;
            }
            double d = candidate.distanceToSqr(this.getX(), this.getY(), this.getZ());
            boolean hostile = candidate instanceof net.minecraft.world.entity.monster.Enemy
                    || candidate instanceof net.minecraft.world.entity.player.Player;
            if (hostile) {
                if (d < bestHostileDist) {
                    bestHostileDist = d;
                    bestHostile = candidate;
                }
            } else if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return bestHostile != null ? bestHostile : best;
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
    private static final int SIGIL_RING_POINTS = 26;   // 贴地余烬环 ✓

    /** 法阵里从地面往上飘的火星数（每 tick ✓）。 */
    private static final int SIGIL_EMBER_POINTS = 22;

    /** 柱顶光球的**粒子数**（每颗每 tick ✓）。 */
    private static final int ORB_PARTICLES = 18;   // 球做大后粒子也加密 ✓

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