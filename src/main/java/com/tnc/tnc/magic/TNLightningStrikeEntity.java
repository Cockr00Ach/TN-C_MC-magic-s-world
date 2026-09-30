package com.tnc.tnc.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 一道<b>从天而降</b>的落雷（纯表现）。
 *
 * <h2>为什么要有它</h2>
 * 作者 2026-09-27："这主链的雷场雷暴雷击用的都是啥，我不是给你闪电的模型了吗？
 * 做成跟**天打五雷轰一样的雷劈的方式**不就好了" ＋ "你这像是在怪身上爆开的，我要**从天上劈下去**啊"。
 *
 * <p>所以它**不是**站在目标身上闪一下 ✗ —— 它在目标**正上方 24 格**生成，
 * 每 tick 落 {@link #FALL_SPEED} 格砸下来 ✓，落下时一路撒电花（拖尾 ✓），
 * 到底后再亮 {@link #life()} tick 就消失 ✓；渲染器把作者的 {@code flash} 模型
 * **底端**画在实体位置上，于是看上去就是"闪电从天上劈到敌人身上" ✓✓。
 *
 * <p>伤害不在这里结算 ✗ —— 机制层在生成它的时候就已经扣血了（见 {@code TnSpellMechanics.strikeEnemy}），
 * 这个实体只负责"看起来是雷劈"。
 */
public class TNLightningStrikeEntity extends Entity {

    /**
     * The particle the heavenly thunder projectile uses for its travel rings
     * ("electric_arc_a"). Falls back to vanilla sparks when the engine is absent.
     */
    private static net.minecraft.core.particles.ParticleOptions arc() {
        net.minecraft.core.particles.ParticleType<?> t =
                net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "spell_engine", "electric_arc_a"));
        return (t instanceof net.minecraft.core.particles.SimpleParticleType simple)
                ? simple : ParticleTypes.ELECTRIC_SPARK;
    }
    /** 每 tick 下落多少格（24 格高 ⇒ 约 4 tick 落地 ✓ 够快，看得出是"劈"下来 ✓）。 */
    private static final double FALL_SPEED = 6.0D;
    /** 生成高度（格）—— 作者要"从天上来" ✓ */
    public static final double FALL_HEIGHT = 24.0D;

    /** scale x 100（同步给客户端 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);
    /** 落地后还亮多少 tick ✓ */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);

    /**
     * 0 = 闪电（主链雷场/雷暴/雷击），1 = 大雷球（神在投篮 t5：每个敌人头顶一颗），
     * 2 = 神（神在投篮 t5 天上那三尊），3 = <b>跟随神</b>（闪电登神时跟在玩家背后的那一尊）✓
     * 同步给客户端，渲染器据此换模型（flash vs lightingball_2 vs lightning_god）✓
     */
    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);

    /** 命中时该晃多猛（度，x100 同步给客户端）✓ */
    private static final EntityDataAccessor<Integer> DATA_SHAKE =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);

    /**
     * 暗色版（黑暗衍 {@code tnc:yan_dark} 专用）✓ —— 同步给客户端，渲染器据此换
     * {@code *_dark} 模型（{@code flash_dark} / {@code god_dark} / {@code lightingball_dark}）。
     *
     * <p>三种形态各有暗色模型，所以**一个布尔就够** ✓（暗色模型的几何与亮色版完全一致，
     * 只有 UV 与贴图不同 —— 见 {@code tools/gen_dark_projectile_models.ps1} ✓）。
     */
    private static final EntityDataAccessor<Boolean> DATA_DARK =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.BOOLEAN);

    /** 落点高度（只有服务端要用 ✓ 不用同步）。 */
    private double fallTo;
    /** 落地那一 tick（-1 = 还没落地 ✓）。 */
    private int landedTick = -1;

    /**
     * 落地那一 tick（**同步给客户端** ✓）：客户端据此只在"砸到地上那一下"晃屏 ✓
     *
     * <p>2026-09-29 作者："怎么刚释放就震动" —— 之前客户端只要附近有落雷实体就按
     * {@link #shake()} 晃屏 ✗，于是三尊神**一生成就开始晃**（`configure(..., 3.0)` ✗），
     * 一直晃 10 秒 ✗。现在只有 {@code tickCount - DATA_IMPACT_AT <= IMPACT_SHAKE_TICKS}
     * 才晃 ✓；神永远不落地（值一直是 -1）⇒ 永远不晃 ✓。
     */
    private static final EntityDataAccessor<Integer> DATA_IMPACT_AT =
            SynchedEntityData.defineId(TNLightningStrikeEntity.class, EntityDataSerializers.INT);
    /** 下落速度（默认 6 格/tick；神在投篮的球会调慢 ✓） */
    private double fallSpeed = FALL_SPEED;

    /**
     * 落地那一下的伤害与半径（0 = 纯表现 ✓）。
     *
     * <p>主链的雷场 / 雷暴 / 雷击**不要**用这个：那些伤害由机制层在生成时就结算了
     * （见 {@link TnSpellMechanics}），这里再来一次会打两遍 ✗。
     * 只有"神在投篮 t5"那颗大雷球走这条路 ✓（它的伤害本来就该发生在落地那一刻）。
     */
    private float landDamage = 0.0F;
    private double landRadius = 0.0D;
    /** 伤害归属者（击杀统计 / 掉落要用）；null = 不结算伤害 ✓。 */
    private java.util.UUID landOwner = null;

    public TNLightningStrikeEntity(EntityType<? extends TNLightningStrikeEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * 生成后立刻调用一次。
     *
     * @param scale    闪电大小（倍数）
     * @param lifeTicks 落地后还亮多少 tick
     * @param fallToY  落到哪个高度（＝目标脚下 ✓）
     */
    public void configure(double scale, int lifeTicks, double fallToY, double shakeStrength) {
        this.entityData.set(DATA_SCALE, (int) Math.round(scale * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        this.fallTo = fallToY;
        this.entityData.set(DATA_SHAKE, (int) Math.round(shakeStrength * 100.0D));
    }

    /** How hard the screen should shake on the hit (degrees) ✓ */
    public double shake() {
        return this.entityData.get(DATA_SHAKE) / 100.0D;
    }

    /** 落地时刻（-1 = 还没落地 / 永不落下）。客户端用它判断"这一下该不该晃屏" ✓ */
    public int impactAt() {
        return this.entityData.get(DATA_IMPACT_AT);
    }

    /** 砸到地上那一下之后还晃几 tick（≈0.25 秒 ✓ 干脆的一下，不是持续震 ✗） */
    public static final int IMPACT_SHAKE_TICKS = 5;

    /** 这一 tick 该不该晃屏：只有**刚落地**那几 tick ✓（神永远 -1 ⇒ 永远不晃 ✓） */
    public boolean isShaking(int age) {
        int at = this.impactAt();
        return at >= 0 && age - at >= 0 && age - at <= IMPACT_SHAKE_TICKS;
    }

    /** 变成"大雷球"形态（神在投篮 t5）✓ */
    public void asBall() {
        this.entityData.set(DATA_KIND, 1);
    }

    public boolean isBall() {
        return this.entityData.get(DATA_KIND) == 1;
    }

    /** 神形态（神在投篮 t5：天上那三尊，不下落 ✓） */
    public void asGod() {
        this.entityData.set(DATA_KIND, 2);
    }

    public boolean isGod() {
        return this.entityData.get(DATA_KIND) == 2;
    }

    /**
     * 变成<b>暗色版</b>（黑暗衍专用）✓ —— 三种形态都能用，渲染器按形态各取 {@code *_dark} 模型。
     *
     * <p>作者 2026-09-30："这个黑暗衍我希望他的魔法也是新的，我制作了 god_dark 和 flash_dark
     * 和 lightingball_dark，你拿去替换他的法术" ✓
     */
    public void asDark() {
        this.entityData.set(DATA_DARK, true);
    }

    public boolean isDark() {
        return this.entityData.get(DATA_DARK);
    }

    // ------------------------------------------------------------------
    //  ★ 跟随神（2026-09-29 作者："我已登神，我希望玩家背后会出现 god 的模型跟随"）
    // ------------------------------------------------------------------

    /** 跟随谁的 UUID（只有服务端要用 ✓ 不用同步）。 */
    private java.util.UUID followOwner = null;
    /** 跟在背后多少格、抬高多少格 ✓ */
    private double followBack = 3.5D;
    private double followUp = 1.2D;

    /**
     * 变成"跟随神"形态：每 tick 贴在 owner 背后 {@code back} 格、抬高 {@code up} 格 ✓，
     * 朝向和 owner 一致（原版约定：facing = (−sin yaw, cos yaw) ⇒ 背后 = +(sin yaw, −cos yaw) ✓）。
     *
     * <p>它和 t5 天上那三尊的区别：
     * <ul>
     *   <li>不落地、不结算伤害（纯跟随/装饰 ✓）</li>
     *   <li>位置由 owner 决定（不是由落点决定）</li>
     *   <li>owner 没了 / 登神 buff 掉了 ⇒ 自己 {@code discard} ✓</li>
     * </ul>
     */
    public void asFollowerGod(java.util.UUID owner, double back, double up) {
        this.entityData.set(DATA_KIND, 3);
        this.followOwner = owner;
        this.followBack = Math.max(0.0D, back);
        this.followUp = up;
    }

    public boolean isFollowerGod() {
        return this.entityData.get(DATA_KIND) == 3;
    }

    /** 跟随目标（只有服务端能拿到 ✓；客户端返回 null）。 */
    public java.util.UUID followOwner() {
        return this.followOwner;
    }

    /** 神形态 / 跟随神都用同一个模型 ✓（渲染器里判的）。 */
    public boolean usesGodModel() {
        return isGod() || isFollowerGod();
    }

    /** 下落速度（格/tick）—— 神在投篮那颗球要慢 ✓ */
    public void setFallSpeed(double speed) {
        this.fallSpeed = Math.max(0.1D, speed);
    }

    /**
     * 让这颗球<b>落地时真的打人</b> ✓（神在投篮 t5：半径 8 格、每只敌人 25 伤）。
     *
     * <p>为什么伤害放在实体里、而不是生成它的那一刻：作者要的就是"<b>砸下来</b>那一下才疼" ✓
     * —— 球在空中那 16 tick，敌人跑开就打不着了（这才像"投篮"）。
     *
     * @param owner  伤害归属者（同时也是"不打自己"的判据）✓ ——
     *               ★ 2026-09-30 从 {@code Player} 放宽到 {@link net.minecraft.world.entity.LivingEntity}：
     *               黑暗衍（boss）的落雷/大雷球也要真的打人 ✓（原来只认玩家 ✗ ⇒ boss 的法术
     *               **一点伤害都没有**，纯放烟花 ✗✗ —— 作者："他的法术怎么感觉一般啊"）
     * @param damage 落地伤害（≤0 = 不结算，退回纯表现 ✓）
     * @param radius 落地伤害半径（格）
     */
    public void setLandImpact(net.minecraft.world.entity.LivingEntity owner, float damage, double radius) {
        this.landOwner = owner == null ? null : owner.getUUID();
        this.landDamage = Math.max(0.0F, damage);
        this.landRadius = Math.max(0.0D, radius);
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }


    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 450);
        this.entityData.define(DATA_LIFE, 6);
        this.entityData.define(DATA_SHAKE, 300);
        this.entityData.define(DATA_KIND, 0);
        this.entityData.define(DATA_IMPACT_AT, -1);
        this.entityData.define(DATA_DARK, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.isFollowerGod()) {
            tickFollowerGod();
            return;
        }
        if (this.isGod()) {
            // 三尊神：悬停在目标上方（生成时给的落点 + FALL_HEIGHT）✓，只冒紫/黑粒子，不落地 ✓
            if (this.tickCount == 1) {
                this.setPos(this.getX(), this.fallTo + FALL_HEIGHT, this.getZ());
            }
            if (this.tickCount > this.life()) {
                this.discard();
                return;
            }
            ServerLevel lvl = (ServerLevel) this.level();
            lvl.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 2.0D, this.getZ(),
                    6, 1.6D, 1.6D, 1.6D, 0.15D);
            lvl.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 2.0D, this.getZ(),
                    4, 1.6D, 1.6D, 1.6D, 0.10D);
            return;
        }
        if (this.tickCount == 1) {
            // 生成在目标正上方（生成时给的是落点坐标 ✓）
            this.setPos(this.getX(), this.fallTo + FALL_HEIGHT, this.getZ());
            return;
        }
        if (this.getY() > this.fallTo) {
            double next = Math.max(this.fallTo, this.getY() - this.fallSpeed);
            // 下落拖尾：一路撒电花 —— "从天劈下来"的观感主要靠它 ✓
            ServerLevel level = (ServerLevel) this.level();
            double rr = 0.7D;
            for (int i = 0; i < 6; i++) {
                double aa = i * (Math.PI / 3.0D) + (this.tickCount * 0.4D);
                double px = this.getX() + Math.cos(aa) * rr;
                double pz = this.getZ() + Math.sin(aa) * rr;
                level.sendParticles(arc(), px, next + 0.5D, pz, 1, 0.05D, 0.6D, 0.05D, 0.15D);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, next + 0.5D, pz, 1, 0.05D, 0.6D, 0.05D, 0.15D);
            }
            level.sendParticles(ParticleTypes.WITCH, this.getX(), next + 0.5D, this.getZ(),
                    4, 0.3D, 0.8D, 0.3D, 0.3D);
            this.setPos(this.getX(), next, this.getZ());
            return;
        }
        if (this.landedTick < 0) {
            this.landedTick = this.tickCount;
            this.entityData.set(DATA_IMPACT_AT, this.tickCount);     // 同步给客户端：该晃屏了 ✓
            // 落地那一下：炸开一圈 ✓
            ServerLevel level = (ServerLevel) this.level();
            level.sendParticles(arc(), this.getX(), this.getY() + 0.4D, this.getZ(),
                    120, 0.7D, 0.6D, 0.7D, 3.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.4D, this.getZ(),
                    120, 0.7D, 0.6D, 0.7D, 3.0D);
            level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 0.4D, this.getZ(),
                    50, 0.5D, 0.4D, 0.5D, 2.0D);
            level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 0.8D, this.getZ(),
                    2, 0.1D, 0.1D, 0.1D, 0.0D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
            landBlast(level);
        } else if (this.tickCount > this.landedTick + this.life()) {
            this.discard();
        }
    }

    /**
     * 跟随神每 tick：贴到 owner 背后、朝向跟着 owner 转、撒一点紫黑粒子 ✓。
     *
     * <p>为什么用"平滑插值"而不是直接 {@code setPos}：直接贴的话，玩家每 tick 的位置抖动
     * （走路/上台阶/被击退）会原样传到比玩家大好几倍的模型上（跟随神 5.6 格高、t5 那三尊 29 格）
     * ⇒ 看起来像在抽 ✗。这里按 0.35 追，正常走路跟得住 ✓；距离超过 8 格（传送/速度过快）直接吸附 ✓。
     *
     * <p>什么情况下自己消失（三道保险，缺一道都会留下"孤儿神" ✗）：
     * <ol>
     *   <li>owner 死掉 / 掉线 / 不在同一个维度</li>
     *   <li>owner 的<b>闪电登神 buff 掉了</b> —— 机制层也会清（{@code TnSpellMechanics}），
     *       但实体自己也要会死 ✓（机制层漏了那次，神会一直跟着你 ✗）</li>
     *   <li>超过 {@link #FOLLOWER_MAX_AGE} tick（10 分钟）—— 兜底，防止任何异常路径把它留下 ✓</li>
     * </ol>
     *
     * <p>★ 2026-09-30：owner 的查找从"只找玩家"放宽到**任意生物** ✓ ——
     * 作者要的 boss 二阶段登神外观正是这个效果（"我想要的登神外观是那个 god 的模型在他背后跟随"）✓，
     * 而黑暗衍是 Mob ✗，原来 {@code getPlayerList().getPlayer(uuid)} 永远查不到它 ⇒ 神一生成就自杀 ✗✗。
     */
    private void tickFollowerGod() {
        ServerLevel level = (ServerLevel) this.level();
        net.minecraft.world.entity.Entity owner = this.followOwner == null
                ? null : level.getEntity(this.followOwner);
        boolean ascended = owner instanceof net.minecraft.world.entity.LivingEntity living
                && TNEffects.LIGHTNING_ASCENSION.isPresent()
                && living.hasEffect(TNEffects.LIGHTNING_ASCENSION.get());
        if (owner == null || owner.isRemoved() || owner.level() != level || !ascended
                || this.tickCount > FOLLOWER_MAX_AGE) {
            this.discard();
            return;
        }
        float yaw = owner.getYRot();
        double rad = Math.toRadians(yaw);
        double tx = owner.getX() + Math.sin(rad) * this.followBack;
        double tz = owner.getZ() - Math.cos(rad) * this.followBack;
        double ty = owner.getY() + this.followUp;
        double dx = tx - this.getX();
        double dy = ty - this.getY();
        double dz = tz - this.getZ();
        if (dx * dx + dy * dy + dz * dz > 64.0D) {
            this.setPos(tx, ty, tz);                        // 太远了：直接吸附 ✓
        } else if (this.tickCount > 1) {
            this.setPos(this.getX() + dx * 0.35D, this.getY() + dy * 0.35D, this.getZ() + dz * 0.35D);
        } else {
            this.setPos(tx, ty, tz);
        }
        this.setYRot(yaw);
        this.setXRot(0.0F);
        // 粒子：和 t5 天上那三尊同一套配色（紫 + 一点点黑）✓
        level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 2.5D, this.getZ(),
                3, 1.0D, 1.2D, 1.0D, 0.10D);
        level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 2.5D, this.getZ(),
                2, 1.0D, 1.2D, 1.0D, 0.06D);
    }

    /** 跟随神的兜底寿命（tick）：10 分钟 ✓ */
    public static final int FOLLOWER_MAX_AGE = 12000;

    /**
     * 落地伤害：以落点为中心、半径 {@link #landRadius} 内的敌人各挨 {@link #landDamage} ✓。
     *
     * <p>只在 {@link #setLandImpact} 被调用过（神在投篮 t5 那颗球）时生效 ✓ ——
     * 主链那些落雷都是纯表现，走到这里会直接 return ✓。
     *
     * <p>判据用 {@link TnSpellMechanics#isEnemy}（包内共享），于是"不打自己、不打队友"这条
     * 和机制层其余法术完全一致 ✓。归属者取不到（比如掉线了）就**不结算**，
     * 也不退化成"无主伤害" ✗ —— 否则击杀统计会记在一个不存在的来源上。
     */
    private void landBlast(ServerLevel level) {
        if (this.landDamage <= 0.0F || this.landOwner == null) {
            return;
        }
        // ★ 归属者可能是玩家（t5 神在投篮）也可能是 boss（黑暗衍）⇒ 一律按 UUID 找 ✓
        net.minecraft.world.entity.Entity rawOwner = level.getEntity(this.landOwner);
        if (!(rawOwner instanceof net.minecraft.world.entity.LivingEntity owner)) {
            return;
        }
        // 爆心 = **球落点**（{@link #fallTo}，也就是地面上的锚点）✗ 不是球心 ——
        // 球半径让球心抬起来了（见 TnSpellMechanics.tickDivineShot），
        // 拿球心当爆心的话：判定框整个悬在半空、地上的敌人一个都打不到 ✗
        double cx = this.getX();
        double cy = this.fallTo;
        double cz = this.getZ();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                cx - this.landRadius, cy - this.landRadius, cz - this.landRadius,
                cx + this.landRadius, cy + this.landRadius, cz + this.landRadius);
        for (net.minecraft.world.entity.LivingEntity target
                : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (!TnSpellMechanics.isEnemy(owner, target)) {
                continue;
            }
            double dx = target.getX() - cx;
            double dy = target.getY() + target.getBbHeight() * 0.5D - cy;
            double dz = target.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz > this.landRadius * this.landRadius) {
                continue;                       // 以**爆心**算距离（不是以球心 ✗）
            }
            target.hurt(level.damageSources().indirectMagic(owner, owner), this.landDamage);
        }

        // ★ **落地才是"爆炸"**（作者 2026-09-29："怎么刚放出来就爆炸，应该等球落地" ✓）
        //   起手那一下原来在法术 JSON 里就炸了（冲击波 + 720 颗粒子）✗ —— 现在 JSON 只留一点点"起手电花"，
        //   真正的爆炸搬到这里：冲击波环（推人 ＋ 震屏 ＋ 向外扩张的光环）＋ 一大团粒子 ＋ 白闪 ✓
        TNShockwaveEntity wave = TNOrbEntities.SHOCKWAVE.get().create(level);
        if (wave != null) {
            wave.configure(this.landRadius, 40);            // 环半径 = 伤害半径 ✓
            wave.exemptFromPush(owner);                     // 作者 2026-09-29："爆炸不要把我击飞" ✓
            wave.moveTo(cx, cy + 0.05D, cz, 0.0F, 0.0F);
            level.addFreshEntity(wave);
        }
        level.sendParticles(arc(), cx, cy + 1.0D, cz, 200, 1.4D, 0.9D, 1.4D, 4.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy + 1.0D, cz,
                400, 1.4D, 0.9D, 1.4D, 6.0D);
        level.sendParticles(ParticleTypes.WITCH, cx, cy + 1.0D, cz,
                150, 1.2D, 0.7D, 1.2D, 4.0D);
        level.sendParticles(ParticleTypes.SQUID_INK, cx, cy + 1.0D, cz,
                60, 1.0D, 0.6D, 1.0D, 3.0D);
        level.sendParticles(ParticleTypes.FLASH, cx, cy + 1.5D, cz, 4, 0.3D, 0.3D, 0.3D, 0.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 纯表现，不存档 ✓
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
        return distanceSqr < 32768.0D;
    }
}
