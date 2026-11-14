package com.tnc.tnc.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
 * <b>黑魔法师</b> —— 暗系新 boss ✓（作者 2026-11-07："你做一个黑魔法师，通身黑暗 …… 然后把现在的暗系 boss 的法术绑到它身上" ✓）。
 *
 * <h2>它和巨兽人领主的分工（作者原话）</h2>
 * "现在有的这个我想到时候拿去做<b>战士那边的 boss</b>" ✓ ⇒
 * <ul>
 *   <li>{@link TNDarkGiantEntity}（巨兽人领主）<b>一个字节都没动</b> ✓，留在场上等作者后面改成战士系 ✓；</li>
 *   <li>这个黑魔法师是<b>法师系</b>的暗系 boss ✓，<b>法术完全一样</b> ✓ ——
 *       招式表直接读 {@link TNDarkGiantSpells}（不复制 ✗，理由见 {@link TNDarkMageSpells} ✓）。</li>
 * </ul>
 *
 * <h2>外形（"通身黑暗"怎么落到模型上）</h2>
 * 模型 {@code geo/entity/dark_mage.geo.json}（16 骨 / 87 方块 / 256×256 图 ✓，高 3.9 格 · 肩宽 1.25 格 ✓），
 * 由 {@code tools/gen_dark_mage.py} 生成 ✓。四件事让它"黑得看得清"✓：
 * <ol>
 *   <li><b>没有脸</b>：兜帽里是一层更深的黑（void 贴图），只有两道暗红眼缝发光 ✓
 *       （{@code dark_mage_emissive.png} + {@code TNDarkMageEmissiveLayer} ✓）；</li>
 *   <li><b>没有脚</b>：袍摆下缘是一圈参差破布，离地 0.6 格 ✓ ⇒ 他是"浮"着的 ✓，
 *       走路是滑不是迈腿 ✓（{@link #ANIM_GLIDE}）;</li>
 *   <li><b>三档明度</b>：近黑袍 / 亮一档破布边 / 暗红符文与杖心 ✓ —— 全压到近黑就只剩一团黑 ✗；</li>
 *   <li><b>剪影</b>：尖兜帽 + 宽护肩 + 比自己还高的法杖 + 头后的<b>断环</b> ✓。</li>
 * </ol>
 *
 * <h2>数值</h2>
 * 血 {@link #MAX_HP}（2600，比巨人 3000 少一点 ✓：他是法师，不该比巨人还肉 ✓）、
 * 移速 0.22（比巨人 0.16 快 ✓：飘着走 ✓）、护甲比巨人低 ✓（没穿甲 ✓）。
 * 二阶段在 50% 触发 ✓（和巨人同一条规则 {@link TNDarkGiantPhase#shouldEnterPhaseTwo} ✓），
 * 但表现不同 ✓：不画背后虚影（那是巨人专属 ✓），而是<b>整个人涨开</b> + 断环加速 + 施法更急 ✓。
 */
public class TNDarkMageEntity extends Monster implements GeoEntity {

    /** 血量 ✓（巨人是 3000 ✓ —— 法师不该比巨人更肉 ✓）。 */
    public static final float MAX_HP = 2600.0F;

    /**
     * 渲染缩放（1.0 = 原样 ✓）。
     *
     * <p>★ 和巨人不一样：巨人的模型是**放大 10 倍**画的（它的 geo 只有 0.6 格高 ✗），
     * 而这个 geo 是按 1 单位 = 1/16 格直接量好的 ✓（总高 62 单位 = 3.875 格 ✓）⇒ 不用缩 ✓。
     */
    public static final double MODEL_SCALE = 1.0D;

    /** 碰撞箱（宽 × 高，格 ✓）—— 量自模型：护肩 1.25 格、帽尖 3.875 格 ✓，留一点余量 ✓。 */
    public static final float HITBOX_WIDTH = 1.1F;
    public static final float HITBOX_HEIGHT = 3.4F;

    /** 静止（循环 ✓）—— 上下浮 + 披风摆 + 断环自转 + 碎块绕 ✓。 */
    public static final String ANIM_IDLE = "idle";
    /** 移动（循环 ✓）—— 他浮着前进，所以是"倾一点 + 袍摆后拖"，不是迈腿 ✓。 */
    public static final String ANIM_GLIDE = "glide";
    /** 二阶段（一次性、停在最后一帧 ✓）—— 整个人涨开 ✓。 */
    public static final String ANIM_PHASE_TWO = "phase2";

    // ---- 2026-11-07 第二轮：作者"他的动作你也可以多做一点，让我有诡异神秘的感觉" ----
    /** 静止·注视（5.2s）：几乎不动，只有头极慢地转向你，中途一片碎块猛地一抽。 */
    public static final String ANIM_WATCH = "idle_watch";
    /** 抽搐（0.9s）：唯一"快"的动作 —— 44° 甩头 + 断环跳 120°，和满身慢动作形成对比。 */
    public static final String ANIM_TWITCH = "twitch";
    /** 招手（2.4s）：空手抬起来"来"两下，头歪着看。 */
    public static final String ANIM_BECKON = "beckon";
    /** 溃散（1.6s）：被打中时像信号不好一样压扁-拉伸-回弹，碎块炸开再收。 */
    public static final String ANIM_UNRAVEL = "unravel";
    /** 逼视（2.2s）：凑近、低头、盯着你。 */
    public static final String ANIM_STARE = "stare";

    // ==================================================================
    //  2026-11-07 第三轮：黑雾分身（作者"我还希望这个boss有分身的能力 ……"）
    // ==================================================================

    /** 分身是在**这一招放完之后**出现的 ✓（作者："能在释放黑雾之后分出两个分身"✓）。 */
    public static final String MIST_MOVE = "devour_light";
    /** 雾散了以后三个身影一起放的那一招 ✓（"等黑雾时间结束之后一起攻击"✓）。 */
    public static final String VOLLEY_MOVE = "night_embrace";
    /** 分几个 ✓。 */
    public static final int CLONE_COUNT = 2;
    /** 分身的血量 ✓（220：玩家有心就能在雾里清掉一个，但又不能一枪秒 ✓）。 */
    public static final float CLONE_MAX_HP = 220.0F;
    /**
     * 打碎一个分身，本体**最大生命**的这个比例直接掉 ✓
     * （作者："摧毁分身会扣除boss百分比血量"✓ —— 8% × 2 = 16%，两次都清掉约等于白送 1/6 管血 ✓，
     * 但反噬**不会**直接打死本体 ✓ 见 {@link #onCloneDefeated}）。
     */
    public static final float CLONE_HP_PENALTY = 0.08F;
    /** 齐射前的预警时长（tick）—— 让玩家有 1 秒反应时间 ✓。 */
    public static final int VOLLEY_WARNING_TICKS = 20;

    /** 动作控制器的名字 ✓（{@code triggerAnim} 用 ✓，和巨人同名 ✓）。 */
    private static final String CONTROLLER_ACTION = "action";

    /** 战斗阶段 ✓（{@link TNDarkGiantPhase} 的两个值 ✓）—— 客户端要读（断环/粒子表现）⇒ 必须同步 ✓。 */
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(TNDarkMageEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(TNDarkMageEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 正在演的小动作（0 = 没有 ✓）。
     *
     * <p>为什么要同步到客户端：客户端的移动控制器要据此**停掉 idle/glide** ✗ 不然会和
     * 触发的动作片段抢同一批骨骼 ⇒ 两个动作叠在一起抽搐 ✗。
     */
    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(TNDarkMageEntity.class, EntityDataSerializers.INT);

    /** 这个是不是黑雾分身（true = 分身 ✓）—— 同步是为了让客户端/存档都能分辨 ✓。 */
    private static final EntityDataAccessor<Boolean> DATA_CLONE =
            SynchedEntityData.defineId(TNDarkMageEntity.class, EntityDataSerializers.BOOLEAN);

    /** 顶部血条 ✓ —— 紫色 ✓（巨人是红的 ✓，两个暗系 boss 摆一起时一眼能分开 ✓）。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---- 施法（形状照抄巨人的那套起手/生效/冷却循环 ✓，只是没有"升天"那一支 ✓）----
    /** 正在起手的那一招（−1 = 没在起手 ✓）。 */
    private int pendingMove = -1;
    /** 还有多少 tick 生效 ✓（= 那一招的 castTicks ✓）。 */
    private int pendingTicks;
    /** 两招之间歇多久 ✓（二阶段更凶 ⇒ 歇得更短 ✓）。 */
    private int castCooldown = 60;
    private int completedCasts;
    private int previousMove = -1;
    private static final int COOLDOWN_PHASE_ONE = 70;
    private static final int COOLDOWN_PHASE_TWO = 40;

    // ---- 小动作的排期（只在服务端算，客户端靠 DATA_ACTION 知道"正在演"）----
    /** 还有多少 tick 演完（0 = 没在演 ✓）。 */
    private int actionTicks;
    /** 下一次"自娱自乐"的倒计时 ✓（5~12 秒一次 ⇒ 不会一直抖，也不会让人以为坏了 ✓）。 */
    private int idlePerformanceCooldown = 80;
    /** 被打中时"溃散"的冷却 ✓（否则被连击会一直僵住、一招都放不出来 ✗）。 */
    private int unravelCooldown;

    // ---- 分身（2026-11-07 第三轮）----
    /** 本体这边记着场上还活着哪些分身 ✓（用来齐射、清理、以及"本体死了分身一起散"✓）。 */
    private final java.util.List<java.util.UUID> swarmClones = new java.util.ArrayList<>();
    /** 雾什么时候散（世界 gameTime ✓；−1 = 现在没有雾 ✓）。 */
    private long swarmEndTick = -1L;
    /** 雾散之后到齐射之间还剩多少 tick（−1 = 没在排队 ✓）—— 那 1 秒是给玩家跑的反应时间 ✓。 */
    private int jointCountdown = -1;
    /** 分身：本体是谁 ✓（打碎的时候按这个找回本体扣血 ✓）。 */
    private java.util.UUID anchorUuid;
    /** 分身：还有多少 tick 自己消散 ✓（0 = 不限，理论上不该出现 ✓）。 */
    private int cloneLife;

    public TNDarkMageEntity(EntityType<? extends TNDarkMageEntity> type, Level level) {
        super(type, level);
        this.xpReward = 600;
        this.refreshDimensions();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose) {
        return net.minecraft.world.entity.EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT);
    }

    public boolean isCasting() {
        return this.entityData.get(DATA_CASTING);
    }

    /** 正在演小动作（抽搐/招手/久视/逼视/溃散）✓。 */
    public boolean isPerforming() {
        return this.entityData.get(DATA_ACTION) != 0;
    }

    /** 起手 或 演小动作 —— 两种都"占着身体"✓：不移动、不放招、不近战 ✓。 */
    public boolean isBusy() {
        return isCasting() || isPerforming();
    }

    /** 这是不是黑雾分身 ✓（分身不近战、不算阶段、没有血条、被打碎会反噬本体 ✓）。 */
    public boolean isClone() {
        return this.entityData.get(DATA_CLONE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)          // 飘着走 ⇒ 比巨人快 ✓
                .add(Attributes.FOLLOW_RANGE, TNDarkMageSpells.FOLLOW_RANGE)
                .add(Attributes.ARMOR, 6.0D)                    // 没穿甲 ⇒ 比巨人(12)低 ✓
                .add(Attributes.ARMOR_TOUGHNESS, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);    // boss 不该被玩家一路推着走 ✓
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, TNDarkGiantPhase.PHASE_ONE);
        this.entityData.define(DATA_CASTING, false);
        this.entityData.define(DATA_ACTION, 0);
        this.entityData.define(DATA_CLONE, false);
    }

    // ------------------------------------------------------------------
    //  二阶段（规则和巨人同一条 ✓，表现是自己的 ✓）
    // ------------------------------------------------------------------

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isPhaseTwo() {
        return this.phase() >= TNDarkGiantPhase.PHASE_TWO;
    }

    /**
     * 进二阶段 ✓（服务端 ✓，一辈子只走一次 ✓ —— 规则在 {@link TNDarkGiantPhase#shouldEnterPhaseTwo} ✓）。
     *
     * <p>三件事：① 同步阶段 ② 变强（移速 ×1.15 / 攻击 ×1.25，和巨人同一组系数 ✓）
     * ③ 表现：播 {@link #ANIM_PHASE_TWO}（整个人涨开 ✓）+ 一声尖啸 + 黑紫烟 + 给附近玩家一行字 ✓。
     */
    private void enterPhaseTwo(ServerLevel level) {
        this.entityData.set(DATA_PHASE, TNDarkGiantPhase.PHASE_TWO);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * TNDarkGiantPhase.PHASE_TWO_SPEED_MULTIPLIER);
        }
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * TNDarkGiantPhase.PHASE_TWO_DAMAGE_MULTIPLIER);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 3.0F, 0.45F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.8D, this.getZ(),
                140, 1.6D, 1.8D, 1.6D, 0.10D);
        level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.6D, this.getZ(),
                80, 1.4D, 1.2D, 1.4D, 0.05D);
        level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 2.6D, this.getZ(),
                60, 1.2D, 1.4D, 1.2D, 0.05D);
        this.triggerAnim(CONTROLLER_ACTION, ANIM_PHASE_TWO);
        for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(64.0D))) {
            nearby.displayClientMessage(Component.literal(
                    "§5[TN-C] §r黑魔法师进入 §l二阶段§r§5 —— 兜帽下的黑暗涨开了"), false);
        }
        LOGGER.info("TN-C/boss: 黑魔法师进入二阶段 phase={} hp={}/{}", this.phase(),
                this.getHealth(), this.getMaxHealth());
    }

    // ------------------------------------------------------------------
    //  行为
    // ------------------------------------------------------------------

    /**
     * 打中目标时**不播动作** ✓ —— 他是法师，贴身抡人本来就不该有招式 ✓。
     * 施法中一律不许近战 ✓（和巨人一致：起手期间别一边放法术一边扇巴掌 ✗）。
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (isBusy() || isClone()) {
            return false;   // 分身不近战 ✓（它们唯一的攻击方式就是那次齐射 ✓）
        }
        return super.doHurtTarget(target);
    }

    /**
     * 被打中时"闪"一下 ✓（只有这个动作是**被动**触发的 ⇒ 玩家打他会有反馈，不会像打一堵墙 ✗）。
     *
     * <p>两个闸门很重要：① 起手中不打断（不然玩家的连击能让他永远放不出法术 ✗）；
     * ② 15 秒冷却（否则一直僵在溃散里 ✗）。
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide() && this.level() instanceof ServerLevel level
                && !isCasting() && !isPerforming() && this.unravelCooldown <= 0 && amount >= 1.0F) {
            this.unravelCooldown = 300;
            perform(ANIM_UNRAVEL, 34, level);
        }
        return hit;
    }


    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof com.tnc.tnc.dark.TNDarkSummonEntity summon && getUUID().equals(summon.owner())) {
            return true;
        }
        // 本体 ↔ 自己的分身、同一批分身之间：友军 ✓（不然黑雾/抽血会打到自己的分身 ✗）
        if (entity instanceof TNDarkMageEntity other) {
            if (isClone() && other.isClone() && java.util.Objects.equals(this.anchorUuid, other.anchorUuid)) {
                return true;
            }
            if (!isClone() && other.isClone() && getUUID().equals(other.anchorUuid)) {
                return true;
            }
            if (isClone() && !other.isClone() && other.getUUID().equals(this.anchorUuid)) {
                return true;
            }
        }
        return super.isAlliedTo(entity);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 直给转向，不走 A*（和巨人同一个理由：boss 体型大，A* 容易卡住 ✓）
        this.goalSelector.addGoal(2, new GiantApproachGoal(this, 1.0D, () -> !isBusy()));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, (float) TNDarkMageSpells.FOLLOW_RANGE));
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
     * 剔除盒 ✓ —— 法杖伸出去 1 格、断环在身后 1 格、二阶段还会往上"涨" ✓，
     * 用本体那么小的盒子会在远处**整根法杖突然消失** ✗。
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(2.0D, 1.5D, 2.0D);
    }

    // ---- 顶部 BOSS 血条 ----
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isClone()) {
            this.bossEvent.addPlayer(player);   // 分身不挂血条 ✓（血条就是"哪个是真的"的唯一提示 ✓）
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    // ---- 存档（分身标记 / 本体记的分身名单，必须存 ✗ 否则读档后分身会变回一个完整 boss ✗）----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TnClone", isClone());
        if (this.anchorUuid != null) {
            tag.putUUID("TnAnchor", this.anchorUuid);
        }
        tag.putInt("TnCloneLife", this.cloneLife);
        tag.putLong("TnSwarmEnd", this.swarmEndTick);
        tag.putInt("TnJoint", this.jointCountdown);
        ListTag swarm = new ListTag();
        for (var id : this.swarmClones) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            swarm.add(entry);
        }
        tag.put("TnSwarm", swarm);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_CLONE, tag.getBoolean("TnClone"));
        this.anchorUuid = tag.hasUUID("TnAnchor") ? tag.getUUID("TnAnchor") : null;
        this.cloneLife = tag.getInt("TnCloneLife");
        this.swarmEndTick = tag.getLong("TnSwarmEnd");
        this.jointCountdown = tag.getInt("TnJoint");
        this.swarmClones.clear();
        ListTag swarm = tag.getList("TnSwarm", 10);   // 10 = CompoundTag
        for (int i = 0; i < swarm.size(); i++) {
            CompoundTag entry = swarm.getCompound(i);
            if (entry.hasUUID("Id")) {
                this.swarmClones.add(entry.getUUID("Id"));
            }
        }
        if (isClone()) {
            // 读档后血量上限必须还是分身的 220 ✓（不然会变回 2600 的"假 boss"✗）
            AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && hp.getBaseValue() > (double) CLONE_MAX_HP) {
                hp.setBaseValue(CLONE_MAX_HP);
            }
            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
            this.xpReward = 0;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // 分身：没有血条、不进二阶段、不排小动作以外的活 ⇒ 走自己的那条 tick ✓
        if (isClone()) {
            this.tickClone(level);
            return;
        }
        var follow = getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && follow.getBaseValue() < TNDarkMageSpells.FOLLOW_RANGE) {
            follow.setBaseValue(TNDarkMageSpells.FOLLOW_RANGE);
        }
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(!this.isDeadOrDying() && this.isAlive());
        if (TNDarkGiantPhase.shouldEnterPhaseTwo(this.getHealth(), this.getMaxHealth(), this.phase())) {
            this.enterPhaseTwo(level);
        }
        this.tickActions(level);
        this.tickCasting(level);
        this.tickSwarm(level);
    }

    /**
     * 小动作的排期（服务端 ✓，2026-11-07 第二轮加）。
     *
     * <p>节奏是这轮"诡异"的核心：**平时几乎不动，偶尔一下快到看不清**✓。
     * 5~12 秒排一次，按权重抽：
     * <ul>
     *   <li>眼里有目标（16 格内）→ 45% 逼视（低头凑近盯着你）✓</li>
     *   <li>35% 抽搐（0.9 秒甩头）✓ —— 全场唯一"快"的动作，靠对比吓人 ✓</li>
     *   <li>15% 久视（5.2 秒几乎静止，只有头慢慢转 + 一片碎块抽一下）✓</li>
     *   <li>剩下的招手 ✓</li>
     * </ul>
     */
    private void tickActions(ServerLevel level) {
        if (this.unravelCooldown > 0) {
            this.unravelCooldown--;
        }
        if (this.actionTicks > 0) {
            this.getNavigation().stop();
            var motion = getDeltaMovement();
            setDeltaMovement(0, motion.y, 0);
            if (--this.actionTicks <= 0) {
                this.entityData.set(DATA_ACTION, 0);
            }
            return;
        }
        if (isCasting()) {
            return;
        }
        if (this.idlePerformanceCooldown > 0) {
            this.idlePerformanceCooldown--;
            return;
        }
        this.idlePerformanceCooldown = 100 + this.random.nextInt(140);   // 5~12 秒
        this.performIdle(level);
    }

    /** 按权重抽一个小动作 ✓（目标在近处就更爱"逼视"✓）。 */
    private void performIdle(ServerLevel level) {
        var target = getTarget();
        int roll = this.random.nextInt(100);
        if (target != null && target.isAlive() && distanceToSqr(target) < 256.0D) {
            if (roll < 45) {
                perform(ANIM_STARE, 46, level);
                return;
            }
            roll -= 45;
        }
        if (roll < 35) {
            perform(ANIM_TWITCH, 20, level);
        } else if (roll < 52) {
            perform(ANIM_WATCH, 106, level);
        } else {
            perform(ANIM_BECKON, 50, level);
        }
    }

    /** 演一个动作 ✓：冻结身体 + 触发片段 + 一句很轻的动静（不播声音就只剩"动作"没有"存在感"✗）。 */
    private void perform(String clip, int ticks, ServerLevel level) {
        this.actionTicks = ticks;
        this.entityData.set(DATA_ACTION, 1);
        this.getNavigation().stop();
        this.triggerAnim(CONTROLLER_ACTION, clip);
        if (ANIM_STARE.equals(clip)) {
            level.playSound(null, this.getX(), this.getY() + 2.4D, this.getZ(),
                    SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.85F, 0.55F);
        } else if (ANIM_TWITCH.equals(clip)) {
            level.playSound(null, this.getX(), this.getY() + 2.4D, this.getZ(),
                    SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.55F, 0.45F);
        }
    }

    /**
     * 把一个刚 {@code create()} 出来的黑魔法师改造成**黑雾分身** ✓（2026-11-07 第三轮）。
     *
     * <p>★ 为什么不另写一个 {@code TNDarkMageCloneEntity}：分身要和本体<b>长得一模一样</b> ✓
     * （作者要的就是"分不清哪个是真的"✓）—— 另写一个类就得再注册一个实体类型、再写一套
     * 模型/渲染器/自发光层（4 个文件），而且以后改本体外观还要记得同步改分身 ✗。
     * 这里只加一个同步标记 {@link #DATA_CLONE} ✓：同一套模型、贴图、13 段动作全部复用 ✓。
     *
     * <p>分身和本体的差别只有四条（都在下面各个方法里）：① 血 220 ✓ ② 不近战、不自己抽招 ✓
     * ③ 没有血条、不进二阶段 ✓ ④ 被打死 ⇒ 本体掉 8% 最大生命 ✓。
     *
     * @param owner     本体 ✓（打碎时按 UUID 找回它扣血 ✓）
     * @param lifeTicks 还能活多久（tick）✓ —— 雾散了、齐射打完了就自己散 ✓
     */
    public void configureAsClone(TNDarkMageEntity owner, int lifeTicks) {
        this.entityData.set(DATA_CLONE, true);
        this.anchorUuid = owner.getUUID();
        this.cloneLife = Math.max(20, lifeTicks);
        this.xpReward = 0;                       // 分身不给经验（不然刷分身比打 boss 还赚 ✗）
        this.setPersistenceRequired();
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(CLONE_MAX_HP);
        }
        this.setHealth(CLONE_MAX_HP);
        this.setTarget(owner.getTarget());
    }

    /** 本体这一侧：雾刚铺开 ⇒ 记下雾什么时候散，并分出两个分身 ✓。 */
    private void beginMist(ServerLevel level) {
        // ★ 判"还有活着的分身"而不是"名单非空"：名单里可能只剩上一批的尸首 UUID ✗
        //   （自己散掉的分身不在 die() 里剔名单）⇒ 那样新的雾就一个分身都分不出来、也不齐射 ✗
        this.swarmClones.removeIf(id -> {
            var entity = level.getEntity(id);
            return entity == null || !entity.isAlive() || entity.isRemoved();
        });
        if (!this.swarmClones.isEmpty() || this.swarmEndTick >= 0L) {
            return;                              // 同一批分身只分一次 ✓（雾重放了也不再翻倍 ✓）
        }
        int life = TNDarkGiantCasting.mistTicks(MIST_MOVE);
        this.swarmEndTick = level.getGameTime() + life;
        this.spawnSwarm(level, life + 80);       // 分身比雾多活 4 秒：够它们打完齐射 ✓
        LOGGER.info("TN-C/boss: 黑魔法师在雾里分出 {} 个分身，雾还有 {} tick", CLONE_COUNT, life);
    }

    /** 左右各分一个 ✓（贴着他身体两侧 2.6 格，位置用他自己的朝向算 ⇒ 面朝玩家分在左右 ✓）。 */
    private void spawnSwarm(ServerLevel level, int lifeTicks) {
        for (int i = 0; i < CLONE_COUNT; i++) {
            var clone = com.tnc.tnc.npc.TNNpcs.DARK_MAGE.get().create(level);
            if (clone == null) {
                continue;
            }
            double side = i == 0 ? Math.PI / 2 : -Math.PI / 2;
            double angle = Math.toRadians(getYRot()) + side;
            double x = getX() + Math.cos(angle) * 2.6D;
            double z = getZ() + Math.sin(angle) * 2.6D;
            clone.configureAsClone(this, lifeTicks);
            clone.moveTo(x, getY(), z, getYRot(), 0.0F);
            level.addFreshEntity(clone);
            this.swarmClones.add(clone.getUUID());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, getY() + 1.8D, z, 40, 0.7D, 1.6D, 0.7D, 0.08D);
            level.sendParticles(ParticleTypes.SQUID_INK, x, getY() + 0.8D, z, 30, 0.8D, 1.2D, 0.8D, 0.05D);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.HOSTILE, 1.2F, 0.4F);
        for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(48.0D))) {
            nearby.displayClientMessage(Component.literal(
                    "§5[TN-C] §r雾里站着 §l三个§r§5 黑魔法师"), true);
        }
    }

    /** 本体每 tick：雾什么时候散、散完 1 秒后三个身影一起抬手 ✓。 */
    private void tickSwarm(ServerLevel level) {
        if (this.swarmClones.isEmpty() && this.swarmEndTick < 0L && this.jointCountdown < 0) {
            return;
        }
        // 名单兜底：/kill、掉进虚空之类不走 onCloneDefeated 的消失也要剔掉 ✓
        this.swarmClones.removeIf(id -> {
            var entity = level.getEntity(id);
            return entity == null || !entity.isAlive() || entity.isRemoved();
        });
        if (this.swarmEndTick >= 0L && level.getGameTime() >= this.swarmEndTick) {
            this.swarmEndTick = -1L;
            this.jointCountdown = VOLLEY_WARNING_TICKS;
            level.playSound(null, getX(), getY() + 2.0D, getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 0.9F, 1.35F);
        }
        if (this.jointCountdown >= 0 && --this.jointCountdown <= 0) {
            this.jointCountdown = -1;
            this.launchJointAttack(level);
        }
    }

    /**
     * 齐射 ✓：本体和<b>还活着的</b>分身同一 tick 起手 {@link #VOLLEY_MOVE}（黑夜之手 T3）✓。
     *
     * <p>三个人各从自己的位置放 ⇒ 三组黑手从三个方向同时砸下来 ✓（"一起攻击"✓）。
     * 玩家提前打碎几个分身，就少几只黑手 + 本体掉一截血 ✓。
     */
    private void launchJointAttack(ServerLevel level) {
        int move = TNDarkMageSpells.indexOf(VOLLEY_MOVE);
        if (move < 0) {
            LOGGER.warn("TN-C/boss: 招式表里没有 {} —— 分身齐射跳过", VOLLEY_MOVE);
            return;
        }
        boolean bossCasts = !isBusy() && getTarget() != null && getTarget().isAlive();
        if (bossCasts) {
            startCast(move, level);
        }
        int fired = 0;
        for (var id : java.util.List.copyOf(this.swarmClones)) {
            if (level.getEntity(id) instanceof TNDarkMageEntity clone && clone.isAlive() && !clone.isRemoved()) {
                clone.startCast(move, level);
                fired++;
            }
        }
        level.playSound(null, getX(), getY() + 2.0D, getZ(), SoundEvents.ENDERMAN_SCREAM,
                SoundSource.HOSTILE, 1.1F, 0.5F);
        for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(48.0D))) {
            nearby.displayClientMessage(Component.literal(
                    "§5[TN-C] §r雾散了 —— §l" + (fired + (bossCasts ? 1 : 0)) + " 个身影§r§5 同时抬手"), true);
        }
        LOGGER.info("TN-C/boss: 黑魔法师齐射 boss={} clones={}", bossCasts, fired);
    }

    /** 分身被打死 ⇒ 本体按**最大生命**的 {@link #CLONE_HP_PENALTY} 掉血 ✓（作者："摧毁分身会扣除boss百分比血量"）。 */
    void onCloneDefeated(TNDarkMageEntity clone, ServerLevel level) {
        this.swarmClones.remove(clone.getUUID());
        float penalty = this.getMaxHealth() * CLONE_HP_PENALTY;
        // ★ 反噬只削血、不击杀：留 1 点保底 ⇒ 玩家不能靠"养分身再打死"把 boss 直接秒掉 ✓
        this.setHealth(Math.max(1.0F, this.getHealth() - penalty));
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1.9D, getZ(),
                26, 0.7D, 1.3D, 0.7D, 0.12D);
        level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.0D, getZ(),
                50, 0.9D, 1.4D, 0.9D, 0.06D);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_HURT,
                SoundSource.HOSTILE, 0.9F, 0.55F);
        for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(48.0D))) {
            nearby.displayClientMessage(Component.literal(
                    "§5[TN-C] §r分身碎了 —— 反噬本体 §c-" + Math.round(penalty) + "§r§5（"
                            + Math.round(CLONE_HP_PENALTY * 100.0F) + "% 最大生命）"), true);
        }
        LOGGER.info("TN-C/boss: 分身被打碎 ⇒ 本体 -{} HP（{} → {}）", Math.round(penalty),
                Math.round(this.getHealth() + penalty), Math.round(this.getHealth()));
    }

    /** 分身的每 tick：寿命到了就散，其余交给 {@link #tickCasting}（只会放已经排好的那一招 ✓）。 */
    private void tickClone(ServerLevel level) {
        if (this.cloneLife > 0 && --this.cloneLife <= 0) {
            this.dissolve(level);
            return;
        }
        this.tickActions(level);
        this.tickCasting(level);
    }

    /** 分身自己散掉 ✓（不是被打碎 ⇒ **不**反噬本体 ✓）。 */
    private void dissolve(ServerLevel level) {
        level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.6D, getZ(),
                50, 0.9D, 1.5D, 0.9D, 0.06D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.9D, getZ(),
                40, 0.8D, 1.4D, 0.8D, 0.08D);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.6F, 0.5F);
        this.discard();
    }

    /**
     * 本体死了 ⇒ 分身一起散 ✓（不然会留下两个"没主的黑魔法师"，玩家还得再打一遍 ✗）。
     * 分身死了 ⇒ 通知本体扣血 ✓（只有<b>被打死</b>才算 ✓，自己散的不算 ✓）。
     */
    @Override
    public void die(DamageSource source) {
        boolean clone = isClone();
        java.util.UUID anchor = this.anchorUuid;
        super.die(source);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (clone) {
            if (anchor != null && level.getEntity(anchor) instanceof TNDarkMageEntity boss && boss.isAlive()) {
                boss.onCloneDefeated(this, level);
            }
            return;
        }
        for (var id : java.util.List.copyOf(this.swarmClones)) {
            if (level.getEntity(id) instanceof TNDarkMageEntity cloneEntity) {
                cloneEntity.dissolve(level);
            }
        }
        this.swarmClones.clear();
        this.swarmEndTick = -1L;
        this.jointCountdown = -1;
    }


    /** 起手 → 到点放一次 → 冷却 ✓（整段形状和巨人一样 ✓，区别只有"没有升天那支"✓）。 */
    private void tickCasting(ServerLevel level) {
        if (this.pendingMove >= 0) {
            this.getNavigation().stop();
            var motion = getDeltaMovement();
            setDeltaMovement(0, motion.y, 0);
            if (getTarget() != null) {
                this.getLookControl().setLookAt(getTarget(), 30, 30);
            }
            if (--this.pendingTicks <= 0) {
                boolean cast = TNDarkMageSpells.cast(this, this.pendingMove);
                if (cast) {
                    this.previousMove = this.pendingMove;
                    this.completedCasts++;
                    // 黑雾落地的那一刻：分出两个分身（2026-11-07 第三轮 ✓）
                    if (MIST_MOVE.equals(TNDarkMageSpells.move(this.pendingMove).path())) {
                        this.beginMist(level);
                    }
                }
                this.pendingMove = -1;
                this.entityData.set(DATA_CASTING, false);
                this.castCooldown = cast ? (this.isPhaseTwo() ? COOLDOWN_PHASE_TWO : COOLDOWN_PHASE_ONE) : 20;
            }
            return;
        }
        if (isClone()) {
            return;      // 分身只放"被排好的那一招"✓，不自己抽招（否则等于三个 boss 在打 ✗）
        }
        if (this.getTarget() == null || !this.getTarget().isAlive()
                || isBusy()
                || distanceToSqr(this.getTarget()) > TNDarkMageSpells.CAST_RANGE * TNDarkMageSpells.CAST_RANGE
                || !hasLineOfSight(this.getTarget()) || --this.castCooldown > 0) {
            return;
        }
        this.startCast(TNDarkMageSpells.pick(this.random, this.completedCasts, this.previousMove), level);
    }

    /** 起手：播这一招对应的动作 ✓（{@link TNDarkMageSpells#animation} 给的片段名 ✓）+ 攒一撮暗火 ✓。 */
    void startCast(int move, ServerLevel level) {
        this.pendingMove = move;
        this.pendingTicks = TNDarkMageSpells.move(move).castTicks();
        this.entityData.set(DATA_CASTING, true);
        this.getNavigation().stop();
        this.triggerAnim(CONTROLLER_ACTION,
                TNDarkMageSpells.animation(TNDarkMageSpells.move(move)));
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.5F, 0.55F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.9D, this.getZ(),
                30, 0.9D, 1.3D, 0.9D, 0.06D);
        level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 2.8D, this.getZ(),
                10, 0.6D, 0.6D, 0.6D, 0.03D);
    }

    /** 身上一直有黑雾在绕 ✓（客户端本地粒子，不走网络包 ✓）—— 和巨人同一套写法，只是半径按他自己的身宽 ✓。 */
    private void blackAuraParticles() {
        if (!level().isClientSide() || !isAlive() || isInvisible() || tickCount % 4 != 0) {
            return;
        }
        var dust = new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(0.004F, 0.003F, 0.006F), isPhaseTwo() ? 3.0F : 2.4F);
        int perLayer = isPhaseTwo() ? 3 : 2;
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < perLayer; i++) {
                double angle = tickCount * 0.035 + layer * 1.1 + i * Math.PI * 2 / perLayer + random.nextDouble() * 0.6;
                double radius = getBbWidth() * (0.55 + random.nextDouble() * 0.45);
                double x = getX() + Math.cos(angle) * radius;
                double z = getZ() + Math.sin(angle) * radius;
                double y = getY() + getBbHeight() * (layer + random.nextDouble()) / 3.0;
                level().addParticle(dust, true, x, y, z,
                        -Math.sin(angle) * 0.05, 0.05 + random.nextDouble() * 0.05, Math.cos(angle) * 0.05);
                if (i == 0) {
                    level().addParticle(ParticleTypes.SQUID_INK, true, x, y, z, 0, 0.03, 0);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.blackAuraParticles();
    }

    // ------------------------------------------------------------------
    //  渲染（GeckoLib）
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 移动：飘着走播 glide ✓，站着播 idle ✓，起手 / 演小动作期间交给 action 控制器 ✓
        // ★ isBusy() 而不是 isCasting()：小动作也要把 idle 停掉 ✗ 否则两个片段抢同一批骨骼 ⇒ 抽搐叠加 ✗
        AnimationController<TNDarkMageEntity> move = new AnimationController<>(this, "move", 5, state -> {
            state.getController().setAnimationSpeed(this.isPhaseTwo() ? 1.15F : 1.0F);
            if (isBusy()) {
                return PlayState.STOP;
            }
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_GLIDE));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE));
        });
        controllers.add(move);

        // 一次性动作：起手时由 startCast() 按招式触发 ✓（片段名来自 TNDarkGiantSpells.animation() ✓）；
        // 小动作由 perform() 触发 ✓（2026-11-07 第二轮：注视 / 抽搐 / 招手 / 溃散 / 逼视）
        AnimationController<TNDarkMageEntity> action = new AnimationController<>(this, CONTROLLER_ACTION, 2,
                state -> PlayState.STOP);
        for (String clip : new String[]{"cast_hands", "cast_sky_hands", "cast_mist", "cast_siphon",
                "command_army", ANIM_PHASE_TWO, ANIM_WATCH, ANIM_TWITCH, ANIM_BECKON, ANIM_UNRAVEL,
                ANIM_STARE}) {
            action.triggerableAnim(clip, RawAnimation.begin().thenPlay(clip));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 资源 ----
    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/dark_mage.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dark_mage_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/dark_mage.animation.json");
    }

    /** 只有眼缝 / 胸前符文 / 杖心发光 ✓（其余全透明 ⇒ 那一层不画 ✓）。 */
    public static ResourceLocation emissiveTextureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dark_mage_emissive.png");
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/boss");
}
