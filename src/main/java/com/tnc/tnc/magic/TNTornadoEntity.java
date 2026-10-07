package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * ★ <b>龙卷风</b>（风系第 3 条链「范围风」的 t3+ 形态，2026-10-10）✓
 *
 * <p>作者原话：「风刃这样吧，你做个龙卷风的模型，<b>t3 开始释放风刃会召唤出一个一直在旋转的
 * 龙卷风，然后持续向外发射风刃</b>」✓
 *
 * <p>所以这是一个**留在原地**的实体（不是投射物 ✗）：法术 t3+ 改成 {@code SELF} 施法，
 * Java 在施法者身前召唤它，它自己负责：
 * <ol>
 *   <li><b>一直转</b> ✓（{@link #spin()} 每 tick 加 9°，渲染器按它绕 Y 转模型）；</li>
 *   <li><b>持续向外甩风刃</b> ✓（每 {@link #FIRE_INTERVAL} tick 朝
 *       {@link #BLADES_PER_BURST} 个均匀分布的方向各生成一发 {@link TNWindBladeEntity}）；</li>
 *   <li><b>卷人</b> ✓（每 {@link #SUCK_INTERVAL} tick 把范围内敌人往风眼拽一点 ＋ 少量伤害）；</li>
 *   <li>到点自己散掉 ✓（{@link #LIFETIME_SECONDS}，t3/t4/t5 = 8/12/16 秒）。</li>
 * </ol>
 *
 * <h2>为什么是"服务端说了算"的实体 ✓</h2>
 * 位置完全由服务端算（生成点固定、不移动 ✗），客户端只负责画 —— 和
 * {@link TNWindSpiritEntity} / {@link TNThunderOrbEntity} 同一套口径 ✓，
 * 不用 AI、不吃重力、不寻路 ⇒ 表现可预期，也不会被地形卡住 ✗。
 *
 * <h2>档位</h2>
 * 档号在生成时定死（{@link #tier()}），并决定：寿命、甩刃频率、甩几发、卷人范围 ✓
 * —— 也就是"t3 一个龙卷风，t5 又大又久又密" ✓。
 */
public class TNTornadoEntity extends Entity {

    // ---------------- 同步数据（客户端渲染要用的） ----------------

    /** 档号 3..5 ✓（渲染缩放/粒子密度看它）。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNTornadoEntity.class, EntityDataSerializers.INT);

    /** 当前转角（度，0..360）✓ —— 渲染器按它绕 Y 转模型，**转起来**就是"一直在旋转" ✓。 */
    private static final EntityDataAccessor<Float> DATA_SPIN =
            SynchedEntityData.defineId(TNTornadoEntity.class, EntityDataSerializers.FLOAT);

    // ---------------- 数值表（改手感只改这里） ----------------

    /** 每档的寿命（秒）—— 索引 = tier − 3 ✓。 */
    public static final int[] LIFETIME_SECONDS = { 8, 12, 16 };

    /** 每档每次甩几发风刃 ✓。 */
    public static final int[] BLADES_PER_BURST = { 3, 5, 7 };

    /**
     * 每档龙卷风的渲染缩放 ✓ —— ★ 作者 2026-10-10 逐档表：t3 <b>2 倍</b> / t4 <b>5 倍</b> /
     * t5 <b>8 倍</b> ✓（索引 = tier − 3）。
     *
     * <p>口径和黑夜之手那条链一致 ✓：<b>作者说的"倍"就是倍率本身</b> ⇒ 模型本体
     * 1.875 格宽 ⇒ 实际约 <b>3.75 / 9.4 / 15 格</b> 宽 ✓。
     */
    public static final double[] SIZE_MULTIPLIER = { 2.0D, 5.0D, 8.0D };

    /**
     * 每档甩出去的风刃**尺寸倍率** ✓ —— 作者逐档表：t3 <b>5</b> / t4 <b>10</b> / t5 <b>20</b> 倍 ✓。
     *
     * <p>⚠️ 和 {@link #SIZE_MULTIPLIER} 是**两张表** ✗：龙卷风 2/5/8、它吐的刃 5/10/20 ✓
     * —— 别合并成一个数（作者是分开给的 ✓）。
     */
    public static final double[] BLADE_SIZE_MULTIPLIER = { 5.0D, 10.0D, 20.0D };

    /** 每档召唤几个龙卷风 ✓ —— 只有 t5 是 2 个（作者："t5……并且生成两个龙卷风" ✓）。 */
    public static final int[] TORNADO_COUNT = { 1, 1, 2 };

    /**
     * t5 两个龙卷风之间的**间距**（格）✓ —— 作者："间距要大一些" ✓。
     *
     * <p>为什么给到 {@value #PAIR_SEPARATION}：t5 的龙卷风是 <b>8 倍</b>、宽约 15 格 ✗
     * ⇒ 间距小于 15 会**互相穿模**、看着是"一坨" ✗。取 24 格（约 1.6 个直径）
     * ⇒ 两个漩涡各自清楚、中间留得出空隙 ✓。
     */
    public static final double PAIR_SEPARATION = 24.0D;

    /** 每档风刃的单发伤害 ✓。 */
    public static final float[] BLADE_DAMAGE = { 5.0F, 6.0F, 7.0F };

    /** 每隔几 tick 甩一轮风刃 ✓。 */
    private static final int FIRE_INTERVAL = 14;

    /** 每隔几 tick 卷一次人 ✓。 */
    private static final int SUCK_INTERVAL = 10;

    /** 风刃飞出去的速度（格/tick）✓。 */
    private static final double BLADE_SPEED = 0.85D;

    /** 卷人的半径 ✓。 */
    private static final double SUCK_RADIUS = 4.5D;

    /** 每 tick 转角（度）✓ —— 9°/tick ⇒ 一圈 40 tick（2 秒），看得清是"在转" ✓。 */
    private static final float SPIN_PER_TICK = 9.0F;

    /** 甩刃的起始仰角（略微向上，弧线好看一点）✓。 */
    private static final double BLADE_PITCH = -0.12D;

    /** 施法者（打人不打自己人 ✓）。 */
    private UUID ownerId;

    public TNTornadoEntity(EntityType<? extends TNTornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;              // 穿墙/穿地形，不会被卡住 ✗
        this.noCulling = true;              // 龙卷风很大，别被视锥剔除掉 ✗
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TIER, 3);
        this.entityData.define(DATA_SPIN, 0.0F);
    }

    // ---------------- 生成 ----------------

    /**
     * 召唤一个龙卷风 ✓。
     *
     * @param owner 施法者（可以 null —— 那就谁都不认，只卷怪 ✓）
     * @param tier  档号（会被夹到 3..5 ✓）
     */
    public static TNTornadoEntity summon(ServerLevel level, Vec3 pos, LivingEntity owner, int tier) {
        if (TNOrbEntities.WIND_TORNADO == null || !TNOrbEntities.WIND_TORNADO.isPresent()) {
            return null;
        }
        TNTornadoEntity tornado = TNOrbEntities.WIND_TORNADO.get().create(level);
        if (tornado == null) {
            return null;
        }
        tornado.setTier(tier);
        tornado.ownerId = owner == null ? null : owner.getUUID();
        tornado.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(tornado);
        return tornado;
    }

    // ---------------- 每 tick ----------------

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            // 客户端：只推进转角 ✓（服务端也会同步一份，这里插值让旋转平滑 ✓）
            setSpinRaw(getSpin() + SPIN_PER_TICK);
            return;
        }

        // ① 一直转 ✓
        setSpinRaw(getSpin() + SPIN_PER_TICK);

        int tier = tier();
        int age = tickCount;

        // ② 到点散掉 ✓
        if (age > LIFETIME_SECONDS[tier - 3] * 20) {
            discard();
            return;
        }

        // ③ 持续向外甩风刃 ✓
        if (age % FIRE_INTERVAL == 0) {
            fireBlades(tier, age);
        }

        // ④ 卷人 ✓
        if (age % SUCK_INTERVAL == 0) {
            suckIn(tier);
        }

        // ⑤ 脚下的风（视觉，服务端发给附近客户端 ✓）
        if (age % 2 == 0) {
            ServerLevel server = (ServerLevel) level();
            double r = 1.5D + 0.4D * (tier - 3);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    getX(), getY() + 0.15D, getZ(), 3, r * 0.4D, 0.05D, r * 0.4D, 0.02D);
        }
    }

    /**
     * 朝一圈均匀方向各甩一发风刃 ✓。
     *
     * <p>为什么要"每一轮错开一个角度"（{@code age / FIRE_INTERVAL} 参与相位）：如果每轮都从
     * 0° 开始，风刃会永远沿着同几个方向飞 ✗，看起来像固定的几根刺；错开之后是**扫射** ✓。
     */
    private void fireBlades(int tier, int age) {
        int count = BLADES_PER_BURST[tier - 3];
        float damage = BLADE_DAMAGE[tier - 3];
        double size = bladeSizeMultiplier();
        double phase = (age / (double) FIRE_INTERVAL) * 0.7D;   // 每轮错开一点 ✓
        double spawnR = spawnRadius();

        for (int i = 0; i < count; i++) {
            double angle = phase + (Math.PI * 2.0D / count) * i;
            Vec3 dir = new Vec3(Math.cos(angle), Math.tan(BLADE_PITCH), Math.sin(angle)).normalize();
            // 出生点挪到**漩涡外壁** ✓：不然大尺寸的刃一出生就在风眼里，
            // 判定半径 5 格 ⇒ 立刻够到被卷进来的怪 ✗（见 TNWindBladeEntity#MAX_HIT_RADIUS）
            Vec3 at = position().add(Math.cos(angle) * spawnR, 0.9D, Math.sin(angle) * spawnR);
            TNWindBladeEntity blade = TNWindBladeEntity.shoot(
                    (ServerLevel) level(), at, dir, BLADE_SPEED, damage, owner(), size);
            if (blade != null) {
                // 让风刃自己转（视觉：斩击在飞的时候是转的 ✓）
                blade.setSpinRate((float) (18.0D + random.nextDouble() * 10.0D));
            }
        }

        // 甩刃的一瞬间来一圈风（视觉反馈 ✓）—— 半径跟着漩涡大小走 ✓
        ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                getX(), getY() + 0.9D, getZ(), 4, spawnR * 0.6D, 0.2D, spawnR * 0.6D, 0.0D);
    }

    /**
     * 风刃的出生半径 ✓ —— 取漩涡的**外壁** ✓。
     *
     * <p>模型本体 1.875 格宽 ⇒ 外壁半径 = {@code 0.9375 × 尺寸倍率} ✓
     * （t3 ≈ 1.9 / t4 ≈ 4.7 / t5 ≈ 7.5 格 ✓）。
     */
    public double spawnRadius() {
        return 0.9375D * sizeScale();
    }

    /**
     * 静态版的外壁半径 ✓ —— 给"还没生成出来"的召唤方用 ✓
     * （{@link TNWindGaleMechanics} 要靠它决定站多远才不会被自己的漩涡吞掉 ✓）。
     */
    public static double spawnRadiusFor(int tier) {
        int index = Mth_clamp(tier, 3, 5) - 3;
        return 0.9375D * SIZE_MULTIPLIER[index];
    }

    /**
     * 把范围内的敌人往风眼拽一点 ＋ 少量伤害 ✓（"被龙卷风卷进去"的读感）。
     *
     * <p>拽的速度**越近越弱**（{@code 1 − dist/radius}）✓：贴着风眼的人不会被反复猛拽 ✗，
     * 边缘的人反而被"吸"得最明显 ✓ —— 这样读起来才像漩涡，而不是磁铁 ✗。
     */
    private void suckIn(int tier) {
        LivingEntity owner = owner();
        // 半径跟着尺寸走 ✓，高度也跟着（大漩涡更高 ✓）
        double radius = suckRadius();
        double halfHeight = 2.0D + 1.5D * (tier - 3);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - radius, getY() - halfHeight, getZ() - radius,
                        getX() + radius, getY() + halfHeight * 2.0D, getZ() + radius),
                e -> e.isAlive() && e != owner && !(e instanceof Player));
        for (LivingEntity target : targets) {
            Vec3 pull = position().subtract(target.position());
            double dist = pull.length();
            if (dist < 0.01D) {
                continue;
            }
            double strength = 0.32D * (1.0D - Math.min(1.0D, dist / radius));
            target.push(pull.x / dist * strength, 0.06D, pull.z / dist * strength);
            target.hurtMarked = true;
            if (tier >= 4) {
                target.hurt(damageSources().indirectMagic(this, owner), 1.5F);
            }
        }
    }

    // ---------------- 取值 ----------------

    /** 档号 3..5 ✓。 */
    public int tier() {
        return Mth_clamp(this.entityData.get(DATA_TIER), 3, 5);
    }

    private void setTier(int tier) {
        this.entityData.set(DATA_TIER, Mth_clamp(tier, 3, 5));
    }

    /** 当前转角（度）✓。 */
    public float spin() {
        return getSpin();
    }

    /** 渲染缩放 ✓（渲染器读它 ✓）。★ 作者逐档表：2/5/8 倍 ✓。 */
    public double sizeScale() {
        return SIZE_MULTIPLIER[tier() - 3];
    }

    /**
     * 它甩出去的风刃该多大 ✓。★ 作者逐档表：5/10/20 倍 ✓。
     *
     * <p>和 {@link #sizeScale()} **不是一个数** ✗ —— 龙卷风 2/5/8、刃 5/10/20 ✓。
     */
    public double bladeSizeMultiplier() {
        return BLADE_SIZE_MULTIPLIER[tier() - 3];
    }

    /**
     * 卷人半径 ✓ —— 跟着**尺寸**放大 ✓。
     *
     * <p>⚠️ 不放大就会很怪 ✗：t5 的龙卷风宽约 15 格，却只卷 4.5 格内的东西 ⇒
     * 站在"漩涡里"的怪反而不被卷 ✗。按 √尺寸 放大（面积直觉 ✓），t5 ≈ 9 格 ✓。
     */
    public double suckRadius() {
        return SUCK_RADIUS * Math.sqrt(sizeScale() / SIZE_MULTIPLIER[0]);
    }

    private float getSpin() {
        return this.entityData.get(DATA_SPIN);
    }

    private void setSpinRaw(float value) {
        float wrapped = value % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        this.entityData.set(DATA_SPIN, wrapped);
    }

    /** 施法者（可能已经下线/死了 ⇒ 返回 null ✓）。 */
    public LivingEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity found = server.getEntity(ownerId);
        return found instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static int Mth_clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    // ---------------- 存盘 ----------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.tickCount = tag.getInt("Age");
        setTier(tag.getInt("Tier"));
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.tickCount);
        tag.putInt("Tier", tier());
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    // ---------------- 不吃伤害 / 不推别人 ----------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /**
     * 视锥剔除用的包围盒：<b>按档位放大</b> ✓。
     *
     * <p>为什么必须给 ✗：t5 的龙卷风宽约 <b>15 格</b>，而本实体的碰撞箱只有 1 格 ⇒
     * 原版按碰撞箱剔除 ⇒ 龙卷风**中心转出屏幕时整根会突然消失** ✗
     * （黑夜之手/天使踩过同一类坑 ✓，见 {@code TNNightGripEntity#getBoundingBoxForCulling}）。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(0.9375D * sizeScale());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // 龙卷风很大 ✗：按碰撞箱的距离剔除会让它"远远地就没了" ⇒ 放到 96 格 ✓
        return distance < 96.0D * 96.0D;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
