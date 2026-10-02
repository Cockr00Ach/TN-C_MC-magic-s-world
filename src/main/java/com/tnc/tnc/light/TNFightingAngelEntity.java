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

    /** 档位 1..5（t1 最小、t5 最大 ✓）—— 只影响名字/属性表 ✓。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNFightingAngelEntity.class, EntityDataSerializers.INT);

    /**
     * 渲染个头（×100 同步 ✓）—— 作者 2026-10-02 定：
     * t1 一只小小 / t3 五只"现在这样的" / t4 十五只 / t5 三只大只的 ✓
     * ⇒ 个头**不再等于档位** ✗（t4 是十五只小的、t5 才是大的 ✓），所以法术召唤时写进来 ✓。
     */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNFightingAngelEntity.class, EntityDataSerializers.INT);

    /** 跟着主人的"舒适距离"（格）—— 悬停在这个距离的斜上方 ✓。 */
    private static final double HOVER_DISTANCE = 2.2D;
    private static final double HOVER_HEIGHT = 1.8D;
    /** 超过这个距离直接瞬移 ✓（和原版宠物一样 ✓）。 */
    private static final double TELEPORT_DISTANCE = 16.0D;

    // ------------------------------------------------------------------
    //  ★ 天使也会放光线链（作者 2026-10-02："可以轮流着放光线链的 t1 和 t4" ✓）
    // ------------------------------------------------------------------

    /** 整队合计多久放一发（tick ✓）—— 十五只一起放会糊成一片 ✗，所以按**队伍**限流 ✓。 */
    private static final int GROUP_CAST_INTERVAL = 40;      // 2 秒
    /** 单个天使两发之间至少隔多久（tick ✓）—— 免得一只把整队的额度吃光 ✗。 */
    private static final int ANGEL_CAST_COOLDOWN = 200;     // 10 秒
    /** 召唤出来之后第一发的延迟（tick ✓）—— 先站稳再放 ✓。 */
    private static final int FIRST_CAST_DELAY = 60;
    /** 超过这个距离就不放（格 ✓）。 */
    private static final double CAST_RANGE = 24.0D;
    /** ★ t4「圣光天降」的法阵**缩小五倍**（作者 2026-10-02 指定 ✓）：22.5 格半径 → 4.5 格 ✓。 */
    private static final double DESCENT_CIRCLE_SCALE = 0.2D;

    /**
     * 每个主人一份"轮流表"：{@code [下一次可以放的刻, 下一招是不是 t4]} ✓。
     *
     * <p><b>为什么按主人记而不是按天使记</b>：作者要的是"**轮流**着放" ✓ ——
     * 十五只各自计冷却就会同一秒里十道光柱一起糊上来 ✗；
     * 按主人限流则变成"这队天使轮着来"✓，数量越多、总节奏不变 ✓（每只的间隔自然拉长 ✓）。
     */
    private static final java.util.Map<UUID, long[]> GROUP_CASTS = new java.util.HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    /** 寿命（tick ✓）—— 法术召唤时写入（0 = 不自动消散 ✓，给 /summon 调试用 ✓）。 */
    private int lifeTicks;
    /** 距离自己下一发还有多久（tick ✓）。 */
    private int castCooldown = FIRST_CAST_DELAY;

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
        this.entityData.define(DATA_SCALE, 100);
    }

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        this.entityData.set(DATA_TIER, Mth.clamp(tier, 1, 5));
    }

    /** 渲染个头（1.0 = 模型原尺寸 ✓）。 */
    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public void setScale(double scale) {
        this.entityData.set(DATA_SCALE, (int) Math.round(Mth.clamp(scale, 0.1D, 4.0D) * 100.0D));
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
        tag.putInt("Scale", this.entityData.get(DATA_SCALE));
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
        if (tag.contains("Scale")) {
            this.entityData.set(DATA_SCALE, tag.getInt("Scale"));
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
        double s = this.scale();
        return this.getBoundingBox().inflate(0.9D * s, 1.3D * s, 0.9D * s);
    }

    /**
     * ★ <b>天使放的光线能打谁</b> ✓ —— 供 {@code TNLightBeamMechanics.isHostile} 用 ✓。
     *
     * <p>尺子和它的**近战目标**一模一样 ✓：敌对生物（{@link Monster}）、不是别的天使、
     * 不是光系 Boss 阿波罗 ✓；<b>绝不打玩家（含主人）</b> ✗ —— 召唤物误伤主人是最糟的手感 ✗。
     */
    public boolean isValidLightTarget(LivingEntity target) {
        if (!(target instanceof Monster)) {
            return false;
        }
        if (target instanceof TNFightingAngelEntity || target instanceof TNAngelEntity) {
            return false;
        }
        if (target instanceof com.tnc.tnc.boss.TNApolloEntity) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(target.getUUID());
    }

    /**
     * ★ 轮流放光线链的 t1 / t4 ✓（作者 2026-10-02 ✓）。
     *
     * <p>节奏是**按主人限流**的（见 {@link #GROUP_CASTS}）：整队每 {@link #GROUP_CAST_INTERVAL}
     * 放一发，谁先轮到谁放，下一发换另一招 ⇒ 表现上就是"这队天使轮着放光线和圣光天降" ✓。
     * 单只天使自己还有 {@link #ANGEL_CAST_COOLDOWN} 的间隔 ✓（免得一只连放 ✗）。
     *
     * <p>t4「圣光天降」的法阵按作者要求**缩小五倍** ✓（{@link #DESCENT_CIRCLE_SCALE} ✓）。
     */
    private void tryCastLight(ServerLevel level, LivingEntity target) {
        if (this.castCooldown > 0) {
            this.castCooldown--;
            return;
        }
        if (target == null || !target.isAlive() || this.distanceTo(target) > CAST_RANGE) {
            return;
        }
        long now = level.getGameTime();
        long[] slot = GROUP_CASTS.computeIfAbsent(this.ownerId, k -> new long[]{now + FIRST_CAST_DELAY, 0L});
        if (now < slot[0]) {
            this.castCooldown = 10;                 // 10 tick 后再问一次（别每 tick 查表 ✗）
            return;
        }
        boolean descent = slot[1] == 1L;
        slot[1] = descent ? 0L : 1L;                // 下一发换另一招 ✓（t1 ↔ t4 轮流 ✓）
        slot[0] = now + GROUP_CAST_INTERVAL;
        if (GROUP_CASTS.size() > 64) {
            GROUP_CASTS.clear();                    // 只是防漏（键是玩家 UUID，不会长到哪去 ✓）
        }
        this.castCooldown = ANGEL_CAST_COOLDOWN;
        // 先扭头对准目标 ✓（光线链是"锥形索敌"：朝向对了才锁得到它 ✓）
        this.lookAt(target, 30.0F, 30.0F);
        TNLightBeamMechanics.onSpellCast(this, descent ? "holy_light_descent" : "light_beam",
                1.0D, descent ? DESCENT_CIRCLE_SCALE : 1.0D);
        this.triggerAnim("action", "cast");
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
        // ★ 有目标就轮流放光线链 ✓（t1 / t4 交替，队伍限流 ✓）—— 放在跟随逻辑之前 ✓
        this.tryCastLight(level, this.getTarget());
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
            // 近战挥击的动画 ✓（光线那招有自己的 cast 动画 ✓，见 tryCastLight ✓）
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
