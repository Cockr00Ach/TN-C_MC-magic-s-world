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

    /** 每档的渲染缩放 ✓（模型本体 1.875 格宽 ⇒ t3/t4/t5 ≈ 1.9/2.4/3.0 格）。 */
    public static final double[] SIZE_SCALE = { 1.0D, 1.3D, 1.6D };

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
        double phase = (age / (double) FIRE_INTERVAL) * 0.7D;   // 每轮错开一点 ✓

        for (int i = 0; i < count; i++) {
            double angle = phase + (Math.PI * 2.0D / count) * i;
            Vec3 dir = new Vec3(Math.cos(angle), Math.tan(BLADE_PITCH), Math.sin(angle)).normalize();
            TNWindBladeEntity blade = TNWindBladeEntity.shoot(
                    (ServerLevel) level(), position().add(0.0D, 0.9D, 0.0D), dir, BLADE_SPEED, damage, owner());
            if (blade != null) {
                // 让风刃自己转（视觉：斩击在飞的时候是转的 ✓）
                blade.setSpinRate((float) (18.0D + random.nextDouble() * 10.0D));
            }
        }

        // 甩刃的一瞬间来一圈风（视觉反馈 ✓）
        ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                getX(), getY() + 0.9D, getZ(), 4, 0.8D, 0.2D, 0.8D, 0.0D);
    }

    /**
     * 把范围内的敌人往风眼拽一点 ＋ 少量伤害 ✓（"被龙卷风卷进去"的读感）。
     *
     * <p>拽的速度**越近越弱**（{@code 1 − dist/radius}）✓：贴着风眼的人不会被反复猛拽 ✗，
     * 边缘的人反而被"吸"得最明显 ✓ —— 这样读起来才像漩涡，而不是磁铁 ✗。
     */
    private void suckIn(int tier) {
        LivingEntity owner = owner();
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - SUCK_RADIUS, getY() - 2.0D, getZ() - SUCK_RADIUS,
                        getX() + SUCK_RADIUS, getY() + 4.0D, getZ() + SUCK_RADIUS),
                e -> e.isAlive() && e != owner && !(e instanceof Player));
        for (LivingEntity target : targets) {
            Vec3 pull = position().subtract(target.position());
            double dist = pull.length();
            if (dist < 0.01D) {
                continue;
            }
            double strength = 0.32D * (1.0D - Math.min(1.0D, dist / SUCK_RADIUS));
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

    /** 渲染缩放 ✓（渲染器读它，和"甩刃范围"同一档 ✓）。 */
    public double sizeScale() {
        return SIZE_SCALE[tier() - 3];
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

    @Override
    public boolean isPushable() {
        return false;
    }
}