package com.tnc.tnc.light;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>光明龙</b> —— 光系第五条链「光龙」<b>扔出去的那一条龙</b> ✓。
 *
 * <p>作者 2026-10-02：<b>"我要的龙不是怪，你把他怪给我删了，我要的是跟雷球一样，扔出去，
 * 一条龙冲出去"</b> ✓ —— 所以它和 {@code magic/TNThunderOrbEntity}（雷球）是**同一类东西**：
 * <ul>
 *   <li>基类是 <b>{@link Entity}</b> ✗ —— **不是 {@code Monster}/{@code Mob}** ✗：
 *       没有 AI、没有 goal、没有寻路、**不注册属性**（{@code TNNpcAttributes} 里根本没有它 ✓）；</li>
 *   <li>打不到它、推不动它、也不参与刷怪/驯服/刷怪蛋那一套 ✓；</li>
 *   <li>位置**自己每 tick 算**（和雷球一样直接 {@code setPos} ✓）：不受重力、不受方块碰撞 ✗；</li>
 *   <li>客户端只负责画（GeckoLib 模型 + {@code dash}/{@code orbit} 动画 ✓），伤害判定在服务端 ✓。</li>
 * </ul>
 *
 * <h2>两种活法</h2>
 * <ul>
 *   <li><b>冲刺</b>（t1/t3/t4/t5）：{@link #charge} —— 朝准星方向直线冲，碰到谁伤谁 ✓；</li>
 *   <li><b>环绕</b>（t2）：{@link #orbit} —— 三条小龙绕着主人转、**首尾相接围成一个整圆** ✓
 *       （作者 2026-10-03："三条龙头对尾绕成一个圆接在一起" ✓）。</li>
 * </ul>
 *
 * <h2>★★ 2026-10-03：位置改成"客户端自己算"（作者："环绕飞龙有点卡顿" ✗）</h2>
 * 原来两边都只由服务端算位置 ⇒ 客户端**每 tick 收到一个位置包就直接 {@code setPos} 啪一下** ✗
 * （{@code Entity.lerpTo} 的默认实现就是硬的 ✓，只有 {@code LivingEntity} 才做插值 ✗）⇒
 * 20 次/秒的硬跳，绕着玩家 5 格转的时候一眼就能看出来"一卡一卡" ✗。
 *
 * <p>现在：**规律写在同步数据里**（方向 / 速度 / 半径 / 角速度 / 主人 ✓），两端各自用它算位置 ✓ ——
 * 客户端每帧渲染时本来就按 {@code xo → getX()} 插值 ✓ ⇒ 丝一样顺 ✓；
 * 服务端那份仍然只用来**判定伤害** ✓（谁挨打永远由服务端说了算 ✓）。
 * 找不到主人（换维度/还没同步下来 ✓）时，客户端会退回"听服务端的位置包" ✓（见 {@link #lerpTo} ✓）。
 *
 * <h2>尺寸（模型原长 22.75 格 = 364 单位：鼻子 z=−165 → 尾焰 z=+199 ✓）</h2>
 * 碰撞箱只给身体那一小段（2.5 × 2 格 ✓，免得卡墙 ✗）；剔除盒按体长自己撑 ✓
 * （{@link #getBoundingBoxForCulling} ✓，不然抬头只看一段时整条会消失 ✓）。
 */
public class TNDragonEntity extends Entity implements GeoEntity {

    /** 模型原长（格 ✓）—— 量自 geo（鼻子 z≈−165 → 尾焰 z≈+199 ⇒ 364 单位 = 22.75 格 ✓）。 */
    public static final double MODEL_LENGTH_BLOCKS = TNDragonOrbitMath.BODY_LENGTH_BLOCKS;

    /**
     * ★ 模型里"<b>身体切线 = 实体朝向</b>"的那一点（{@code body1} 的 pivot，z = −96 单位 = <b>6 格</b> ✓）——
     * 环绕模式下，放在**环上**的就是它 ✓（实体原点还要再往回退 {@code 6 × 个头} 格 ✓）。
     *
     * <p>为什么不是原点：{@code orbit} 动画把身体从鼻子到尾巴均匀弯了 120° ✓，
     * 弯曲的"圆心方向"在 {@code body1} 那一节正好等于实体朝向 ✓ ⇒ 只有这一点落在环上，整条身体才贴着环 ✓。
     */
    public static final double CURL_CENTER_BLOCKS = 6.0D;

    /** 冲刺（t1/t3/t4/t5 ✓）。 */
    public static final int MODE_CHARGE = 0;
    /** 环绕（t2 ✓）。 */
    public static final int MODE_ORBIT = 1;

    /** 渲染个头（×100 同步 ✓）—— 扔出去时由法术写入 ✓。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 档次 1..5 ✓（只用来区分/调试 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);

    // ---- ★ 同步给客户端、让它自己算位置的几个数 ✓（见类注释 ✓）----
    /** {@link #MODE_CHARGE} / {@link #MODE_ORBIT} ✓。 */
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 主人的实体 id（环绕时环心就是它 ✓；−1 = 没有 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_OWNER_EID =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 冲刺方向（单位向量 ✓）。 */
    private static final EntityDataAccessor<Float> DATA_DIR_X =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Y =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Z =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.FLOAT);
    /** 冲刺速度（格/tick ×1000 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SPEED =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 环绕半径（格 ×100 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_ORBIT_RADIUS =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 环绕角速度（度/tick ×100，**带正负** ✓ = 转的方向 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_ORBIT_DEG =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 环绕起始角（度 ×100 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_ORBIT_START =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /** 环心比主人脚底高多少（格 ×100 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_ORBIT_HEIGHT =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);
    /**
     * ★ 暗龙标记（0/1 ✓）—— 作者 2026-10-02："复制一下光龙，生成一个暗龙" ✓。
     *
     * <p>龙只有**这一个**实体类 ✓：暗龙是**另一个实体类型**（{@code tnc:dark_dragon} ✓）＋
     * 另一张贴图/另一个模型类（{@code dark/client/TNDarkDragonGeoModel} ✓）。
     * 这个标记只用来换**粒子颜色**（光尘/金光 ⇒ 灵魂火/黑烟 ✓）—— 加得很少 ✗，
     * 免得把暗龙做成第二份逻辑 ✓。
     */
    private static final EntityDataAccessor<Integer> DATA_DARK =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);

    /** 头部锚定（0/1 ✓）—— 见 {@link #headAnchored()} ✓。 */
    private static final EntityDataAccessor<Integer> DATA_HEAD_ANCHOR =
            SynchedEntityData.defineId(TNDragonEntity.class, EntityDataSerializers.INT);

    /** 龙的贴图：光龙默认这张 ✓；暗龙走**另一个模型类**（{@code dark/client} ✓）不靠覆写 ✗。 */
    public static final net.minecraft.resources.ResourceLocation LIGHT_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dragon_bedrock.png");
    /** 暗龙的贴图 ✓（同一套 geo/动画，只换这张 ✓）。 */
    public static final net.minecraft.resources.ResourceLocation DARK_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dragon_dark_bedrock.png");

    /** 冲刺撞人的判定，横向给多宽（格 ✓）—— 纵向按体长给 ✓。 */
    private static final double SWEEP_WIDTH = 3.0D;
    /** 判定盒纵向**封顶**多少格 ✓（t5 放大 12 倍后体长 273 格 ✗ —— 见 {@link #sweepBox()}）。 */
    private static final double SWEEP_MAX_LENGTH = 96.0D;
    /** 没显式给参数时（比如 /summon 出来的）用这套默认值 ✓。 */
    private static final double DEFAULT_SPEED = 0.28D;
    private static final int DEFAULT_TICKS = 200;
    private static final double DEFAULT_DAMAGE = 20.0D;

    /**
     * ★ 出生后多少 tick 内**不做撞墙判定** ✓（作者 2026-10-04："怎么模型一出来闪一下就消失了" ✗）。
     *
     * <p>龙是法术在**施法者正前方最多几十格**处生成的 ✓（{@code SPAWN_DISTANCE + 体长 × 0.25}），
     * 生成点很可能**就在地形里面** ✗（贴着墙放、在山里放、在树冠里放 ✓）——
     * 原来的判定是"龙头前方那一格不是空气就爆开" ✓ ⇒ 这种时候**第一 tick 就自爆** ✗，
     * 实机看到的就是"闪一下就没了" ✓（出生粒子是服务端发的，所以那一下还是看得见的 ✓）。
     *
     * <p>现在头 {@link #SPAWN_GRACE_TICKS} tick 里不判墙 ✓：让它先飞出来 ✓
     * （它本来就是"无碰撞投射物"✓，穿出几格土石完全没问题 ✓），
     * 之后恢复正常 —— 真的是墙那也照样爆 ✓，只是不再"出生即死" ✓。
     */
    private static final int SPAWN_GRACE_TICKS = 6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** 施法者（伤害记在它头上 ✓）；可能为 null ✓。 */
    private UUID ownerId;
    private Vec3 chargeDir = new Vec3(0.0D, 0.0D, -1.0D);
    private double chargeSpeed = DEFAULT_SPEED;
    private double chargeDamage = DEFAULT_DAMAGE;
    private int chargeTicks;
    /** 这一次冲刺已经扫过谁（按实体 id ✓）—— 同一个敌人只挨一下 ✓。 */
    private final Set<Integer> hitThisCharge = new HashSet<>();
    private boolean configured;

    // ---- ★ 环绕模式（作者 2026-10-03："t2 放三条小龙围绕着自己" + "三条龙头对尾绕成一个圆" ✓）----
    /** 是不是"绕着主人转"（false = 老样子：直线冲出去 ✓）。 */
    private boolean orbiting;
    /** 环绕半径（格 ✓）/ 每 tick 转多少度（带正负 ✓）/ 当前角 ✓（三条龙错开 120° ✓）。 */
    private double orbitRadius = 5.0D;
    private double orbitDegPerTick = 3.0D;
    private double orbitAngle;
    /** 环绕期间对同一个敌人的伤害冷却（tick ✓）—— 不然贴着的怪会被每 tick 割 ✗。 */
    private final Map<Integer, Integer> orbitNextHit = new HashMap<>();
    private static final int ORBIT_HIT_COOLDOWN = 10;
    /** 客户端第一次 tick 时把同步下来的起始角读进来 ✓（数据包和实体一起到 ✓）。 */
    private boolean clientAngleInit;

    public TNDragonEntity(EntityType<? extends TNDragonEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);          // 和雷球一样：位置完全由我们每 tick 设定 ✓
        this.noPhysics = true;            // 不受方块碰撞（撞墙自己判 ✓）
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 30);
        this.entityData.define(DATA_TIER, 3);
        this.entityData.define(DATA_MODE, MODE_CHARGE);
        this.entityData.define(DATA_OWNER_EID, -1);
        this.entityData.define(DATA_DIR_X, 0.0F);
        this.entityData.define(DATA_DIR_Y, 0.0F);
        this.entityData.define(DATA_DIR_Z, -1.0F);
        this.entityData.define(DATA_SPEED, (int) Math.round(DEFAULT_SPEED * 1000.0D));
        this.entityData.define(DATA_ORBIT_RADIUS, 500);
        this.entityData.define(DATA_ORBIT_DEG, 300);
        this.entityData.define(DATA_ORBIT_START, 0);
        this.entityData.define(DATA_ORBIT_HEIGHT, 120);
        this.entityData.define(DATA_DARK, 0);
        this.entityData.define(DATA_HEAD_ANCHOR, 1);
    }

    /** 这是暗龙吗 ✓（只影响粒子颜色 ✓，见 {@link #DATA_DARK}）。 */
    public boolean isDark() {
        return this.entityData.get(DATA_DARK) != 0;
    }

    /**
     * ★ <b>头部锚定</b> ✓（作者 2026-10-04："t5 根本不显示，t3 显示一会也消失了" ✗）。
     *
     * <p>为什么必须这样：龙是**施法者正前方**生成的 ✓，而体长是 23 格 × 个头 ✗ ——
     * 如果按"实体原点 = 身体中点"来放（老做法 ✗），t5 的原点会被推到
     * {@code 8 + 273×0.25 ≈ 76 格}外 ✗：超出可见/已加载范围、经常直接落在地形里 ⇒
     * 第一 tick 撞墙自爆 ⇒ 实机就是"**根本不显示**"✗；t3（68 格）也有一半身子在视野外 ✗。
     *
     * <p>现在改成：<b>把鼻子（局部 −Z 那一端）锚在实体位置上</b> ✓
     * ⇒ 法术只要把实体放在施法者前方几格 ✓，整条龙身就从那里**往后铺开、正从你身边掠过** ✓，
     * 永远在视野里 ✓（渲染时的平移见 {@link #modelCenterOffset()} ✓、判定的走廊见 {@link #sweepBox()} ✓）。
     */
    public boolean headAnchored() {
        return this.entityData.get(DATA_HEAD_ANCHOR) != 0;
    }

    public void setHeadAnchored(boolean anchored) {
        this.entityData.set(DATA_HEAD_ANCHOR, anchored ? 1 : 0);
    }

    public void setDark(boolean dark) {
        this.entityData.set(DATA_DARK, dark ? 1 : 0);
    }

    /** 拖尾/爆开用的粒子 ✓（暗龙换成灵魂火 + 黑烟 ✓）。 */
    private net.minecraft.core.particles.SimpleParticleType trailParticle() {
        return this.isDark() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD;
    }

    private net.minecraft.core.particles.SimpleParticleType burstParticle() {
        return this.isDark() ? ParticleTypes.SMOKE : ParticleTypes.FIREWORK;
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    /**
     * 设置个头 ✓。
     *
     * <p>★ 上限 2026-10-04 从 4.0 提到 <b>64.0</b> ✗ —— 作者："t345 模型都放大一倍" ✓
     * ⇒ t4 = 6.0、t5 = **12.0** ✓，原来那个 4.0 的钳位会把它们**悄悄压回 4.0** ✗
     * （表现就是"我改大了但游戏里看不出变化"✗，这种静默失效最难查 ✓）。
     */
    public void setScale(double scale) {
        this.entityData.set(DATA_SCALE, (int) Math.round(Mth.clamp(scale, 0.05D, 64.0D) * 100.0D));
    }

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        this.entityData.set(DATA_TIER, Mth.clamp(tier, 1, 5));
    }

    /** 现在是不是"绕着转" ✓（两端的**动画控制器**也看这个 ✓ ⇒ 必须是同步数据 ✓）。 */
    public boolean orbiting() {
        return this.entityData.get(DATA_MODE) == MODE_ORBIT;
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    /**
     * ★ 扔出去：朝 {@code dir} 冲 {@code ticks} tick、每 tick 走 {@code speed} 格、
     * 碰到敌人打 {@code damage} ✓（法术在生成的那一刻调它 ✓）。
     */
    public void charge(Vec3 dir, double speed, int ticks, double damage) {
        Vec3 d = dir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, -1.0D) : dir.normalize();
        this.chargeDir = d;
        this.chargeSpeed = Math.max(0.02D, speed);
        this.chargeTicks = Math.max(1, ticks);
        this.chargeDamage = Math.max(1.0D, damage);
        this.configured = true;
        this.orbiting = false;
        this.entityData.set(DATA_MODE, MODE_CHARGE);
        this.entityData.set(DATA_DIR_X, (float) d.x);
        this.entityData.set(DATA_DIR_Y, (float) d.y);
        this.entityData.set(DATA_DIR_Z, (float) d.z);
        this.entityData.set(DATA_SPEED, (int) Math.round(this.chargeSpeed * 1000.0D));
        this.faceDir(d, true);
    }

    /**
     * ★ <b>绕着主人转</b>（作者 2026-10-03："t2 放三条小龙围绕着自己" ✓ +
     * "三条龙头对尾绕成一个圆接在一起" ✓）。
     *
     * <p>这个模式下它**不往前冲** ✗，而是每 tick 沿环走 {@code degPerTick} 度 ✓
     * （半径 {@code radius} 格、开始于 {@code startAngle} 度 ✓），转到 {@code ticks} 到点就爆开消失 ✓
     * （天数正好和"光龙鳞甲"的 buff 一样长 ✓）。三条一起放时起始角错开 360/3 = 120° ✓。
     *
     * @param ownerEid 主人的**实体 id** ✓ —— 客户端要靠它找环心 ✓
     * @param degPerTick 度/tick，**带正负** ✓（正负 = 绕哪边转 ✓，见 {@code TNDragonOrbitConfig} ✓）
     * @param height   环心比主人脚底高多少格 ✓
     * @param damage   贴到敌人时的伤害 ✓（同一个敌人每 {@link #ORBIT_HIT_COOLDOWN} tick 才挨一下 ✗ 不然贴脸会被割死 ✗）
     */
    public void orbit(UUID owner, int ownerEid, double radius, double degPerTick, double height,
                      int ticks, double damage, double startAngle) {
        this.ownerId = owner;
        this.orbiting = true;
        this.orbitRadius = Math.max(0.5D, radius);
        this.orbitDegPerTick = degPerTick;
        this.orbitAngle = startAngle;
        this.chargeTicks = Math.max(1, ticks);
        this.chargeDamage = Math.max(0.0D, damage);
        this.configured = true;
        this.entityData.set(DATA_MODE, MODE_ORBIT);
        this.entityData.set(DATA_OWNER_EID, ownerEid);
        this.entityData.set(DATA_ORBIT_RADIUS, (int) Math.round(this.orbitRadius * 100.0D));
        this.entityData.set(DATA_ORBIT_DEG, (int) Math.round(degPerTick * 100.0D));
        this.entityData.set(DATA_ORBIT_START, (int) Math.round(startAngle * 100.0D));
        this.entityData.set(DATA_ORBIT_HEIGHT, (int) Math.round(height * 100.0D));
    }

    // ---- 投射物不做交互 ✓ ----
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /**
     * 剔除盒按**体长**撑 ✗（22.75 格长的模型，光看碰撞箱会在抬头时整条消失 ✓）。
     *
     * <p>★ 2026-10-04：纵向从 {@code len*0.35 / len*0.55} 改成按**龙头到龙尾的实际长度**给 ✗ ——
     * 原来那个 0.55 只罩住 12 格（0.3 个头的小龙还够 ✓，t3 放大 3 倍之后就罩不住了 ✗）。
     * 顺带把横向也按体长放宽一点 ✓（龙是弯的 ✓，不是一根直线 ✗）。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double len = MODEL_LENGTH_BLOCKS * this.scale();
        return this.getBoundingBox().inflate(SWEEP_WIDTH + len * 0.15D, len * 0.45D, len * 0.65D);
    }

    /**
     * ★ <b>不做视锥剔除</b> ✓（作者 2026-10-04："怎么模型一出来闪一下就消失了" ✗）。
     *
     * <p>原版 {@code Entity.shouldRenderAtSqrDistance} 只看**实体原点**到摄像机的距离 ✓，
     * 而视锥剔除是拿原点那一小块盒子和视锥求交 ✗ —— 对一条 <b>23 格长 → 放大后 273 格长</b>
     * 的龙来说完全不成立 ✗：<b>整条身体有一大半在屏幕里、原点却在视锥外</b>的时候，
     * 原版会说"不可见"⇒ 整条龙**凭空消失** ✓（这正好就是作者看到的"闪一下就消失" ✓）。
     *
     * <p>改成：只要在 {@link #RENDER_DISTANCE} 格内就**无条件画** ✓（跳过视锥那一步 ✓）。
     * 这是这个工程里其它大模型早就用过的写法 ✓（见 {@code TNLightningStrikeRenderer} /
     * {@code TNWaterSpellRenderer} ✓）；龙每次最多几条 ✓，代价可以忽略 ✓。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    /** 多远之内无条件画（格 ✓）—— 够 t5 那条 273 格长的龙横穿视野 ✓，又不至于全图都画 ✗。 */
    private static final double RENDER_DISTANCE = 512.0D;

    /**
     * 冲刺扫过的判定走廊 ✓。
     *
     * <p>★ 体长**封顶 {@link SWEEP_MAX_LENGTH}** ✗ —— 作者 2026-10-04 把 t5 放大到 12 倍之后
     * 体长是 273 格 ✗，按全长的立方体去查实体每一下都要扫几百个区块段 ✗（会卡顿 ✓）。
     * 封顶之后：够得着的敌人照样一下一个 ✓（每个敌人整次冲刺只挨一次 ✓），
     * 只是**离身体很远**那段不再吃伤害 ✓。
     *
     * <p>★ 2026-10-04 头部锚定之后重算 ✓（作者："t5 根本不显示" ✗ ⇒ 改成鼻子锚定 ✓）：
     * 老版本是"以实体为中心的立方体"✗ —— 那是按"原点 = 身体中点"写的 ✓；
     * 现在原点在**鼻尖** ✓，身体全在**身后** ✗ ⇒ 再按中心给盒子就会**漏掉整条龙身、
     * 还多扫身前半个身位** ✗。现在按**飞行方向**铺一条走廊 ✓：
     * 身前 {@code SWEEP_AHEAD} 格（龙下一刻会扫到哪儿 ✓）+ 身后按体长（封顶 ✓）。
     */
    private AABB sweepBox() {
        double body = Math.min(SWEEP_MAX_LENGTH, MODEL_LENGTH_BLOCKS * this.scale());
        Vec3 dir = this.chargeDir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, -1.0D)
                : this.chargeDir.normalize();
        double ahead = SWEEP_AHEAD;
        // 头部锚定 ⇒ 身体全在身后（整条算 ✓）；老的中心模式 ⇒ 只算半条 ✓（和从前一致 ✓）
        double behind = this.headAnchored() ? body : body * 0.5D;
        double ax = this.getX() + dir.x * ahead;
        double az = this.getZ() + dir.z * ahead;
        double bx = this.getX() - dir.x * behind;
        double bz = this.getZ() - dir.z * behind;
        return new AABB(Math.min(ax, bx) - SWEEP_HALF, this.getY() - 1.5D,
                Math.min(az, bz) - SWEEP_HALF,
                Math.max(ax, bx) + SWEEP_HALF, this.getY() + 2.5D,
                Math.max(az, bz) + SWEEP_HALF);
    }

    /** 判定走廊：身前给多少格 ✓ / 横向+竖向各撑多宽 ✓。 */
    private static final double SWEEP_AHEAD = 6.0D;
    private static final double SWEEP_HALF = 2.5D;

    /**
     * ★ 身体中点相对**实体原点**的偏移（格 ✓，已含个头 ✓）—— 头部锚定时渲染要往回挪这么多 ✓。
     *
     * <p>为什么：模型自己的原点在**身体中段** ✓（鼻子 z≈−165、尾焰 z≈+199 ⇒ 中点大约在
     * z ≈ +17 单位 ≈ 1 格 ✓，"鼻子到原点"是 10.3 格 ✓）。要让**鼻子**落在实体位置上 ✓，
     * 就得把模型沿**它的 +Z（身后）**挪 {@code 10.3 × 个头} 格 ✓ —— 方向用实体的
     * {@code yRot} 现算 ✓（不能用 {@code renderYaw} ✗，那个是给渲染矩阵用的、含修正 ✓）。
     */
    public Vec3 modelCenterOffset() {
        if (!this.headAnchored()) {
            return Vec3.ZERO;
        }
        double back = MODEL_NOSE_BLOCKS * this.scale();
        double yaw = Math.toRadians(this.getYRot());
        return new Vec3(-Math.sin(yaw) * back, 0.0D, -Math.cos(yaw) * back);
    }

    /** 鼻子（局部 −Z 那端）离模型原点多远（格 ✓）：165 单位 / 16 ✓。 */
    public static final double MODEL_NOSE_BLOCKS = 165.0D / 16.0D;

    /** 这条龙能打谁 ✓（只敌对生物；不打玩家/施法者/天使/别的龙/阿波罗 ✓）。 */
    public boolean isValidLightTarget(LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.monster.Monster)) {
            return false;
        }
        // 注意：龙自己不是生物（投射物 ✓）⇒ 这里**不写** instanceof TNDragonEntity ✗
        // （那在 Java 里是"不相干的类型"编译错误 ✓；两条龙本来也不会互相打 ✓）
        if (target instanceof TNFightingAngelEntity || target instanceof TNAngelEntity
                || target instanceof com.tnc.tnc.boss.TNApolloEntity) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(target.getUUID());
    }

    /**
     * ★ 客户端**不再照抄**服务端的位置包 ✓（作者 2026-10-03："环绕飞龙有点卡顿" ✗）。
     *
     * <p>{@code Entity} 基类的 {@code lerpTo} 是**硬赋值**（{@code setPos} + {@code setRot} ✓）✗ ——
     * 20 次/秒啪一下，绕着玩家转的时候就是"一卡一卡" ✗。位置我们两端都会自己算 ✓ ⇒
     * 服务端那份只当**兜底**：主人找不到 / 被传送 / 不是自己算位置的时候才采纳 ✓。
     *
     * <p>★ 2026-10-04：再加一条"**追不上就拉回来**" ✓ ——
     * 客户端虽然自己算位置 ✓，但和服务端一定会**慢慢走偏** ✗（卡顿、区块没加载、
     * 主人被瞬移、服务端那次撞墙判定把它挪了 ✗…）。两边各算各的、又永远不接受修正，
     * 偏到一定程度就是"**画面上一个地方、判定在另一个地方**"✗（看着像闪一下就没 ✓）。
     * 所以现在：偏 &lt; {@link #DESYNC_SNAP} 格（正常情况每 tick 差几厘米 ✓）**一声不响**；
     * 偏得离谱就**贴回服务端那个点** ✓（一帧的跳，总比整个人都不见了强 ✓）。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (!this.level().isClientSide() || teleport || !this.selfDriven()) {
            super.lerpTo(x, y, z, yaw, pitch, steps, teleport);
            return;
        }
        if (this.distanceToSqr(x, y, z) > DESYNC_SNAP * DESYNC_SNAP) {
            super.lerpTo(x, y, z, yaw, pitch, steps, teleport);
        }
    }

    /** 客户端自己的预测和服务端离多远就认输、贴回服务端（格 ✓）。 */
    private static final double DESYNC_SNAP = 4.0D;

    /** 客户端能不能自己算位置 ✓（环绕必须先在客户端找到主人 ✓）。 */
    private boolean selfDriven() {
        if (this.entityData.get(DATA_MODE) != MODE_ORBIT) {
            return true;
        }
        Entity owner = this.level().getEntity(this.entityData.get(DATA_OWNER_EID));
        return owner instanceof LivingEntity living && living.isAlive();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientTick();
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!this.configured) {
            // /summon 出来的：沿当前朝向自己冲一段 ✓（不然它原地不动，看着像坏了 ✗）
            this.charge(this.getLookAngle(), DEFAULT_SPEED, DEFAULT_TICKS, DEFAULT_DAMAGE);
        }
        if (this.orbiting) {
            this.tickOrbit(level);
            return;
        }
        this.tickCharge(level);
    }

    /** 冲刺（服务端 ✓ —— 只负责**判定**，画面上怎么动由客户端自己算 ✓）。 */
    private void tickCharge(ServerLevel level) {
        this.chargeTicks--;
        if (this.chargeTicks <= 0) {
            this.burst(level, 30, 1.2D);
            this.discard();
            return;
        }
        // 前方（**龙头**那个点 ✓）是实心方块 ⇒ 撞墙爆开 ✓
        //   ★ 出生后 SPAWN_GRACE_TICKS tick 内**不判墙** ✗ —— 龙是在施法者正前方几十格处
        //     生成的 ✓，落在地形里的时候原来会"第一 tick 自爆"，看着就是"闪一下就消失" ✗
        //     （见 SPAWN_GRACE_TICKS 的注释 ✓）
        if (this.tickCount > SPAWN_GRACE_TICKS) {
            double headAhead = 10.4D * this.scale() + Math.max(1.0D, this.chargeSpeed);
            Vec3 head = this.position().add(this.chargeDir.scale(headAhead));
            BlockPos pos = BlockPos.containing(head.x, head.y, head.z);
            if (!level.getBlockState(pos).isAir()) {
                this.burst(level, 40, 1.5D);
                this.discard();
                return;
            }
        }
        this.advance(this.chargeDir, this.chargeSpeed);
        // 碰到就伤 ✓（一次冲刺对同一个敌人只打一下 ✓）
        //   ★ t5 放大之后判定盒有几个区块宽 ✗ ⇒ 大龙**隔 tick 扫一次** ✓
        //     （每个敌人反正只挨一下 ✓）
        boolean heavy = this.scale() >= 1.0D;
        if (!heavy || (this.tickCount & 1) == 0) {
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
                if (!this.isValidLightTarget(victim) || !this.hitThisCharge.add(victim.getId())) {
                    continue;
                }
                LivingEntity caster = this.ownerId == null ? null : level.getPlayerByUUID(this.ownerId);
                victim.hurt(level.damageSources().indirectMagic(this, caster == null ? this : caster),
                        (float) this.chargeDamage);
                victim.push(this.chargeDir.x * 2.0D, 0.45D, this.chargeDir.z * 2.0D);
                victim.hurtMarked = true;
                level.sendParticles(this.burstParticle(),
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                        18, 0.5D, 0.6D, 0.5D, 0.08D);
            }
        }
    }

    /**
     * ★ 环绕模式的一 tick（服务端 ✓）：绕主人转圈 ✓、贴到敌人就咬一口（每个敌人有冷却 ✗）✓、到点爆开走人 ✓。
     *
     * <p>主人没了（死了/换维度）⇒ 立刻爆开消失 ✓（不留一条孤儿龙在天上转 ✗）。
     */
    private void tickOrbit(ServerLevel level) {
        LivingEntity owner = this.ownerId == null ? null : level.getPlayerByUUID(this.ownerId);
        if (owner == null || !owner.isAlive()) {
            this.burst(level, 24, 1.0D);
            this.discard();
            return;
        }
        if (--this.chargeTicks <= 0) {
            this.burst(level, 30, 1.2D);
            this.discard();
            return;
        }
        this.orbitAngle += this.orbitDegPerTick;
        this.placeOnRing(owner, this.orbitAngle);
        // 贴到敌人咬一口（同一个敌人 10 tick 才一下 ✓）
        if (this.chargeDamage <= 0.0D) {
            return;
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
            if (!this.isValidLightTarget(victim)) {
                continue;
            }
            int now = this.tickCount;
            Integer next = this.orbitNextHit.get(victim.getId());
            if (next != null && now < next) {
                continue;
            }
            this.orbitNextHit.put(victim.getId(), now + ORBIT_HIT_COOLDOWN);
            victim.hurt(level.damageSources().indirectMagic(this, owner), (float) this.chargeDamage);
            victim.hurtMarked = true;
            level.sendParticles(this.burstParticle(),
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                    10, 0.4D, 0.5D, 0.4D, 0.06D);
        }
    }

    // ------------------------------------------------------------------
    //  ★ 客户端：自己算位置 + 自己放拖尾 ✓（见类注释"卡顿"那一节 ✓）
    // ------------------------------------------------------------------

    private void clientTick() {
        if (this.entityData.get(DATA_MODE) == MODE_ORBIT) {
            this.clientOrbitTick();
        } else {
            this.clientChargeTick();
        }
        this.tickRenderOffsets();     // 这一 tick 的角度 → 上一 tick ✓（渲染插值用 ✓）
    }

    private void clientChargeTick() {
        Vec3 dir = new Vec3(this.entityData.get(DATA_DIR_X), this.entityData.get(DATA_DIR_Y),
                this.entityData.get(DATA_DIR_Z));
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        dir = dir.normalize();
        this.advance(dir, this.entityData.get(DATA_SPEED) / 1000.0D);
        this.trail(dir);
    }

    private void clientOrbitTick() {
        Entity owner = this.level().getEntity(this.entityData.get(DATA_OWNER_EID));
        if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
            return;   // 找不到主人 ⇒ 这一 tick 不动；位置包会兜底把它摆回去 ✓（见 lerpTo ✓）
        }
        if (!this.clientAngleInit) {
            this.clientAngleInit = true;
            this.orbitAngle = this.entityData.get(DATA_ORBIT_START) / 100.0D;
        }
        this.orbitAngle += this.entityData.get(DATA_ORBIT_DEG) / 100.0D;
        this.placeOnRing(living, this.orbitAngle);
    }

    /** 往前走一步 + 朝向对齐飞行方向 ✓（两端共用 ✓）。 */
    private void advance(Vec3 dir, double speed) {
        this.setPos(this.getX() + dir.x * speed, this.getY() + dir.y * speed, this.getZ() + dir.z * speed);
        this.faceDir(dir, false);
    }

    /**
     * ★ 把龙摆到环上（两端共用 ✓ —— 服务端拿它判定伤害、客户端拿它画面 ✓，同一个公式 ⇒ 两边一致 ✓）。
     *
     * <p>环上的点是 <b>{@link #CURL_CENTER_BLOCKS}</b> 那一节（身体切线 = 实体朝向 ✓），
     * 实体原点再沿**前进方向**往回退 {@code 6 × 个头} 格 ✓；
     * 前进方向 = 转的方向的切线 ✓（所以 {@code degPerTick} 的正负决定了绕哪边转 ✓）。
     *
     * <p>★ 生成的那一刻法术也会直接调一次 ✓（不先摆的话，第一帧会闪在主人脚下再飞出去 ✗）。
     */
    public void placeOnRing(Entity center, double angleDeg) {
        double radius = this.entityData.get(DATA_ORBIT_RADIUS) / 100.0D;
        double deg = this.entityData.get(DATA_ORBIT_DEG) / 100.0D;
        double sign = deg < 0.0D ? -1.0D : 1.0D;
        double rad = Math.toRadians(angleDeg);
        double px = center.getX() + Math.cos(rad) * radius;
        double pz = center.getZ() + Math.sin(rad) * radius;
        double tx = -Math.sin(rad) * sign;
        double tz = Math.cos(rad) * sign;
        double back = CURL_CENTER_BLOCKS * this.scale();
        double y = center.getY() + this.entityData.get(DATA_ORBIT_HEIGHT) / 100.0D;
        this.setPos(px - tx * back, y, pz - tz * back);
        this.faceDir(new Vec3(tx, 0.0D, tz), false);
    }

    /**
     * 拖尾（★ 客户端放 ✓）：位置既然是客户端自己算的，粒子当然也得在客户端放 ✗
     * （服务端 {@code sendParticles} 还是 20 次/秒一撮 ✗，那就白改了 ✓）。
     *
     * <p>★ 暗龙（作者 2026-10-04："黑龙不要自发光了，让他周身弥漫着黑雾" ✓）：
     * 不走光龙那条白金光尘 ✗ —— 改成沿**整条身体**撒大团黑烟 ✓（体长越长撒得越多 ✓），
     * 另加少量灵魂火当"眼睛/缝隙里透出来的光"✓。渲染那边也去掉了自发光 ✓
     * （见 {@code dark/client/TNDarkDragonRenderer} ✓）。
     */
    private void trail(Vec3 dir) {
        // ★ 头部锚定：实体原点在**鼻尖** ✓ ⇒ 身体整条在**身后** ✗
        //   ⇒ 撒粒子要沿 −dir 从鼻子往后铺 ✓（不锚定就还是老算法：沿 +Z 那半截 ✓）
        double forwardFromOrigin = this.headAnchored() ? 0.0D : 0.45D;
        if (this.isDark()) {
            double len = MODEL_LENGTH_BLOCKS * this.scale();
            int puffs = Math.min(24, 4 + (int) (len * 0.30D));
            for (int i = 0; i < puffs; i++) {
                double t = (this.tickCount * 0.13D + i / (double) puffs) % 1.0D;
                Vec3 at = this.position().add(dir.scale(len * (forwardFromOrigin - t)));
                double a = this.tickCount * 0.37D + i * 1.7D;
                double r = 1.0D + 1.6D * this.scale();
                this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                        at.x + Math.cos(a) * r, at.y + 0.7D + Math.sin(a * 0.7D) * 0.7D,
                        at.z + Math.sin(a) * r, 0.0D, 0.012D, 0.0D);
            }
            if (this.tickCount % 3 == 0) {
                this.level().addParticle(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D,
                        this.getZ(), 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        Vec3 tail = this.position()
                .subtract(dir.scale(MODEL_LENGTH_BLOCKS * this.scale()
                        * (this.headAnchored() ? 0.90D : 0.35D)));
        this.level().addParticle(this.trailParticle(), tail.x, tail.y + 1.0D, tail.z, 0.0D, 0.0D, 0.0D);
        if (this.tickCount % 4 == 0) {
            this.level().addParticle(this.trailParticle(), this.getX(), this.getY() + 1.0D, this.getZ(),
                    0.0D, 0.0D, 0.0D);
        }
    }

    /** 消散时的爆开 ✓（服务端一次性 ✓，两端都放会翻倍 ✗）。 */
    private void burst(ServerLevel level, int count, double spread) {
        level.sendParticles(this.burstParticle(),
                this.getX(), this.getY() + 1.0D, this.getZ(), count, spread, spread * 0.7D, spread, 0.08D);
        level.sendParticles(this.burstParticle(),
                this.getX(), this.getY() + 1.0D, this.getZ(), count / 2, spread, spread * 0.7D, spread, 0.10D);
    }

    /**
     * 把身体/头都转向飞行方向 ✓（这条龙是朝前冲的，不是横着飘 ✗）。
     *
     * @param alsoOld 服务端传 true ✓（服务端要的就是"立刻"✓）；<b>客户端传 false</b> ✓ ——
     *                客户端留着 {@code yRotO} 让渲染去插值 ✓，不然每 tick 硬转 20 次/秒又是一跳一跳 ✗
     */
    private void faceDir(Vec3 dir, boolean alsoOld) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))
                * (180.0D / Math.PI)));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.updateRenderOffsets();
        if (alsoOld) {
            this.yRotO = yaw;
            this.xRotO = pitch;
            this.renderYawOffset = yaw;
            this.renderYawOffsetO = yaw;
            this.renderPitchOffset = pitch;
            this.renderPitchOffsetO = pitch;
        }
    }

    // ------------------------------------------------------------------
    //  ★★ 渲染要用的朝向：GeckoLib 对**非生物实体**根本读不到我们的 yRot ✗
    //     （见 light/client/TNDragonPose 的类注释：反汇编出来 yaw 恒为 0 ✓）
    //     ⇒ 实体这边把"要用的那对角度"算好摆在这儿，渲染器一个字都不用猜 ✓。
    // ------------------------------------------------------------------

    /** 渲染用的偏航 / 俯仰（度 ✓）—— 由 {@link #updateRenderOffsets()} 从 {@code yRot/xRot} 推出来 ✓。 */
    private float renderYawOffset;
    private float renderYawOffsetO;
    private float renderPitchOffset;
    private float renderPitchOffsetO;

    /**
     * 把 {@code yRot/xRot} 换算成"渲染器该用的角度" ✓。
     *
     * <p>★ 推导（别凭感觉改 ✗ —— 这里差一个 180° 就是作者看到的"倒着飞"✓）：<br>
     * 龙模型鼻子在<b>局部 −Z</b> ✓（作者手改的 geo：下颌 z=−165、尾巴 z=+199 ✓，
     * 现场可用 {@code tools/check_dragon_charge_facing.py} 从 geo 里量出来 ✓）。
     *
     * <p>渲染链（{@code mulPose} 是右乘 ⇒ 角度直接相加 ✓）：
     * <pre>
     *   GeckoLib  : ry(180 − 0)                     = 180°   ← 非生物实体它硬取 yaw = 0 ✗
     *   fixFacing : ry(MODEL_YAW_OFFSET) + ry(renderYaw)
     * </pre>
     * 鼻子要指向飞行方向，就是要 <b>{@code ry(total)·(0,0,−1) = 飞行方向}</b> ✓；
     * MC 的飞行方向是 {@code (−sin yRot, 0, cos yRot)} ✓（{@code Entity.calculateViewVector} ✓）；
     * 而实测 {@code ry(θ)·(0,0,−1) = (−sin θ, 0, −cos θ)} ✓ ⇒
     * 要的是 <b>{@code θ = 180 − yRot}</b> ✓（验算：yRot=0 ⇒ θ=180 ⇒ (0,0,1) 正南 ✓；
     * yRot=−90（正东）⇒ θ=270 ⇒ (1,0,0) 正东 ✓）⇒
     * {@code 180 + MODEL_YAW_OFFSET + renderYaw = 180 − yRot}
     * ⇒ <b>{@code renderYaw = −yRot − MODEL_YAW_OFFSET}</b> ✓（MODEL_YAW_OFFSET = 180 时即 180 − yRot ✓）。
     *
     * <p>⚠️ 这一步千万别"凭对称性"写成 {@code yRot ± 180} ✗ —— 我就在这里栽过：
     * 先算 180 + 180 + yRot，再看"180 和 −180 在模 360 下等价"就以为能换，
     * 结果**南/北对了、东/西正好反 180°** ✗（角度不是先取模再相加的 ✓）。
     * 现在这个式子是用 {@code tools/check_dragon_charge_facing.py} 把
     * 东/南/西/北 × 俯仰 −45…+45 全跑了一遍 4/4 ✓ 才写下来的 ✓，改之前先跑它 ✓。
     *
     * <p>为什么不在 {@code lerpTo} 里做 ✗：位置包对"自己算位置"的模式本来就**不采纳** ✓，
     * 只在 {@link #DESYNC_SNAP} 之外才贴回去 ✓ —— 靠它来同步朝向会漏 ✓。
     * 而 {@code advance() → faceDir()} 是**两端每 tick 都调**的 ✓，所以这里最稳 ✓。
     * （渲染器每帧还会再调一次兜底 ✓：第一帧 / 数据包比实体先到的情况也不会歪 ✓）
     */
    private void updateRenderOffsets() {
        this.renderYawOffset = 180.0F - this.getYRot();
        this.renderPitchOffset = this.getXRot();
    }

    /** 渲染器读它 ✓（{@code partialTick} 用来在上一 tick 和这一 tick 之间插值 ✓）。 */
    public float renderYaw(float partialTick) {
        this.updateRenderOffsets();
        return Mth.rotLerp(partialTick, this.renderYawOffsetO, this.renderYawOffset);
    }

    /** 渲染器读它 ✓（俯仰同理 ✓）。 */
    public float renderPitch(float partialTick) {
        this.updateRenderOffsets();
        return Mth.lerp(partialTick, this.renderPitchOffsetO, this.renderPitchOffset);
    }

    /** 客户端每 tick 收尾：把"这一 tick 的角度"存成"上一 tick"✓（渲染插值用 ✓）。 */
    private void tickRenderOffsets() {
        this.updateRenderOffsets();
        this.renderYawOffsetO = this.renderYawOffset;
        this.renderPitchOffsetO = this.renderPitchOffset;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Scale", this.entityData.get(DATA_SCALE));
        tag.putInt("Tier", this.tier());
        tag.putInt("Mode", this.entityData.get(DATA_MODE));
        tag.putDouble("ChargeSpeed", this.chargeSpeed);
        tag.putDouble("ChargeDamage", this.chargeDamage);
        tag.putInt("ChargeTicks", this.chargeTicks);
        tag.putDouble("DirX", this.chargeDir.x);
        tag.putDouble("DirY", this.chargeDir.y);
        tag.putDouble("DirZ", this.chargeDir.z);
        tag.putDouble("OrbitRadius", this.orbitRadius);
        tag.putDouble("OrbitDeg", this.orbitDegPerTick);
        tag.putDouble("OrbitAngle", this.orbitAngle);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        if (tag.contains("Scale")) {
            this.entityData.set(DATA_SCALE, tag.getInt("Scale"));
        }
        if (tag.contains("Tier")) {
            this.setTier(tag.getInt("Tier"));
        }
        if (tag.contains("OrbitRadius")) {
            this.orbitRadius = tag.getDouble("OrbitRadius");
            this.orbitDegPerTick = tag.getDouble("OrbitDeg");
            this.orbitAngle = tag.getDouble("OrbitAngle");
        }
        if (tag.contains("ChargeSpeed")) {
            this.chargeSpeed = tag.getDouble("ChargeSpeed");
            this.chargeDamage = tag.getDouble("ChargeDamage");
            this.chargeTicks = tag.getInt("ChargeTicks");
            this.chargeDir = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"),
                    tag.getDouble("DirZ"));
            this.configured = true;
            // 同步数据不进存档 ✗ ⇒ 读档后自己重新填一遍 ✓（投射物本来活不过 20 秒 ✓，
            // 这只是**别让它读档后变成一条不会动的死龙** ✗）
            int mode = tag.contains("Mode") ? tag.getInt("Mode") : MODE_CHARGE;
            this.orbiting = mode == MODE_ORBIT;
            this.entityData.set(DATA_MODE, mode);
            this.entityData.set(DATA_DIR_X, (float) this.chargeDir.x);
            this.entityData.set(DATA_DIR_Y, (float) this.chargeDir.y);
            this.entityData.set(DATA_DIR_Z, (float) this.chargeDir.z);
            this.entityData.set(DATA_SPEED, (int) Math.round(this.chargeSpeed * 1000.0D));
            this.entityData.set(DATA_ORBIT_RADIUS, (int) Math.round(this.orbitRadius * 100.0D));
            this.entityData.set(DATA_ORBIT_DEG, (int) Math.round(this.orbitDegPerTick * 100.0D));
            this.entityData.set(DATA_ORBIT_START, (int) Math.round(this.orbitAngle * 100.0D));
            if (this.ownerId != null && this.level() instanceof ServerLevel server) {
                net.minecraft.server.level.ServerPlayer owner =
                        server.getServer().getPlayerList().getPlayer(this.ownerId);
                if (owner != null) {
                    this.entityData.set(DATA_OWNER_EID, owner.getId());
                }
            }
            this.faceDir(this.chargeDir, true);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // ★ 作者 2026-10-04："我希望是龙绕着一个圆环绕，现在的实机效果是三条弯弯的龙在那转，
        //   根本不是圆" ✗ ⇒ **不再用那段"把身体弯成 120° 弧"的 orbit 动画** ✗。
        //   现在绕圈也播 `dash`（＝直着往前飞 ✓）：龙保持自己的形状、**沿着一个圆飞** ✓，
        //   这样看到的才是"一条条龙绕着圆心转"✓（圆是靠**飞行的轨迹**画出来的 ✓，
        //   不是靠把身体掰弯 ✗ —— 掰弯那种只有俯视才像圆，实机看着就是三条弯管子 ✗）。
        //   （`orbit` 那段动画还留在 json 里没删 ✓，想再试随时可以切回来 ✓）
        controllers.add(new AnimationController<>(this, "move", 5, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop("dash"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 资源（作者手改的那条东方光明龙 ✓）----
    public static net.minecraft.resources.ResourceLocation modelResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/dragon.geo.json");
    }

    public static net.minecraft.resources.ResourceLocation textureResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/dragon_bedrock.png");
    }

    public static net.minecraft.resources.ResourceLocation animationResource() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/dragon.animation.json");
    }
}
