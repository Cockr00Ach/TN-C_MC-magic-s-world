package com.tnc.tnc.boss;

import com.tnc.tnc.magic.TNEffects;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNLightningStrikeEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
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

    /**
     * ★ 雷雨天（作者 2026-09-30："这个黑暗衍怎么放神在投篮没有雷雨天啊"）
     *
     * <p>玩家自己放 t5「神在投篮」时会**把天变黑下雷雨** ✓（{@code TnSpellMechanics} 里的
     * {@code DIVINE_WEATHER_TICKS = 200}），而黑暗衍这四个技能是它**自己实现**的 ✗ ——
     * 之前只放了球和三尊神、**完全没碰天气** ⇒ 少了那半截"神降"的气氛 ✗。
     *
     * <p>所以这里补两处：
     * <ol>
     *   <li>{@link #castGodDescent} 时开雷雨（{@link #GOD_WEATHER_TICKS} = 15 秒 ✓
     *       —— 二阶段技能间隔只有 60 tick，15 秒够把两次连起来 ✓）</li>
     *   <li>二阶段（永久闪电登神那一段）**打架期间一直保持雷雨** ✓，每
     *       {@link #PHASE2_WEATHER_REFRESH} tick 续一次 ⇒ 整场二阶段天都是黑的 ✓
     *       （只在"有目标"时续，别让他在世界角落里到处改天气 ✗）</li>
     * </ol>
     *
     * <p>⚠️ 和玩家法术一样的规矩：**只在当前没打雷时才改** ✓ —— 水法那份天气是带租约的、
     * 玩家自己 {@code /weather} 的结果也不该被一个 boss 盖掉 ✓。
     */
    private static final int GOD_WEATHER_TICKS = 300;
    private static final int PHASE2_WEATHER_REFRESH = 200;

    /**
     * ★ Boss 血量条（作者 2026-09-30："我希望他会显示 boss 血量条啊，就是那种 boss 战的血量条，
     * 你可以用凋零的替代"）。
     *
     * <p>用的就是**凋零那套**：{@link BossEvent.BossBarColor#PURPLE} ＋
     * {@link BossEvent.BossBarOverlay#PROGRESS} ✓ —— 和本项目别处（AbyssCitadelJobs /
     * LargeLandmarkJobs）那两根进度条同样的配色，一处一眼就认得出来 ✓。
     *
     * <p>血量条显示的是**当前这一阶段**的血（每阶段 3000 ✓）：一阶段打空 → 二阶段回满 3000
     * ⇒ 条子会**再灌满一次** ＋ 名字后面挂上「· 神降」✓ —— 玩家一眼看出"进二阶段了" ✓。
     *
     * <p>加入/移除玩家走原版那对钩子（{@code startSeenByPlayer} / {@code stopSeenByPlayer} ✓，
     * 和凋零、末影龙一样）：谁看得见 boss、谁就看到条子 ✓，走远了自动消失 ✓。
     */
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

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

    // ------------------------------------------------------------------
    //  Boss 血量条（凋零那套 ✓）
    // ------------------------------------------------------------------

    /** 玩家开始看得见他 ⇒ 给他加一条血量条 ✓（原版凋零/末影龙同款钩子）。 */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.refreshBossBar();
        this.bossEvent.addPlayer(player);
    }

    /** 玩家看不见了（走远/换维度/下线）⇒ 把条子摘掉 ✓。 */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /** 每 tick（服务端）刷新条子的长度 ✓ —— 原版凋零也是在这里 setProgress 的 ✓。 */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    /** 条子的名字：二阶段挂上「· 神降」✓（名字本身来自 lang：公孙衍（迷失）✓）。 */
    private void refreshBossBar() {
        this.bossEvent.setName(this.phase >= 2
                ? this.getDisplayName().copy().append(Component.literal(" §5· 神降"))
                : this.getDisplayName());
    }

    /** 死了/被清掉时把条子收干净 ✓（不然玩家那边会留一条空条 ✗）。 */
    @Override
    public void remove(Entity.RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        this.bossEvent.setVisible(false);
        super.remove(reason);
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
            this.refreshBossBar();              // 条子名字挂上「· 神降」，血条同时回满 ✓
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
        // ★ 二阶段：打架期间把雷雨续上（作者要看"神降"的天 ✓，见 GOD_WEATHER_TICKS 的注释）
        if (this.phase == 2 && PHASE2_WEATHER_REFRESH > 0
                && this.tickCount % PHASE2_WEATHER_REFRESH == 0
                && this.level() instanceof ServerLevel stormLevel) {
            this.startThunderstorm(stormLevel, PHASE2_WEATHER_REFRESH + 100);
        }
        if (--this.castCooldown > 0) {
            return;
        }
        this.castCooldown = (this.phase == 2) ? CAST_INTERVAL_P2 : CAST_INTERVAL_P1;
        this.castRandomSpell();
    }

    /** 把天变黑下雷雨 ✓（**只在现在没打雷时才改** —— 和玩家法术同一条规矩，见常量注释）。 */
    private void startThunderstorm(ServerLevel level, int ticks) {
        if (!level.isThundering()) {
            level.setWeatherParameters(0, Math.max(20, ticks), true, true);
        }
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
        // ★ 先把天变黑（作者 2026-09-30："怎么放神在投篮没有雷雨天啊"）✓
        //   玩家自己放这一招会开雷雨，boss 之前没开 ⇒ 现在补上，两边观感一致 ✓
        this.startThunderstorm(level, GOD_WEATHER_TICKS);
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