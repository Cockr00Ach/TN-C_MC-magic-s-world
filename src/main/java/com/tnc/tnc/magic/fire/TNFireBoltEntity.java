package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 火球链的<b>自有投射物</b>（照 {@code TNWaterBoltEntity} 的结构写）。
 *
 * <h2>为什么不用引擎的 PROJECTILE</h2>
 * 灼烧（{@link TNScorch 焚身}）的每秒伤害 = <b>那一下的实际命中伤害 × 10%</b>，
 * 所以命中时必须知道「是哪个法术、打了多少」。引擎的投射物在 {@code LivingHurtEvent}
 * 里只是一个无名的魔法伤害，拿不到法术 id —— 于是这条链改成自有实体：
 * <b>伤害自己算、命中自己判定、焚身自己挂</b>，归因零误差。
 *
 * <h2>判定方式（照抄水系，别自己发明）</h2>
 * 每 tick 用「线段 × 包围盒」扫掠：先 {@code clip} 撞墙，再对线上所有敌人做盒体裁剪，
 * 取<b>离出发点最近</b>的那个 —— 高速飞行也不会穿人。
 */
public final class TNFireBoltEntity extends Projectile {

    /** 服务端定形、同步给客户端（客户端要靠它画大小）。 */
    private static final EntityDataAccessor<String> SPELL =
            SynchedEntityData.defineId(TNFireBoltEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(TNFireBoltEntity.class, EntityDataSerializers.FLOAT);
    /** 射程（格）—— 同步给客户端，客户端靠它算"该在什么时候开始消失"。 */
    /**
     * ★ 锁定的目标实体 id（-1 = 没锁 ✓）—— 作者 2026-10-05 第 6 条：
     * 「施法时的索敌功能没有实现」✗
     *
     * <p>根因：之前只把**初速**朝向目标 ⇒ 之后直线飞 ✗，看不出在追 ✗
     * ⇒ 现在存住目标 ✓，每 tick 限速拐过去 ✓（客户端也能读到 ⇒ 渲染方向跟着拐 ✓）
     */
    private static final EntityDataAccessor<Integer> TARGET =
            SynchedEntityData.defineId(TNFireBoltEntity.class, EntityDataSerializers.INT);

    /** 每 tick 最多朝目标转几度 ✓（和光柱索敌同一个手感：看得见在拐 ✓）。 */
    private static final double HOMING_DEGREES_PER_TICK = 14.0D;

    private static final EntityDataAccessor<Float> RANGE =
            SynchedEntityData.defineId(TNFireBoltEntity.class, EntityDataSerializers.FLOAT);

    /** 飞过射程的这个比例之后开始逐渐消失（见 {@link #fade}）。 */
    public static final double FADE_START = 0.65D;

    /** 出手速度（格/tick）。作者 2026-10-05：从 1.3 降下来，让焰尾看得清。 */
    public static final double LAUNCH_SPEED = 1.0D;

    /** 这一发的伤害（服务端算好；客户端不用）。 */
    private float damage;
    /** 还能飞多远（格）。 */
    private double remaining = 40.0D;
    /** 命中挂的焚身是不是 II 级。 */
    private boolean heavyScorch;
    /** 命中后是否在落点留下熔岩地（t3 熔岩火球起才有）。 */
    private boolean lavaField;

    /** 「火龙术」（射线链 t3）：穿透 + 命中不爆 + 消失时剧烈爆炸 ✓ */
    private boolean isDragon() {
        return "fire_dragon".equals(spellPath());
    }

    /**
     * 射线链 t1/t2（作者 2026-10-05 第 2 条：「射线是一段长度有限的线条，
     * 你可以理解为是长条状的火球」✓）—— 它们**会飞出去** ✓，外形是一条火焰长条 ✓
     */
    private boolean isRayShot() {
        String path = spellPath();
        return "sun_ray".equals(path) || "blast_ray".equals(path);
    }

    /**
     * <b>会不会穿透生物</b> ✓
     *
     * <p>作者 2026-10-05 的全局要求：「穿透效果只能穿透生物，
     * 不能穿透土块的方块，当无法穿透时攻击提前消失」✓
     * ⇒ 火龙（t3）与两条射线（t1/t2）都穿生物 ✓；撞到方块一律停下 ✓
     */
    private boolean penetrates() {
        return isDragon() || isRayShot();
    }

    /** 火龙已经打过的目标（穿透：每个目标只结算一次 ✓）。 */
    private final java.util.Set<java.util.UUID> dragonHit = new java.util.HashSet<>();

    /** 火龙那一下爆炸只炸一次（撞墙 / 到期 都走它 ✓）。 */
    private boolean dragonExploded;

    /**
     * 火龙术：<b>消失时那一下剧烈爆炸</b> ✓
     *
     * <p>作者 2026-10-05：「当火龙消失时发生一次剧烈爆炸，爆炸会有较为强烈的视角震动，
     * 该次爆炸伤害是该法术伤害 × 3」✓
     */
    private void dragonExplode(net.minecraft.server.level.ServerLevel server) {
        if (dragonExploded) {
            return;
        }
        dragonExploded = true;
        Vec3 at = position();
        float blast = damage * FireSpellRules.DRAGON_EXPLODE_MULTIPLIER;
        double radius = FireSpellRules.DRAGON_EXPLODE_RADIUS;
        LivingEntity caster = getOwner() instanceof LivingEntity living ? living : null;
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                FireSpellRules.uprightArea(at, radius, 3.0D),
                t -> FireSpellRules.hittable(caster, t))) {
            victim.invulnerableTime = 0;
            victim.hurt(server.damageSources().indirectMagic(this, getOwner()), blast);
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 120, radius * 0.5D, radius * 0.4D, radius * 0.5D, 0.35D);
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 40, radius * 0.4D, 0.3D, radius * 0.4D, 0.2D);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0D, at.z, 30, radius * 0.4D, 0.6D, radius * 0.4D, 0.1D);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 3.0F, 1.0F);
        TNShockwaveEntity.blast(server, at, 3.0D + FireSpellRules.SHAKE_T4 * 0.6D,
                FireSpellRules.SHAKE_T4, getOwner(), 0.0F);
    }
    /** 命中后的爆炸半径（0 = 不爆炸；t4 熔岳天倾的那些火球才有）。 */
    private double blastRadius;
    /**
     * 命中后<b>再按目标最大生命</b>扣掉的比例（0 = 不扣）。
     *
     * <p>目前只有<b>陨星坠</b>（t5）用：作者 2026-10-05 要求「造成 10% 的最大生命值伤害」。
     */
    private float maxHealthPercent;

    /**
     * 距离衰减 <b>1 → 0</b>：飞过射程的 {@link #FADE_START} 之后一路缩到看不见。
     *
     * <p>作者 2026-10-05 要求「当火球离开一定距离后会逐渐消失」——
     * 之前是 {@code remaining <= 0} 时直接 {@code discard()}，火球会"啪"地凭空不见 ✗。
     *
     * <p>怎么算"飞了多远"：射程是同步字段（{@link #RANGE}），
     * 每 tick 的位移长度从同步的速度拿（{@code getDeltaMovement()}），
     * 两者相乘即已飞距离 —— 所以<b>不需要每 tick 同步剩余距离</b>，省带宽。
     *
     * @return 1.0 = 还是完整的球；0.0 = 已经该看不见了
     */
    public float fade(float partial) {
        double speed = getDeltaMovement().length();
        double range = range();
        if (speed <= 0.01D || range <= 0.01D) {
            return 1.0F;                      // 还没出手（成形阶段）→ 不衰减
        }
        // ⚠️ 射线链 t1/t2：**整段射程都保持满亮度** ✗ ——
        //    原来从 65% 射程起就开始变淡 ✗，8 格射程里 5.2 格就淡了 ⇒
        //    看起来就像"射线飞到一半没了"✗（作者报"没有穿透"很可能有这个成分 ✓）
        if (isRayShot()) {
            return 1.0F;
        }
        double progress = (tickCount + partial) * speed / range;
        if (progress <= FADE_START) {
            return 1.0F;
        }
        double k = (progress - FADE_START) / (1.0D - FADE_START);
        return (float) Math.max(0.0D, 1.0D - k);
    }

    /** 这一发的射程（同步字段）。 */
    public double range() {
        return entityData.get(RANGE);
    }

    /**
     * <b>成形阶段</b>的长度（tick）—— 作者 2026-10-05 要求「先形成一个球形再发射出去」。
     *
     * <p>这 5 tick（0.25 秒）里火球<b>停在手前不动、也不判定命中</b>，
     * 渲染器把它从 25% 大小长到 100%（见 {@link #formProgress}），
     * 长满的那一帧喷一次火星表示"射出去了"，然后才开始飞。
     *
     * <p>为什么不靠法术 JSON 的 {@code cast.particles} 做这件事：那只能摆粒子，
     * 摆不出"一颗球"；而且引擎的粒子形状/原点语义没有文档，猜错了就是在脚下冒烟 ✗
     */
    public static final int FORM_TICKS = 3;

    /** 火龙术的成形时长（tick）—— 法阵由外到里描绘 + 龙头渐渐凝出来 ✓（作者要"逐渐出现"✗）。 */
    public static final int DRAGON_FORM_TICKS = 18;

    /**
     * 出手缓冲：离开手之后的这几 tick 里，位移从 {@link #LAUNCH_START_FRACTION} 涨到全速。
     *
     * <p>⚠️ 作者 2026-10-05 实测「火球术、大火球术和熔岩火球的产生和发射过程不流畅」——
     * 原因是原来<b>一离手就走满 1 格/tick（= 20 格/秒）</b>：
     * 球先在手里悬停 5 tick 不动，然后"啪"地一下弹到满速，看着就是一顿 ✗
     * 现在给它一段加速，读起来才像"被推出去" ✓
     *
     * <p>⚠️ 只对手抛的火球生效；<b>天上掉下来的陨石不加速</b>
     * （自由落体越落越快才合理，减速反而怪 ✗）—— 见 {@code meteorLike()}
     */
    public static final int LAUNCH_RAMP_TICKS = 4;
    /** 刚离手时走全速的几成。0.30 = 第一 tick 只走 30%，第 4 tick 起满速。 */
    public static final double LAUNCH_START_FRACTION = 0.30D;

    public TNFireBoltEntity(EntityType<? extends TNFireBoltEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** 由法术入口调用：定好法术、伤害、初速。 */
    public void configure(LivingEntity owner, FireSpellRules.Bolt bolt, String spellPath,
                          Vec3 at, Vec3 velocity) {
        setOwner(owner);
        entityData.set(SPELL, spellPath);
        entityData.set(RADIUS, bolt.radius());
        entityData.set(RANGE, bolt.range());
        setPos(at);
        setDeltaMovement(velocity);
        damage = FireSpellRules.damage(bolt, FireSpellRules.power(owner));
        heavyScorch = bolt.heavyScorch();
        lavaField = bolt.lavaField();
        blastRadius = bolt.blastRadius();
        maxHealthPercent = bolt.maxHealthPercent();
        remaining = bolt.range();
    }

    /** 锁定一个目标 ⇒ 这一发之后会**拐弯追它** ✓。 */
    public void lockTarget(LivingEntity target) {
        entityData.set(TARGET, target == null ? -1 : target.getId());
    }

    /** 当前锁定的目标（没有/已消失 = null ✓）。 */
    public LivingEntity targetEntity() {
        int id = entityData.get(TARGET);
        if (id < 0 || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return null;
        }
        return server.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    /**
     * 把 {@code dir} 朝 {@code want} 转最多 {@code maxDegrees} 度 ✓（返回单位方向 ✓）。
     *
     * <p>不用四元数/旋转矩阵：这两个向量都很短，线性插值再归一化在 14 度这种小角度下
     * 和真正的球面插值肉眼无差 ✓，而且没有三角函数以外的开销 ✓
     */
    private static Vec3 turnToward(Vec3 dir, Vec3 want, double maxDegrees) {
        Vec3 d = dir.normalize();
        double dot = Math.max(-1.0D, Math.min(1.0D, d.dot(want)));
        double angle = Math.acos(dot);
        if (angle < 1.0E-5D) {
            return want;
        }
        double max = Math.toRadians(maxDegrees);
        if (angle <= max) {
            return want;
        }
        double t = max / angle;
        Vec3 mixed = d.scale(1.0D - t).add(want.scale(t));
        return mixed.lengthSqr() < 1.0E-6D ? want : mixed.normalize();
    }

    public String spellPath() {
        return entityData.get(SPELL);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    /**
     * 由 {@code TnSpellMechanics.onSpellCast} 转调：这一发是火球链的就放出去。
     *
     * <p>和 {@code TNWaterBoltEntity} / {@code TNWaterFieldEntity} 的 {@code cast} 同一个约定：
     * <b>不是本链的法术就返回 false 让别的链去处理</b>，所以派发处可以无脑全调一遍。
     *
     * @return 真的放出去了才 true
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (player == null || spellId == null || !spellId.getNamespace().equals(TNMod.MODID)) {
            return false;
        }
        FireSpellRules.Bolt bolt = FireSpellRules.bolt(spellId.getPath());
        if (bolt == null) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = FireSpellRules.muzzle(player, 0.6D);
        Vec3 right = FireSpellRules.right(look);
        for (int i = 0; i < bolt.launches(); i++) {
            // 连珠：横向错开一点，免得三发叠成一根。单发时 spread = 0，行为不变
            double spread = bolt.launches() > 1
                    ? (i - (bolt.launches() - 1) / 2.0D) * 0.055D
                    : 0.0D;
            Vec3 velocity = look.scale(LAUNCH_SPEED).add(right.scale(spread));
            TNFireBoltEntity boltEntity = new TNFireBoltEntity(
                    com.tnc.tnc.magic.TNOrbEntities.FIRE_BOLT.get(), player.level());
            boltEntity.configure(player, bolt, spellId.getPath(), muzzle, velocity);
            player.level().addFreshEntity(boltEntity);
        }
        return true;
    }

    /** 刚出生那一帧到长满之前都还没有速度，别让 normalize 炸。 */
    public Vec3 safeDirection() {
        Vec3 motion = getDeltaMovement();
        return motion.lengthSqr() < 0.01D ? new Vec3(0.0D, 0.0D, 1.0D) : motion.normalize();
    }

    /**
     * 这一档的<b>成形时长</b>（tick）。
     *
     * <p>手扔的火球要"先在手里凝成一颗球再射出去"，所以有 {@link #FORM_TICKS} 的悬停；
     * 但<b>陨星坠的陨石不能有</b> —— 它是从天而降的，悬在天上不动 6 tick 会明显顿一下 ✗
     * （作者 2026-10-05 反馈「陨石的下落不流畅」）
     */
    private int formTicks() {
        if (skyFall()) {
            return 0;
        }
        // 火龙术：要"在面前渐渐生成法阵 + 龙头成形"，3 tick 太短 ✗ ⇒ 给足 18 tick（0.9 秒）✓
        return isDragon() ? DRAGON_FORM_TICKS : FORM_TICKS;
    }

    /**
     * 「从天上砸下来」的那些（t4 熔岳天倾的火球 + t5 的陨石/伴随陨石）。
     *
     * <p>它们<b>不是从手里推出去</b>的 ✗ ⇒ 既不做成形悬停、也不加出手缓冲：
     * <ul>
     *   <li>悬停 3 tick 会看着"顿一下" ✗</li>
     *   <li>出手缓冲会让下落**先慢后快** ✗ —— 作者 2026-10-05 说「下落流畅点」，
     *       落到一半突然加速正是"不流畅" ✗</li>
     * </ul>
     * ⇒ 它们一律<b>匀速直接砸</b> ✓（只有手扔的火球保留"凝聚 + 推出去"那套 ✓）
     */
    private boolean skyFall() {
        String path = spellPath();
        return meteorLike() || "molten_skyfall".equals(path);
    }

    /** 陨石类（大陨石 + 装饰小陨石）—— 拖尾、跳过成形、岩块外观都按这个判。 */
    private boolean meteorLike() {
        String path = spellPath();
        return "meteor_fall".equals(path) || "meteor_shard".equals(path);
    }

    /**
     * 装饰小陨石的初始化：和普通火球一样，但<b>尺寸按比例缩放</b>。
     *
     * <p>作者 2026-10-05 要求跟随的陨石「大小不一」—— 所以尺寸得逐个给，
     * 不能都在 {@link FireSpellRules.Bolt} 里写死一个值 ✓
     */
    public void configureShard(LivingEntity owner, FireSpellRules.Bolt bolt, String spellPath,
                               Vec3 at, Vec3 velocity, float scale) {
        configure(owner, bolt, spellPath, at, velocity);
        entityData.set(RADIUS, (float) (bolt.radius() * scale));
    }

    /**
     * 成形进度 0.25 → 1.0（渲染器用）—— 决定这颗球现在多大。
     *
     * <p>起点给 0.25 而不是 0：一出生就有一小团在手里，看着才像"凝聚"而不是"凭空出现"。
     */
    public float formProgress(float partial) {
        int form = formTicks();
        if (form <= 0) {
            return 1.0F;                       // 不做成形阶段（陨石）→ 一出生就是满大小
        }
        double t = (tickCount + partial) / (double) form;
        return t >= 1.0D ? 1.0F : (float) Math.max(0.25D, t);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SPELL, "");
        entityData.define(RADIUS, 0.35F);
        entityData.define(RANGE, 40.0F);
        entityData.define(TARGET, -1);
    }

    @Override
    public void tick() {
        super.tick();

        // ---------------- 成形阶段：先在手前凝成一颗球，再射出去 ----------------
        // 这 5 tick 里**不移动、也不判定命中**（火球还在手里），渲染器负责让它长大
        if (tickCount <= formTicks()) {
            if (level().isClientSide) {
                // 往中心收拢的几粒火星 —— "凝聚"的感觉
                if (tickCount % 2 == 0) {
                    double r = radius() * (1.5D - formProgress(0.0F));
                    for (int i = 0; i < 3; i++) {
                        double a = tickCount * 0.9D + i * Math.PI * 2.0D / 3.0D;
                        level().addParticle(ParticleTypes.SMALL_FLAME,
                                getX() + Math.cos(a) * r, getY() + Math.sin(a) * r * 0.6D,
                                getZ() + Math.sin(a) * r,
                                -Math.cos(a) * 0.04D, 0.01D, -Math.sin(a) * 0.04D);
                    }
                }
            } else {
                // ⚠️ 成形期间**别完全钉死**：原来这里一点不挪，球就在手里悬着，
                //    然后突然满速弹出去 —— 作者说的"产生过程不流畅"就有这一半 ✗
                //    现在让它一边长大一边**微微向前漂**（12% 速度），读起来像"正在被推出去" ✓
                if (formTicks() > 0) {
                    setPos(position().add(getDeltaMovement().scale(0.12D)));
                }
                // 长满的那一帧喷一次火星：给"射出去了"一个明确的瞬间。
                // ⚠️ 这里原来是 `else if (tickCount == FORM_TICKS)` ——
                //    被上面的 `tickCount <= formTicks()` 整个吃掉了，**从来没触发过** ✗
                if (tickCount == formTicks()) {
                    ((ServerLevel) level()).sendParticles(ParticleTypes.FLAME,
                            getX(), getY(), getZ(), 18, 0.14D, 0.14D, 0.14D, 0.10D);
                }
            }
            return;
        }

        // ---------------- 客户端：只负责好看 ----------------
        if (level().isClientSide) {
            // ⚠️ 粒子只做"余烬点缀" —— 火球的形状由 TNFireBoltRenderer 的几何体负责。
            //    作者 2026-10-05 明确指出"不要一个球外面一些火焰粒子"，
            //    所以这里从"每 2 tick 撒 4 颗 + 一颗 LAVA"减到"每 3 tick 在尾后留一颗小火星"，
            //    并且**删掉了 LAVA 粒子**（它是一坨，看着像旁边多长了一颗小球 ✗）
            if (tickCount % 3 == 0 && getDeltaMovement().lengthSqr() > 0.01D) {
                Vec3 tail = getDeltaMovement().normalize().scale(-radius() * 3.0D);
                level().addParticle(ParticleTypes.SMALL_FLAME,
                        getX() + tail.x + (random.nextDouble() - 0.5D) * radius(),
                        getY() + tail.y + (random.nextDouble() - 0.5D) * radius(),
                        getZ() + tail.z + (random.nextDouble() - 0.5D) * radius(),
                        -getDeltaMovement().x * 0.02D, 0.015D, -getDeltaMovement().z * 0.02D);
            }
            // ⚠️ **客户端绝对不能再自己挪实体**（作者 2026-10-05 实测「陨石的下落不流畅」）。
            //
            //    抛出物的位置由**服务端**算好、每 tick 同步过来
            //    （见 TNOrbEntities.FIRE_BOLT 的 updateInterval(1)），客户端只负责插值。
            //    这里原来还有一句 setPos(position().add(getDeltaMovement()))，
            //    等于两边各挪一次 —— 客户端的外推和服务端的同步互相打架，
            //    看上去就是**一顿一顿 / 来回弹** ✗
            return;
        }

        // ---------------- 服务端：有效性 ----------------
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || tickCount > 100 || remaining <= 0
                || owner instanceof net.minecraft.world.entity.player.Player p
                        && DownedCombat.isDowned(p)
                || owner.level() != level()) {
            // ⚠️ 火龙术：**飞完射程消失时的那一下剧烈爆炸**就挂在这儿 ✓
            //    （这是它唯一的爆炸 —— 命中生物时是不爆的 ✓）
            if (isDragon() && level() instanceof net.minecraft.server.level.ServerLevel sl) {
                dragonExplode(sl);
            }
            discard();
            return;
        }

        ServerLevel server = (ServerLevel) level();
        Vec3 from = position();
        Vec3 motion = getDeltaMovement();
        // ⚠️ 作者 2026-10-05：「把锥形追踪删了」✓
        //    原因（实测）：射线锁住第一个目标后会**一直往它拐**✗，
        //    于是永远对不上第二个目标 ⇒ 表现就是"只有第一个僵尸受伤"✗
        //    ⇒ 现在射线**走直线** ✓：出手方向 = 准心方向（由 TNFireRays 定 ✓），
        //      之后不再拐弯 ⇒ 谁在线上谁挨打 ✓，穿透才是干净的 ✓
        // 出手缓冲：刚离手那几 tick 只走一部分，LAUNCH_RAMP_TICKS 内涨到全速。
        // ⚠️ 只改**实际走了多远**，不改 getDeltaMovement() ——
        //    改速度会每 tick 触发一次速度同步包，反而更卡 ✗
        Vec3 step = motion;
        if (!skyFall()) {
            double ramp = Math.min(1.0D,
                    (tickCount - formTicks()) / (double) LAUNCH_RAMP_TICKS);
            step = motion.scale(LAUNCH_START_FRACTION + (1.0D - LAUNCH_START_FRACTION) * ramp);
        }
        Vec3 to = from.add(step);

        // 陨星坠的陨石：身周冒火 + **身后铺一条拖尾**
        // （作者 2026-10-05：「我没有看到陨石」→ 先加粒子；又要求「下落过程可以带点拖尾」→ 再铺长）
        if (meteorLike() && motion.lengthSqr() > 0.01D) {
            float scale = Math.max(0.35F, radius() / 2.0F);   // 越大拖得越粗
            server.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z,
                    (int) (10.0F * scale) + 4, 0.55D * scale, 0.55D * scale, 0.55D * scale, 0.03D);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z,
                    (int) (4.0F * scale) + 2, 0.7D * scale, 0.7D * scale, 0.7D * scale, 0.01D);
            // 身后 5 段，越远越少 —— 连成一条看得见的火尾
            Vec3 back = motion.normalize().scale(-1.0D);
            for (int i = 1; i <= 5; i++) {
                Vec3 tail = from.add(back.scale(i * 0.85D));
                server.sendParticles(ParticleTypes.FLAME, tail.x, tail.y, tail.z,
                        Math.max(1, 5 - i), 0.4D * scale, 0.4D * scale, 0.4D * scale, 0.02D);
                if (i <= 3) {
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, tail.x, tail.y, tail.z,
                            2, 0.5D * scale, 0.5D * scale, 0.5D * scale, 0.01D);
                }
            }
        }
        if (!server.hasChunkAt(BlockPos.containing(to))) {
            discard();
            return;
        }

        // 先看会不会撞墙
        BlockHitResult wall = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 limit = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();

        // 再在线段上找最近的敌人（盒体裁剪，别用球心距离）
        LivingEntity hit = null;
        Vec3 impact = limit;
        double closest = from.distanceToSqr(limit) + 1e-7D;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(0.4D), t -> FireSpellRules.hittable(owner, t))) {
            AABB box = target.getBoundingBox().inflate(0.15D);
            Optional<Vec3> point = box.contains(from) ? Optional.of(from) : box.clip(from, limit);
            if (point.isPresent() && point.get().distanceToSqr(from) < closest) {
                closest = point.get().distanceToSqr(from);
                hit = target;
                impact = point.get();
            }
        }

        // 火龙术：这次扫到的目标如果**已经打过**，就当没打中 ✓
        //   ⇒ 于是下面的 if 不成立（只要没撞墙）⇒ 它**继续往前飞** = 穿透 ✓
        // ★ 射线链 t1/t2：**这一 tick 扫描框里的所有生物都打一遍** ✓
        //   作者 2026-10-05 实测：「并排贴着才算出 2，隔开就只算 1」✗
        //   根因：下面那段"最近的一个"逻辑**每 tick 只留一个目标** ✗ ——
        //       两只贴着时，第二只还能在下一 tick 的框里补上 ✓；
        //       中间隔开时，射线穿过第一只之后第二只**永远进不了同一 tick 的框** ✗ ⇒ 漏掉 ✗
        //   ⇒ 射线单独走这条"全部命中"的路 ✓（这才是穿透 ✓）
        if (isRayShot()) {
            for (LivingEntity pierced : server.getEntitiesOfClass(LivingEntity.class,
                    new AABB(from, to).inflate(0.4D), e -> FireSpellRules.hittable(owner, e))) {
                if (!dragonHit.add(pierced.getUUID())) {
                    continue;                       // 这个目标已经挨过这一发了 ✓
                }
                boolean piercedHurt = pierced.hurt(
                        server.damageSources().indirectMagic(this, owner), damage);
                if (piercedHurt && damage > 0.0F && owner instanceof ServerPlayer pCaster) {
                    TNScorch.apply(pierced, pCaster, damage, heavyScorch);
                }
                // 穿过去时留一小撮火花（不炸开 ✗，否则看着像"打到就停"✗）
                server.sendParticles(ParticleTypes.FLAME,
                        pierced.getX(), pierced.getY() + pierced.getBbHeight() * 0.5D, pierced.getZ(),
                        3, 0.15D, 0.15D, 0.15D, 0.02D);
                if (owner instanceof ServerPlayer dbg) {
                    dbg.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§e[射线调试] 穿透命中 " + dragonHit.size() + " 个"
                                    + " path=" + spellPath() + " 撞墙=" + (wall.getType() != HitResult.Type.MISS)
                                    + " 剩程=" + (int) remaining), false);
                }
            }
            hit = null;                             // 单目标那段对射线不再生效 ✓
        } else if (penetrates() && hit != null && !dragonHit.add(hit.getUUID())) {
            hit = null;
        }
        if (hit != null || wall.getType() != HitResult.Type.MISS) {
            if (hit != null) {
                // ⚠️ 临时调试（作者报"射线没有穿透"✗）—— 作者放一次，看聊天栏这行就知道
                //    path 对不对 / penetrates 是不是 true / 已经命中过几个 ✓
                //    ⚠️ 定位完就删掉这一小段 ✗
                if (owner instanceof ServerPlayer dbg) {
                    dbg.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§e[射线调试] path=" + spellPath()
                                    + " 穿透=" + penetrates()
                                    + " 已命中=" + dragonHit.size()
                                    + " 撞墙=" + (wall.getType() != HitResult.Type.MISS)
                                    + " 剩程=" + (int) remaining), false);
                }
                boolean hurt = hit.hurt(server.damageSources().indirectMagic(this, owner), damage);
                // 焚身：命中就挂（基数 = 这一发实际打出的伤害）。
                // 已经挂了同级或更高的目标由 TNScorch 自己判「跳过」——这里不用管"不刷新"
                // ⚠️ `damage > 0` 这道门是给**装饰小陨石**留的：它的伤害是 0，
                //    不拦的话会"挂上焚身但每秒掉 0 血"—— 看着就是白白多了个图标 ✗
                if (hurt && damage > 0.0F && owner instanceof ServerPlayer caster) {
                    TNScorch.apply(hit, caster, damage, heavyScorch);
                }
                // 陨星坠：直击之后**再按目标最大生命扣一次**（作者 2026-10-05 要求 10%）。
                // ⚠️ 一定要先把无敌帧清掉 —— 上面那次直击刚把它设成 20，
                //    不清的话这一下会被原版的无敌帧整个吞掉（和灼烧跳伤同一个坑）
                if (maxHealthPercent > 0.0F) {
                    hit.invulnerableTime = 0;
                    hit.hurt(server.damageSources().indirectMagic(this, owner),
                            hit.getMaxHealth() * maxHealthPercent);
                }
            }
            // ⚠️ 射线链 t1/t2：**命中生物时不炸粒子** ✗
            //    原来这里是一团爆开的火焰粒子（"不要啪地消失"的本意 ✓），
            //    但射线是**穿透**的 —— 打中第一个目标就爆一团，看起来就像"打到就没了"✗
            //    ⇒ 射线改成只留一小撮火花，真正的爆开留给撞墙那一下 ✓
            if (isRayShot()) {
                server.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 3,
                        0.15D, 0.15D, 0.15D, 0.02D);
            } else {
                server.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 14, 0.25D, 0.25D, 0.25D, 0.06D);
                // 命中时**炸开**成一团火焰粒子（作者 2026-10-05 要求：不要"啪"地直接消失）。
                // ⚠️ 纯视觉、**零伤害** —— 真正的爆炸伤害在上面 blastRadius 那一段，两者互不影响
                spawnImpactBurst(server, impact);
            }
            // 陨星坠：命中时来一场**大范围**的火焰扩散
            // （作者 2026-10-05：「爆开后会产生大量火焰粒子向四周扩散，场景壮观点」）
            if ("meteor_fall".equals(spellPath())) {
                meteorImpactBurst(server, impact);
            }
            // 声音 + 震屏（作者 2026-10-05）：t3 响亮爆炸+轻微震动 / t4 巨大爆炸+轰鸣+较强烈 /
            // t5 巨大爆炸+轰鸣+强烈 —— 逐档加强，不会出现低档比高档猛 ✗
            boom(server, impact);
            // 熔岩地：在落点**下方**留一块持续灼烧的地面（t3 熔岩火球起才有）。
            // 它的每秒伤害和焚身是两条独立结算，会同时触发 —— 这是作者明确要的 ✓
            if (lavaField) {
                TNLavaFieldEntity.spawn(server, groundBelow(server, impact),
                        owner instanceof ServerPlayer caster ? caster : null,
                        FireSpellRules.lavaFieldPerSecond(damage),
                        FireSpellRules.LAVA_FIELD_RADIUS, FireSpellRules.LAVA_FIELD_LIFE_TICKS);
            }
            // 爆炸（t4 熔岳天倾的火球才有）：对范围内敌人造成**火球伤害的一半**。
            // 直击目标也会吃到（先被直接命中、再被爆炸波及）—— 爆炸本来就该是这样
            if (blastRadius > 0.0D) {
                float blast = FireSpellRules.blastDamage(damage);
                for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                        FireSpellRules.uprightArea(impact, blastRadius, 2.0D),
                        t -> FireSpellRules.hittable(owner, t))) {
                    victim.invulnerableTime = 0;   // 直击刚把这个设成 20，不清的话爆炸会被吞
                    victim.hurt(server.damageSources().indirectMagic(this, owner), blast);
                    // 陨星坠：**范围内**的生物也挂 II 级焚身（作者 2026-10-05 要求）。
                    // 判据就用 maxHealthPercent > 0 —— 目前"按最大生命扣血 + 范围焚身"
                    // 是陨星坠这一档独有的组合；将来真有第二个法术只要其中之一，再拆独立字段
                    if (maxHealthPercent > 0.0F && owner instanceof ServerPlayer caster) {
                        TNScorch.apply(victim, caster, damage, true);
                    }
                }
                server.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                server.sendParticles(ParticleTypes.LAVA, impact.x, impact.y, impact.z, 20,
                        blastRadius * 0.4D, 0.4D, blastRadius * 0.4D, 0.2D);
            }
            // 火龙术：**打到生物不停** ✗ —— 只有撞到方块才在这里结束 ✓
            if (penetrates() && wall.getType() == HitResult.Type.MISS) {
                // 继续飞：不 discard、不 return ✓（下面照样推进位置 ✓）
            } else {
                if (isDragon() && level() instanceof net.minecraft.server.level.ServerLevel sl2) {
                    dragonExplode(sl2);            // 撞墙 ⇒ 也是那一下剧烈爆炸 ✓
                }
                discard();
                return;
            }
        }

        setPos(to);
        remaining -= step.length();
    }

    /**
     * 命中时炸成一团火焰粒子 —— <b>纯视觉，零伤害</b>（作者 2026-10-05 要求）。
     *
     * <p>目的只有一个：火球打到东西时不要"啪"地凭空消失，而是<b>炸开</b>。
     * 所以这里<b>只发粒子、不碰任何伤害逻辑</b>；
     * 熔岳天倾那种"真的有爆炸伤害"的是上面 {@code blastRadius} 那一段，两者完全独立 ✓
     *
     * <p>规模跟着球的大小走（{@link #radius()}）—— 大火球的爆开也更壮观。
     */
    private void spawnImpactBurst(ServerLevel server, Vec3 at) {
        double scale = Math.max(0.8D, radius() / 0.35D);       // 火球术 = 1.0 倍
        double spread = 0.30D * scale;

        server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, (int) (34 * scale),
                spread, spread, spread, 0.10D);
        server.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, (int) (20 * scale),
                spread * 1.2D, spread * 1.2D, spread * 1.2D, 0.14D);
        // 几点熔岩渣 + 烟：给它"炸开"的重量感，而不是一团浮在空中的火
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, (int) (6 * scale),
                spread * 0.75D, spread * 0.75D, spread * 0.75D, 0.0D);
        server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, (int) (8 * scale),
                spread, spread, spread, 0.03D);
    }

    /** 熔岩地要贴地：从命中点往下找一层可站立的平面，找不到就退回命中点本身。 */
    private Vec3 groundBelow(ServerLevel level, Vec3 impact) {
        Vec3 down = impact.add(0.0D, -4.0D, 0.0D);
        BlockHitResult floor = level.clip(new ClipContext(impact, down, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 at = floor.getType() == HitResult.Type.MISS ? impact : floor.getLocation();
        return new Vec3(at.x, at.y + 0.06D, at.z);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    /**
     * 陨星坠落地那一下的<b>大范围火焰扩散</b>（作者 2026-10-05：「场景壮观点」）。
     *
     * <p>分四层，从里到外：
     * <ol>
     *   <li>中心一团爆炸 + 大量岩浆粒子（横向铺开，像溅起的熔岩）</li>
     *   <li><b>三层向外扩散的火焰</b>：每层半径与速度都更大 —— 这才是"向四周扩散"</li>
     *   <li>升起的浓烟柱</li>
     *   <li>贴着地面炸开的一圈火环（半径 = 爆炸半径，让人一眼看出打到了多大范围）</li>
     * </ol>
     *
     * <p>⚠️ 纯视觉，<b>零伤害</b> —— 真正的伤害在上面 blastRadius / maxHealthPercent 两段。
     */
    private void meteorImpactBurst(ServerLevel server, Vec3 at) {
        double blast = FireSpellRules.METEOR_BOLT.blastRadius();

        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.4D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3D, at.z, 70, 1.2D, 0.7D, 1.2D, 0.45D);

        // 三层向外扩散的火焰：层数越外，铺得越开、飞得越快
        for (int ring = 0; ring < 3; ring++) {
            double spread = 1.6D + ring * 1.1D;
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3D + ring * 0.35D, at.z,
                    90, spread, 0.5D, spread, 0.55D + ring * 0.45D);
        }

        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.2D, at.z, 50, 2.2D, 1.2D, 2.2D, 0.18D);
        server.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.2D, at.z, 60, 2.6D, 0.4D, 2.6D, 0.25D);

        // 贴地的一圈火环：半径就是爆炸半径，让"打到多大范围"看得见
        int spokes = 40;
        for (int i = 0; i < spokes; i++) {
            double a = i * Math.PI * 2.0D / spokes;
            server.sendParticles(ParticleTypes.FLAME,
                    at.x + Math.cos(a) * blast, at.y + 0.25D, at.z + Math.sin(a) * blast,
                    4, 0.25D, 0.25D, 0.25D, 0.08D);
        }
    }

    /**
     * 命中时的「动静」：<b>声音 + 震屏</b>（作者 2026-10-05 要求，且必须逐档加强 ✗）。
     *
     * <ul>
     *   <li><b>t3 熔岩火球</b>：较响亮的爆炸声 + <b>轻微</b>视角震动</li>
     *   <li><b>t4 熔岳天倾</b>：巨大的爆炸与轰鸣声 + <b>较强烈</b>震动</li>
     *   <li><b>t5 陨星坠</b>：巨大的爆炸与轰鸣声 + <b>强烈</b>震动</li>
     * </ul>
     *
     * <p>用原版音效（不需要额外素材 ✓），靠<b>音量</b>拉开档次；
     * 震屏复用现有的 {@link TNShockwaveEntity} —— 客户端本来就在侦测它 ✓，
     * 只是现在带上「这一发自己的强度」✓（见 TNShockwaveEntity.DATA_SHAKE）。
     *
     * <p>⚠️ 装饰小陨石（meteor_shard）走 default 分支：<b>不出声、不震屏</b> ——
     *    它是纯装饰，响了反而分不清主次 ✗
     */
    private void boom(ServerLevel server, Vec3 at) {
        String path = spellPath();
        float shake;
        float volume;
        switch (path) {
            case "lava_fireball" -> {
                shake = FireSpellRules.SHAKE_T3;
                volume = 1.8F;
            }
            case "molten_skyfall" -> {
                shake = FireSpellRules.SHAKE_T4;
                volume = 3.5F;
            }
            case "meteor_fall" -> {
                shake = FireSpellRules.SHAKE_T5;
                volume = 6.0F;
            }
            default -> {
                return;
            }
        }
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, volume, 1.0F);
        // ⚠️ 作者 2026-10-05：「把 t4、t5 的雷声去掉」✗
        //    原来这里叠了一声 LIGHTNING_BOLT_THUNDER（我按"轰鸣声"理解的），
        //    但作者要的是**爆炸的轰鸣**，不是打雷 ✗ ⇒ 整段拿掉，只留 GENERIC_EXPLODE ✓
        // 震屏：复用冲击波实体；半径跟着强度走，视觉上也分得出大小 ✓
                // ⚠️ 最后一个参数 0 = 不发白闪 ✗ —— 作者 2026-10-05：「把震动时的界面变灰删去」
        //    （那个"变灰"是 TNSpellClientVisuals 在 flash 时盖的白色 ✗）
        TNShockwaveEntity.blast(server, at, 3.0D + shake * 0.6D, shake, getOwner(), 0.0F);
    }
}
