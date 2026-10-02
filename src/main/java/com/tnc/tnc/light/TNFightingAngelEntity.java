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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 战斗天使 —— <b>光法第 4 条链「召唤天使」的召唤物</b>（作者 2026-10-02 指定 ✓）。
 *
 * <h2>会飞 ✓（作者 2026-10-02："这个召唤出来的天使要能飞的" ✓）</h2>
 * 它不是"贴着地走路的天使" ✗ —— 三条一起改才算真的会飞 ✓：
 * <ol>
 *   <li><b>{@code setNoGravity(true)}</b> ⇒ 不往下掉（不然飞起来立刻被重力拽回地面 ✗）；</li>
 *   <li><b>{@link FlyingMoveControl}</b>（取代原版走路用的 {@code MoveControl}）⇒ 位移是<b>三轴</b>的，
 *       会自己升降到目标高度 ✓（蜜蜂/恶魂那一套 ✓）；</li>
 *   <li><b>{@link FlyingPathNavigation}</b>（取代 {@code GroundPathNavigation}）⇒ 寻路可以<b>穿空气</b> ✓
 *       （不然它只会沿地面绕路，你一飞高它就卡在下面仰头 ✗）。</li>
 * </ol>
 * 所以"跟着主人"也是三维的：主人飞上天，它跟着升空悬停在主人斜上方 1.8 格 ✓；
 * 打怪时是俯冲下去近战 ✓（见 {@link #aiStep()} 里那段垂直助力 ✓）。
 *
 * <h2>动作</h2>
 * <ul>
 *   <li>空中移动播 {@code fly} ✓（翅拍得快 ✓）、地面移动播 {@code walk} ✓、静止播 {@code idle} ✓；</li>
 *   <li>攻击触发 {@code attack} ✓、施法触发 {@code cast} ✓、死亡触发 {@code death} ✓。</li>
 * </ul>
 *
 * <h2>其它</h2>
 * <ul>
 *   <li><b>会跟着主人</b> ✓：超过 16 格瞬移到主人身边，4～16 格自己飞过去 ✓；</li>
 *   <li><b>只打敌对生物</b> ✓（{@link Monster}），<b>不打玩家、不打别的天使</b> ✓；</li>
 *   <li><b>主人的伤害免了</b> ✓（{@link #hurt}）—— 免得放技能时被自己人打死 ✗；</li>
 *   <li>主人没了（死亡/换维度）就自己消散 ✓。</li>
 * </ul>
 */
public class TNFightingAngelEntity extends Monster implements GeoEntity {

    /** 档位 1..5（t1 最小、t5 最大 ✓）—— 渲染器按它放大（0.70 → 1.40 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNFightingAngelEntity.class, EntityDataSerializers.INT);

    /** 跟着主人的"舒适距离"（格）—— 悬停在这个距离的斜上方 ✓。 */
    private static final double HOVER_DISTANCE = 2.2D;
    private static final double HOVER_HEIGHT = 1.8D;
    /** 超过这个距离直接瞬移 ✓（和原版宠物一样 ✓）。 */
    private static final double TELEPORT_DISTANCE = 16.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    /** 寿命（tick ✓）—— 法术召唤时写入（0 = 不自动消散 ✓，给 /summon 调试用 ✓）。 */
    private int lifeTicks;

    public TNFightingAngelEntity(EntityType<? extends TNFightingAngelEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        // ★ 会飞：无重力 + 三轴移动 ✓（这两行是"能飞"的地基，缺一个就变成"飘着走路" ★）
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    /** ★ 会飞的第三块拼图：寻路必须能穿空气 ✓（默认是 {@code GroundPathNavigation} ✗）。 */
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
        this.entityData.define(DATA_TIER, 1);
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

    public UUID owner() {
        return this.ownerId;
    }

    /** 召唤时写入：活多少 tick 后自己消散 ✓（0 = 永久 ✓）。 */
    public void setLifetime(int ticks) {
        this.lifeTicks = Math.max(0, ticks);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Tier", this.tier());
        tag.putInt("Life", this.lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        if (tag.contains("Tier")) {
            this.setTier(tag.getInt("Tier"));
        }
        this.lifeTicks = tag.getInt("Life");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 飞过去近战 ✓（路径由 FlyingPathNavigation 给，所以空中/地面都追得到 ✓）
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        // 只打敌对生物 ✓：不打玩家、不打别的战斗天使/光天使、不打光系 Boss 阿波罗 ✓
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true,
                e -> !(e instanceof TNFightingAngelEntity)
                        && !(e instanceof TNAngelEntity)
                        && !(e instanceof com.tnc.tnc.boss.TNApolloEntity)));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * 视锥剔除用的包围盒 ✓ —— <b>被缩放过的模型必须自己撑大它</b> ✗。
     *
     * <p>原版判"要不要画"用的是**碰撞箱**（0.9 × 2.2 格）✗，而 t5 那只渲染放大到 1.4 倍、
     * 模型高 2.44 × 1.4 ≈ 3.4 格 ⇒ 你抬头只看上半身时整只天使会**突然消失** ✗
     * （和第二条链那尊雕像天使同一个坑 ✓，见 {@code TNAngelEntity.getBoundingBoxForCulling} ✓）。
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        double s = 0.7D + 0.175D * (this.tier() - 1);
        return this.getBoundingBox().inflate(0.9D * s, 1.3D * s, 0.9D * s);
    }

    /** 主人的伤害不生效 ✓（自己人 ✗）。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (this.ownerId != null && attacker != null && this.ownerId.equals(attacker.getUUID())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * 跟随 + 悬停 + 俯冲 ✓ —— <b>全程三维</b>（这就是"会飞"和"会跳"的区别 ✓）。
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // ★ 寿命：召唤出来的天使到点自己消散 ✓（走 tick 递减而不是比 tickCount ✗ —— 读档后 tickCount 会归零 ✗）
        if (this.lifeTicks > 0) {
            this.lifeTicks--;
            if (this.lifeTicks == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        this.getX(), this.getY() + this.getBbHeight() * 0.6D, this.getZ(),
                        26, 0.5D, 0.9D, 0.5D, 0.06D);
                this.discard();
                return;
            }
        }
        // 飞行时拖一点白/淡黄光点 ✓（"会飞的光之造物"看得出来是在飞 ✗ 不然高空里很难发现它 ✗）
        if (this.tickCount % 4 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    this.getX(), this.getY() + this.getBbHeight() * 0.55D, this.getZ(),
                    1, 0.22D, 0.22D, 0.22D, 0.005D);
        }
        if (this.ownerId == null) {
            return;
        }
        Player owner = level.getPlayerByUUID(this.ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
            this.discard();
            return;
        }
        double d = this.distanceTo(owner);
        if (d > TELEPORT_DISTANCE) {
            // 太远了直接瞬移（原版宠物那套 ✓）—— 注意连高度一起带过去 ✓
            this.getNavigation().stop();
            this.teleportTo(owner.getX(), owner.getY() + HOVER_HEIGHT, owner.getZ());
        } else if (this.getTarget() == null) {
            // 没敌人 ⇒ 飞回主人斜上方悬停 ✓（主人飞高它跟着升 ✓，因为它用的是飞行移动控制 ✓）
            double angle = Math.toRadians(owner.getYRot() + 135.0F);
            double tx = owner.getX() + Math.cos(angle) * HOVER_DISTANCE;
            double tz = owner.getZ() + Math.sin(angle) * HOVER_DISTANCE;
            double ty = owner.getY() + HOVER_HEIGHT + Math.sin(this.tickCount * 0.08D) * 0.15D;
            this.getMoveControl().setWantedPosition(tx, ty, tz, 1.0D);
        } else {
            // 有敌人 ⇒ 给一点垂直助力，免得目标在地面时它悬在头顶够不着 ✗
            LivingEntity target = this.getTarget();
            double dy = (target.getY() + target.getBbHeight() * 0.5D)
                    - (this.getY() + this.getBbHeight() * 0.5D);
            if (Math.abs(dy) > 0.6D) {
                this.setDeltaMovement(this.getDeltaMovement()
                        .add(0.0D, Mth.clamp(dy * 0.05D, -0.14D, 0.14D), 0.0D));
                this.hurtMarked = true;
            }
            if (this.tickCount % 20 == 0) {
                this.triggerAnim("action", "attack");
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        this.triggerAnim("action", "death");
        super.die(source);
    }

    /** 空中在动吗 ✓（{@code walkAnimation} 只看水平位移，飞行时还要看竖直分量 ✓）。 */
    private boolean isMovingInAir() {
        if (this.walkAnimation.isMoving()) {
            return true;
        }
        Vec3 v = this.getDeltaMovement();
        return v.horizontalDistanceSqr() + v.y * v.y > 0.0025D;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 体态 ✓：腾空移动 → fly（翅拍得快 ✓）、贴地移动 → walk、静止 → idle ✓
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            if (!this.isMovingInAir()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop(
                    this.onGround() ? "walk" : "fly"));
        }));
        // 一次性动作 ✓
        AnimationController<TNFightingAngelEntity> action = new AnimationController<>(this, "action", 2,
                state -> PlayState.STOP);
        action.triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"));
        action.triggerableAnim("cast", RawAnimation.begin().thenPlay("cast"));
        action.triggerableAnim("death", RawAnimation.begin().thenPlay("death"));
        controllers.add(action);
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

    // ---- 资源 ----
    public static net.minecraft.resources.ResourceLocation modelResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/fightingangel.geo.json");
    }

    public static net.minecraft.resources.ResourceLocation textureResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/fightingangel_bedrock.png");
    }

    public static net.minecraft.resources.ResourceLocation animationResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/fightingangel.animation.json");
    }
}
