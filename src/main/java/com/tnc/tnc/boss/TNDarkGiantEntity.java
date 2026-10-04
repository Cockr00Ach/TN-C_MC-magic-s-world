package com.tnc.tnc.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * <b>暗系巨人领主</b>（作者 2026-10-03："做一个暗系 boss 的模型，我希望他是一个巨人的模型，
 * 然后通身穿戴盔甲，这个盔甲可以直接融到模型里面去，一个巨兽人领主的感觉" ✓）。
 *
 * <h2>★ 2026-10-03 第二版：二阶段 + 背后虚影（作者要求 ✓）</h2>
 * "我希望他有<b>二阶段</b>，二阶段的时候他的<b>背后会出现他自己模型的虚影</b>（他自己的<b>两倍高</b>），
 * <b>虚影跟他做一样的动作</b>" ✓ ⇒
 * <ul>
 *   <li><b>触发</b>：血量 ≤ {@link TNDarkGiantPhase#PHASE_TWO_AT}（一半 ✓）时切二阶段 ✓，
 *       同步给客户端（{@link #DATA_PHASE} ✓，虚影是客户端画的 ✓），一辈子只触发一次 ✓；</li>
 *   <li><b>虚影</b>：客户端 {@code TNDarkGiantPhantomLayer} 把**同一个模型**再画一遍（×2 高、背后 1.6 格、
 *       半透明暗紫 ✓）—— 动作自然和本体**一模一样** ✓（同一个骨架、同一帧动画 ✓，不用同步任何东西 ✓）；</li>
 *   <li><b>进了二阶段更凶</b>：移速 ×1.15、攻击 ×1.25 ✓ + 吼一声 + 冒暗紫烟 ✓ +
 *       播作者那个"召唤"动作（{@code playmagicsummonandsmogy} ✓ —— 虚影就是他自己召出来的 ✓）。</li>
 * </ul>
 *
 * <h2>作者的模型与动作（2026-10-03 他自己更新 ✓）</h2>
 * 模型 {@code geo/entity/dark_giant.geo.json}（13 骨 / 80 方块 / 512×512 图 ✓，肩宽 4 格 · 高约 6 格 ✓），
 * 动作在 {@code animations/entity/dark_giant.animation.json} ✓：
 * <ul>
 *   <li>{@link #ANIM_WALK} = {@code treadon} —— 走路 ✓（走起来循环播 ✓，站着回静止姿 ✓）；</li>
 *   <li>{@link #ANIM_MAGIC} = {@code playmagic} —— 施法 ✓（{@link #playMagic()} ✓）；</li>
 *   <li>{@link #ANIM_SUMMON} = {@code playmagicsummonandsmogy} —— 召唤 + 冒烟 ✓（{@link #playSummon()} ✓，进二阶段自动播 ✓）。</li>
 * </ul>
 * 还没有 idle 片段 ✗ ⇒ 站着的时候是模型的**静止姿** ✓（作者要"站着也动"就再导一个 idle ✓，我照 {@link #ANIM_WALK} 那样接上 ✓）。
 *
 * <p>★ 招式（放哪条链的法术）作者还没定 ✗ —— 定了就在 {@link #aiStep()} 里照阿波罗那套接 ✓，
 * 施法时调 {@link #playMagic()} 就会有动作 ✓。
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

    // ---- 作者给的三个动作片段 ✓ ----
    /** 走路（循环 ✓）。 */
    public static final String ANIM_WALK = "treadon";
    /** 施法（一次性 ✓）。 */
    public static final String ANIM_MAGIC = "playmagic";
    /** 召唤 + 冒烟（一次性 ✓）—— 进二阶段播它 ✓。 */
    public static final String ANIM_SUMMON = "playmagicsummonandsmogy";

    /** 动作控制器的名字 ✓（{@code triggerAnim} 用 ✓）。 */
    private static final String CONTROLLER_ACTION = "action";

    /**
     * 战斗阶段 ✓（{@link TNDarkGiantPhase#PHASE_ONE} / {@link TNDarkGiantPhase#PHASE_TWO} ✓）。
     * ★ 必须**同步** ✓ —— 虚影完全在客户端画 ✓，客户端不看这个数就不知道该不该画 ✗。
     */
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(TNDarkGiantEntity.class, EntityDataSerializers.INT);

    /** 顶部血条（作者要"boss 那种血量条"✓）。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---- 招式（作者 2026-10-03："会放暗龙的 t5 和召唤的 t4t5 和手的 t3t4" ✓ 见 TNDarkGiantSpells ✓）----
    /** 正在起手的那一招（−1 = 没在起手 ✓）。 */
    private int pendingMove = -1;
    /** 还有多少 tick 生效 ✓（= 作者那段施法动画的长度附近 ✓）。 */
    private int pendingTicks;
    /** 两招之间歇多久 ✓（二阶段更凶 ⇒ 歇得更短 ✓）。 */
    private int castCooldown = 80;
    private static final int COOLDOWN_PHASE_ONE = 90;
    private static final int COOLDOWN_PHASE_TWO = 45;

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, TNDarkGiantPhase.PHASE_ONE);
    }

    // ------------------------------------------------------------------
    //  二阶段
    // ------------------------------------------------------------------

    /** 现在第几阶段 ✓（客户端也读得到 ✓）。 */
    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    /** 是不是二阶段 ✓（虚影层看的就是它 ✓）。 */
    public boolean isPhaseTwo() {
        return this.phase() >= TNDarkGiantPhase.PHASE_TWO;
    }

    /**
     * ★ 进二阶段 ✓（服务端 ✓，一辈子只走一次 ✓）。
     *
     * <p>做四件事：① 同步阶段（客户端由此开始画虚影 ✓）② 本体变强 ✓
     * ③ 吼一声 + 暗紫烟 + 地面魔法阵那种表现 ✓ ④ 播作者的"召唤"动作 ✓ + 给附近玩家打一行字 ✓。
     */
    private void enterPhaseTwo(ServerLevel level) {
        this.entityData.set(DATA_PHASE, TNDarkGiantPhase.PHASE_TWO);
        // ① 本体变强（二阶段该更凶 ✓）
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * TNDarkGiantPhase.PHASE_TWO_SPEED_MULTIPLIER);
        }
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * TNDarkGiantPhase.PHASE_TWO_DAMAGE_MULTIPLIER);
        }
        // ② 表现：一声闷吼 + 一圈暗紫烟 + 脚下翻上来的魂火 ✓
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.55F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.0D, this.getZ(),
                120, 2.0D, 1.6D, 2.0D, 0.05D);
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(),
                80, 2.2D, 1.0D, 2.2D, 0.03D);
        // ③ 播作者那个"召唤"动作 ✓ —— 虚影就是他召出来的 ✓
        this.playSummon();
        // ④ 告诉附近的人
        for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(64.0D))) {
            nearby.displayClientMessage(Component.literal(
                    "§5[TN-C] §r巨兽人领主进入 §l二阶段§r§5 —— 他背后站起了自己的虚影"), false);
        }
        LOGGER.info("TN-C/boss: 巨兽人领主进入二阶段 phase={} hp={}/{}", this.phase(),
                this.getHealth(), this.getMaxHealth());
    }

    // ------------------------------------------------------------------
    //  动作（作者的三个片段 ✓）
    // ------------------------------------------------------------------

    /** 播"施法"动作 ✓（以后接法术时在起手那一刻调它 ✓）。 */
    public void playMagic() {
        this.triggerAnim(CONTROLLER_ACTION, "magic");
    }

    /** 播"召唤+冒烟"动作 ✓（进二阶段会自动调一次 ✓）。 */
    public void playSummon() {
        this.triggerAnim(CONTROLLER_ACTION, "summon");
    }

    // ---- 目标/行为 ----
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

    /**
     * 剔除盒（决定"什么时候整个不画"✓）—— ★ 二阶段要**放大** ✗：
     * 背后那个虚影有本体**两倍高**（12 格）✓，用本体那么小的盒子去剔除，
     * 从远处/仰角看的时候虚影会**整块突然消失** ✗（作者一眼就会看出"虚影一闪一闪"✗）。
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        net.minecraft.world.phys.AABB box = super.getBoundingBoxForCulling();
        if (!this.isPhaseTwo()) {
            return box;
        }
        return box.inflate(4.0D, 3.0D, 4.0D).expandTowards(0.0D, HITBOX_HEIGHT * 2.0D, 0.0D);
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
        // ★ 血线到了就进二阶段 ✓（只会进一次 ✓ 见 TNDarkGiantPhase ✓）
        if (TNDarkGiantPhase.shouldEnterPhaseTwo(this.getHealth(), this.getMaxHealth(), this.phase())) {
            this.enterPhaseTwo(level);
        }
        // 脚下冒暗色尘（纯表现，让这尊巨人站在那儿有存在感 ✓）；二阶段更浓、还往上飘魂火 ✓
        if (this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2D, this.getZ(),
                    6, HITBOX_WIDTH * 0.6D, 0.2D, HITBOX_WIDTH * 0.6D, 0.01D);
        }
        if (this.isPhaseTwo() && this.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + (this.random.nextDouble() - 0.5D) * 3.0D,
                    this.getY() + 0.2D,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * 3.0D,
                    2, 0.3D, 0.4D, 0.3D, 0.02D);
        }
        this.tickCasting(level);
    }

    /**
     * 出招的节奏 ✓（照阿波罗那套：**起手 → 作者的施法动画播完才生效** ✓）。
     *
     * <p>没目标不放 ✗；两招之间有冷却 ✓；正在起手时这一 tick 不干别的 ✓。
     */
    private void tickCasting(ServerLevel level) {
        if (this.pendingMove >= 0) {
            if (--this.pendingTicks <= 0) {
                TNDarkGiantSpells.cast(this, this.pendingMove);
                this.pendingMove = -1;
                this.castCooldown = this.isPhaseTwo() ? COOLDOWN_PHASE_TWO : COOLDOWN_PHASE_ONE;
            }
            return;
        }
        if (this.getTarget() == null || --this.castCooldown > 0) {
            return;
        }
        this.startCast(TNDarkGiantSpells.pick(this.random), level);
    }

    /** 起手：播作者的施法动作（{@code playmagic} ✓）＋ 攒一撮暗火 ✓。 */
    private void startCast(int move, ServerLevel level) {
        this.pendingMove = move;
        this.pendingTicks = TNDarkGiantSpells.move(move).castTicks();
        this.playMagic();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.6F, 0.7F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 3.0D, this.getZ(),
                30, 1.2D, 1.0D, 1.2D, 0.04D);
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
        // 走路：动起来循环播作者的 treadon ✓；站着**不播**（作者的动画文件里还没有 idle ✗ ⇒ 回静止姿 ✓）
        AnimationController<TNDarkGiantEntity> move = new AnimationController<>(this, "move", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_WALK));
            }
            return PlayState.STOP;
        });
        controllers.add(move);

        // 一次性动作：由 playMagic() / playSummon() 触发 ✓（照抄 TNApolloEntity ✓）
        AnimationController<TNDarkGiantEntity> action = new AnimationController<>(this, CONTROLLER_ACTION, 2,
                state -> PlayState.STOP);
        action.triggerableAnim("magic", RawAnimation.begin().thenPlay(ANIM_MAGIC));
        action.triggerableAnim("summon", RawAnimation.begin().thenPlay(ANIM_SUMMON));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 资源（作者的模型 / 贴图 / 动作 ✓）----
    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/dark_giant.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dark_giant_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/dark_giant.animation.json");
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/boss");
}
