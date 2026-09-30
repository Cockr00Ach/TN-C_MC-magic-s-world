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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
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
     * 黑暗衍版"神在投篮"的三尊神参数 ✓
     *
     * <p>2026-09-30 作者："神的格数太高了，改成 20 格高吧，持续时间可以短一点" ⇒
     * 悬停 **20 格**（原来是 30 ✗）、存活 **80 tick = 4 秒**（原来 100 ✗）✓。
     * 尺寸保持 12（作者验收过的大小 ✓，和玩家 t5 那三尊的 26 是两个不同的观感 ✓）。
     */
    private static final double GOD_HOVER_HEIGHT = 20.0D;
    private static final double GOD_SCALE = 12.0D;
    private static final int GOD_LIFE = 80;

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

    // ------------------------------------------------------------------
    //  ★ 凋零那一套音效（作者 2026-09-30："音乐也用凋零的好了"）
    //
    //  说明：**原版凋零其实没有专属 BGM** ✗ —— 全游戏只有末影龙有 boss 音乐
    //  （{@code SoundEvents.MUSIC_DRAGON}）。"凋零的那套"就是它的**叫声**：
    //  生成咆哮 / 平时低吼 / 受伤 / 死亡 / 发射骷髅头 ✓。这里整套照搬，
    //  再加上"变身神降"时的一声生成咆哮 ✓ —— 打过凋零的人一听就知道这是 boss ✓。
    //
    //  平时的低吼/受伤/死亡走原版那三个 getter ✓（节流、音量、音高全由原版管，
    //  不用自己写计时器 ✗）；只有"生成"和"施法"两处要我们自己放 ✓。
    // ------------------------------------------------------------------

    /** 平时低吼 ✓（原版按 {@code getAmbientSoundInterval()} 自动节流）。 */
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    /** 受伤 ✓。 */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    /** 死亡 ✓。 */
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    /**
     * 生成时那声咆哮 ✓（凋零标志性的 {@code WITHER_SPAWN}）。
     *
     * <p>为什么要自己放：原版只有 {@code WitherBoss} 自己在生成动画里播这一声 ✗，
     * 我们的实体没有那段动画 ⇒ 得在**第一次 tick**（服务端）补上 ✓。
     */
    private void playSpawnRoar() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    /** 二阶段：全程闪电登神（**永久** ✓ —— 作者："boss 二阶段是要一直开着闪电登神的"）。 */
    private void ensurePhaseTwoAura() {
        if (TNEffects.LIGHTNING_ASCENSION.isPresent()
                && !this.hasEffect(TNEffects.LIGHTNING_ASCENSION.get())) {
            this.addEffect(new MobEffectInstance(TNEffects.LIGHTNING_ASCENSION.get(),
                    PHASE2_ASCENSION_TICKS, 0, false, false));
        }
    }

    /**
     * 二阶段的登神**时长**（tick）✓
     *
     * <p>作者提过"或者直接把 boss 的闪电登神时间改成 2 分钟？" —— 这里直接用
     * {@link Integer#MAX_VALUE}（≈3.4 年 ⇒ 实战就是**永久** ✓）：
     * 因为 {@link #ensurePhaseTwoAura()} 每 tick 都会检查、掉了就补 ✓，
     * 所以"2 分钟"反而会变成"每 2 分钟断一瞬、下一 tick 又接上" ✗，没必要 ✓。
     * 想改成有限时长就把这个常量改成 {@code 2400}（2 分钟 ✓）—— 逻辑不用动 ✓。
     */
    private static final int PHASE2_ASCENSION_TICKS = Integer.MAX_VALUE;

    /**
     * 二阶段的登神**外观**（作者："boss 二阶段是要一直开着闪电登神的"）✓
     *
     * <p>血的教训：{@code TNEffects.LIGHTNING_ASCENSION} 只给**数值**（攻击力/法术强度）✗，
     * 电弧 + 环绕电光 + 地上雷印是谁画的？是 {@code TnSpellMechanics.ascensionAura} ✓ ——
     * 而那一套以前**只挂在玩家的每 tick 逻辑上** ✗ ⇒ boss 身上有 buff、却一点电光都没有，
     * 看起来"根本没开登神" ✗✗。现在由 boss 自己每 tick 调那两个方法 ✓（它们已经放宽到
     * 任意 {@code LivingEntity} ✓），玩家那边一个字都没改 ✓。
     */
    private void phaseTwoAscensionVisuals() {
        long time = this.level().getGameTime();
        com.tnc.tnc.magic.TnSpellMechanics.ascensionAura(this, time);
        com.tnc.tnc.magic.TnSpellMechanics.sparkMarks(this, time);
        // ★★ 作者要的"登神外观"＝背后跟随的那尊 god ✓（"我想要的登神外观是那个 god 的模型
        //    在他背后跟随的那个效果"）—— 黑暗衍用它那套暗色模型（god_dark ✓），
        //    尺寸按 boss 的体型放大到 GOD_FOLLOWER_SCALE ✓。
        com.tnc.tnc.magic.TnSpellMechanics.maintainGodFollower(this, time, true, GOD_FOLLOWER_SCALE);
        // ★★ 作者 2026-09-30："boss 二阶段我希望他身上一直散发着黑色和紫色粒子" ✓
        this.phaseTwoAuraParticles();
    }

    /**
     * 二阶段常驻的**黑 + 紫**粒子 ✓（作者："身上一直散发着黑色和紫色粒子"）。
     *
     * <p>配色沿用 t5 三尊神那套（紫 {@code witch} ＋ 黑 {@code squid_ink} ✓）——
     * 整个雷系"神"的视觉语言就这两种颜色，别再引入第三种 ✗。
     * 每 tick 各来一小撮（约 120/秒 紫 + 80/秒 黑），贴着身体中段往上飘 ✓。
     */
    private void phaseTwoAuraParticles() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        double cy = this.getY() + this.getBbHeight() * 0.6D;
        level.sendParticles(ParticleTypes.WITCH, this.getX(), cy, this.getZ(),
                6, 0.7D, 1.1D, 0.7D, 0.02D);
        level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), cy, this.getZ(),
                4, 0.7D, 1.1D, 0.7D, 0.01D);
    }

    /**
     * 黑暗衍背后那尊跟随神的尺寸 ✓
     *
     * <p>玩家那尊是 5.0（≈5.6 格高）；boss 本体 2.4 格高、而且要撑住"神降"的气势 ⇒ 放大到
     * **9.0**（≈10 格高）✓。想改就改这一个数 ✓。
     */
    private static final double GOD_FOLLOWER_SCALE = 9.0D;

    /**
     * 二阶段登场那一下的"<b>万雷归体</b>"✓ —— 三圈雷电同时向内收 + 一记白闪。
     *
     * <p>和玩家 t5 登神链释放时的收尾（{@code TnSpellMechanics.closingBurst}）同一个观感 ✓：
     * 玩家一眼就能读出"他现在是被雷灌满的状态"✓，配合永久登神 + 雷雨 + 血条回满 ⇒ 变身成立 ✓。
     */
    private void phaseTwoGatherBurst(ServerLevel level) {
        double cy = this.getY() + this.getBbHeight() * 0.55D;
        for (int ring = 0; ring < 3; ring++) {
            double radius = 6.0D + ring * 3.0D;
            int points = 12 + ring * 4;
            for (int i = 0; i < points; i++) {
                double a = i * (Math.PI * 2.0D / points) + ring * 0.3D;
                Vec3 from = new Vec3(
                        this.getX() + Math.cos(a) * radius,
                        cy + (ring - 1) * 1.5D,
                        this.getZ() + Math.sin(a) * radius);
                com.tnc.tnc.magic.TnSpellMechanics.arcLine(level, from,
                        new Vec3(this.getX(), cy, this.getZ()), 5, 0.15D);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, this.getX(), cy, this.getZ(), 3, 0.2D, 0.3D, 0.2D, 0.0D);
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
                // ★ 二阶段变身＝再来一次"生成咆哮" ✓（凋零那套；听过一次就忘不掉 ✓）
                this.playSpawnRoar();
                // ★ 万雷归体：三圈雷向内收 ⇒ "他被雷灌满了" ✓（配合下面的永久登神 ✓）
                this.phaseTwoGatherBurst(level);
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
        // ★ 生成咆哮（凋零那套）：第一次 tick 播一次 ✓（见 playSpawnRoar 的说明）
        if (this.tickCount == 1) {
            this.playSpawnRoar();
        }
        if (this.phase == 2) {
            this.ensurePhaseTwoAura();
            this.phaseTwoAscensionVisuals();     // ★ 电弧/环绕电光/雷印（不然"看不出在登神" ✗）
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
        // ★ 施法那一下：凋零发射骷髅头的声音（{@code WITHER_SHOOT}）✓ —— 音高压低一点，
        //   听起来像"蓄能"而不是"吐口水" ✓；技能本身的落雷声照旧在各自方法里播 ✓
        level.playSound(null, this.getX(), this.getY() + 1.0D, this.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.6F, 0.6F);
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
            // 悬停高度：实体 tick 里是 `fallTo + FALL_HEIGHT(24)` ⇒ 想悬停在 GOD_HOVER_HEIGHT
            // 就得把 fallTo 设成「目标高度 − 24」✓（作者 2026-09-30："神的格数太高了，改成 20 格高"）
            god.configure(GOD_SCALE, GOD_LIFE,
                    at.y + GOD_HOVER_HEIGHT - TNLightningStrikeEntity.FALL_HEIGHT, 3.0D);
            god.moveTo(at.x + Math.cos(ang) * 6.0D, at.y + GOD_HOVER_HEIGHT,
                    at.z + Math.sin(ang) * 6.0D, 0.0F, 0.0F);
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