package com.tnc.tnc.boss;

import com.tnc.tnc.magic.TNEffects;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNLightningStrikeEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 公孙衍（迷失）—— 两阶段 Boss。
 *
 * <h2>数值（作者 2026-09-30 指定）</h2>
 * <ul>
 *   <li><b>3000 血</b> ✓；打空后进入<b>二阶段</b>：<b>再回满 3000</b> ＋ <b>全程挂着"闪电登神"</b> ✓</li>
 *   <li>技能：<b>雷暴 / 天打五雷轰 / 神在投篮 / 闪电移位</b> 四个<b>随机</b>放 ✓（技能用我们自己的原语实现：
 *       落雷实体 {@code tnc:lightning_strike} 的三种形态 ＋ 短距瞬移 ✓，不需要引擎的法术系统 ✓）</li>
 *   <li>名字：<b>公孙衍（迷失）</b>（lang 里给 ✓）；<b>可直接 {@code /summon tnc:yan_dark}</b> ✓</li>
 * </ul>
 */
public class YanDarkBossEntity extends Monster implements GeoEntity {

    /** 每个阶段的满血（作者："二阶段还是三千血" ✓）。 */
    public static final float PHASE_HEALTH = 3000.0F;

    /** 技能间隔（tick）：一阶段 100、二阶段 60（更快 ✓）。 */
    private static final int CAST_INTERVAL_P1 = 100;
    private static final int CAST_INTERVAL_P2 = 60;

    /** 1 = 一阶段，2 = 二阶段 ✓。 */
    private int phase = 1;
    private int castCooldown = CAST_INTERVAL_P1;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 两个施法动作（作者做的 playmagic_1/2 ✓）可按需触发 ✓ */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<YanDarkBossEntity> action = new AnimationController<>(this, "action", 2,
                state -> PlayState.STOP);
        action.triggerableAnim("playmagic_1", RawAnimation.begin().thenPlay("playmagic_1"));
        action.triggerableAnim("playmagic_2", RawAnimation.begin().thenPlay("playmagic_2"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public YanDarkBossEntity(EntityType<? extends YanDarkBossEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, PHASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
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

    public int getPhase() {
        return this.phase;
    }

    /** 二阶段：全程闪电登神（无限时长 ✓）。 */
    private void ensurePhaseTwoAura() {
        if (TNEffects.LIGHTNING_ASCENSION.isPresent()
                && !this.hasEffect(TNEffects.LIGHTNING_ASCENSION.get())) {
            this.addEffect(new MobEffectInstance(TNEffects.LIGHTNING_ASCENSION.get(),
                    Integer.MAX_VALUE, 0, false, false));
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (!hit || this.level().isClientSide()) {
            return hit;
        }
        // 一阶段被打空 -> 进入二阶段：回满血 + 永久闪电登神 + 演出 ✓
        if (this.phase == 1 && this.getHealth() <= 0.0F) {
            this.phase = 2;
            this.setHealth(PHASE_HEALTH);
            this.castCooldown = CAST_INTERVAL_P2;
            this.ensurePhaseTwoAura();
            if (this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.5D, this.getZ(),
                        6, 0.4D, 0.6D, 0.4D, 0.0D);
                level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 1.0D, this.getZ(),
                        120, 1.2D, 1.2D, 1.2D, 0.4D);
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.8F);
            }
            return false;                    // 这一下不把他打死 ✓
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.phase == 2) {
            this.ensurePhaseTwoAura();
        }
        if (this.getTarget() == null) {
            return;
        }
        if (--this.castCooldown > 0) {
            return;
        }
        this.castCooldown = (this.phase == 2) ? CAST_INTERVAL_P2 : CAST_INTERVAL_P1;
        this.castRandomSpell();
    }

    /** 随机放四个技能之一（作者的四个"随机放" ✓）。 */
    private void castRandomSpell() {
        LivingEntity target = this.getTarget();
        if (target == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.triggerAnim("action", this.random.nextBoolean() ? "playmagic_1" : "playmagic_2");
        switch (this.random.nextInt(4)) {
            case 0 -> this.castStorm(level, target);
            case 1 -> this.castHeavenlyThunder(level, target);
            case 2 -> this.castGodDescent(level, target);
            default -> this.castBlink(level, target);
        }
    }

    /** 落雷：在 from 上方落下并砸向目标点 ✓（形态 0，**暗色版** ✓） */
    private void bolt(ServerLevel level, Vec3 at, double scale, double shake) {
        TNLightningStrikeEntity b = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        if (b == null) {
            return;
        }
        b.asDark();                                  // 黑暗衍专用模型：flash_dark ✓
        b.configure(scale, 6, at.y, shake);
        b.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(b);
    }

    /** 雷暴：目标周围 5 道 ✓ */
    private void castStorm(ServerLevel level, LivingEntity target) {
        for (int i = 0; i < 5; i++) {
            double dx = (this.random.nextDouble() - 0.5D) * 10.0D;
            double dz = (this.random.nextDouble() - 0.5D) * 10.0D;
            this.bolt(level, target.position().add(dx, 0.0D, dz), 4.5D, 3.0D);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5F, 1.0F);
    }

    /** 天打五雷轰：目标头顶连落 8 道（大一号）✓ */
    private void castHeavenlyThunder(ServerLevel level, LivingEntity target) {
        for (int i = 0; i < 8; i++) {
            double dx = (this.random.nextDouble() - 0.5D) * 6.0D;
            double dz = (this.random.nextDouble() - 0.5D) * 6.0D;
            this.bolt(level, target.position().add(dx, 0.0D, dz), 9.0D, 8.0D);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.9F);
    }

    /** 神在投篮：目标头顶一颗大雷球（慢速下落 ✓）＋ 三尊神 ✓ —— 全部用**暗色**模型 ✓ */
    private void castGodDescent(ServerLevel level, LivingEntity target) {
        Vec3 at = target.position();
        TNLightningStrikeEntity ball = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        if (ball != null) {
            ball.asBall();
            ball.asDark();                           // lightingball_dark ✓
            ball.setFallSpeed(1.5D);
            ball.configure(20.0D, 6, at.y, 12.0D);
            ball.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            level.addFreshEntity(ball);
        }
        for (int i = 0; i < 3; i++) {
            double ang = i * (Math.PI * 2.0D / 3.0D);
            TNLightningStrikeEntity god = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
            if (god == null) {
                continue;
            }
            god.asGod();
            god.asDark();                            // god_dark ✓
            god.configure(12.0D, 100, at.y + 6.0D, 3.0D);
            god.moveTo(at.x + Math.cos(ang) * 6.0D, at.y + 30.0D, at.z + Math.sin(ang) * 6.0D, 0.0F, 0.0F);
            level.addFreshEntity(god);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    /** 闪电移位：瞬移到目标身侧（带一圈**暗色**电花 ✓：黑雾 + 少量蓝电） */
    private void castBlink(ServerLevel level, LivingEntity target) {
        Vec3 from = this.position();
        Vec3 to = target.position().add(
                (this.random.nextDouble() - 0.5D) * 6.0D, 0.0D, (this.random.nextDouble() - 0.5D) * 6.0D);
        darkSparks(level, from, 60);
        this.teleportTo(to.x, to.y, to.z);
        darkSparks(level, to, 60);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 1.2F);
    }

    /** 黑暗衍的"电花"＝ 黑雾为主 + 一点蓝电 ✓（亮蓝色 60 颗看着像普通雷法，不像他 ✗）。 */
    private void darkSparks(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0D, at.z,
                count, 0.6D, 0.8D, 0.6D, 0.5D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0D, at.z,
                Math.max(1, count / 4), 0.6D, 0.8D, 0.6D, 0.6D);
    }

    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/yan_dark.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/yan_dark_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/yan_dark.animation.json");
    }
}