package com.tnc.tnc.light;

import com.tnc.tnc.magic.TnSpellMechanics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>一道实体光线</b>（光系第三条链「光线」✓）—— 作者 2026-10-01：
 * "光线我想要实体的" ✗（粒子撒出来的管看着不像光 ✗）。
 *
 * <h2>为什么用实体而不是粒子</h2>
 * 和魔法阵同一个理由 ✓：粒子撒出来永远是"一串点"✗；实体能把**一整根光柱**当几何画 ✓，
 * 边缘干净、颜色能沿轴线做彩虹渐变 ✓（顶点色 ✓），加粗也只是改一个半径 ✗。
 *
 * <h2>姿态</h2>
 * 实体自己在 {@code (0,0,0)}，光柱沿**本地 +Z** 方向长 {@link #length()} 格 ✓；
 * 生成时用 {@link #configure} 设定朝向（{@code yRot/xRot} ✓）——
 * 渲染器只管把"本地 +Z"那根柱子画出来 ✓（不用管世界朝向 ✓）。
 *
 * <h2>同步</h2>
 * 客户端只要四件事：多粗、多长、活多久、什么形态 ✓（{@link SynchedEntityData} ✓，
 * {@code updateInterval(1)} 见 {@code TNOrbEntities} ✓ —— 不然会"晚半拍才冒出来" ✗）。
 * 伤害判定只在服务端做 ✓（每个敌人每条光线**只挨一次** ✓）。
 */
public class TNLightBeamEntity extends Entity {

    /** 形态：0 = 前射（彩虹渐变 ✓），1 = 天降（更亮、白芯更大 ✓）。 */
    public static final int STYLE_RAY = 0;
    public static final int STYLE_DESCENT = 1;

    /**
     * ★ 每 tick 最多转多少度（作者 2026-10-01："就像雷球那样索敌" ✓）。
     *
     * <p>雷球是引擎投射物的 {@code homing_angle: 0.5}（拐弯追人 ✓）；这里用"每 tick 限速转向"达到同样的手感 ✓：
     * 18°/tick ⇒ 转 90° 要 5 tick（0.25 秒 ✓）—— **看得见它拐过去** ✓，而不是瞬移对准 ✗。
     */
    private static final float TURN_PER_TICK = 18.0F;

    /** 半径 × 100（同步用整数 ✓；天降那档半径能到 4~5 格 ✓）。 */
    protected static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(TNLightBeamEntity.class, EntityDataSerializers.INT);
    /** 长度 × 100 ✓。 */
    protected static final EntityDataAccessor<Integer> DATA_LENGTH =
            SynchedEntityData.defineId(TNLightBeamEntity.class, EntityDataSerializers.INT);
    /** 存在时长（tick ✓）。 */
    protected static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightBeamEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(TNLightBeamEntity.class, EntityDataSerializers.INT);
    /** 施法者的实体 id（判"只打敌人"要用 ✓；-1 = 找不到 ✓）。 */
    protected static final EntityDataAccessor<Integer> DATA_CASTER =
            SynchedEntityData.defineId(TNLightBeamEntity.class, EntityDataSerializers.INT);
    /**
     * ★ <b>锁定目标</b>的实体 id（-1 = 没锁 ✓）。
     *
     * <p>作者 2026-10-01："我是要那种对着敌人释放然后**瞄着敌人**的那种" ✓ ——
     * 光柱不是"朝一个方向射出去就不管了" ✗，而是**每一 tick 都重新瞄准目标** ✓：
     * 目标跑，光柱跟着转 ✓（服务端改朝向/位置 ✓，客户端渲染器也拿这个 id 做平滑跟随 ✓）。
     */
    protected static final EntityDataAccessor<Integer> DATA_TARGET =
            SynchedEntityData.defineId(TNLightBeamEntity.class, EntityDataSerializers.INT);

    /** 伤害只在服务端算 ✓（不进同步 ✓）。 */
    private float damage;
    /** 已经挨过这道光的敌人 ✓（只打一次 ✓）。 */
    private final Set<UUID> hit = new HashSet<>();
    public TNLightBeamEntity(EntityType<? extends TNLightBeamEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** 生成后调用一次：多粗、多长、多疼、活多久、什么形态、谁放的、锁谁 ✓。 */
    public void configure(Entity caster, double radius, double length, float damage,
                          int lifeTicks, int style, LivingEntity target) {
        this.entityData.set(DATA_RADIUS, (int) Math.round(radius * 100.0D));
        this.entityData.set(DATA_LENGTH, (int) Math.round(length * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.entityData.set(DATA_STYLE, style);
        this.entityData.set(DATA_CASTER, caster == null ? -1 : caster.getId());
        this.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
        this.damage = damage;
        // ★ 这里**故意不立刻对准目标**：出生时朝向 = 施法方向 ✓，之后每 tick limited 拐向目标 ✓
        //   ⇒ 看得见"索敌"的过程（雷球的 homing 也是这样拐的 ✓）。
    }

    /** 锁定目标的 id（-1 = 没锁 ✓）。 */
    public int targetId() {
        return this.entityData.get(DATA_TARGET);
    }

    /** 施法者的实体 id（-1 = 找不到 ✓）—— 渲染器要用它拿"眼睛位置" ✓。 */
    public int casterId() {
        return this.entityData.get(DATA_CASTER);
    }

    /** 是不是在"瞄着某个敌人" ✓（渲染器要用它做平滑跟随 ✓）。 */
    public boolean tracking() {
        return this.targetId() >= 0;
    }

    public double radius() {
        return this.entityData.get(DATA_RADIUS) / 100.0D;
    }

    public double length() {
        return this.entityData.get(DATA_LENGTH) / 100.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    public int style() {
        return this.entityData.get(DATA_STYLE);
    }

    /** 光柱末端（世界坐标 ✓）—— 判伤和末端粒子都用它 ✓。 */
    public Vec3 endPoint() {
        return this.position().add(this.getLookAngle().scale(this.length()));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 20);
        this.entityData.define(DATA_LENGTH, 2000);
        this.entityData.define(DATA_LIFE, 12);
        this.entityData.define(DATA_STYLE, STYLE_RAY);
        this.entityData.define(DATA_CASTER, -1);
        this.entityData.define(DATA_TARGET, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.tickCount > this.life()) {
            this.discard();
            return;
        }
        this.trackTarget();                      // ★ 瞄着敌人：每 tick 重新瞄准 ✓
        this.damageAlong();
        this.sparkle();
    }

    /**
     * ★ <b>瞄着敌人</b>（作者 2026-10-01："就像雷球那样索敌" ✓）——
     * 雷球的索敌是引擎投射物的 {@code homing_angle: 0.5}（发射后**拐弯追人** ✓），
     * 所以这里也做成"**拐弯**"而不是"一出来就瞬间对准"✗：
     * 每 tick 只把朝向往目标转最多 {@link #TURN_PER_TICK} 度 ✓ —— 手感和雷球一致 ✓，而且**看得出在追** ✓。
     *
     * <ul>
     *   <li><b>前射形态</b>：起点**跟着施法者的眼睛走**（人动光也动 ✓），朝向每 tick 往目标拐 ✓，
     *       长度 = 到目标的距离 ✓；</li>
     *   <li><b>天降形态</b>：整根柱子**悬在目标头顶**跟着平移 ✓，永远竖直向下 ✓；</li>
     *   <li><b>目标死了</b>：在**当前朝向**的锥（{@link TNLightBeamMechanics#HOMING_CONE_DEGREES} ✓）
     *       里再找一个最近的 ✓ 继续追（雷球也是这个意思 ✓）；实在没有就保持原方向飞完 ✓。</li>
     * </ul>
     */
    private void trackTarget() {
        LivingEntity target = this.targetEntity();
        if (target == null) {
            target = this.reacquire();           // 目标死了/丢了 ⇒ 再找一个 ✓
        }
        if (target == null) {
            return;                              // 没得追：保持当前朝向飞完 ✓
        }
        if (this.style() == STYLE_DESCENT) {
            double ground = TNLightBeamMechanics.groundY((ServerLevel) this.level(), target.position());
            this.setPos(target.getX(), ground + TNLightBeamMechanics.skyHeight(), target.getZ());
            this.setYRot(0.0F);
            this.setXRot(90.0F);                 // +Z 转到竖直向下 ✓（渲染器同一套约定 ✓）
            return;
        }
        // 前射：起点跟着施法者（找不到施法者就用当前位置 ✓）
        if (this.level() instanceof ServerLevel server) {
            int id = this.entityData.get(DATA_CASTER);
            if (id >= 0 && server.getEntity(id) instanceof LivingEntity caster) {
                Vec3 eye = caster.getEyePosition();
                this.setPos(eye.x, eye.y, eye.z);
            }
        }
        this.turnToward(target);
        double dist = this.position().distanceTo(center(target));
        this.entityData.set(DATA_LENGTH, (int) Math.round(Math.max(1.0D, dist) * 100.0D));
    }

    /**
     * ★ 往目标**拐**（每 tick 最多 {@link #TURN_PER_TICK} 度 ✓）——
     * 这就是"索敌"看得见的那部分 ✓：不瞬间贴上去，而是明显转过去 ✓。
     */
    private void turnToward(LivingEntity target) {
        Vec3 want = center(target).subtract(this.position()).normalize();
        float wantYaw = TNLightBeamMechanics.yawFor(want);
        float wantPitch = TNLightBeamMechanics.pitchFor(want);
        float dy = Mth.wrapDegrees(wantYaw - this.getYRot());
        float dp = wantPitch - this.getXRot();
        this.setYRot(this.getYRot() + Mth.clamp(dy, -TURN_PER_TICK, TURN_PER_TICK));
        this.setXRot(this.getXRot() + Mth.clamp(dp, -TURN_PER_TICK, TURN_PER_TICK));
        this.yRotO = this.getYRot();             // 端上自己插值也顺一点 ✓
        this.xRotO = this.getXRot();
    }

    /** 目标没了 ⇒ 在**当前朝向**的锥里再找一个最近的敌人 ✓（没有就 null ✓）。 */
    private LivingEntity reacquire() {
        if (!(this.level() instanceof ServerLevel server)) {
            return null;
        }
        if (!(server.getEntity(this.entityData.get(DATA_CASTER)) instanceof ServerPlayer caster)) {
            return null;
        }
        double reach = Math.max(24.0D, this.length());
        List<LivingEntity> candidates = server.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(reach));
        List<LivingEntity> found = TNLightBeamMechanics.coneTargets(caster, candidates,
                this.getLookAngle(), reach, TNLightBeamMechanics.HOMING_CONE_DEGREES);
        if (found.isEmpty()) {
            return null;
        }
        LivingEntity next = found.get(0);
        this.entityData.set(DATA_TARGET, next.getId());
        return next;
    }

    /** 当前锁定的目标实体（客户端也能拿到 ⇒ 渲染器做平滑跟随 ✓）。 */
    public LivingEntity targetEntity() {
        int id = this.targetId();
        if (id < 0 || this.level() == null) {
            return null;
        }
        return this.level().getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static Vec3 center(LivingEntity e) {
        return e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
    }

    /** 沿柱子打伤害 ✓ —— 敌人**每条光线只挨一次** ✓（否则贴脸会被打几十下 ✗）。 */
    private void damageAlong() {
        Vec3 from = this.position();
        Vec3 to = this.endPoint();
        AABB box = new AABB(from, to).inflate(this.radius() + 0.6D);
        LivingEntity owner = null;
        if (this.level() instanceof ServerLevel server) {
            int id = this.entityData.get(DATA_CASTER);
            if (id >= 0 && server.getEntity(id) instanceof LivingEntity living) {
                owner = living;
            }
        }
        if (owner == null) {
            return;
        }
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner || !target.isAlive()) {
                continue;
            }
            if (owner instanceof ServerPlayer caster && !TnSpellMechanics.isEnemy(caster, target)) {
                continue;                        // 村民/动物/剧情 NPC 不打 ✗
            }
            if (!this.hit.add(target.getUUID())) {
                continue;
            }
            target.hurt(this.level().damageSources().indirectMagic(owner, owner), this.damage);
        }
    }

    /** 一点火花：起手处 + 末端 ✓（主体是几何 ✓，这里只是点缀 ✓）。 */
    private void sparkle() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 to = this.endPoint();
        server.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z,
                6, this.radius() * 0.4D, this.radius() * 0.4D, this.radius() * 0.4D, 0.02D);
        Vec3 from = this.position();
        server.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 纯表现实体，不存档 ✓
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 同上
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 65536.0D;           // 光柱很长，别被早早剔掉 ✓
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // ★ 实体自己只有 0.1 格，但画出来是几十格长的柱子 ✗ ⇒ 剔除盒必须撑到整根柱子 ✓
        return new AABB(this.position(), this.endPoint()).inflate(this.radius() + 1.0D);
    }
}
