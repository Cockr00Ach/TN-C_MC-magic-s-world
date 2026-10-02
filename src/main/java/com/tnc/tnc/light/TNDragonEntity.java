package com.tnc.tnc.light;

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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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
 * <b>光明龙</b> —— 光系第五条链「光龙」的召唤物 ✓（作者 2026-10-02："新增加一条光龙链" ✓）。
 *
 * <p>模型/贴图/动画就是那条**东方光明龙**（{@code geo/entity/dragon.geo.json} ✓ 作者手改的 ✓、
 * 贴图 {@code dragon_bedrock.png} ✓、动画 {@code dragon.animation.json} 的 {@code idle}/{@code dash} ✓）。
 *
 * <h2>行为（这条龙最要紧的是"冲刺"✓）</h2>
 * <ul>
 *   <li><b>会飞</b> ✓：无重力 + {@link FlyingMoveControl} + {@link FlyingPathNavigation}（和战斗天使同一套 ✓）；</li>
 *   <li><b>跟着主人</b> ✓：主人身边悬停（超过 24 格直接瞬移过去 ✓）；</li>
 *   <li><b>有敌人就低头冲刺</b> ✓：每 {@link #DASH_GAP} tick 冲一次，一次 {@link #DASH_LENGTH} tick，
 *       沿冲刺方向高速前进并播 {@code dash} 动画 ✓，路上用一条"扫过判定盒"把敌人各扫一次 ✓
 *       （判定盒按**体长**给 ⇒ 龙越大扫得越宽 ✓）；</li>
 *   <li><b>寿命</b> ✓：召唤时写死，到点白光一散自己消散 ✓；主人没了也消散 ✓。</li>
 * </ul>
 *
 * <h2>尺寸（23 格长的模型，两个数要配套 ✓）</h2>
 * <ul>
 *   <li><b>碰撞箱</b>只给身体那一小段（2.5 × 2 格 ✓）—— 整条龙都进碰撞箱会卡墙 ✗（作者的模型长 23 格 ✓）；</li>
 *   <li><b>剔除盒</b>必须按体长自己撑大 ✗（{@link #getBoundingBoxForCulling} ✓）——
 *       不然抬头只看一段时整条龙会突然消失（天使/雕像那两个都踩过 ✓）；</li>
 *   <li>渲染缩放由法术写进实体（{@code setScale ✓}，t3 0.30 / t4 0.40 / t5 0.45 ✓）。</li>
 * </ul>
 */
public class TNDragonEntity extends Monster implements GeoEntity {

    /** 模型原长（格 ✓）—— 量自 geo（吻 z≈−168 → 尾焰 z≈+200 ⇒ 368 单位 = 23 格 ✓）。 */
    public static final double MODEL_LENGTH_BLOCKS = 23.0D;

    /** 渲染个头（×100 同步 ✓）—— 法术召唤时写入 ✓。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 档次 1..5 ✓ —— 只用来做"同档覆盖"（再放一次 t3 先把上一批 t3 送走 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 正在冲刺吗（0/1 ✓）—— 只用来驱动动画 ✓（能不能打是服务端自己算的 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_DASH =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);

    /** 冲刺持续多久（tick ✓）。 */
    private static final int DASH_LENGTH = 26;
    /** 两次冲刺之间隔多久（tick ✓）—— 冲完先绕一圈再冲，才像活物 ✓。 */
    private static final int DASH_GAP = 70;
    /** 第一次冲刺前先等一下（tick ✓）。 */
    private static final int FIRST_DASH_DELAY = 30;
    /** 冲刺时每 tick 走多远（格 ✓）—— 比它自己的寻路快得多 ⇒ 看起来就是"扑"过去 ✓。 */
    private static final double DASH_SPEED = 1.35D;
    /** 超过这个距离直接瞬移到主人身边（格 ✓）。 */
    private static final double TELEPORT_DISTANCE = 24.0D;
    /** 悬停在主人身边的距离 / 高度（格 ✓）。 */
    private static final double HOVER_DISTANCE = 6.0D;
    private static final double HOVER_HEIGHT = 3.0D;
    /** 冲刺撞人的判定，横向给多宽（格 ✓）—— 纵向按体长给 ✓（见 {@link #sweepBox()}）。 */
    private static final double SWEEP_WIDTH = 3.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    /** 寿命（tick ✓；0 = 不自动消散，给 /summon 调试用 ✓）。 */
    private int lifeTicks;
    /** 冲刺撞一下多少伤害（召唤时写入 ✓）。 */
    private double dashDamage = 10.0D;
    private int dashCooldown = FIRST_DASH_DELAY;
    private int dashTicks;
    private Vec3 dashDir = Vec3.ZERO;
    /** 这一次冲刺已经扫过谁（按实体 id ✓）—— 一次冲刺对同一个敌人只打一下 ✓。 */
    private final Set<Integer> dashHit = new HashSet<>();

    public TNDragonEntity(EntityType<? extends TNDragonEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 30);
        this.entityData.define(DATA_TIER, 3);
        this.entityData.define(DATA_DASH, 0);
    }

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        this.entityData.set(DATA_TIER, Mth.clamp(tier, 1, 5));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 目标只用来"找谁冲" ✓ —— 追人/咬人交给冲刺那一套（见 aiStep ✓），不用 MeleeAttackGoal ✗
        // 尺子就是 isValidLightTarget ✓（只敌对生物、不打主人/天使/阿波罗 ✓）
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true,
                this::isValidLightTarget));
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public void setScale(double scale) {
        this.entityData.set(DATA_SCALE, (int) Math.round(Mth.clamp(scale, 0.05D, 4.0D) * 100.0D));
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    public UUID owner() {
        return this.ownerId;
    }

    public void setLifetime(int ticks) {
        this.lifeTicks = Math.max(0, ticks);
    }

    public void setDashDamage(double damage) {
        this.dashDamage = Math.max(1.0D, damage);
    }

    public boolean isDashing() {
        return this.entityData.get(DATA_DASH) != 0;
    }

    private void setDashing(boolean dashing) {
        this.entityData.set(DATA_DASH, dashing ? 1 : 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Life", this.lifeTicks);
        tag.putInt("Scale", this.entityData.get(DATA_SCALE));
        tag.putInt("Tier", this.tier());
        tag.putDouble("DashDamage", this.dashDamage);
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
        this.lifeTicks = tag.getInt("Life");
        if (tag.contains("DashDamage")) {
            this.dashDamage = tag.getDouble("DashDamage");
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * 剔除盒必须按**体长**撑 ✗（23 格长的模型，光看碰撞箱会在抬头时整条消失 ✓）。
     * 只管剔除、不影响碰撞/推挤 ✓。
     */
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

    /** 这条龙的光能打谁 ✓（和战斗天使同一把尺子 ✓：只打敌对生物，绝不打主人 ✗）。 */
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
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (this.ownerId != null && attacker != null && this.ownerId.equals(attacker.getUUID())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        // ★ 寿命（走 tick 递减 ✓ —— 读档后 tickCount 会归零 ✗）
        if (this.lifeTicks > 0) {
            this.lifeTicks--;
            if (this.lifeTicks == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        this.getX(), this.getY() + 1.0D, this.getZ(), 40, 1.2D, 1.2D, 1.2D, 0.08D);
                this.discard();
                return;
            }
        }
        // 光尘拖尾 ✓（23 格长的东西在高空很容易看不见 ✗）
        if (this.tickCount % 3 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    this.getX(), this.getY() + 0.8D, this.getZ(), 2, 0.8D, 0.5D, 0.8D, 0.01D);
        }
        if (this.ownerId == null) {
            return;
        }
        Player owner = level.getPlayerByUUID(this.ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
            this.discard();
            return;
        }
        LivingEntity target = this.getTarget();
        if (this.dashTicks > 0) {
            this.dashStep(level);
        } else {
            this.setDashing(false);
            if (this.dashCooldown > 0) {
                this.dashCooldown--;
            }
            if (target != null && target.isAlive() && this.dashCooldown <= 0) {
                this.startDash(target);
            } else if (target == null) {
                this.hoverNear(owner);
            }
        }
    }

    /** 没敌人的时候：绕回主人身边悬停 ✓（主人飞高它跟着升 ✓）。 */
    private void hoverNear(Player owner) {
        double d = this.distanceTo(owner);
        if (d > TELEPORT_DISTANCE) {
            this.getNavigation().stop();
            this.teleportTo(owner.getX(), owner.getY() + HOVER_HEIGHT, owner.getZ());
            return;
        }
        double angle = Math.toRadians(owner.getYRot() + 140.0F);
        this.getMoveControl().setWantedPosition(
                owner.getX() + Math.cos(angle) * HOVER_DISTANCE,
                owner.getY() + HOVER_HEIGHT,
                owner.getZ() + Math.sin(angle) * HOVER_DISTANCE, 1.0D);
    }

    /** 开始一次冲刺：方向 = 冲向目标 ✓（顺带把身体转过去 ✓）。 */
    private void startDash(LivingEntity target) {
        Vec3 dir = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D));
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = this.getLookAngle();
        }
        this.dashDir = dir.normalize();
        this.dashTicks = DASH_LENGTH;
        this.dashHit.clear();
        this.setDashing(true);
        this.getNavigation().stop();
        this.faceDir(this.dashDir);
    }

    /** 冲刺中：沿方向高速推进 + 扫过谁就打谁 ✓。 */
    private void dashStep(ServerLevel level) {
        this.dashTicks--;
        this.setDeltaMovement(this.dashDir.scale(DASH_SPEED));
        this.hurtMarked = true;
        this.faceDir(this.dashDir);
        if (this.dashTicks <= 0) {
            this.dashCooldown = DASH_GAP;
            this.setDashing(false);
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
            if (!this.isValidLightTarget(victim) || !this.dashHit.add(victim.getId())) {
                continue;
            }
            victim.hurt(level.damageSources().indirectMagic(this, this), (float) this.dashDamage);
            victim.push(this.dashDir.x * 1.6D, 0.35D, this.dashDir.z * 1.6D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                    14, 0.4D, 0.5D, 0.4D, 0.06D);
        }
    }

    /** 把身体/头都转向某个方向 ✓（冲刺时整条龙要冲着目标 ✓）。 */
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
        // 冲刺播 dash、其余播 idle ✓（两段都是循环 ✓，所以来回切不会"卡在最后一帧"✗）
        controllers.add(new AnimationController<>(this, "move", 5, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop(
                        this.isDashing() ? "dash" : "idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
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
