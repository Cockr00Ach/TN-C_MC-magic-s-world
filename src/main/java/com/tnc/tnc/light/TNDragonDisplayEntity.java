package com.tnc.tnc.light;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>龙的本体</b> —— 一条被扔出去往前冲（或绕着主人转）的东方巨龙 ✓。
 *
 * <h2>★★ 2026-10-04：从 GeckoLib 换成 <b>方块模型 + 原版 Display 实体</b></h2>
 * 作者原话：<b>"龙释放还是异常，都没法出现啊大哥，你把他当成block来使用好不好"</b> ✓。
 *
 * <p>前几版用 {@code GeoEntityRenderer} 画 ✓，但那条路上全是**看不见的坑** ✗：
 * <ul>
 *   <li>GeckoLib 对**非生物实体**恒取 {@code yaw = 0} ✗（反汇编 {@code geckolib-4.8.4} 确认 ✓）
 *       —— 龙是 {@code extends Entity} ⇒ 模型永远被钉在一个朝向上 ✗（"倒着飞" ✓）；</li>
 *   <li>剔除按**实体原点**判 ✗ —— 23→273 格长的模型经常"身子在屏幕里、原点在视锥外"
 *       ⇒ 整条**凭空消失** ✗；</li>
 *   <li>外加半透明、渲染距离、出生点被推到几十格外 ✗ —— 最后就是"**根本没法出现**" ✗。</li>
 * </ul>
 * 三次修补都没能一次做对 ⇒ 说明**这条路本身就不该走** ✗。现在：
 * <ul>
 *   <li>模型 = **原版方块模型** ✓（{@code tools/gen_dragon_block_model.py} 从作者手改的 geo
 *       烘出来 ✓，见 {@code assets/tnc/models/block/dragon_display_*.json} ✓）；</li>
 *   <li>实体 = <b>{@link Display.BlockDisplay}</b> ✓ —— 走**原版自己的**打包/渲染/剔除管线 ✓，
 *       由 {@code BlockDisplayRenderer} 调 {@code renderSingleBlock} 画 ✓，
 *       没有第三方渲染器、没有可见性特判 ✓；</li>
 *   <li>顺带白拿一个**位置插值** ✓（原版 Display 自带 ✓，当年"环绕一卡一卡"也就没了 ✓）。</li>
 * </ul>
 *
 * <p>代价：模型是**静态姿态**（烘的时候取 {@code dash} 里弯得最明显的那一帧 ✓），
 * 没有骨骼动画 ✗ —— 换来"一定能看见" ✓，这个交换眼下是值的 ✓。
 *
 * <h2>朝向（★ 这次是**原版**规则，和 GeckoLib 那套不一样 ✗）</h2>
 * 原版实体模型（含 Display 画的方块模型）约定 <b>正面 = +Z</b> ✓，{@code setYRot} 一转就跟着走 ✓。
 * 这条龙的鼻子在**局部 −Z** ✓（作者手改的 geo：下颌 z=−165 ✓）⇒ 转 {@code yRot + 180} ✓
 * （见 {@link #faceDir} ✓）。这个结论由 {@code tools/check_dragon_charge_facing.py}
 * 的"东南西北 × 俯仰"验算背书 ✓（那边算出来的正是"总角度 = 180 − yRot"✓）。
 *
 * <h2>头部锚定</h2>
 * 实体位置 = **鼻尖** ✓（模型沿自身朝向往回挪"鼻子那一段"✓，写在 Display 的 transformation 里 ✓，
 * 由**原版**去应用 ✓）⇒ 出生点只要放在身体半径之外一点点 ✓，再也不用随体长往外推 ✗
 * （老公式把 t5 的原点推到 76 格外 ⇒ "根本不显示" ✗，见 {@code TNLightDragonChain.release} ✓）。
 */
public class TNDragonDisplayEntity extends Display.BlockDisplay {

    /** 模型原长（格 ✓）—— 鼻子 z=−165 → 尾焰 z=+199 ⇒ 364 单位 = 22.75 格 ✓。 */
    public static final double MODEL_LENGTH_BLOCKS = 22.75D;
    /** 鼻子离模型原点（格 ✓）= 165 / 16 ✓。 */
    public static final double MODEL_NOSE_BLOCKS = 165.0D / 16.0D;
    /** 尾焰离模型原点（格 ✓）= 199 / 16 ✓（拖尾用 ✓）。 */
    private static final double MODEL_TAIL_BLOCKS = 199.0D / 16.0D;

    /** 冲刺 / 环绕 ✓（和老的 {@link TNDragonEntity} 同名，方便对照 ✓）。 */
    public static final int MODE_CHARGE = 0;
    public static final int MODE_ORBIT = 1;

    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_EID =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DIR_X =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Y =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Z =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SPEED =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_RADIUS =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_DEG =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_HEIGHT =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);
    /** 暗龙（0/1 ✓）—— 只影响粒子 ✓（模型在生成时就用另一个载体方块 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_DARK =
            SynchedEntityData.defineId(TNDragonDisplayEntity.class, EntityDataSerializers.INT);

    private static final double DEFAULT_SPEED = 0.28D;
    private static final int DEFAULT_TICKS = 200;
    private static final double DEFAULT_DAMAGE = 20.0D;
    private static final double SWEEP_AHEAD = 6.0D;
    private static final double SWEEP_HALF = 2.5D;
    private static final double SWEEP_MAX_LENGTH = 96.0D;
    private static final int SPAWN_GRACE_TICKS = 6;
    private static final double DESYNC_SNAP = 4.0D;
    /** 原版 Display 的位置插值时长（tick ✓）—— 3 tick 足够把 20Hz 的位置包抹平 ✓。 */
    private static final int INTERPOLATION_TICKS = 3;

    private UUID ownerId;
    private Vec3 chargeDir = new Vec3(0.0D, 0.0D, -1.0D);
    private double chargeSpeed = DEFAULT_SPEED;
    private double chargeDamage = DEFAULT_DAMAGE;
    private int chargeTicks;
    private boolean configured;
    private final Set<Integer> hitThisCharge = new HashSet<>();

    private boolean orbiting;
    private double orbitRadius = 5.0D;
    private double orbitDegPerTick = 3.0D;
    private double orbitAngle;
    private final Map<Integer, Integer> orbitNextHit = new HashMap<>();
    private static final int ORBIT_HIT_COOLDOWN = 10;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/dragon");

    public TNDragonDisplayEntity(EntityType<? extends TNDragonDisplayEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        // ★ 这些 setter 在原版是 private ✗ ⇒ 用 META-INF/accesstransformer.cfg 放开 ✓
        this.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        this.setViewRange(8.0F);            // 8 × 16 = 128 格可见 ✓（够 t5 那条 273 格的龙 ✓）
        this.setShadowRadius(0.0F);
        this.setShadowStrength(0.0F);
        this.setInterpolationDuration(INTERPOLATION_TICKS);
        this.setInterpolationDelay(0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 30);
        this.entityData.define(DATA_TIER, 1);
        this.entityData.define(DATA_MODE, MODE_CHARGE);
        this.entityData.define(DATA_OWNER_EID, -1);
        this.entityData.define(DATA_DIR_X, 0.0F);
        this.entityData.define(DATA_DIR_Y, 0.0F);
        this.entityData.define(DATA_DIR_Z, -1.0F);
        this.entityData.define(DATA_SPEED, (int) Math.round(DEFAULT_SPEED * 1000.0D));
        this.entityData.define(DATA_ORBIT_RADIUS, 500);
        this.entityData.define(DATA_ORBIT_DEG, 300);
        this.entityData.define(DATA_ORBIT_HEIGHT, 120);
        this.entityData.define(DATA_DARK, 0);
    }

    // ------------------------------------------------------------------
    //  模型 / 缩放 / 平移
    // ------------------------------------------------------------------

    /**
     * 用哪一个"载体方块" ✓ —— 由法术在生成时指定 ✓。
     *
     * <p>{@link Display.BlockDisplay} 画的就是**这个方块状态对应的模型** ✓
     * （{@code BlockDisplayRenderer → renderSingleBlock} ✓）⇒ 光龙/暗龙各挂一份方块模型 ✓。
     */
    public void setCarrier(Block block, boolean dark) {
        this.setBlockState(block.defaultBlockState());
        this.entityData.set(DATA_DARK, dark ? 1 : 0);
        // 光龙要"自发光"（作者原话："贴图里的亮金/白热像素就是要它发光" ✓）；
        // 暗龙**不能**满亮 ✗（作者："黑龙不要自发光了" ✓ —— 满亮会让黑鳞变成灰的 ✗）
        if (dark) {
            this.setBrightnessOverride(null);
        } else {
            this.setBrightnessOverride(Brightness.FULL_BRIGHT);
        }
    }

    public boolean isDark() {
        return this.entityData.get(DATA_DARK) != 0;
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public void setScale(double scale) {
        this.entityData.set(DATA_SCALE, (int) Math.round(Mth.clamp(scale, 0.05D, 64.0D) * 100.0D));
    }

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        this.entityData.set(DATA_TIER, Mth.clamp(tier, 1, 5));
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    /**
     * 把"缩放 + 沿自身朝向的平移"写进 Display 的 transformation ✓。
     *
     * <p>平移量 = **鼻子那一段**（沿朝向取负 ✓）⇒ 实体位置落在**鼻尖**上 ✓（头部锚定 ✓）。
     */
    public void pushTransform() {
        float s = (float) Math.max(0.01D, this.scale());
        Vec3 back = this.facing().scale(-MODEL_NOSE_BLOCKS * this.scale());
        this.setTransformation(new Transformation(
                new Vector3f((float) back.x, (float) back.y, (float) back.z),
                new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
    }

    /** 当前朝向（带俯仰 ✓）：冲刺方向 ✓；环绕时就是切线方向 ✓。 */
    private Vec3 facing() {
        Vec3 d = new Vec3(this.entityData.get(DATA_DIR_X), this.entityData.get(DATA_DIR_Y),
                this.entityData.get(DATA_DIR_Z));
        return d.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, -1.0D) : d.normalize();
    }

    // ------------------------------------------------------------------
    //  两种活法
    // ------------------------------------------------------------------

    /** 扔出去：朝 {@code dir} 冲 ✓。 */
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
        this.pushTransform();
        LOGGER.info("TN-C/dragon: 冲刺 spawn tier={} scale={} speed={} ticks={} pos=({},{},{})",
                this.tier(), this.scale(), this.chargeSpeed, this.chargeTicks,
                String.format(java.util.Locale.ROOT, "%.1f", this.getX()),
                String.format(java.util.Locale.ROOT, "%.1f", this.getY()),
                String.format(java.util.Locale.ROOT, "%.1f", this.getZ()));
    }

    /** 绕着主人转 ✓。 */
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
        this.orbiting = true;
        this.entityData.set(DATA_MODE, MODE_ORBIT);
        this.entityData.set(DATA_OWNER_EID, ownerEid);
        this.entityData.set(DATA_ORBIT_RADIUS, (int) Math.round(this.orbitRadius * 100.0D));
        this.entityData.set(DATA_ORBIT_DEG, (int) Math.round(degPerTick * 100.0D));
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
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        return true;
    }

    /** 剔除盒按体长撑 ✓（原版 Display 自己给的是个小盒子 ✗）。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double len = MODEL_LENGTH_BLOCKS * this.scale();
        return this.getBoundingBox().inflate(SWEEP_HALF + len * 0.1D, len * 0.45D, len * 0.65D);
    }

    /** 判定走廊（沿飞行方向铺 ✓，身后按体长 ✓）。 */
    private AABB sweepBox() {
        double body = Math.min(SWEEP_MAX_LENGTH, MODEL_LENGTH_BLOCKS * this.scale());
        Vec3 dir = this.facing();
        double ax = this.getX() + dir.x * SWEEP_AHEAD;
        double az = this.getZ() + dir.z * SWEEP_AHEAD;
        double bx = this.getX() - dir.x * body;
        double bz = this.getZ() - dir.z * body;
        return new AABB(Math.min(ax, bx) - SWEEP_HALF, this.getY() - 1.5D,
                Math.min(az, bz) - SWEEP_HALF,
                Math.max(ax, bx) + SWEEP_HALF, this.getY() + 2.5D,
                Math.max(az, bz) + SWEEP_HALF);
    }

    /** 只打敌对生物 ✓；不打玩家/施法者/天使/阿波罗 ✓。 */
    public boolean isValidTarget(LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.monster.Monster)) {
            return false;
        }
        if (target instanceof TNFightingAngelEntity || target instanceof TNAngelEntity
                || target instanceof com.tnc.tnc.boss.TNApolloEntity) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(target.getUUID());
    }

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
        if (this.entityData.get(DATA_MODE) == MODE_ORBIT) {
            this.tickOrbit();
        } else {
            this.tickCharge();
        }
    }

    /** 冲刺 ✓（两端都自己走 ✓ —— 位置包只当兜底 ✓，原版 Display 会把画面插值抹平 ✓）。 */
    private void tickCharge() {
        if (!this.configured) {
            this.charge(this.getLookAngle(), DEFAULT_SPEED, DEFAULT_TICKS, DEFAULT_DAMAGE);
        }
        if (--this.chargeTicks <= 0 && !this.level().isClientSide()) {
            this.burst(20, 1.2D);
            this.discard();
            return;
        }
        if (this.level().isClientSide()) {
            this.advance(this.facing(), this.entityData.get(DATA_SPEED) / 1000.0D);
            this.trail(this.facing());
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        // 龙头前方是实心方块 ⇒ 撞墙爆开 ✓（出生后 SPAWN_GRACE_TICKS tick 内不判 ✓，
        //   因为出生点常常正好在地形里 ✓ —— 那会造成"出生即自爆"✗）
        if (this.tickCount > SPAWN_GRACE_TICKS) {
            double headAhead = 10.4D * this.scale() + Math.max(1.0D, this.chargeSpeed);
            Vec3 head = this.position().add(this.chargeDir.scale(headAhead));
            BlockPos pos = BlockPos.containing(head.x, head.y, head.z);
            if (!level.getBlockState(pos).isAir()) {
                this.burst(30, 1.5D);
                this.discard();
                return;
            }
        }
        this.advance(this.chargeDir, this.chargeSpeed);
        boolean heavy = this.scale() >= 1.0D;
        if (heavy && (this.tickCount & 1) != 0) {
            return;
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
            if (!this.isValidTarget(victim) || !this.hitThisCharge.add(victim.getId())) {
                continue;
            }
            LivingEntity caster = this.ownerId == null ? null : level.getPlayerByUUID(this.ownerId);
            victim.hurt(level.damageSources().indirectMagic(this, caster == null ? this : caster),
                    (float) this.chargeDamage);
            victim.hurtMarked = true;
            level.sendParticles(this.burstParticle(),
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                    12, 0.5D, 0.6D, 0.5D, 0.08D);
        }
    }

    /** 环绕 ✓（服务端管判定 ✓，客户端管画面 ✓，同一个公式 ✓）。 */
    private void tickOrbit() {
        Entity owner = this.level().getEntity(this.entityData.get(DATA_OWNER_EID));
        if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
            if (!this.level().isClientSide()) {
                this.burst(16, 1.0D);
                this.discard();
            }
            return;
        }
        if (!this.level().isClientSide() && --this.chargeTicks <= 0) {
            this.burst(20, 1.2D);
            this.discard();
            return;
        }
        this.orbitAngle += this.entityData.get(DATA_ORBIT_DEG) / 100.0D;
        this.placeOnRing(living, this.orbitAngle);
        if (this.level().isClientSide() || this.chargeDamage <= 0.0D) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.sweepBox())) {
            if (!this.isValidTarget(victim)) {
                continue;
            }
            int now = this.tickCount;
            Integer next = this.orbitNextHit.get(victim.getId());
            if (next != null && now < next) {
                continue;
            }
            this.orbitNextHit.put(victim.getId(), now + ORBIT_HIT_COOLDOWN);
            victim.hurt(level.damageSources().indirectMagic(this, living), (float) this.chargeDamage);
            victim.hurtMarked = true;
        }
    }

    /** 把龙摆到环上 ✓。 */
    public void placeOnRing(Entity center, double angleDeg) {
        double radius = this.entityData.get(DATA_ORBIT_RADIUS) / 100.0D;
        double deg = this.entityData.get(DATA_ORBIT_DEG) / 100.0D;
        double sign = deg < 0.0D ? -1.0D : 1.0D;
        double rad = Math.toRadians(angleDeg);
        double px = center.getX() + Math.cos(rad) * radius;
        double pz = center.getZ() + Math.sin(rad) * radius;
        double tx = -Math.sin(rad) * sign;
        double tz = Math.cos(rad) * sign;
        double y = center.getY() + this.entityData.get(DATA_ORBIT_HEIGHT) / 100.0D;
        this.setPos(px, y, pz);
        this.faceDir(new Vec3(tx, 0.0D, tz), false);
    }

    private void advance(Vec3 dir, double speed) {
        this.setPos(this.getX() + dir.x * speed, this.getY() + dir.y * speed,
                this.getZ() + dir.z * speed);
        this.faceDir(dir, false);
    }

    /**
     * 朝向 ✓ —— <b>原版规则</b>：模型正面是 +Z ✓，而这条龙的鼻子在 −Z ✓ ⇒ 转 {@code yaw + 180} ✓。
     *
     * <p>俯仰取负 ✓：MC 的 {@code xRot} 是"低头为正" ✓，原版 Display 内部是
     * {@code orientation.rotationYXZ(-yRot, +xRot)} ✓ ⇒ 给 {@code -pitch} 正好让龙跟着准星低头 ✓
     * （和渲染器版本同一个结论 ✓，由 {@code tools/check_dragon_charge_facing.py} 背书 ✓）。
     */
    private void faceDir(Vec3 dir, boolean alsoOld) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))
                * (180.0D / Math.PI)));
        this.setYRot(yaw + 180.0F);
        this.setXRot(-pitch);
        if (alsoOld) {
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
        this.pushTransform();
    }

    // ------------------------------------------------------------------
    //  粒子
    // ------------------------------------------------------------------

    private net.minecraft.core.particles.SimpleParticleType burstParticle() {
        return this.isDark() ? ParticleTypes.SMOKE : ParticleTypes.FIREWORK;
    }

    private net.minecraft.core.particles.SimpleParticleType trailParticle() {
        return this.isDark() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD;
    }

    private void trail(Vec3 dir) {
        double len = MODEL_LENGTH_BLOCKS * this.scale();
        if (this.isDark()) {
            int puffs = Math.min(20, 4 + (int) (len * 0.25D));
            for (int i = 0; i < puffs; i++) {
                double t = (this.tickCount * 0.13D + i / (double) puffs) % 1.0D;
                Vec3 at = this.position().subtract(dir.scale(len * t));
                double a = this.tickCount * 0.37D + i * 1.7D;
                double r = 1.0D + 1.4D * this.scale();
                this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                        at.x + Math.cos(a) * r, at.y + 0.7D + Math.sin(a * 0.7D) * 0.7D,
                        at.z + Math.sin(a) * r, 0.0D, 0.012D, 0.0D);
            }
            return;
        }
        Vec3 tail = this.position().subtract(dir.scale(MODEL_TAIL_BLOCKS * this.scale()));
        this.level().addParticle(this.trailParticle(), tail.x, tail.y + 1.0D, tail.z,
                0.0D, 0.0D, 0.0D);
        if (this.tickCount % 4 == 0) {
            this.level().addParticle(this.trailParticle(), this.getX(), this.getY() + 1.0D,
                    this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    private void burst(int count, double spread) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(this.burstParticle(), this.getX(), this.getY() + 1.0D, this.getZ(),
                count, spread, spread * 0.7D, spread, 0.08D);
    }

    // ------------------------------------------------------------------
    //  存档（投射物活不长 ✓，这里只是"读档别变死龙" ✓；同步数据不进存档 ⇒ 自己补一遍 ✓）
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Scale", this.entityData.get(DATA_SCALE));
        tag.putInt("Tier", this.tier());
        tag.putInt("Mode", this.entityData.get(DATA_MODE));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        if (tag.contains("Scale")) {
            this.entityData.set(DATA_SCALE, tag.getInt("Scale"));
        }
        if (tag.contains("Tier")) {
            this.setTier(tag.getInt("Tier"));
        }
        if (tag.contains("DirX")) {
            this.entityData.set(DATA_DIR_X, tag.getFloat("DirX"));
            this.entityData.set(DATA_DIR_Y, tag.getFloat("DirY"));
            this.entityData.set(DATA_DIR_Z, tag.getFloat("DirZ"));
        }
    }
}
