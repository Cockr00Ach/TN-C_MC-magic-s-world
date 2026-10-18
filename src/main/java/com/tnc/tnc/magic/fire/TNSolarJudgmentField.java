package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>射线链 t5「太阳の审判」</b>（作者 2026-10-05 设计）✓
 *
 * <h2>作者原话</h2>
 * <p>「目标下方出现一个持续 10s 巨大法阵，法阵正上方出现一个类似太阳的光球，
 * 向法阵范围内发射大量光线造成伤害，结束后光球爆炸，攻击范围是一个球体，
 * 其具有破坏方块的能力，除基岩外都能被破坏，爆开化为大量火焰粒子并带有视角强烈震动，
 * 光线命中伤害与爆炸伤害等同」✓
 *
 * <h2>两个必须交代清楚的工程决定</h2>
 * <ol>
 *   <li><b>破坏方块是分帧做的</b> ✗ —— 直径 32（半径 16）的球体约 <b>1.7 万个方块</b> ✗，
 *       一 tick 全删会直接把主线程卡住几秒 ✗。所以爆炸那一下之后，
 *       每 tick 删一个水平层（{@link FireSpellRules#T5_DESTROY_LAYERS_PER_TICK} 层 ✓），
 *       十几 tick 内删完 ✓ —— 观感上还是"一整片塌掉" ✓，但不会顿一下 ✗</li>
 *   <li><b>只删方块本体、不搞连锁</b> ✗ —— 用 {@code setBlock(AIR, 2)}（不触发邻居更新 ✓），
 *       跳过空气 / 基岩 / 流体 ✓（作者："除基岩外都能被破坏"✓，流体不是"方块"✗）</li>
 * </ol>
 *
 * <p>⚠️ 这会真的拆掉玩家建筑 ✓ —— 作者 2026-10-05 明确要"直径 32、除基岩外全炸" ✓，
 * 所以没有做保护 ✓；要收紧改 {@link FireSpellRules#T5_EXPLODE_RADIUS} 一个常量即可 ✓
 *
 * <h2>伤害口径</h2>
 * <p>光线的跳伤总量 = 系数 × 基础伤害 × 火法强 ✓，收场那次球形爆炸再来同样的量 ✓
 * ⇒ 一次 t5 的总量 ≈ 2 × 系数 ✓（作者要"光线命中伤害与爆炸伤害等同"，
 * 这里实现为"两者用同一个数值"✓，所以最终是两倍 ✓）
 *
 * <p><b>判定方式是"区域"，不是"命中"</b> ✓ —— 作者 2026-10-05 明确：
 * 「伤害判定是位于法阵范围内，和射线是否命中无关」✓。所以跳伤每 1s 直接结算给
 * <b>阵内所有</b>可打的生物（{@link #insideEntities}），天上那些乱射的射线粒子
 * 只是表现、与伤害完全无关 ✓（射线条数 {@link #RAY_STREAMS} 也因此是定值 ✓）。
 */
public class TNSolarJudgmentField extends Entity {

    private static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(TNSolarJudgmentField.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNSolarJudgmentField.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER =
            SynchedEntityData.defineId(TNSolarJudgmentField.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SHAKE =
            SynchedEntityData.defineId(TNSolarJudgmentField.class, EntityDataSerializers.INT);

    /** 单次光线伤害（服务端 ✓）。 */
    private float beamDamage;

    /** 收场爆炸伤害（服务端 ✓）—— 和单次光线**同一个数值** ✓（作者要"等同"✓）。 */
    private float explodeDamage;

    private boolean exploded;

    /** 破坏方块还剩几层没删（>0 表示正在塌 ✓）。 */
    private int layersLeft;

    public TNSolarJudgmentField(EntityType<? extends TNSolarJudgmentField> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public void configure(Entity caster, double radius, int lifeTicks,
                          float beamDamage, float explodeDamage, double shake) {
        this.entityData.set(DATA_RADIUS, (int) Math.round(radius * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.entityData.set(DATA_CASTER, caster == null ? -1 : caster.getId());
        this.entityData.set(DATA_SHAKE, (int) Math.round(shake * 100.0D));
        this.beamDamage = beamDamage;
        this.explodeDamage = explodeDamage;
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

    /** 太阳光球的位置（法阵正上方 ✓）。 */
    public Vec3 sunPosition() {
        return new Vec3(this.getX(), this.getY() + FireSpellRules.T5_SUN_HEIGHT, this.getZ());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 1000);
        this.entityData.define(DATA_LIFE, 200);
        this.entityData.define(DATA_CASTER, -1);
        this.entityData.define(DATA_SHAKE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }

        // 破坏方块的分帧收尾（爆炸之后才开始 ✓）
        if (this.layersLeft > 0) {
            this.destroyLayers(server);
        }

        if (!(server.getEntity(this.casterId()) instanceof LivingEntity caster)
                || !caster.isAlive() || this.tickCount > this.life()) {
            this.explode(server);
            // [BUG FIX] 塌方是分帧删的（destroyLayers）—— 这里立刻 discard 的话，
            // 实体消失后 tick 不再执行，方块一个也删不掉。留着把自己删完再消失。
            if (this.layersLeft > 0 && this.tickCount <= this.life() + 400) {
                return;
            }
            this.discard();
            return;
        }

        // 伤害判定：**只看"在不在法阵里"** ✓ —— 作者 2026-10-05 明确
        // 「伤害判定是位于法阵范围内，和射线是否命中无关」✓
        //   ⇒ 每 T5_BEAM_INTERVAL_TICKS(20 = 1s) 把这一跳的伤害结算给**阵内所有**能打的生物 ✓，
        //     不检测射线轨迹、也不统计命中数 —— 天上那些乱射的粒子纯粹是表现 ✓
        if (this.tickCount % FireSpellRules.T5_BEAM_INTERVAL_TICKS == 0) {
            for (LivingEntity victim : this.insideEntities(server, caster)) {
                victim.invulnerableTime = 0;
                victim.hurt(server.damageSources().indirectMagic(this, caster), this.beamDamage);
                if (caster instanceof ServerPlayer player) {
                    TNScorch.apply(victim, player, this.beamDamage, true);   // 焚身 II ✓
                }
            }
        }

        // 太阳附近往外喷的火焰（纯表现 ✓）
        Vec3 sun = this.sunPosition();
        server.sendParticles(ParticleTypes.FLAME, sun.x, sun.y, sun.z, 6, 1.1D, 0.4D, 1.1D, 0.05D);
        // ★ 作者 2026-10-18「优化下太阳の审判的特效」⇒ 太阳改成"亮核 + 球体 + 光晕环"三层 ✓
        //   ① 亮核：贴着球心的一大团近白热尘 —— 旧版全是 1.4~2.8 的尘、中心没有更亮的东西，
        //      整颗球读起来是"一团橙雾"✗；有了亮核才有"一颗太阳"的重心 ✓
        for (int k = 0; k < SUN_CORE_POINTS; k++) {
            double rr = SUN_RADIUS * 0.24D * Math.sqrt(Math.random());
            double theta = Math.random() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * Math.random() - 1.0D);
            dust(server,
                    sun.x + Math.sin(phi) * Math.cos(theta) * rr,
                    sun.y + Math.cos(phi) * rr,
                    sun.z + Math.sin(phi) * Math.sin(theta) * rr,
                    1.00F, 0.99F, 0.92F, 4.2F);
        }
        //   ② 光晕环：两圈反向自转的光带（隔 tick 出 ⇒ 开销减半 ✓）
        if (this.tickCount % 2 == 0) {
            double spin = this.tickCount * 0.035D;
            for (int ring = 0; ring < 2; ring++) {
                double rad = SUN_RADIUS * (ring == 0 ? 1.10D : 1.26D);
                double tilt = ring == 0 ? 0.42D : -0.28D;
                double dir = ring == 0 ? 1.0D : -1.0D;
                for (int k = 0; k < HALO_POINTS; k++) {
                    double a = spin * dir + k * Math.PI * 2.0D / HALO_POINTS;
                    dust(server,
                            sun.x + Math.cos(a) * rad,
                            sun.y + Math.sin(a * 2.0D + spin) * tilt,
                            sun.z + Math.sin(a) * rad,
                            1.00F, 0.80F, 0.34F, 2.6F);
                }
            }
        }
        // ★ 作者 2026-10-05：「将上方的**球体和发射的射线改为粒子状态**；
        //   球体的粒子密度**由内向外减少**，粒子颜色**内部深、外部浅**」✓
        //   ① 球体：半径 r = R·u² ⇒ 采样点被挤向圆心 ⇒ **内密外疏** ✓
        //      颜色按 u 分档：中心暗红 → 橙 → 最外亮黄白 ⇒ **内深外浅** ✓
        for (int k = 0; k < SUN_POINTS; k++) {
            double u = Math.random();                     // 0 = 球心、1 = 球面 ✓
            double rr = SUN_RADIUS * u * u;               // ← 内密外疏的关键 ✓
            double theta = Math.random() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * Math.random() - 1.0D);
            double dx = Math.sin(phi) * Math.cos(theta) * rr;
            double dy = Math.cos(phi) * rr;
            double dz = Math.sin(phi) * Math.sin(theta) * rr;
            sunDust(server, sun.x + dx, sun.y + dy, sun.z + dz, u);
        }
        // ② 射线：从太阳往外喷的粒子流 ✓
        //   [作者 2026-10-05]「发射的粒子流会移动，并且不是固定轨迹，采用随机的方式」✓
        //   旧版是 a = tickCount * 0.012 + b * 2pi/beams ⇒ 机械匀速旋转的固定扇形 ✗
        //   现在每一 tick 重掷方向/长度/仰角，并沿路径加垂直抖动 ⇒ 轨迹永不重复 ✓
        {
            double rr = this.radius();
            // [作者 2026-10-05]「射线减少 1/3，并且不要全在一个锥面上移动，中间也是可移动区域」✓
            //   以前落点固定成「水平 0.45~0.98 个半径、高度只差 ±2.6 格」⇒ 所有射线都贴在太阳
            //   那一层的**一个很扁的锥面**上飞，太阳正下方那一大块（"中间"）永远是空的 ✗
            //   现在落点改到**整个锥体内部**取：
            //     · 水平 d = rr·√u ⇒ 均匀落在圆盘内（中间密、边缘疏 ✓）
            //     · 高度从贴近阵面一路到贴近太阳 ⇒ 射线会穿过中间，整根锥体都被扫到 ✓
            //   数量是**定值**（RAY_STREAMS）✓ —— 以前拿"这一跳打了几个目标"去推条数，
            //   那等于让表现跟着命中走 ✗；作者 2026-10-05 定死"伤害与射线无关"，
            //   所以这里彻底断开：阵内有没有目标、打中几个，天上都是同样多的射线 ✓
            int beams = RAY_STREAMS;
            for (int b = 0; b < beams; b++) {
                double a = Math.random() * Math.PI * 2.0D;                   // 随机方向（每 tick 重掷）
                double d = rr * Math.sqrt(Math.random()) * 0.98D;            // 随机落点（圆盘内均匀）
                double drop = 0.6D + (FireSpellRules.T5_SUN_HEIGHT - 1.6D) * Math.random();  // 随机落点高度
                Vec3 to = new Vec3(this.getX() + Math.cos(a) * d,
                        this.getY() + 0.35D + drop,
                        this.getZ() + Math.sin(a) * d);
                Vec3 delta = to.subtract(sun);
                // 采样点数**夹在 3..RAY_STEPS_MAX**：旧版按长度 0.9~1.5 倍算，长射线能到 27 个点，
                // 12 条一起撒就是 300+ 粒子/tick ✗（作者 2026-10-18 说"优化"，
                // 这里既省开销、也让每条的亮度更集中 ✓）
                int steps = (int) Math.max(3.0D, Math.min(RAY_STEPS_MAX,
                        delta.length() * (0.55D + 0.35D * Math.random())));
                // 垂直抖动：中间散得最开、两端收回 ⇒ 每次轨迹都不一样 ✓
                double ox = (Math.random() - 0.5D) * 1.4D;
                double oz = (Math.random() - 0.5D) * 1.4D;
                for (int s = 0; s <= steps; s++) {
                    double u = s / (double) steps;
                    Vec3 q = sun.add(delta.scale(u));
                    double w = Math.sin(u * Math.PI) * 0.9D;
                    // 越靠外越亮（和球体一致：内深外浅 ✓）
                    sunDust(server, q.x + ox * w, q.y + (Math.random() - 0.5D) * 1.3D * w,
                            q.z + oz * w, 0.30D + 0.70D * u);
                }
                // 落点：法阵地面上一记小闪光 + 溅起的火星 ⇒ 射线才像"打下来"而不是"飘过去"✓
                double gx = this.getX() + Math.cos(a) * d;
                double gz = this.getZ() + Math.sin(a) * d;
                double gy = this.getY() + 0.28D;
                dust(server, gx, gy, gz, 1.00F, 0.90F, 0.56F, 3.0F);
                server.sendParticles(ParticleTypes.FLAME, gx, gy, gz, 3, 0.16D, 0.10D, 0.16D, 0.02D);
                server.sendParticles(ParticleTypes.SMALL_FLAME, gx, gy + 0.10D, gz, 2,
                        0.22D, 0.16D, 0.22D, 0.03D);
            }
        }
        // ③ 法阵里的粒子：贴地的火星带**缓慢自转** ✓
        //   （旧版是纯随机散点 ⇒ 看着"死"✗；现在每颗都挂在一条匀速转动的环带上 ✓）
        double sigilSpin = this.tickCount * 0.045D;
        for (int k = 0; k < SOLAR_SIGIL_POINTS; k++) {
            double a = sigilSpin + k * Math.PI * 2.0D / SOLAR_SIGIL_POINTS
                    + (Math.random() - 0.5D) * 0.25D;
            double d = this.radius() * (0.24D + 0.74D * Math.random());
            sunDust(server, this.getX() + Math.cos(a) * d,
                    this.getY() + 0.12D + Math.random() * 0.55D,
                    this.getZ() + Math.sin(a) * d, 0.55D + Math.random() * 0.45D);
        }
        // ④ 每秒（= 伤害跳）推一圈从阵心外扩到阵缘的光环 ⇒ "这一秒挨了一下"看得见 ✓
        int pulse = this.tickCount % FireSpellRules.T5_BEAM_INTERVAL_TICKS;
        if (pulse % 2 == 0) {
            double d = this.radius() * (pulse / (double) FireSpellRules.T5_BEAM_INTERVAL_TICKS);
            for (int k = 0; k < PULSE_POINTS; k++) {
                double a = k * Math.PI * 2.0D / PULSE_POINTS + sigilSpin;
                dust(server, this.getX() + Math.cos(a) * d, this.getY() + 0.22D,
                        this.getZ() + Math.sin(a) * d, 1.00F, 0.62F, 0.20F, 2.8F);
            }
        }
    }

    /** 阵内所有能打的生物 ✓。 */
    private java.util.List<LivingEntity> insideEntities(ServerLevel server, LivingEntity caster) {
        double r = this.radius();
        java.util.List<LivingEntity> list = new java.util.ArrayList<>();
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(this.getX() - r, this.getY() - 3.0D, this.getZ() - r,
                        this.getX() + r, this.getY() + FireSpellRules.T5_SUN_HEIGHT + 3.0D, this.getZ() + r),
                t -> FireSpellRules.hittable(caster, t))) {
            double dx = candidate.getX() - this.getX();
            double dz = candidate.getZ() - this.getZ();
            if (dx * dx + dz * dz <= r * r) {
                list.add(candidate);
            }
        }
        return list;
    }

    /** 收场：球形爆炸 + 强烈震动 + 开始分帧塌方 ✓。 */
    private void explode(ServerLevel server) {
        if (this.exploded) {
            return;
        }
        this.exploded = true;
        LivingEntity caster = server.getEntity(this.casterId()) instanceof LivingEntity living ? living : null;
        // 作者：「火球爆炸的半径为**火球高度的 1.5 倍**」——半径跟着火球高度走，
        // 说明爆炸中心就是火球本身，而不是脚下的法阵中心。
        Vec3 at = this.sunPosition();

        // 伤害：整个球体范围 ✓
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                FireSpellRules.uprightArea(at, FireSpellRules.T5_EXPLODE_RADIUS,
                        FireSpellRules.T5_EXPLODE_RADIUS),
                t -> FireSpellRules.hittable(caster, t))) {
            victim.invulnerableTime = 0;
            victim.hurt(server.damageSources().indirectMagic(this, caster), this.explodeDamage);
        }

        // 表现：大量火焰粒子往外炸 ✓
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z,
                8, 3.0D, 3.0D, 3.0D, 0.0D);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z,
                600, FireSpellRules.T5_EXPLODE_RADIUS * 0.55D, FireSpellRules.T5_EXPLODE_RADIUS * 0.45D,
                FireSpellRules.T5_EXPLODE_RADIUS * 0.55D, 0.55D);
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z,
                120, FireSpellRules.T5_EXPLODE_RADIUS * 0.5D, FireSpellRules.T5_EXPLODE_RADIUS * 0.4D,
                FireSpellRules.T5_EXPLODE_RADIUS * 0.5D, 0.3D);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 2.0D, at.z,
                140, FireSpellRules.T5_EXPLODE_RADIUS * 0.5D, 2.0D, FireSpellRules.T5_EXPLODE_RADIUS * 0.5D, 0.12D);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 8.0F, 0.85F);
        TNShockwaveEntity.blast(server, at, 4.0D + (float) this.shake() * 0.7D,
                (float) this.shake(), caster, 0.0F);

        // 塌方：留给后面十几 tick 分帧删 ✓
        this.layersLeft = (int) Math.ceil(FireSpellRules.T5_EXPLODE_RADIUS * 2.0D);
    }

    /**
     * 破坏方块：每 tick 删 {@link FireSpellRules#T5_DESTROY_LAYERS_PER_TICK} 个水平层 ✓
     *
     * <p>⚠️ 为什么分帧：半径 16 的球 ≈ 1.7 万方块 ✗，一 tick 删完主线程会卡几秒 ✗。
     * 分帧之后观感仍是"整片塌掉"✓，但不会顿 ✗
     */
    private void destroyLayers(ServerLevel server) {
        int radius = (int) Math.floor(FireSpellRules.T5_EXPLODE_RADIUS);
        Vec3 center = this.sunPosition();            // 球心 = 火球位置
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        int total = radius * 2 + 1;

        for (int n = 0; n < FireSpellRules.T5_DESTROY_LAYERS_PER_TICK && this.layersLeft > 0; n++) {
            int layer = total - this.layersLeft;            // 从下往上删 ✓
            this.layersLeft--;
            int dy = layer - radius;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // 球形（不是方形 ✗ —— 作者明确"攻击范围是一个球体"✓）
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    if (!server.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = server.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK)) {
                        continue;                               // 空气与基岩跳过 ✓
                    }
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        continue;                               // 流体不是"方块" ✓
                    }
                    // flag 2 = 不发邻居更新（只同步给客户端 ✓）—— 快，而且不会连锁 ✓
                    server.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    public static TNSolarJudgmentField spawn(ServerLevel level, ServerPlayer caster, double radius,
                                             int lifeTicks, float beamDamage, float explodeDamage,
                                             double shake, Vec3 at) {
        TNSolarJudgmentField field = new TNSolarJudgmentField(TNOrbEntities.SOLAR_JUDGMENT.get(), level);
        field.setPos(at);
        field.configure(caster, radius, lifeTicks, beamDamage, explodeDamage, shake);
        level.addFreshEntity(field);
        return field;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
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
        return distanceSqr < 200.0D * 200.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = this.radius() + 2.0D;
        return new AABB(this.getX() - r, this.getY() - 2.0D, this.getZ() - r,
                this.getX() + r, this.getY() + FireSpellRules.T5_SUN_HEIGHT + 4.0D, this.getZ() + r);
    }

    /**
     * 太阳"球体"的粒子数（每 tick ✓）。
     *
     * <p>⚠️ 2026-10-18 从 90 降到 66 —— 作者同时要求"优化特效"，
     * 而旧版整套是 ~400 粒子/tick（球 90 + 射线 12×约 20 + 阵内 60），
     * 卡的根源就在这儿 ✗。现在总数压到 ~250/tick，但多了亮核、光晕环、落点闪光 ✓
     */
    private static final int SUN_POINTS = 66;

    /**
     * 天上乱射的射线粒子**条数**（定值 ✓）。
     *
     * <p>与阵内有没有目标、打中几个**完全无关** ✓ —— 作者 2026-10-05：
     * 「伤害判定是位于法阵范围内，和射线是否命中无关」✓，射线只是表现 ✓。
     * 2026-10-18 从 12 降到 10（省开销；每条末端补了落点闪光，观感反而更实 ✓）。
     */
    private static final int RAY_STREAMS = 10;

    /** 每条射线最多采样几个点（越少越省；末端另有一颗放大 + 落点闪光补足 ✓）。 */
    private static final int RAY_STEPS_MAX = 12;

    /** 法阵里额外撒的粒子数（每 tick ✓）。 */
    private static final int SOLAR_SIGIL_POINTS = 44;

    /** 太阳亮核的粒子数（每 tick，近白热、大颗粒 ✓）。 */
    private static final int SUN_CORE_POINTS = 6;

    /** 每圈光晕环的粒子数（两圈，隔 tick 出 ✓）。 */
    private static final int HALO_POINTS = 14;

    /** 每秒外扩那圈光环的粒子数 ✓。 */
    private static final int PULSE_POINTS = 24;

    /** 太阳的半径（格 ✓）。 */
    private static final double SUN_RADIUS = 5.0D;

    /**
     * 撒一颗太阳粒子 —— 颜色<b>内深外浅</b> ✓（作者明确要求 ✗）。
     *
     * @param u 0 = 球心、1 = 球面 ⇒ 用来分档取色 + 决定粒子大小 ✓
     */
    private static void sunDust(net.minecraft.server.level.ServerLevel sl,
                                double x, double y, double z, double u) {
        float r;
        float g;
        float b;
        if (u < 0.30D) {
            r = 0.55F; g = 0.06F; b = 0.03F;      // 内部**深**（暗红）✓
        } else if (u < 0.60D) {
            r = 0.90F; g = 0.30F; b = 0.08F;      // 中段橙红 ✓
        } else if (u < 0.85D) {
            r = 1.00F; g = 0.62F; b = 0.18F;      // 外圈橙 ✓
        } else {
            r = 1.00F; g = 0.96F; b = 0.72F;      // 最外**浅**（亮黄白）✓
        }
        float scale = 1.4F + (float) u * 1.4F;    // 外面也更大一点 ✓
        dust(sl, x, y, z, r, g, b, scale);
    }

    /** 撒一颗指定颜色的尘 ✓（亮核 / 光晕环 / 落点闪光 / 外扩光环都用它）。 */
    private static void dust(ServerLevel sl, double x, double y, double z,
                             float r, float g, float b, float scale) {
        sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(r, g, b), scale),
                x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
    }
}
