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
 * <b>光明龙</b> —— 光系第五条链「光龙」<b>扔出去的那一条龙</b> ✓。
 *
 * <p>作者 2026-10-02：<b>"我要的龙不是怪，你把他怪给我删了，我要的是跟雷球一样，扔出去，
 * 一条龙冲出去"</b> ✓ —— 所以它和 {@code magic/TNThunderOrbEntity}（雷球）是**同一类东西**：
 * <ul>
 *   <li>基类是 <b>{@link Entity}</b> ✗ —— **不是 {@code Monster}/{@code Mob}** ✗：
 *       没有 AI、没有 goal、没有寻路、**不注册属性**（{@code TNNpcAttributes} 里根本没有它 ✓）；</li>
 *   <li>打不到它、推不动它、也不参与刷怪/驯服/刷怪蛋那一套 ✓；</li>
 *   <li>位置**自己每 tick 算**（和雷球一样直接 {@code setPos} ✓）：不受重力、不受方块碰撞 ✗；</li>
 *   <li>客户端只负责画（GeckoLib 模型 + {@code dash} 动画 ✓），逻辑全在服务端 ✓。</li>
 * </ul>
 *
 * <h2>行为：扔出去 → 冲 → 碰到就伤 → 撞墙/到点爆开 ✓</h2>
 * <ul>
 *   <li>{@link #charge} 由法术在生成的那一刻调用（方向 = 准星方向 ✓，多条按扇形散开 ✓）；</li>
 *   <li>每 tick 往前飞，并拿一条**按体长给的判定盒**扫一遍：碰到谁伤谁，
 *       每个敌人**整次冲刺只挨一下** ✓（击退 + 金光爆开 ✓，伤害记在施法者头上 ✓）；</li>
 *   <li>撞墙判的是**龙头**那个点（原点前方约 10.5 × 个头 格 ✓）—— "头撞到山才炸" ✓；</li>
 *   <li>飞满时长自己爆开消散 ✓；只打敌对生物（不打玩家/天使/别的龙/阿波罗 ✓）。</li>
 * </ul>
 *
 * <h2>尺寸（模型原长 23 格）</h2>
 * 碰撞箱只给身体那一小段（2.5 × 2 格 ✓，免得卡墙 ✗）；剔除盒按体长自己撑 ✓
 * （{@link #getBoundingBoxForCulling} ✓，不然抬头只看一段时整条会消失 ✓）。
 */
public class TNDragonEntity extends Entity implements GeoEntity {

    /** 模型原长（格 ✓）—— 量自 geo（吻 z≈−168 → 尾焰 z≈+200 ⇒ 368 单位 = 23 格 ✓）。 */
    public static final double MODEL_LENGTH_BLOCKS = 23.0D;

    /** 渲染个头（×100 同步 ✓）—— 扔出去时由法术写入 ✓。 */
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
    /** 施法者（伤害记在它头上 ✓）；可能为 null ✓。 */
    private UUID ownerId;
    private Vec3 chargeDir = new Vec3(0.0D, 0.0D, -1.0D);
    private double chargeSpeed = DEFAULT_SPEED;
    private double chargeDamage = DEFAULT_DAMAGE;
    private int chargeTicks;
    /** 这一次冲刺已经扫过谁（按实体 id ✓）—— 同一个敌人只挨一下 ✓。 */
    private final Set<Integer> hitThisCharge = new HashSet<>();
    private boolean configured;

    public TNDragonEntity(EntityType<? extends TNDragonEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);          // 和雷球一样：位置完全由我们每 tick 设定 ✓
        this.noPhysics = true;            // 不受方块碰撞（撞墙自己判 ✓）
    }

    @Override
    protected void defineSynchedData() {
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
     * ★ 扔出去：朝 {@code dir} 冲 {@code ticks} tick、每 tick 走 {@code speed} 格、
     * 碰到敌人打 {@code damage} ✓（法术在生成的那一刻调它 ✓）。
     */
    public void charge(Vec3 dir, double speed, int ticks, double damage) {
        Vec3 d = dir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, -1.0D) : dir.normalize();
        this.chargeDir = d;
        this.chargeSpeed = Math.max(0.1D, speed);
        this.chargeTicks = Math.max(1, ticks);
        this.chargeDamage = Math.max(1.0D, damage);
        this.configured = true;
        this.faceDir(d);
    }

    // ---- 投射物不做交互 ✓ ----
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
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
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
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
        if (!(target instanceof net.minecraft.world.entity.monster.Monster)) {
            return false;
        }
        // 注意：龙自己不是生物（投射物 ✓）⇒ 这里**不写** instanceof TNDragonEntity ✗
        // （那在 Java 里是"不相干的类型"编译错误 ✓；两条龙本来也不会互相打 ✓）
        if (target instanceof TNFightingAngelEntity || target instanceof TNAngelEntity
                || target instanceof com.tnc.tnc.boss.TNApolloEntity) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(target.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
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
        // 前方（**龙头**那个点 ✓）是实心方块 ⇒ 撞墙爆开 ✓
        double headAhead = 10.5D * this.scale() + Math.max(1.0D, this.chargeSpeed);
        Vec3 head = this.position().add(this.chargeDir.scale(headAhead));
        BlockPos pos = BlockPos.containing(head.x, head.y, head.z);
        if (!level.getBlockState(pos).isAir()) {
            this.burst(level, 40, 1.5D);
            this.discard();
            return;
        }
        // 位置**自己算**（和雷球一样直接 setPos ✓）：往前走 + 朝向对齐飞行方向 ✓
        this.setPos(this.getX() + this.chargeDir.x * this.chargeSpeed,
                this.getY() + this.chargeDir.y * this.chargeSpeed,
                this.getZ() + this.chargeDir.z * this.chargeSpeed);
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
        //   ★ t5 放大 10 倍后判定盒有 69 格宽 ✗ ⇒ 大龙**隔 tick 扫一次** ✓
        //     （它每 tick 才走 1.85 格，隔一 tick 也漏不掉谁 ✓；每个敌人反正只挨一下 ✓）
        boolean heavy = this.scale() >= 1.0D;
        if (!heavy || (this.tickCount & 1) == 0) {
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
                if (!this.isValidLightTarget(victim) || !this.hitThisCharge.add(victim.getId())) {
                    continue;
                }
                LivingEntity caster = this.ownerId == null ? null : level.getPlayerByUUID(this.ownerId);
                victim.hurt(level.damageSources().indirectMagic(this, caster == null ? this : caster),
                        (float) this.chargeDamage);
                victim.push(this.chargeDir.x * 2.0D, 0.45D, this.chargeDir.z * 2.0D);
                victim.hurtMarked = true;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                        18, 0.5D, 0.6D, 0.5D, 0.08D);
            }
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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
    protected void readAdditionalSaveData(CompoundTag tag) {
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
