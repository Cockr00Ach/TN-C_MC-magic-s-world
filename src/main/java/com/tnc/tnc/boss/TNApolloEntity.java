package com.tnc.tnc.boss;

import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.TNLightningStrikeEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 阿波罗 —— 光系 Boss（哥特大祭司外形）。
 *
 * <h2>作者 2026-10-01 指定</h2>
 * <ul>
 *   <li>法术：<b>光线链</b> t1 / t4 / t5 ＋ <b>光耀链</b> t4 / t5 ✓</li>
 *   <li>频率：<b>光线 t1 最高</b>（权重 6）、光线 t4/t5 <b>中</b>（各 2）、光耀 t4/t5 <b>低</b>（各 1）✓</li>
 *   <li><b>施法要等动画播完再生效</b> ✓ —— 选法术 → 触发对应动画 → 按动画时长 ticking → 播完才生效 ✓</li>
 *   <li>对应动画：t1 → {@code palymagic_t1}（2.75s）；t4/t5（两条链共四个）→ {@code palymagic_t4_t5}（3s）✓</li>
 * </ul>
 *
 * <p>法术效果用我们自己的原语"演"出来（和公孙衍(迷失)同思路 ✓）：<b>光线</b>＝一条亮白光柱扫向目标并造成伤害 ✓；
 * <b>光耀</b>＝自身爆发一圈光并向周围造成伤害 ✓。要改成别的表现（治疗/增益/范围形态）随时说 ✓。
 */
public class TNApolloEntity extends Monster implements GeoEntity {

    /** 血量（作者没指定，我先定 2500 ✓；改这一个常量即可 ✓）。 */
    public static final float MAX_HP = 2500.0F;

    /**
     * 五个法术：{索引, 权重} ✓。
     *
     * <p>★ 作者 2026-10-01 调整："把 boss 的法术里 t1 光线删了，加上 t2 和 t3 吧，
     * 然后光耀链只留下 t5，然后 boss 的 t2t3 光线长度增加三倍" ✓
     * ⇒ 现在的五招是：光线 t2 / t3 / t4 / t5 ＋ 光耀 t5 ✗（t1 与光耀 t4 都删了 ✓）。
     */
    private static final int[][] SPELLS = {{0, 4}, {1, 3}, {2, 2}, {3, 2}, {4, 1}};

    /** 五招分别对应哪个法术 ✓（顺序和 {@link #SPELLS} 的索引一致 ✓）。 */
    private static final String[] PATHS = {
            "great_light_beam", "giant_light_beam", "holy_light_descent", "radiant_barrage", "angel_mercy"};

    /** 是不是"光线链"的招 ✓（前四个是；最后一个是光耀链 ✗ —— 两条链的入口不一样 ✓）。 */
    private static final boolean[] IS_BEAM = {true, true, true, true, false};

    /** 光柱长度倍数 ✓ —— 作者："boss 的 t2t3 光线长度增加三倍" ⇒ 前两招 ×3 ✓，其余原长 ✓。 */
    private static final double[] LENGTH_SCALE = {3.0D, 3.0D, 1.0D, 1.0D, 1.0D};

    /** 每个法术对应的动画名与动画时长（tick）——"等动画播完再施法" ✓ */
    private static final String[] ANIM = {
            "palymagic_t1", "palymagic_t1", "palymagic_t4_t5", "palymagic_t4_t5", "palymagic_t4_t5"};
    private static final int[] CAST_TICKS = {55, 55, 60, 60, 60};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int castCooldown = 80;
    private int pendingSpell = -1;
    private int pendingTicks = 0;

    public TNApolloEntity(EntityType<? extends TNApolloEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.bossEvent = new net.minecraft.server.level.ServerBossEvent(this.getDisplayName(),
                net.minecraft.world.BossEvent.BossBarColor.WHITE,
                net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
    }

    // ---- 顶部 BOSS 血条（作者：要"boss 那种血量条" ✓）----
    private final net.minecraft.server.level.ServerBossEvent bossEvent;

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /** 每 tick 同步血条（血量比例 + 名字）✓。 */
    private void tickBossBar() {
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(!this.isDeadOrDying() && this.isAlive());
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<TNApolloEntity> action = new AnimationController<>(this, "action", 2,
                state -> PlayState.STOP);
        action.triggerableAnim("palymagic_t1", RawAnimation.begin().thenPlay("palymagic_t1"));
        action.triggerableAnim("palymagic_t4_t5", RawAnimation.begin().thenPlay("palymagic_t4_t5"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.tickBossBar();
        }
        if (this.level().isClientSide()) {
            return;
        }
        // —— 正在唱法：等动画播完再生效 ✓
        if (this.pendingSpell >= 0) {
            if (--this.pendingTicks <= 0) {
                this.applySpell(this.pendingSpell);
                this.pendingSpell = -1;
                this.castCooldown = 60;
            }
            return;
        }
        if (this.getTarget() == null || --this.castCooldown > 0) {
            return;
        }
        this.startCast(this.pickSpell());
    }

    /** 按权重随机挑一个法术 ✓（权重越大越常放 ✓）。 */
    private int pickSpell() {
        int total = 0;
        for (int[] s : SPELLS) {
            total += s[1];
        }
        int roll = this.random.nextInt(total);
        for (int[] s : SPELLS) {
            roll -= s[1];
            if (roll < 0) {
                return s[0];
            }
        }
        return 0;
    }

    /** 开始施法：触发动画并开始倒计时（时长＝动画时长 ✓）。 */
    private void startCast(int spell) {
        this.pendingSpell = spell;
        this.pendingTicks = CAST_TICKS[spell];
        this.triggerAnim("action", ANIM[spell]);
        if (this.level() instanceof ServerLevel level) {
            // ★ 作者 2026-10-01："阿波罗施法技能就晴天" ✓ —— 太阳神一出手天就放晴 ✓
            //   （和光耀 t3「神光」的"回归晴天"同一套写法 ✓；6000 tick = 5 分钟 ✓）
            level.setWeatherParameters(6000, 0, false, false);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.2F, 1.1F);
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0D, this.getZ(),
                    20, 0.5D, 0.6D, 0.5D, 0.05D);
        }
    }

    /**
     * 动画播完 → 法术生效 ✓。
     *
     * <h2>★ 2026-10-01 作者："他应该可以放光线链的 t4t5，和光耀的 t4t5" / "就跟公孙衍迷失一样，能放玩家的法术"</h2>
     * 原来这里是用自己的粒子"演"的 ✗（一条 {@code END_ROD} 光带 ＋ 一圈爆发 ✗）——
     * 现在直接**调玩家那套机制** ✓：{@link com.tnc.tnc.light.TNLightBeamMechanics}（光线的实体光柱 ＋
     * 天上开阵 ＋ 1 秒后落柱 ＋ 画面震动 ✓）与 {@link com.tnc.tnc.light.TNLightChainMechanics}
     * （光耀的法阵 ＋ 天使 ＋ 减伤 buff ✓），施法者就是**阿波罗自己** ✓。
     * 也就是说：玩家放 t4/t5 长什么样，Boss 放就长什么样 ✓（同一份代码、同一张数值表 ✗ 不会走偏 ✓）。
     */
    private void applySpell(int spell) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        String path = PATHS[spell];
        if (IS_BEAM[spell]) {
            com.tnc.tnc.light.TNLightBeamMechanics.onSpellCast(this, path, LENGTH_SCALE[spell]);
        } else {
            com.tnc.tnc.light.TNLightChainMechanics.onSpellCast(this, path);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4F, 1.4F);
    }

    // ---- 资源 ----
    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/gothic_priest.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/gothic_priest_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/gothic_priest.animation.json");
    }
}