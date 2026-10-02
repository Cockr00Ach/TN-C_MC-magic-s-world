package com.tnc.tnc.light;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <b>光明龙</b> —— 光系第五条链「光龙」放出去的那条**向前冲刺的龙** ✓
 * （作者 2026-10-02："不要做成召唤物啊，我要释放出一条巨龙往前冲，触碰造成伤害" ✓）。
 *
 * <h2>它不是宠物 ✗ —— 是一发"活体弹道" ✓</h2>
 * <ul>
 *   <li><b>放出来就往前冲</b> ✓：召唤时写死方向 + 速度 + 能飞多久（{@link #charge} ✓），
 *       之后**直线飞行**、不跟人、不索敌、不悬停 ✗；</li>
 *   <li><b>碰到就伤</b> ✓：每 tick 用一条"按体长给的判定盒"扫一遍，
 *       每个敌人**整次冲刺只挨一下** ✓（{@link #hitThisCharge} ✓），命中有击退 + 金光爆开 ✓；</li>
 *   <li><b>撞墙/到点就散</b> ✓：前方方块是实心就爆开消失 ✓（不然会一头钻进山体里 ✗）；</li>
 *   <li>飞行途中**无敌** ✓（{@link #isInvulnerableTo}）—— 一发弹道不该被怪打断 ✗；</li>
 *   <li>只打敌对生物 ✓：不打玩家（含施法者）、不打天使/别的龙、不打阿波罗 ✓。</li>
 * </ul>
 *
 * <h2>动画 / 朝向</h2>
 * 全程播 {@code dash}（那段就是"低头前冲"✓，循环 ✓）；朝向每 tick 对齐飞行方向 ✓
 * —— 龙是**朝前冲**的，不是横着飘 ✗。
 *
 * <h2>尺寸（23 格长的模型）</h2>
 * 碰撞箱只给身体那一小段（2.5 × 2 格 ✓，免得卡墙 ✗）；剔除盒按体长自己撑 ✓
 * （{@link #getBoundingBoxForCulling} ✓，不然抬头只看一段时整条会消失 ✓）。
 */
public class TNDragonEntity extends Monster implements GeoEntity {

    /** 模型原长（格 ✓）—— 量自 geo（吻 z≈−168 → 尾焰 z≈+200 ⇒ 368 单位 = 23 格 ✓）。 */
    public static final double MODEL_LENGTH_BLOCKS = 23.0D;

    /** 渲染个头（×100 同步 ✓）—— 释放时由法术写入 ✓。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 档次 1..5 ✓（只用来区分/调试 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);

    /** 冲刺撞人的判定，横向给多宽（格 ✓）—— 纵向按体长给 ✓。 */
    private static final double SWEEP_WIDTH = 3.0D;
    /** 没显式给参数时（比如 /summon 出来的）用这套默认值 ✓。 */
    private static final double DEFAULT_SPEED = 1.4D;
    private static final int DEFAULT_TICKS = 60;
    private static final double DEFAULT_DAMAGE = 20.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** 施法者（伤害记在它头上 ✓ / 判"不打自己人" ✓）；可能为 null ✓。 */
    private UUID ownerId;
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeSpeed = DEFAULT_SPEED;
    private double chargeDamage = DEFAULT_DAMAGE;
    private int chargeTicks;
    /** 这一次冲刺已经扫过谁（按实体 id ✓）—— 同一个敌人只挨一下 ✓。 */
    private final Set<Integer> hitThisCharge = new HashSet<>();
    private boolean configured;

    public TNDragonEntity(EntityType<? extends TNDragonEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setNoGravity(true);
        this.noPhysics = true;                 // 弹道不受推挤/碰撞箱卡住 ✓（撞墙靠下面自己判 ✓）
        this.setInvulnerable(true);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    /** 一条龙不需要任何 AI ✓（不寻路、不选目标 ✓）。 */
    @Override
    protected void registerGoals() {
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 30);
        this.entityData.define(DATA_TIER, 3);
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public void setScale(double scale) {
        this.entityData.set(DATA_SCALE, (int) Math.round(Mth.clamp(scale, 0.05D, 4.0D) * 100.0D));
    }

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        this.entityData.set(DATA_TIER, Mth.clamp(tier, 1, 5));
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    /**
     * ★ 放出去：朝 {@code dir} 冲 {@code ticks} tick、每 tick 走 {@code speed} 格、
     * 碰到敌人打 {@code damage} ✓（法术在生成的那一刻调它 ✓）。
     */
    public void charge(Vec3 dir, double speed, int ticks, double damage) {
        Vec3 d = dir.lengthSqr() < 1.0E-6D ? Vec3.ZERO : dir.normalize();
        if (d == Vec3.ZERO) {
            d = new Vec3(0.0D, 0.0D, -1.0D);
        }
        this.chargeDir = d;
        this.chargeSpeed = Math.max(0.1D, speed);
        this.chargeTicks = Math.max(1, ticks);
        this.chargeDamage = Math.max(1.0D, damage);
        this.configured = true;
        this.faceDir(d);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Scale", this.entityData.get(DATA_SCALE));
        tag.putInt("Tier", this.tier());
        tag.putDouble("ChargeSpeed", this.chargeSpeed);
        tag.putDouble("ChargeDamage", this.chargeDamage);
        tag.putInt("ChargeTicks", this.chargeTicks);
        tag.putDouble("DirX", this.chargeDir.x);
        tag.putDouble("DirY", this.chargeDir.y);
        tag.putDouble("DirZ", this.chargeDir.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        if (tag.contains("Scale")) {
            this.entityData.set(DATA_SCALE, tag.getInt("Scale"));
        }
        if (tag.contains("Tier")) {
            this.setTier(tag.getInt("Tier"));
        }
        if (tag.contains("ChargeSpeed")) {
            this.chargeSpeed = tag.getDouble("ChargeSpeed");
            this.chargeDamage = tag.getDouble("ChargeDamage");
            this.chargeTicks = tag.getInt("ChargeTicks");
            this.chargeDir = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"),
                    tag.getDouble("DirZ"));
            this.configured = true;
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** 一发弹道不该被打断 ✗（也别被别的怪当靶子 ✓）。 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    /** 剔除盒按**体长**撑 ✗（23 格长的模型，光看碰撞箱会在抬头时整条消失 ✓）。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double len = MODEL_LENGTH_BLOCKS * this.scale();
        return this.getBoundingBox().inflate(SWEEP_WIDTH, len * 0.35D, len * 0.55D);
    }

    /** 冲刺扫过的判定盒 ✓（以龙自己为中心、按体长给 ✓）。 */
    private AABB sweepBox() {
        double len = MODEL_LENGTH_BLOCKS * this.scale();
        return new AABB(this.getX() - len * 0.5D, this.getY() - 1.5D, this.getZ() - len * 0.5D,
                this.getX() + len * 0.5D, this.getY() + 2.5D, this.getZ() + len * 0.5D);
    }

    /** 这条龙能打谁 ✓（只敌对生物；不打玩家/施法者/天使/别的龙/阿波罗 ✓）。 */
    public boolean isValidLightTarget(LivingEntity target) {
        if (!(target instanceof Monster)) {
            return false;
        }
        if (target instanceof TNDragonEntity || target instanceof TNFightingAngelEntity
                || target instanceof TNAngelEntity
                || target instanceof com.tnc.tnc.boss.TNApolloEntity) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(target.getUUID());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!this.configured) {
            // /summon 出来的：沿当前朝向自己冲一段 ✓（不然它原地不动，看着像坏了 ✗）
            this.charge(this.getLookAngle(), DEFAULT_SPEED, DEFAULT_TICKS, DEFAULT_DAMAGE);
        }
        this.chargeTicks--;
        if (this.chargeTicks <= 0) {
            this.burst(level, 30, 1.2D);
            this.discard();
            return;
        }
        // 前方是实心方块 ⇒ 撞墙爆开 ✓（noPhysics 会直接穿过去，所以要自己判 ✗）
        Vec3 next = this.position().add(this.chargeDir.scale(Math.max(1.0D, this.chargeSpeed)));
        BlockPos pos = BlockPos.containing(next.x, next.y + this.getBbHeight() * 0.5D, next.z);
        if (!level.getBlockState(pos).isAir()) {
            this.burst(level, 40, 1.5D);
            this.discard();
            return;
        }
        // 往前冲 ✓ + 朝向对齐 ✓
        this.setDeltaMovement(this.chargeDir.scale(this.chargeSpeed));
        this.hurtMarked = true;
        this.faceDir(this.chargeDir);
        // 拖尾（光尘 + 偶尔金光 ✓）
        Vec3 tail = this.position().subtract(this.chargeDir.scale(MODEL_LENGTH_BLOCKS * this.scale() * 0.35D));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                tail.x, tail.y + 1.0D, tail.z, 3, 1.0D, 0.6D, 1.0D, 0.02D);
        if (this.tickCount % 4 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 4, 0.8D, 0.6D, 0.8D, 0.03D);
        }
        // 碰到就伤 ✓（一次冲刺对同一个敌人只打一下 ✓）
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
            if (!this.isValidLightTarget(victim) || !this.hitThisCharge.add(victim.getId())) {
                continue;
            }
            LivingEntity caster = this.ownerId == null ? this : level.getPlayerByUUID(this.ownerId);
            victim.hurt(level.damageSources().indirectMagic(this, caster == null ? this : caster),
                    (float) this.chargeDamage);
            victim.push(this.chargeDir.x * 2.0D, 0.45D, this.chargeDir.z * 2.0D);
            this.hurtMarked = true;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                    18, 0.5D, 0.6D, 0.5D, 0.08D);
        }
    }

    private void burst(ServerLevel level, int count, double spread) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                this.getX(), this.getY() + 1.0D, this.getZ(), count, spread, spread * 0.7D, spread, 0.08D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                this.getX(), this.getY() + 1.0D, this.getZ(), count / 2, spread, spread * 0.7D, spread, 0.10D);
    }

    /** 把身体/头都转向飞行方向 ✓（这条龙是朝前冲的，不是横着飘 ✗）。 */
    private void faceDir(Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))
                * (180.0D / Math.PI)));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 全程 dash ✓（那段动画本来就是"低头前冲"✓，循环着播正好 ✓）
        controllers.add(new AnimationController<>(this, "move", 5, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop("dash"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 资源（作者手改的那条东方光明龙 ✓）----
    public static net.minecraft.resources.ResourceLocation modelResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/dragon.geo.json");
    }

    public static net.minecraft.resources.ResourceLocation textureResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dragon_bedrock.png");
    }

    public static net.minecraft.resources.ResourceLocation animationResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/dragon.animation.json");
    }
}
