package com.tnc.tnc.magic;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>黑夜之手：一只从施法者背后甩出去、抓住人不放的手</b> ✓
 * （作者 2026-10-10：「暗魔法的黑夜之手可能要整个改一下」，参考《Re:Zero》486 的不可视之手 ✓）。
 *
 * <h2>为什么要我们自己飞（这是本文件存在的全部理由）</h2>
 * 之前这条路是<b>引擎的投射物</b>（spell JSON 的 {@code release.target.type = PROJECTILE}）。
 * 作者 2026-10-10 的实机反馈把这条路判死了 ✗：
 * <ol>
 *   <li><b>「手一直在旋转」</b> ✗ —— 引擎的 {@code SpellProjectileRenderer} 在
 *       {@code ALONG_MOTION} 下把模型对齐到<b>运动矢量</b>，而 {@code homing} 让运动矢量
 *       每一 tick 都在拐 ⇒ 手就一直在偏航（反编译确认：那一段是
 *       {@code atan2} ＋ 两次 {@code Axis.rotation} 的连续量）；</li>
 *   <li><b>「不是掌心对着别人」</b> ✗ —— 姿态由引擎那一套决定，我们只能给两个字段
 *       （{@code orientation} / {@code rotate_degrees_offset}），定不住"掌心朝目标"；</li>
 *   <li><b>「不能所有手都是直直飞出去，要有弯的弧线」</b> ✗✗ —— 引擎的
 *       {@code LaunchProperties} <b>只有</b> {@code velocity / extra_launch_count /
 *       extra_launch_delay}（javap 确认），<b>没有散射角、也没有弧线</b>；
 *       {@code extra_launch_*} 只是"沿同一条直线多发几发"⇒ 三只手永远是一条直线 ✗。</li>
 * </ol>
 * ⇒ 三件事都指向同一个结论：<b>飞行这一段必须由我们自己拥有</b>。
 *
 * <h2>现在这只手怎么动</h2>
 * <pre>
 *   ① 甩出（FLY）：从施法者<b>后背</b>出发，沿<b>二次贝塞尔弧线</b>甩向目标
 *      （起点 = 后背，控制点 = 中点 ＋ 侧向张开 ＋ 上抛，终点 = 目标）
 *      ⇒ 每只手一条**不同的弧**，"从背后伸出来好几只手"的观感 ✓；
 *   ② 抓住（GRIP）：到了就贴住目标 —— 压制它的移动、每 {@link #SQUEEZE_INTERVAL} tick
 *      捏一次、t3 起往回拽、t5 松手前捏碎 ✓（这一段与上一版相同 ✓）。
 * </pre>
 *
 * <h2>几个刻意的决定</h2>
 * <ul>
 *   <li><b>朝向不用"运动方向"</b> ✗：手飞的时候让掌心<b>一直朝着目标</b>
 *       （渲染器用同步字段 {@link #DATA_TARGET} 算方向）⇒ 从任何角度看都不该出现"翻滚"✓；</li>
 *   <li><b>不需要自己写命中判定</b> ✓：目标在施法那一刻就由瞄准锁定下来了（见
 *       {@link TNDarkHandMechanics} ⇒ 与作者要的"玩家瞄住敌人"一致 ✓），
 *       飞行途中只需按它的当前位置收弧、到了就抓 ✓；</li>
 *   <li><b>没瞄到人也能用</b> ✓：这时手沿弧线飞到准星前方 {@code range} 处，然后散掉 ✓
 *       （"没瞄准就抓不到人"的手感 ✓）。</li>
 * </ul>
 *
 * <h2>数值表（想调手感只改这几张表）</h2>
 * <pre>
 *   档  法术            甩出  抓住时长   每段捏   拽(格/10t)  末尾捏碎
 *   t1  night_hand      0.6s   1.0 s     2.0      —          —
 *   t2  night_raid      0.6s   1.5 s     3.0      —          —
 *   t3  night_embrace   0.6s   2.0 s     4.0      0.35       —
 *   t4  black_ruin      0.6s   2.5 s     5.5      0.45       —
 *   t5  slay_light      0.6s   3.0 s     7.0      0.55       10.0
 * </pre>
 * ⚠️ <b>未实机验收</b>（开发环境看不到画面）：几何、字段、闸门这些静态能核的都核了 ✓；
 * 弧线的弧度与手感请作者进游戏看，参数就在下面这几张表里 ✓。
 */
public class TNNightGripEntity extends Entity {

    // ------------------------------------------------------------------
    //  甩出（弧线）
    // ------------------------------------------------------------------

    /** 甩出用多久（tick）：12 tick = 0.6 秒，与法术 JSON 的 {@code cast.duration} 对齐 ✓。 */
    private static final int FLY_TICKS = 12;

    /** 弧线控制点抬多高（格）⇒ 手"甩"出去而不是直线推出去 ✓。 */
    private static final double ARC_LIFT = 1.35D;

    /** 相邻两只手的弧线左右分开多少格（作者："好几只手"✓）。 */
    private static final double ARC_SPREAD = 1.9D;

    /** 每只手再额外错开一点上抛量，免得几条弧完全平行 ✓。 */
    private static final double ARC_LIFT_STEP = 0.45D;

    /** 起点从施法者后背再往外挪一点 ⇒ 明确是"从背后出来"的 ✓。 */
    private static final double BACK_OFFSET = 0.55D;

    /** 起点高度（占身高比例）✓。 */
    private static final double BACK_HEIGHT = 0.65D;

    // ------------------------------------------------------------------
    //  抓住（与上一版相同的部分）
    // ------------------------------------------------------------------

    /** 每档：抓住多久（tick）· 每段伤害 · 每次拽多远（0 = 不拽）· 末尾捏碎伤害（0 = 不捏）。 */
    private static final GripStats[] STATS = {
            new GripStats(20, 2.0F, 0.00D, 0.0F),   // t1 黑夜之手
            new GripStats(30, 3.0F, 0.00D, 0.0F),   // t2 黑夜之袭
            new GripStats(40, 4.0F, 0.35D, 0.0F),   // t3 黑夜之拥：开始往回拽
            new GripStats(50, 5.5F, 0.45D, 0.0F),   // t4 黑之破灭
            new GripStats(60, 7.0F, 0.55D, 10.0F),  // t5 戮光：松手前捏碎
    };

    /** 每几 tick 捏一次 ✓。 */
    private static final int SQUEEZE_INTERVAL = 10;

    /**
     * ★ <b>这一击的伤害范围（半径，格）</b> —— 作者 2026-10-10：
     * 「**手造成伤害的范围扩大**」✓。
     *
     * <p>取值沿用重做前法术 JSON 里那份 {@code area_impact.radius}
     * （2 / 2.5 / 4 / 5.5 / 7）✓ —— 也就是说"手感回到重做前那一档，但由我们自己结算"✓。
     * 每一次"捏"都会打范围内**所有**活着的非友军（不只是被抓住的那一只 ✓），
     * 抓住的那一下也会先扫一次 ✓。
     */
    private static final double[] RADII = {2.0D, 2.5D, 4.0D, 5.5D, 7.0D};

    /** 手永远从这个高度以上"够到"目标 ⇒ 锚点放在脚下，模型底边贴地 ✓（作者："落地点从中心改到底部"）。 */
    private static final double ANCHOR_Y = 0.0D;

    /** 拽的时候带一点上抬，免得目标卡进地面 ✓。 */
    private static final double PULL_LIFT = 0.22D;

    /** 离施法者多近就不再拽（免得在脸上抖）✓。 */
    private static final double PULL_STOP_DISTANCE = 2.0D;

    /** 被攥住时水平速度每一 tick 压到原来的多少 ✓。 */
    private static final double HELD_DRAG = 0.35D;

    /** 手到这个距离以内就算"够到了"，进入抓住阶段 ✓。 */
    private static final double GRAB_DISTANCE = 1.6D;

    /** 抓住以后掌心相对目标中心往施法者那边退多少格 ✓。 */
    private static final double PALM_BACKOFF = 0.5D;

    /** 抓住期间给目标挂的失明时长（tick/档）—— 沿用重做前的数值 ✓。 */
    private static final int[] BLIND_TICKS = {60, 80, 100, 140, 180};

    /** 每档最多同时几只（由机制层生成，这里只用来算弧线张开量）✓。 */
    private static final int FALLBACK_TIER = 1;

    /** 黑索最多几个点（防止 t5 的大手拉出几百颗粒子）✓。 */
    private static final int CORD_MAX_POINTS = 48;
    /** 黑索两点之间多少格一颗粒子 ✓。 */
    private static final double CORD_SPACING = 0.35D;

    /** 抓空之后在原地散掉用多久（tick）✓。 */
    private static final int FADE_TICKS = 8;

    private record GripStats(int holdTicks, float damage, double pull, float crush) {
    }

    // ------------------------------------------------------------------
    //  同步字段（客户端渲染器要用）
    // ------------------------------------------------------------------

    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNNightGripEntity.class, EntityDataSerializers.INT);
    /** 施法者 entity id ⇒ 渲染器据此算"从哪边伸出来的" ✓。 */
    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(TNNightGripEntity.class, EntityDataSerializers.INT);
    /** 目标 entity id ⇒ **渲染器让掌心一直朝着它** ✓（-1 = 没瞄到人）。 */
    private static final EntityDataAccessor<Integer> DATA_TARGET =
            SynchedEntityData.defineId(TNNightGripEntity.class, EntityDataSerializers.INT);
    /** 模式（0 弧线抓取 / 1 天降砸）⇒ 渲染器据此决定"掌心朝目标"还是"掌心朝正下方" ✓。 */
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(TNNightGripEntity.class, EntityDataSerializers.INT);

    // ---- 运行期状态（只在服务端用 ✓）----

    private int casterId = -1;
    private int targetId = -1;
    private int tier = FALLBACK_TIER;
    /** 这一段弧线是这一批里的第几只 / 共几只 ⇒ 决定侧向张开量与上抛错开 ✓。 */
    private int handIndex;
    private int handCount = 1;
    /** 没瞄到人时，手飞到哪儿停（提前算好，免得中途东张西望 ✗）。 */
    private Vec3 aimPoint;

    // 弧线三个控制点（甩出那一刻定下来，之后每一 tick 只求值 ✓）
    private Vec3 arcStart = Vec3.ZERO;
    private Vec3 arcControl = Vec3.ZERO;
    private Vec3 arcEnd = Vec3.ZERO;

    /** 已经进入"抓住"阶段了吗 ✓。 */
    private boolean gripping;
    /** 抓空落地时那一次范围伤害已经打过了吗 ✓（只打一次，免得每 tick 刷 ✗）。 */
    private boolean landedSweep;
    /** 天降：砸地发生在哪一 tick（-1 = 还没砸）✓ —— 砸完还要压在地上一下再散。 */
    private int slamTick = -1;
    /** 抓住阶段已经捏了几次 ✓。 */
    private int squeezes;
    private float dealtTotal;
    private double pulledTotal;

    public TNNightGripEntity(EntityType<? extends TNNightGripEntity> type, Level level) {
        super(type, level);
        // 位置完全由我们每 tick 设定：不受重力、不参与方块碰撞 ✓
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TIER, FALLBACK_TIER);
        this.entityData.define(DATA_OWNER, -1);
        this.entityData.define(DATA_TARGET, -1);
        this.entityData.define(DATA_MODE, MODE_ARC);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 不存档：它只活几秒 ✓
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // ------------------------------------------------------------------
    //  对外只读（渲染器/日志用 ✓）
    // ------------------------------------------------------------------

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    public int ownerEntityId() {
        return this.entityData.get(DATA_OWNER);
    }

    /** 目标 entity id（渲染器据此让掌心朝目标 ✓）；-1 = 这次没瞄到人。 */
    public int targetEntityId() {
        return this.entityData.get(DATA_TARGET);
    }

    public GripStats stats() {
        return STATS[Math.max(0, Math.min(STATS.length - 1, tier - 1))];
    }

    /** 这一档的伤害半径（格）✓。 */
    public double radius() {
        return RADII[Math.max(0, Math.min(RADII.length - 1, tier - 1))];
    }

    /**
     * 视锥剔除用的包围盒：<b>按档位放大</b> ✓。
     *
     * <p>为什么必须给：手最大一档是 32 格，而本实体的碰撞箱只有 1 格 ✗ ——
     * 原版按碰撞箱剔除 ⇒ 手**中心转出屏幕时整只手会突然消失** ✗
     * （10 格高的天使踩过同一类坑，见 {@code TNAngelEntity} 的同名方法 ✓）。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(handHalfExtent());
    }

    public double handHalfExtent() {
        // 模型半高 = 13/16 格（y 跨度 ±13 单位）⇒ 随档位缩放一起放大 ✓
        // （原来是个和渲染器各写一份的近似式 0.5+0.35*tier ✗，现在只有一个来源 ✓）
        return 13.0D / 16.0D * sizeScale();
    }

    /** 手不是可交互实体 ⇒ 别让它挡住玩家/引擎的准星射线 ✓。 */
    @Override
    public boolean isPickable() {
        return false;
    }

    // ------------------------------------------------------------------
    //  甩出：由机制层在"施法那一刻"调用 ✓
    // ------------------------------------------------------------------

    /**
     * 安排一次甩出 ✓（由 {@link TNDarkHandMechanics} 在 SPELL_CAST 的 RELEASE 上调用）。
     *
     * @param caster    施法者（服务端实体）
     * @param targetId  瞄准锁定的目标 entity id；-1 = 没瞄到人
     * @param tier      档位 1..5
     * @param handIndex 这一批里的第几只（0 起）
     * @param handCount 这一批共几只
     */
    public void launch(LivingEntity caster, int targetId, int tier, int handIndex, int handCount) {
        this.tier = Math.max(1, Math.min(STATS.length, tier));
        this.handIndex = handIndex;
        this.handCount = Math.max(1, handCount);
        this.casterId = caster.getId();
        this.targetId = targetId;
        this.entityData.set(DATA_TIER, this.tier);
        this.entityData.set(DATA_OWNER, casterId);
        this.entityData.set(DATA_TARGET, targetId);

        LivingEntity target = resolve(targetId);
        // ★ 落点＝目标的**脚下**（作者 2026-10-10："落地点从中心改到底部"）——
        //   配合渲染器"模型底边贴着锚点"（见 TNNightGripRenderer）⇒ 整只手是**贴地站住**的 ✓，
        //   而不是浮在人家胸口中间 ✗。
        Vec3 end = target != null
                ? target.position().add(0.0D, ANCHOR_Y, 0.0D)
                : caster.getEyePosition().add(caster.getLookAngle().scale(reach()));
        if (target == null) {
            this.aimPoint = end;
        }
        this.arcStart = backPoint(caster, handIndex, handCount);
        this.arcEnd = end;
        this.arcControl = controlPoint(arcStart, end, handIndex, handCount);
        setPos(arcStart);
    }

    /**
     * 安排一次「天降」✓ —— t5 专用（作者 2026-10-10：「t5 变成有很多个大手从天而降砸向敌人，
     * **大手的掌心向下**」）。
     *
     * <p>和 {@link #launch} 的区别：不进"抓住"阶段 ✗ —— 从落点**正上方**压下来，
     * 落地那一下就是一记范围重击（{@link #slam}），然后再压在地上 {@link #SLAM_LINGER} tick ✓。
     * 掌心朝下由**渲染器**按模式决定（见 {@code TNNightGripRenderer}）✓。
     *
     * @param landing 落点（一般是某个敌人的脚下；没瞄到人时是准星前方的地面 ✓）
     */
    public void launchSkyfall(LivingEntity caster, int targetId, int tier,
                              int handIndex, int handCount, Vec3 landing) {
        this.tier = Math.max(1, Math.min(STATS.length, tier));
        this.handIndex = handIndex;
        this.handCount = Math.max(1, handCount);
        this.casterId = caster.getId();
        this.targetId = targetId;
        this.entityData.set(DATA_TIER, this.tier);
        this.entityData.set(DATA_OWNER, casterId);
        this.entityData.set(DATA_TARGET, targetId);
        this.entityData.set(DATA_MODE, MODE_SKYFALL);

        // 相邻两只手在水平面上错开一点（编号奇偶分别偏 x / z）⇒ 不会叠成一只 ✗
        double offset = (handIndex - (handCount - 1) / 2.0D) * SKYFALL_SPREAD;
        boolean alongX = handIndex % 2 == 0;
        Vec3 start = landing.add(alongX ? offset : -offset, SKYFALL_HEIGHT, alongX ? -offset : offset);
        this.arcStart = start;
        this.arcEnd = landing;
        setPos(start);
    }

    /** 手最远伸多少格（没瞄到人时用它决定落在哪 ✓）。 */
    private double reach() {
        return 12.0D + 4.0D * tier;
    }

    // ------------------------------------------------------------------
    //  ★ 2026-10-10 追加：t5 换形态 —— 「很多只大手从天而降、掌心向下砸向敌人」
    //     （作者：「t5 变成有很多个大手从天而降砸向敌人，**大手的掌心向下**」✓）
    //     t5 不再"飞出去抓住"，而是**从天上压下来砸**：落地一下范围伤害，然后压在地上一下再散 ✓。
    // ------------------------------------------------------------------

    /** 天降：从落点上方多高开始落（格）✓（与落雷"目标正上方 24 格"同一量级）。 */
    private static final double SKYFALL_HEIGHT = 26.0D;

    /** 天降：下落速度（格/tick）✓ —— 不是自由落体，是"压下来"：约 8 tick 落地，看得清整只手 ✓。 */
    private static final double SKYFALL_SPEED = 3.2D;

    /** 天降：砸到之后还压在地上多久（tick）✓（"掌心按着地"那一下）。 */
    private static final int SLAM_LINGER = 12;

    /** 天降：砸下去那一下的伤害倍率（相对该档每段伤害）✓。 */
    private static final double SLAM_DAMAGE_MULT = 1.6D;

    /** 天降：相邻两只手横向错开多少格（免得完全重叠 ✗）。 */
    private static final double SKYFALL_SPREAD = 1.1D;

    /** 模式：0 = 从后背甩出弧线抓住（t1~t4）；1 = 从天而降砸（t5）✓。 */
    private static final int MODE_ARC = 0;
    private static final int MODE_SKYFALL = 1;

    /**
     * ★ <b>每档的手有多大</b>（＝模型缩放；模型本身 1.625 格高 × 这个数）✓。
     *
     * <p>作者 2026-10-10：「**我想把 t4 的手的尺寸换成 t5 那么大**」✓
     * ⇒ 索引 3（t4）与索引 4（t5）现在是**同一个值 19.69**（≈ 32 格）✓。
     *
     * <p>⚠️ 这张表是**唯一来源** ✓：客户端渲染器直接读 {@link #sizeScale()}，
     * 视锥剔除盒子也从它推出来 ⇒ 不会再出现"渲染器和剔除盒子各说各话"的错位 ✗。
     */
    private static final float[] SIZE_SCALE = {0.98F, 1.97F, 4.92F, 19.69F, 19.69F};

    /** 这一档的模型缩放 ✓（渲染器与剔除盒子共用）。 */
    public float sizeScale() {
        return SIZE_SCALE[Math.max(0, Math.min(SIZE_SCALE.length - 1, tier - 1))];
    }

    /** 这只手是天降模式吗（渲染器据此把**掌心朝正下方** ✓）。 */
    public boolean skyfall() {
        return this.entityData.get(DATA_MODE) == MODE_SKYFALL;
    }

    /**
     * 起点：施法者<b>后背</b>，并按"这一批里的第几只"左右错开 ⇒
     * 三只手是从肩胛两侧分别出来的，不是同一个点 ✗。
     */
    private Vec3 backPoint(LivingEntity caster, int index, int count) {
        Vec3 look = caster.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        if (back.lengthSqr() < 1.0E-6D) {
            back = new Vec3(0.0D, 0.0D, 1.0D);
        }
        back = back.normalize();
        Vec3 side = new Vec3(-back.z, 0.0D, back.x);          // 水平面内与"后背方向"垂直 ✓
        double offset = (index - (count - 1) / 2.0D) * ARC_SPREAD * 0.5D;
        return caster.position()
                .add(0.0D, caster.getBbHeight() * BACK_HEIGHT, 0.0D)
                .add(back.scale(BACK_OFFSET))
                .add(side.scale(offset));
    }

    /**
     * 弧线的控制点：中点 ＋ 侧向张开 ＋ 上抛 ✓。
     *
     * <p>二次贝塞尔 {@code B(t) = (1−t)²P0 + 2(1−t)tP1 + t²P2} 的形状**完全由 P1 决定** ✓ ——
     * 把 P1 往侧面推、往上抬，手就是**甩**出去的弧线（而不是直线 ✗）；
     * 每只手的侧向/上抛量都不同 ⇒ 几条弧线不重合 ✓（这正是作者要的"弯的弧线"✓）。
     */
    private Vec3 controlPoint(Vec3 from, Vec3 to, int index, int count) {
        Vec3 mid = from.add(to).scale(0.5D);
        Vec3 dir = to.subtract(from);
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
        Vec3 side = flat.lengthSqr() < 1.0E-6D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-flat.normalize().z, 0.0D, flat.normalize().x);
        double offset = (index - (count - 1) / 2.0D) * ARC_SPREAD;
        double lift = ARC_LIFT + ARC_LIFT_STEP * (index % 3);
        return mid.add(side.scale(offset)).add(0.0D, lift, 0.0D);
    }

    /** 贝塞尔求值 ✓。 */
    private static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, double t) {
        double u = 1.0D - t;
        return p0.scale(u * u).add(p1.scale(2.0D * u * t)).add(p2.scale(t * t));
    }

    // ------------------------------------------------------------------
    //  每 tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();       // 客户端靠它做插值，位置看起来才顺 ✓
        if (this.level().isClientSide()) {
            return;
        }
        LivingEntity caster = resolve(casterId);

        // 施法者死了/下线了 ⇒ 手立刻散掉 ✓
        if (caster == null) {
            discard();
            return;
        }

        // ★ t5：天降砸地（不进抓住阶段 ✗）
        if (skyfall()) {
            fall(caster);
            return;
        }
        if (!gripping) {
            fly(caster);
            return;
        }
        grip(caster);
    }

    /**
     * ③ <b>天降阶段</b>：从落点正上方匀速压下来，落地就是"砸"✓
     * （作者 2026-10-10：「t5 变成有很多个大手从天而降砸向敌人」）。
     *
     * <p>为什么不用自由落体：那样一眨眼就没了、看不出是"手压下来"✗；
     * 匀速 {@link #SKYFALL_SPEED} 约 8 tick 落地 ⇒ 玩家看得清整只手掌心朝下压下来 ✓。
     */
    private void fall(LivingEntity caster) {
        Vec3 landing = arcEnd;
        if (position().y - landing.y > SKYFALL_SPEED) {
            setPos(getX(), getY() - SKYFALL_SPEED, getZ());
            fallTrail();
            return;
        }
        setPos(landing.x, landing.y, landing.z);
        if (!landedSweep) {
            landedSweep = true;
            slam(caster);
        }
        if (slamTick >= 0 && tickCount > slamTick + SLAM_LINGER) {
            discard();
        }
    }

    /** 下落拖尾：一条黑的竖直烟柱（"很多只大手压下来"的压迫感 ✓）。 */
    private void fallTrail() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(black(), getX(), getY(), getZ(), 10, 0.3D, 0.6D, 0.3D, 0.03D);
        server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.6D, getZ(), 6, 0.35D, 0.8D, 0.35D, 0.02D);
        server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 4, 0.2D, 0.4D, 0.2D, 0.0D);
    }

    /**
     * 砸地那一下 ✓：一次性范围重击 ＋ 扬尘/黑雾/闷响，然后压在地上 {@link #SLAM_LINGER} tick。
     *
     * <p>半径与"捏"用同一张表（{@link #RADII}）✓；伤害 = 该档每段伤害 × {@link #SLAM_DAMAGE_MULT}
     * （只打一下，所以给得比"每 0.5 秒捏一次"重 ✓）。
     */
    private void slam(LivingEntity caster) {
        slamTick = tickCount;
        float dealt = sweep(null, caster, (float) (stats().damage() * SLAM_DAMAGE_MULT));
        dealtTotal += dealt;
        if (level() instanceof ServerLevel server) {
            Vec3 at = position();
            double r = Math.max(1.2D, radius());
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.4D, at.z,
                    40, r * 0.35D, 0.25D, r * 0.35D, 0.06D);
            server.sendParticles(black(), at.x, at.y + 0.3D, at.z,
                    50, r * 0.4D, 0.2D, r * 0.4D, 0.05D);
            server.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.5D, at.z,
                    12, r * 0.25D, 0.2D, r * 0.25D, 0.02D);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_BREAK_BLOCK,
                    SoundSource.PLAYERS, 1.0F, 0.55F);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE,
                    SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        LOGGER.info("TN-C: night hand slam tier={} r={} dealt={} at={}",
                tier, radius(), dealt, position());
    }

    /** ① 甩出阶段：沿弧线走 FLY_TICKS 帧，到了就抓 ✓。 */
    private void fly(LivingEntity caster) {
        // 手飞的时候目标可能在动 ⇒ 终点与控制点每 tick 按目标当前位置重算
        // （不做"拐弯追踪"，只让这条弧跟着目标平移 ⇒ 不会出现引擎那种一直偏航 ✗）
        LivingEntity target = resolve(targetId);
        Vec3 end = target != null
                ? target.position().add(0.0D, ANCHOR_Y, 0.0D)
                : (aimPoint != null ? aimPoint : arcEnd);
        arcEnd = end;
        arcControl = controlPoint(arcStart, end, handIndex, handCount);

        double progress = Math.min(1.0D, (tickCount + 1) / (double) FLY_TICKS);
        double t = 1.0D - (1.0D - progress) * (1.0D - progress);   // 出得快、收得慢 ⇒ 像甩出去的 ✓
        setPos(bezier(arcStart, arcControl, end, t));

        cord(caster);
        trail();

        boolean arrived = progress >= 1.0D
                || (target != null && position().distanceTo(end) <= GRAB_DISTANCE);
        if (!arrived) {
            return;
        }
        if (target == null) {
            // 没瞄到人：落到准星前方、**扫一次范围伤害**（"甩出去砸了一下"✓），停一下再散掉 ✓
            if (!landedSweep) {
                landedSweep = true;
                burst(position(), Math.max(0.8D, radius() * 0.5D), 24);
                sweep(null, caster, stats().damage());
            }
            if (tickCount > FLY_TICKS + FADE_TICKS) {
                discard();
            }
            return;
        }
        startGripping(target, caster);
    }

    /** 抓空时最后几帧的收尾（在 {@link #fly} 里已经 discard 了，这里只补特效）✓。 */
    private void trail() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(black(), getX(), getY(), getZ(), 6, 0.18D, 0.18D, 0.18D, 0.01D);
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 2, 0.12D, 0.12D, 0.12D, 0.0D);
        }
    }

    /** 到点了 ⇒ 进入抓住阶段 ✓。 */
    private void startGripping(LivingEntity target, LivingEntity caster) {
        gripping = true;
        // 抓住那一刻归零计时 ⇒ 抓住时长与飞行时长互不挤占 ✓
        squeezes = 0;
        setPos(gripPos(target, caster));
        burst(target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 1.0D, 26);
        // ★ 抓住那一下先扫一遍范围伤害（作者："手造成伤害的范围扩大"✓）
        //   ≈ 重做前引擎那条 impact.DAMAGE ＋ area_impact，只是现在由我们自己结算 ✓
        dealtTotal += sweep(target, caster, stats().damage());
        // 失明（沿用重做前的数值 ✓；重做前是引擎的 impact 挂上的，现在由我们自己挂 ✓）
        int blind = BLIND_TICKS[Math.max(0, Math.min(BLIND_TICKS.length - 1, tier - 1))];
        if (blind > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0, false, true));
        }
        LOGGER.info("TN-C: night hand grip target={} tier={} hands={} hold={}t radius={} owner={}",
                target.getName().getString(), tier, handCount, stats().holdTicks(), radius(), casterId);
    }

    /** ② 抓住阶段（与上一版相同 ✓）。 */
    private void grip(LivingEntity caster) {
        GripStats stats = stats();
        if (tickCount > FLY_TICKS + stats.holdTicks()) {
            finish();
            return;
        }
        LivingEntity victim = resolve(targetId);
        if (victim == null || !victim.isAlive()) {
            finish();
            return;
        }
        setPos(gripPos(victim, caster));
        hold(victim);

        int held = tickCount - FLY_TICKS;
        if (held > 0 && held % SQUEEZE_INTERVAL == 0 && squeezes * SQUEEZE_INTERVAL < stats.holdTicks()) {
            squeezes++;
            dealtTotal += squeeze(victim, caster, stats.damage());
        }
        if (stats.pull() > 0.0D && held > 0 && held % SQUEEZE_INTERVAL == 0) {
            pulledTotal += pullToward(victim, caster, stats.pull());
        }

        cord(caster);
        shroud(victim);
        if (tickCount % 2 == 0) {
            burst(position(), 0.45D, 6);
        }
    }

    /** 收工：t5 先捏碎，再消失 ✓。 */
    private void finish() {
        LivingEntity victim = resolve(targetId);
        LivingEntity caster = resolve(casterId);
        GripStats stats = stats();
        if (victim != null && victim.isAlive() && stats.crush() > 0.0F) {
            dealtTotal += crush(victim, caster, stats.crush());
        }
        if (dealtTotal > 0.0F && caster instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(Component.literal(String.format(
                    "§5[TN-C] §r黑夜之手 · 共捏碎 §c%.1f§r 点生命%s",
                    dealtTotal,
                    pulledTotal > 0.01D ? String.format(" · 已拖回 §d%.1f§r 格", pulledTotal) : ""))
                    .withStyle(net.minecraft.ChatFormatting.RESET), true);
        }
        LOGGER.info("TN-C: night hand end tier={} squeeze={} dealt={} pulled={}",
                tier, squeezes, dealtTotal, String.format("%.2f", pulledTotal));
        discard();
    }

    /**
     * 手站哪儿：目标**脚下**（不是身体中心 ✗），再从水平方向朝施法者退 {@link #PALM_BACKOFF} 格 ✓。
     *
     * <p>作者 2026-10-10：「**落地有点不好看，你把落地点从中心改到底部**」——
     * 现在锚点在脚底，配合渲染器"模型底边贴锚点"⇒ 一只手掌**贴地立着**按向目标 ✓
     * （重做前它悬在人家的腰/胸口那个高度，看着像插在身上 ✗）。
     * 退让方向特意**压平到水平面**✗：不然目标比施法者高时手会往下钻进地里 ✓。
     */
    private Vec3 gripPos(LivingEntity victim, LivingEntity caster) {
        Vec3 feet = victim.position().add(0.0D, ANCHOR_Y, 0.0D);
        if (caster == null) {
            return feet;
        }
        Vec3 flat = new Vec3(feet.x - caster.getX(), 0.0D, feet.z - caster.getZ());
        if (flat.lengthSqr() < 1.0E-6D) {
            return feet;
        }
        return feet.subtract(flat.normalize().scale(PALM_BACKOFF));
    }

    /** 压制：把目标的水平速度压住 ⇒ 读感是"被攥住动不了"✓。 */
    private void hold(LivingEntity victim) {
        Vec3 delta = victim.getDeltaMovement();
        double dampedX = delta.x * HELD_DRAG;
        double dampedZ = delta.z * HELD_DRAG;
        if (Math.abs(dampedX - delta.x) > 1.0E-4D || Math.abs(dampedZ - delta.z) > 1.0E-4D) {
            victim.setDeltaMovement(dampedX, delta.y, dampedZ);
            victim.hurtMarked = true;
        }
    }

    private float squeeze(LivingEntity victim, LivingEntity caster, float amount) {
        return sweep(victim, caster, amount);
    }

    /**
     * ★ <b>一次范围伤害（"捏"/"砸"）</b> —— 作者 2026-10-10：「**手造成伤害的范围扩大**」✓。
     *
     * <p>以**手自己为中心**、半径按档位（{@link #RADII}）打范围内所有活着的非友军 ✓，
     * 不只打被抓住的那一只 ✗。抓住的那一下、以及"没瞄到人"落地那一下也各扫一次 ✓
     * （≈ 重做前引擎那条 {@code impact.DAMAGE} ＋ {@code area_impact} 的合体，
     * 只是现在由我们自己结算 ✓）。
     *
     * <p>注意 AABB 是方的 ⇒ 再按球形筛一遍距离 ✓（不然墙角会莫名其妙挨打 ✗）。
     *
     * @param latched 被抓住的那只（只为日志用，可以是 null ✓）
     */
    private float sweep(LivingEntity latched, LivingEntity caster, float amount) {
        if (!(level() instanceof ServerLevel server) || amount <= 0.0F) {
            return 0.0F;
        }
        double radius = radius();
        AABB box = new AABB(getX() - radius, getY() - radius, getZ() - radius,
                getX() + radius, getY() + radius, getZ() + radius);
        float total = 0.0F;
        int hits = 0;
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator()
                        && e != caster
                        && !(caster != null && e.isAlliedTo(caster)))) {
            if (candidate.distanceTo(this) > radius) {
                continue;
            }
            total += dealDamage(candidate, caster, amount);
            hits++;
        }
        if (hits > 0) {
            LOGGER.debug("TN-C: night hand sweep r={} hits={} total={} latched={}",
                    radius, hits, total, latched == null ? "-" : latched.getName().getString());
        }
        return total;
    }

    private float crush(LivingEntity victim, LivingEntity caster, float amount) {
        burst(victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), 1.6D, 60);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.9F, 0.65F);
        }
        return dealDamage(victim, caster, amount);
    }

    /**
     * 真正扣血。
     *
     * <p>⚠️ 扣之前**必须先把目标的受伤窗口清 0** ✗ —— 和 {@code TNDarkSacrificeMechanics}
     * 的燃血同一个原因：{@code hurt()} 在 {@code invulnerableTime > 0}（刚被打过）时会被
     * **静默丢掉** ⇒ 表现是"有时掉有时不掉" ✗（作者 10-09 抓到过这个现象 ✓）。
     */
    private float dealDamage(LivingEntity victim, LivingEntity caster, float amount) {
        if (amount <= 0.0F) {
            return 0.0F;
        }
        float before = victim.getHealth();
        victim.invulnerableTime = 0;
        boolean landed = victim.hurt(level().damageSources().indirectMagic(this, caster), amount);
        return landed ? Math.max(0.0F, before - victim.getHealth()) : 0.0F;
    }

    /**
     * 把被抓住的目标朝施法者拖 ✓（作者 2026-10-10："抓住敌人手再把敌人拉回来"）。
     *
     * <p>为什么用"间隔冲量"而不是每 tick 设速度：生物的 {@code travel()} 每 tick 都会按
     * 自己的意向重设速度 ⇒ 每 tick 设会被它抢走 ✗；{@code hurtMarked} 不能省 ✗
     * （否则服务端改了速度客户端不知道 ✓）。
     */
    private double pullToward(LivingEntity victim, LivingEntity caster, double strength) {
        Vec3 delta = caster.position().subtract(victim.position());
        if (delta.lengthSqr() < PULL_STOP_DISTANCE * PULL_STOP_DISTANCE) {
            return 0.0D;
        }
        double moved = Math.min(strength, Math.max(0.0D, delta.length() - PULL_STOP_DISTANCE));
        Vec3 pull = delta.normalize().scale(moved).add(0.0D, PULL_LIFT, 0.0D);
        victim.setDeltaMovement(victim.getDeltaMovement().add(pull));
        victim.hurtMarked = true;
        return moved;
    }

    // ------------------------------------------------------------------
    //  特效（作者："别啥都看不见，特效要做出来"✓）
    // ------------------------------------------------------------------

    /**
     * <b>黑索</b>：从施法者后背到手，拉一条黑线 ✓
     * （作者 2026-10-10：「要那种从我的后背伸出来的感觉……可以做一个黑线连接着手腕跟后背」）。
     *
     * <p>为什么是粒子不是真几何线：那要自己写世界空间渲染
     * （{@code RenderLevelStageEvent} ＋ 自建 RenderType），风险与工作量都大得多 ✗；
     * 而**密到 {@link #CORD_SPACING} 格间距的粒子本来就成线** ✓。
     */
    private void cord(LivingEntity caster) {
        if (!(level() instanceof ServerLevel server) || caster == null) {
            return;
        }
        Vec3 from = caster.position().add(0.0D, caster.getBbHeight() * BACK_HEIGHT, 0.0D);
        Vec3 to = position();
        double distance = from.distanceTo(to);
        if (distance < 0.2D) {
            return;
        }
        int points = (int) Math.min(CORD_MAX_POINTS, Math.ceil(distance / CORD_SPACING));
        for (int i = 1; i <= points; i++) {
            Vec3 at = from.lerp(to, i / (double) points);
            server.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 目标身上裹一层黑雾 ⇒ 旁边的人看得见"他被什么抓住了" ✓。 */
    private void shroud(LivingEntity victim) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        double height = victim.getBbHeight();
        server.sendParticles(black(), victim.getX(), victim.getY() + height * 0.5D, victim.getZ(),
                10, victim.getBbWidth() * 0.6D, height * 0.4D, victim.getBbWidth() * 0.6D, 0.01D);
        if (tickCount % 5 == 0) {
            server.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + height * 0.8D,
                    victim.getZ(), 6, victim.getBbWidth() * 0.5D, 0.2D, victim.getBbWidth() * 0.5D, 0.02D);
        }
    }

    private void burst(Vec3 at, double radius, int count) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(black(), at.x, at.y, at.z, count, radius, radius, radius, 0.02D);
        server.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, Math.max(2, count / 6),
                radius * 0.7D, radius * 0.7D, radius * 0.7D, 0.01D);
    }

    /**
     * 跨模组的黑粒子：查一次、缓存，拿不到就退回原版 `squid_ink` ✓。
     *
     * <p>⚠️ 粒子 id 不能写死 ✗（铁律第十三条）；且 {@code ParticleType} 本身**不是**
     * {@code ParticleOptions}（只有 {@code SimpleParticleType} 才是）⇒ 必须先 {@code instanceof} ✓。
     */
    private static ParticleOptions blackParticle;

    private static ParticleOptions black() {
        if (blackParticle == null) {
            blackParticle = crossMod("soulsweapons", "black_flame", ParticleTypes.SQUID_INK);
        }
        return blackParticle;
    }

    private static ParticleOptions crossMod(String namespace, String path, ParticleOptions fallback) {
        var type = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath(namespace, path));
        return (type instanceof ParticleOptions options) ? options : fallback;
    }

    // ------------------------------------------------------------------
    //  小工具
    // ------------------------------------------------------------------

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/nighthand");

    /** 按 id 在服务端重新解析实体（跨 tick 拿着旧引用会指向死掉的实体 ✗）。 */
    private LivingEntity resolve(int entityId) {
        if (entityId < 0 || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(entityId);
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }
}
