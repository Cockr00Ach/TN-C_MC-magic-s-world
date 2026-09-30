package com.tnc.tnc.light;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * <b>光天使</b> —— 光系第二条链 t3/t4/t5（神光 / 天使降临 / 天使的悲悯）法阵中心召唤的那尊 ✓。
 *
 * <h2>为什么是"纯表现实体"（但是个 Monster）</h2>
 * GeckoLib 的 {@code GeoEntityRenderer} 只收 {@code LivingEntity} ✗，所以它必须是个生物 ✓ ——
 * 但这里把它做成**完全无害的雕像**：
 * <ul>
 *   <li>没有任何 AI goal ⇒ 不寻路、不攻击、不乱看 ✓（{@link #registerGoals()} 空实现 ✓）</li>
 *   <li>{@code setNoGravity(true)} + {@code noPhysics} ⇒ 悬在原地、不落地不卡墙 ✓</li>
 *   <li>无敌（{@link #isInvulnerableTo} 恒 true ＋ {@link #hurt} 恒 false）⇒ 玩家打不烂、Boss 也打不烂 ✓</li>
 *   <li>推不动别人（{@link #isPushable()} false ＋ {@link #doPush} / {@link #push} 空实现）✓</li>
 *   <li>寿命由 {@link #configure} 定，到了自己 {@code discard} ✓；不写进存档 ✓</li>
 * </ul>
 *
 * <h2>尺寸（作者：神光 2 格 / 天使降临 3 格 / 天使的悲悯 5 格）</h2>
 * 模型原生高 2.5 格（量自 {@code geo/entity/angel.geo.json} 的骨骼包围盒 y −1..39 ✓）⇒
 * 想要 H 格高就 {@code scale = H / 2.5} ✓（见 {@link #scaleFactor()} ✓，渲染器里用它缩放 ✓）。
 *
 * <h2>粒子</h2>
 * 每 tick 在自己身上撒**白 + 淡黄**的粒子 ✓（{@code end_rod} 纯白 ＋ {@code glow} 淡黄 ✓，
 * 作者："持续散发白色淡黄粒子" ✓）。只在服务端发（{@code ServerLevel.sendParticles} ✓）。
 */
public class TNAngelEntity extends Monster implements GeoEntity {

    /** 模型原生高度（格）—— 量自 angel.geo.json 的 y −1..39（40 单位）✓。 */
    public static final double MODEL_HEIGHT_BLOCKS = 2.5D;

    /** 存活时长（tick）与"想要多高"（格 × 100，同步给客户端缩放用 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNAngelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEIGHT =
            SynchedEntityData.defineId(TNAngelEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TNAngelEntity(EntityType<? extends TNAngelEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(true);
        this.xpReward = 0;
    }

    /** 生成后调用一次：活多少 tick、要几格高 ✓（2 / 3 / 5 ✓）。 */
    public void configure(int lifeTicks, double heightBlocks) {
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.entityData.set(DATA_HEIGHT, (int) Math.round(heightBlocks * 100.0D));
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    public double heightBlocks() {
        return this.entityData.get(DATA_HEIGHT) / 100.0D;
    }

    /** 渲染缩放 = 想要的高度 ÷ 模型原生高度 ✓（2 格 → 0.8 / 3 格 → 1.2 / 5 格 → 2.0 ✓）。 */
    public double scaleFactor() {
        return Math.max(0.1D, heightBlocks() / MODEL_HEIGHT_BLOCKS);
    }

    /** 纯雕像：一条 goal 都不加 ✓（作者要的是"法阵中心站着一尊天使"✗ 不是战斗宠物 ✓）。 */
    @Override
    protected void registerGoals() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：没有动画文件，也不播动作 ✓
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_LIFE, 200);
        this.entityData.define(DATA_HEIGHT, 200);
    }

    /** 无敌 + 不被推动 ✓。 */
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
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    /** 不会被当成目标/不会被怪物攻击（它不是玩家也不是怪 ✓，这里再兜一层 ✓）。 */
    @Override
    public boolean canAttackType(EntityType<?> type) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // ★ 持续散发白 + 淡黄粒子（作者："持续散发白色淡黄粒子"✓）
        double cy = this.getY() + this.getBbHeight() * 0.55D;
        double spread = Math.max(0.4D, this.heightBlocks() * 0.25D);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), cy, this.getZ(),
                4, spread, spread * 0.9D, spread, 0.015D);
        level.sendParticles(ParticleTypes.GLOW, this.getX(), cy, this.getZ(),
                3, spread, spread * 0.9D, spread, 0.01D);
        if (this.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.FIREWORK, this.getX(), cy, this.getZ(),
                    6, spread * 0.8D, spread * 0.8D, spread * 0.8D, 0.02D);
        }
        if (this.tickCount > this.life()) {
            this.discard();
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // 纯表现，不存档 ✓
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // 同上
    }

    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/angel.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/angel_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/angel.animation.json");
    }
}
