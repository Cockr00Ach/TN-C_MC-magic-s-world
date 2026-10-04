package com.tnc.tnc.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * <b>暗系巨人领主</b>（作者 2026-10-03："做一个暗系 boss 的模型，我希望他是一个巨人的模型，
 * 然后通身穿戴盔甲，这个盔甲可以直接融到模型里面去，一个巨兽人领主的感觉" ✓）。
 *
 * <h2>这一版只做"人"（模型 + 实体 + 血条 + 近战）</h2>
 * 法术/招式还没定 ✗ —— 先让作者能把它叫出来、看清模型、走两步 ✓：
 * <ul>
 *   <li>模型：{@code geo/entity/dark_giant.geo.json}（盔甲是**长在模型里的**方块 ✓，
 *       不是额外穿装备 ✗，见 {@code tools/gen_dark_giant.ps1} 与 {@code docs/previews/dark_giant_preview.png} ✓）；</li>
 *   <li>个头：{@link #MODEL_SCALE} 把模型放大到约 <b>4.5 格</b>高（巨人 ✓）——
 *       渲染器里缩放，碰撞箱也跟着给大 ✓；</li>
 *   <li>行为：和别的 boss 一样的基础近战 + 顶部血条（{@link ServerBossEvent} ✓）。</li>
 * </ul>
 *
 * <p>★ 招式部分等作者定了再加 ✓（要放哪条链的法术、什么节奏，都是照着阿波罗那套接就行 ✓）。
 */
public class TNDarkGiantEntity extends Monster implements GeoEntity {

    /** 血量（作者没指定，先给 3000 ✓ —— 比阿波罗(2500)更肉一点，配得上"巨人领主"✓）。 */
    public static final float MAX_HP = 3000.0F;

    /**
     * 渲染缩放（1.0 = 原样 ✓）—— 模型实测：脚底 y=0 → 头顶（含角）y=97 单位 ≈ <b>6 格</b> ✓，
     * <b>肩宽 64 单位 = 4 格</b> ✓（宽高比 1.5 ⇒ 作者要的"粗壮 / 雄壮"，不是竹竿 ✓；玩家是 3.0）。
     */
    public static final double MODEL_SCALE = 1.0D;

    /** 碰撞箱（宽 × 高，格 ✓）—— 量自模型（肩甲外缘 4 格 · 头顶 5.6 格 ✓），不然会穿墙/浮空 ✗。 */
    public static final float HITBOX_WIDTH = 2.4F;
    public static final float HITBOX_HEIGHT = 5.6F;

    /** 顶部血条（作者要"boss 那种血量条"✓）。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TNDarkGiantEntity(EntityType<? extends TNDarkGiantEntity> type, Level level) {
        super(type, level);
        this.xpReward = 800;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)      // 巨人走得慢一点 ✓
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 12.0D)               // 通身盔甲 ⇒ 护甲给高 ✓
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D); // 巨人推不动 ✓
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 40.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ---- 顶部 BOSS 血条 ----
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(!this.isDeadOrDying() && this.isAlive());
        // 脚下冒一点暗色尘（纯表现，让这尊巨人站在那儿有存在感 ✓）
        if (this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2D, this.getZ(),
                    6, HITBOX_WIDTH * 0.6D, 0.2D, HITBOX_WIDTH * 0.6D, 0.01D);
        }
    }

    /** 走的比普通怪慢，但每一步都沉（落地扬尘 ✓）。 */
    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 0
                && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.9F, 0.6F);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：作者的动画文件是空的 ✓（要加挥砍/咆哮再补 triggerableAnim ✓，照抄 TNApolloEntity ✓）
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 资源（和模型生成脚本里的路径一一对应 ✓）----
    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/dark_giant.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dark_giant_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/dark_giant.animation.json");
    }
}
