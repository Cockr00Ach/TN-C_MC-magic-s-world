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
     * <p>2026-09-30 作者："神的格数太高了，改成 20 格高吧，持续时间可以短一点" →
     * 然后又"三个 god 的高度降低到 10 格吧，间距可以拉大点" ⇒ 悬停 **10 格**（30 → 20 → 10 ✓）、
     * 环绕半径 **12 格**（原来 6 ✓）、存活 **80 tick = 4 秒** ✓。
     * 尺寸保持 12（作者验收过的大小 ✓，和玩家 t5 那三尊的 26 是两个不同的观感 ✓）。
     */
    private static final double GOD_HOVER_HEIGHT = 10.0D;
    private static final double GOD_RADIUS = 12.0D;
    private static final double GOD_SCALE = 12.0D;
    private static final int GOD_LIFE = 80;

    /**
     * 四个技能的伤害 / 范围 ✓
     *
     * <p>★★ 2026-09-30 大发现（作者："他的法术怎么感觉一般啊"）：{@code TNLightningStrikeEntity}
     * 只在 {@code setLandImpact(...)} 被调用过时才结算伤害 ✗，而黑暗衍的 {@code bolt()} 从来
     * **没有调过它** ⇒ 他放的全是**纯视觉烟花、一点伤害都没有** ✗✗；这次又删掉了近战
     * ⇒ 他完全打不到人 ✗✗✗。现在四个技能都挂上归属与伤害 ✓。
     */
    private static final float STORM_DAMAGE = 6.0F;
    private static final double STORM_RADIUS = 3.0D;
    private static final float THUNDER_DAMAGE = 10.0F;
    private static final double THUNDER_RADIUS = 4.0D;
    private static final float BALL_DAMAGE = 24.0F;
    private static final double BALL_BLAST = 7.0D;
    /** 神在投篮那颗球的下落速度（作者："球速太快了" ⇒ 1.5 → **0.4** ✓，和玩家 t5 的 0.5 同档 ✓） */
    private static final double BALL_FALL_SPEED = 0.4D;

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
                // ★ 2026-09-30 作者："他应该站在原地，有很长的施法距离" ⇒ 索敌/施法距离拉到 64 格 ✓
                //   （原来 40 格 ✗；四个技能全是从远处打的：落雷/雷球/神在投篮都生成在目标身上 ✓）
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    /**
     * AI 目标 —— ★ 2026-09-30 作者："他怎么能一直追着我撕咬呢，他应该站在原地，有很长的施法距离"。
     *
     * <p>所以这里**只留"会动嘴/动脚"以外的目标**：
     * <ul>
     *   <li>✗ 删掉 {@code MeleeAttackGoal}（原来是第 2 优先级 —— 就是它让他一路追着你咬 ✗）</li>
     *   <li>✗ 删掉 {@code RandomStrollGoal}（没事乱晃也会凑过来 ✗）</li>
     *   <li>✓ 保留 FloatGoal（掉水里不至于淹死）／LookAtPlayerGoal（**只转头**看你 ✓）／
     *       RandomLookAroundGoal ✓</li>
     *   <li>✓ 保留 HurtByTargetGoal（被打会还手 ⇒ 还是会施法打你 ✓）与
     *       NearestAttackableTargetGoal（配合 64 格 FOLLOW_RANGE ⇒ **站桩远程开火** ✓）</li>
     * </ul>
     * 没有 movement goal ⇒ 他**根本不会寻路**，只能原地转身 ✓（这正是作者要的"站在原地" ✓）。
     *
     * <p>★ 2026-09-30 追加：作者"boss 虽然不动，他的朝向应该一直看着玩家啊" ⇒
     * 「转头看你」这件事**不再交给 LookAtPlayerGoal/RandomLookAroundGoal** ✗ ——
     * 那两个 goal 只动**头**（`yHeadRot`），而 GeckoLib 画的是整个身体 ⇒ 看着像没转 ✗。
     * 现在由 {@link #faceTarget()} 每 tick **直接写身体 yaw**（`setYRot` + `yBodyRot` ✓），
     * 比 goal 更可靠、也不会被"随机张望"打断 ✓。
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * 每 tick 把**整个身体**转向目标 ✓（作者："boss 虽然不动，他的朝向应该一直看着玩家啊"）。
     *
     * <p>约定：原版 {@code getViewVector} = {@code (−sin yaw, cos yaw)} ⇒
     * 想面向 (dx, dz) 就得 {@code yaw = atan2(−dx, dz)} ✓（x 分量最容易写反 ✗）。
     * 三个都写：`setYRot`（实体朝向）、`yBodyRot`（渲染器用的身体角 ✓）、`yHeadRot`（头部）✓。
     */
    private void faceTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.setYHeadRot(yaw);
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
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
        // ★ 一直看着玩家（作者："boss 虽然不动，他的朝向应该一直看着玩家啊"）✓ 在施法/目标判定之前 ✓
        this.faceTarget();
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

    /**
     * 落雷：从天上砸到 at ✓（形态 0，**暗色版** ✓），并且**真的会打人** ✓。
     *
     * @param damage 落地伤害（≤0 = 纯表现）；{@code radius} 落地伤害半径
     */
    private void bolt(ServerLevel level, Vec3 at, double scale, double shake,
                      float damage, double radius) {
        TNLightningStrikeEntity b = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        if (b == null) {
            return;
        }
        b.asDark();                                  // 黑暗衍专用模型：flash_dark ✓
        b.configure(scale, 6, at.y, shake);
        if (damage > 0.0F) {
            b.setLandImpact(this, damage, radius);   // ★ 关键：不调这句就是纯烟花 ✗
        }
        b.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(b);
    }

    /** 雷暴：目标周围 5 道（各带伤害 ✓） */
    private void castStorm(ServerLevel level, LivingEntity target) {
        for (int i = 0; i < 5; i++) {
            double dx = (this.random.nextDouble() - 0.5D) * 10.0D;
            double dz = (this.random.nextDouble() - 0.5D) * 10.0D;
            this.bolt(level, target.position().add(dx, 0.0D, dz), 4.5D, 3.0D,
                    STORM_DAMAGE, STORM_RADIUS);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5F, 1.0F);
    }

    /**
     * 天打五雷轰：目标头顶连落 **12 道**（大一号 ＋ 会炸 ✓）＋ 脚下铺一张魔法阵 ✓。
     *
     * <p>作者 2026-09-30："他的法术怎么感觉一般啊，他没有天打五雷轰吗" ⇒ 有，但原来只有 8 道、
     * scale 9、**没有伤害也没有阵** ✗ ⇒ 现在 12 道、scale 12、每道 10 伤 4 格，
     * 并且照玩家 t5 那套在目标脚下铺 14 格魔法阵 ✓（`spawnMagicCircleAt` 已改成 public ✓）。
     */
    private void castHeavenlyThunder(ServerLevel level, LivingEntity target) {
        Vec3 at = target.position();
        com.tnc.tnc.magic.TnSpellMechanics.spawnMagicCircleAt(level, at, 14.0D, 210);
        for (int i = 0; i < 12; i++) {
            double dx = (this.random.nextDouble() - 0.5D) * 8.0D;
            double dz = (this.random.nextDouble() - 0.5D) * 8.0D;
            this.bolt(level, at.add(dx, 0.0D, dz), 12.0D, 8.0D, THUNDER_DAMAGE, THUNDER_RADIUS);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.9F);
    }

    /**
     * 神在投篮：目标头顶一颗大雷球（**慢慢**下落 ＋ 落地重炸 ✓）＋ 三尊神 ✓ —— 全部暗色 ✓。
     *
     * <p>作者 2026-09-30："他的神的投篮球速太快了我感觉，而且爆炸威力一般" ⇒
     * 下落 1.5 → **0.4**（和玩家 t5 的 0.5 同档 ✓）、落地 **24 伤 7 格** ＋ 冲击波/大团粒子 ✓
     * （调了 {@code setLandImpact} 才会走 {@code landBlast} 那一整套演出 ✓）。
     */
    private void castGodDescent(ServerLevel level, LivingEntity target) {
        Vec3 at = target.position();
        // ★ 先把天变黑（作者 2026-09-30："怎么放神在投篮没有雷雨天啊"）✓
        //   玩家自己放这一招会开雷雨，boss 之前没开 ⇒ 现在补上，两边观感一致 ✓
        this.startThunderstorm(level, GOD_WEATHER_TICKS);
        TNLightningStrikeEntity ball = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        if (ball != null) {
            ball.asBall();
            ball.asDark();                           // lightingball_dark ✓
            ball.setFallSpeed(BALL_FALL_SPEED);
            ball.configure(20.0D, 6, at.y, 12.0D);
            ball.setLandImpact(this, BALL_DAMAGE, BALL_BLAST);   // ★ 落地才有伤害/爆炸 ✓
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
            double gx = at.x + Math.cos(ang) * GOD_RADIUS;
            double gz = at.z + Math.sin(ang) * GOD_RADIUS;
            // 朝向：面向目标 ✓ —— 约定 look = (−sin yaw, +cos yaw) ⇒ yaw = atan2(−dx, dz) ✓
            //   （2026-09-30 实机反馈"朝向反了"：x 分量不能写反 ✓）
            float yaw = (float) Math.toDegrees(Math.atan2(gx - at.x, at.z - gz));
            // 悬停高度：实体 tick 里是 `fallTo + FALL_HEIGHT(24)` ⇒ 想悬停在 GOD_HOVER_HEIGHT
            // 就得把 fallTo 设成「目标高度 − 24」✓（作者："三个 god 的高度降低到 10 格"）
            god.configure(GOD_SCALE, GOD_LIFE,
                    at.y + GOD_HOVER_HEIGHT - TNLightningStrikeEntity.FALL_HEIGHT, 3.0D);
            god.moveTo(gx, at.y + GOD_HOVER_HEIGHT, gz, yaw, 0.0F);
            level.addFreshEntity(god);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    /**
     * 闪电移位：**原地小幅换位**（不是朝你冲过来 ✗）。
     *
     * <p>作者 2026-09-30："他怎么能一直追着我撕咬呢，他应该站在原地" ⇒ 原来这一招是瞬移到
     * <b>目标身边</b>（{@code target.position() ± 3} ✗）—— 等于每放一次就贴脸一次 ✗，
     * 配合 64 格施法距离完全不该这样 ✓。现在改成：**在自己附近 8 格内**随机挪一格
     * （保持"闪电移位"的观感 ✓，但永远留在远处开火 ✓）。
     */
    private void castBlink(ServerLevel level, LivingEntity target) {
        Vec3 from = this.position();
        double a = this.random.nextDouble() * Math.PI * 2.0D;
        double r = 4.0D + this.random.nextDouble() * 4.0D;
        Vec3 to = new Vec3(this.getX() + Math.cos(a) * r, this.getY(), this.getZ() + Math.sin(a) * r);
        // 兜一层：万一身位算法把落点放到目标 12 格以内了，就往外挪一挪 ✓（"站在原地"优先）
        Vec3 away = to.subtract(target.position());
        if (away.length() < 12.0D) {
            to = target.position().add(away.normalize().scale(12.0D)).add(0.0D, this.getY() - target.getY(), 0.0D);
        }
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