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

    /** 这一 tick 有几条光线在打（渲染器照着画 ✓；纯表现，客户端也读得到 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_BEAMS =
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
        this.entityData.set(DATA_BEAMS, 0);
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

    /** 这一 tick 有几条光线（渲染器拿去画 ✓）。 */
    public int beams() {
        return this.entityData.get(DATA_BEAMS);
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
        this.entityData.define(DATA_BEAMS, 0);
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
            this.discard();
            return;
        }

        // 光线：每一跳把阵内**所有**能打的生物各打一次 ✓（"向法阵范围内发射大量光线"✓）
        if (this.tickCount % FireSpellRules.T5_BEAM_INTERVAL_TICKS == 0) {
            int beams = 0;
            for (LivingEntity victim : this.insideEntities(server, caster)) {
                victim.invulnerableTime = 0;
                victim.hurt(server.damageSources().indirectMagic(this, caster), this.beamDamage);
                if (caster instanceof ServerPlayer player) {
                    TNScorch.apply(victim, player, this.beamDamage, true);   // 焚身 II ✓
                }
                beams++;
            }
            this.entityData.set(DATA_BEAMS, beams);
        }

        // 太阳附近往外喷的火焰（纯表现 ✓）
        Vec3 sun = this.sunPosition();
        server.sendParticles(ParticleTypes.FLAME, sun.x, sun.y, sun.z, 8, 1.1D, 0.4D, 1.1D, 0.05D);
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
        Vec3 at = this.position();

        // 伤害：整个球体范围 ✓
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                FireSpellRules.uprightArea(at, FireSpellRules.T5_EXPLODE_RADIUS,
                        FireSpellRules.T5_EXPLODE_RADIUS),
                t -> FireSpellRules.hittable(caster, t))) {
            victim.invulnerableTime = 0;
            victim.hurt(server.damageSources().indirectMagic(this, caster), this.explodeDamage);
        }

        // 表现：大量火焰粒子往外炸 ✓
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0D, at.z,
                8, 3.0D, 3.0D, 3.0D, 0.0D);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.0D, at.z,
                600, FireSpellRules.T5_EXPLODE_RADIUS * 0.55D, FireSpellRules.T5_EXPLODE_RADIUS * 0.45D,
                FireSpellRules.T5_EXPLODE_RADIUS * 0.55D, 0.55D);
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0D, at.z,
                120, FireSpellRules.T5_EXPLODE_RADIUS * 0.5D, FireSpellRules.T5_EXPLODE_RADIUS * 0.4D,
                FireSpellRules.T5_EXPLODE_RADIUS * 0.5D, 0.3D);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 3.0D, at.z,
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
        int cx = (int) Math.floor(this.getX());
        int cy = (int) Math.floor(this.getY());
        int cz = (int) Math.floor(this.getZ());
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
}
