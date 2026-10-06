package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.entity.SpellSpawnedEntity;
import net.spell_engine.api.spell.Spell;

import java.util.List;

/**
 * <b>抓住不放的黑手</b> —— 暗系第一条链「黑夜之手」重做的抓取端 ✓
 * （作者 2026-10-10：「抓住敌人手再把敌人拉回来可以不」＋
 * 「暗魔法的黑夜之手可能要整个改一下」，参考《Re:Zero》486 的不可视之手）。
 *
 * <h2>它补的是"飞出去的手"缺的那一半</h2>
 * 重做前这条链只有"飞出去 + 撞一下"✗ —— 撞完引擎就把投射物回收了，
 * 于是<b>没有"抓住"这件事</b>：不抓、不拽、不持续伤害，"手"只是个伤害球 ✓。
 * 现在：投射物照旧飞出去（那一段的观感作者已经验收过 ✓），
 * <b>命中那一刻</b>由引擎生成本实体 ⇒ 手"抓住"目标并留在那儿：
 * <ol>
 *   <li><b>压制</b>：每 tick 把目标的水平速度压住（被攥住的感觉 ✓）；</li>
 *   <li><b>捏</b>：每 {@link #SQUEEZE_INTERVAL} tick 结算一次伤害（不是"一下就没" ✓）；</li>
 *   <li><b>拽</b>：t3 起把目标一段段拖向施法者（引擎把 SPAWN 实体放在命中点 ⇒
 *       它本来就在目标身上 ✓）；</li>
 *   <li><b>捏碎</b>：t5 在松手那一刻追加一次爆发（"捏"的第二段 ✓）；</li>
 *   <li><b>特效</b>：从施法者<b>后背</b>拉一条黑索到手的位置 ✓（作者 10-10 要的
 *       "从我的后背伸出来"）＋ 目标身上裹一层黑雾 ✓ —— 作者本次特别强调
 *       "别游戏里做的啥都看不见"，所以这里的粒子是**加量**的 ✗ 不是减量 ✓。</li>
 * </ol>
 *
 * <h2>为什么用 {@code SPAWN} 而不是自己监听施法</h2>
 * 引擎正好留了一个干净的接口（本项目已经在「以伤换伤 · 献祭」那条链上<b>实机用过</b> ✓）：
 * <pre>
 * interface SpellSpawnedEntity {
 *     void onCreatedFromSpell(LivingEntity caster, ResourceLocation spellId, Spell$Impact$Action$Spawn spawn);
 * }
 * </pre>
 * 法术 JSON 的 {@code impact} 里加一条 {@code SPAWN}（{@code entity_type_id: "tnc:night_grip"}）⇒
 * <b>命中那一刻</b>引擎会造出本实体、并**把施法者一起交给我们** ✓
 * （反编译 {@code SpellHelper.performImpact} 确认：它会 {@code instanceof SpellSpawnedEntity} 然后回调 ✓）。
 * 于是"抓住 ＋ 拖回来 ＋ 捏碎"这三件引擎做不到的事，全落在 {@link #onCreatedFromSpell} 与
 * {@link #tick()} 里 ✓ —— 完全相同的一套做法见 {@link TNDarkDrainEntity} ✓。
 *
 * <h2>数值表（想调手感只改这一张表）</h2>
 * <pre>
 *   档  法术            抓住时长   每段伤害   拽（格/10t）  末尾捏碎
 *   t1  night_hand      1.0 s      2.0        —            —
 *   t2  night_raid      1.5 s      3.0        —            —
 *   t3  night_embrace   2.0 s      4.0        0.35         —
 *   t4  black_ruin      2.5 s      5.5        0.45         —
 *   t5  slay_light      3.0 s      7.0        0.55         10.0
 * </pre>
 * ⚠️ <b>未实机验收</b>（开发环境看不到画面）：可静态核对的部分都核了，手感请作者进游戏试 ✓。
 */
public class TNNightGripEntity extends Entity implements SpellSpawnedEntity {

    // ------------------------------------------------------------------
    //  数值表
    // ------------------------------------------------------------------

    /** 每档：抓住多久（tick）· 每段伤害 · 每次拽多远（0 = 不拽）· 末尾捏碎伤害（0 = 不捏）。 */
    private static final GripStats[] STATS = {
            new GripStats(20, 2.0F, 0.00D, 0.0F),   // t1 黑夜之手：抓住拉一下
            new GripStats(30, 3.0F, 0.00D, 0.0F),   // t2 黑夜之袭
            new GripStats(40, 4.0F, 0.35D, 0.0F),   // t3 黑夜之拥：开始往回拽
            new GripStats(50, 5.5F, 0.45D, 0.0F),   // t4 黑之破灭
            new GripStats(60, 7.0F, 0.55D, 10.0F),  // t5 戮光：松手前捏碎
    };

    /** 每几 tick 捏一次 ✓（和 {@code TNDarkDrainEntity.PULL_INTERVAL} 同一个节奏）。 */
    private static final int SQUEEZE_INTERVAL = 10;

    /** 拽的时候带一点上抬，免得目标卡进地面 ✓。 */
    private static final double PULL_LIFT = 0.22D;

    /** 离施法者多近就不再拽（免得在脸上抖）✓。 */
    private static final double PULL_STOP_DISTANCE = 2.0D;

    /** 被攥住时水平速度每一 tick 压到原来的多少 ✓（越小越"动不了"）。 */
    private static final double HELD_DRAG = 0.35D;

    /** 手掌心相对目标中心往施法者那边退多少格（让"掌面按在他身上"而不是穿过去）✓。 */
    private static final double PALM_BACKOFF = 0.5D;

    /** 没在目录里查到档位时用的兜底 ✓。 */
    private static final int FALLBACK_TIER = 1;

    /** 黑索最多几个点（防止 t5 的大手拉出几百颗粒子）✓。 */
    private static final int CORD_MAX_POINTS = 48;

    /** 黑索两点之间多少格一颗粒子 ✓（沿用 {@code TNDarkHandCord} 的观感）。 */
    private static final double CORD_SPACING = 0.35D;

    /** 起点往外挪一点 ⇒ 看起来是**从后背**出来，而不是从脚底 ✓。 */
    private static final double BACK_OFFSET = 0.45D;

    private record GripStats(int holdTicks, float damage, double pull, float crush) {
    }

    // ------------------------------------------------------------------
    //  同步字段（客户端渲染器要用：缩放看 tier、朝向看 owner 在哪边 ✓）
    // ------------------------------------------------------------------

    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNNightGripEntity.class, EntityDataSerializers.INT);
    /** 施法者的 entity id ⇒ 渲染器据此把"掌心"转向目标（从施法者指向本实体）✓。 */
    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(TNNightGripEntity.class, EntityDataSerializers.INT);

    // ---- 运行期状态（只在服务端用，不进存档 ✓）----

    /** 被抓住的目标（只存 id：跨 tick 拿着旧引用会指向死掉的实体 ✗）。 */
    private int victimId = -1;
    /** 施法者（同上 ✓）。 */
    private int casterId = -1;
    /** 档位（生成那一刻定下来 ✓）。 */
    private int tier = FALLBACK_TIER;
    /** 已经捏过几次 ✓。 */
    private int squeezes;
    /** 累计捏掉多少血（最后报一次总账 ✓）。 */
    private float dealtTotal;
    /** 捏碎/拽累计走了多远（给日志用 ✓）。 */
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 不存档：它只活几秒 ✓
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // ------------------------------------------------------------------
    //  对外只读（渲染器与日志用 ✓）
    // ------------------------------------------------------------------

    public int tier() {
        return this.entityData.get(DATA_TIER);
    }

    /** 施法者的 entity id（客户端渲染器用它算朝向 ✓）；-1 = 不知道。 */
    public int ownerEntityId() {
        return this.entityData.get(DATA_OWNER);
    }

    public GripStats stats() {
        return STATS[Math.max(0, Math.min(STATS.length - 1, tier - 1))];
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

    /** 这个档位的手有多大（格，半径）—— 与渲染器 {@code TNNightGripRenderer.SCALE} 同一张表的口径 ✓。 */
    public double handHalfExtent() {
        return 0.5D + 0.35D * Math.max(1, tier);
    }

    /** 手不是可交互实体 ⇒ 别让它挡住玩家/引擎的准星射线（本项目其它法术实体同样处理 ✓）。 */
    @Override
    public boolean isPickable() {
        return false;
    }

    // ------------------------------------------------------------------
    //  生成：引擎在"命中那一刻"回调这里 ✓
    // ------------------------------------------------------------------

    @Override
    public void onCreatedFromSpell(LivingEntity caster, ResourceLocation spellId,
                                   Spell.Impact.Action.Spawn spawn) {
        if (level().isClientSide()) {
            return;
        }
        tier = tierOf(spellId);
        this.entityData.set(DATA_TIER, tier);
        casterId = caster == null ? -1 : caster.getId();
        this.entityData.set(DATA_OWNER, casterId);

        LivingEntity victim = findVictim(caster);
        if (victim == null) {
            // 没抓到人（打在方块上/打空了）⇒ 立刻收工，别留一只空手在那儿 ✗
            discard();
            return;
        }
        victimId = victim.getId();
        setPos(gripPos(victim, caster));

        // 抓住那一下来一团黑雾（"啪"地攥住 ✓）
        burst(victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), 1.0D, 26);
        LOGGER.info("TN-C: night hand grip target={} tier={} hold={}t owner={}",
                victim.getName().getString(), tier, stats().holdTicks(), casterId);
    }

    /** 施法者 vs 目标：把"抓住"的位置放在目标身上、但朝施法者那边退 {@link #PALM_BACKOFF} 格 ✓。 */
    private Vec3 gripPos(LivingEntity victim, LivingEntity caster) {
        Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        if (caster == null) {
            return center;
        }
        Vec3 dir = center.subtract(caster.position().add(0.0D, caster.getBbHeight() * 0.5D, 0.0D));
        if (dir.lengthSqr() < 1.0E-6D) {
            return center;
        }
        return center.subtract(dir.normalize().scale(PALM_BACKOFF));
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
        GripStats stats = stats();
        if (tickCount > stats.holdTicks()) {
            finish(stats);
            return;
        }

        LivingEntity victim = resolve(victimId);
        LivingEntity caster = resolve(casterId);
        if (victim == null || !victim.isAlive()) {
            finish(stats);
            return;
        }

        // ① 贴住目标（它被推走/被打飞时手跟着走 ✓）＋ 压制它的移动（"被攥住"✓）
        setPos(gripPos(victim, caster));
        hold(victim);

        // ② 捏：每 SQUEEZE_INTERVAL tick 一次（不是"一下就没" ✓）
        if (tickCount % SQUEEZE_INTERVAL == 0 && squeezes * SQUEEZE_INTERVAL < stats.holdTicks()) {
            squeezes++;
            float dealt = squeeze(victim, caster, stats.damage());
            dealtTotal += dealt;
        }

        // ③ 拽：t3 起往回拖（作者："抓住敌人手再把敌人拉回来"✓）
        if (caster != null && stats.pull() > 0.0D && tickCount % SQUEEZE_INTERVAL == 0) {
            pulledTotal += pullToward(victim, caster, stats.pull());
        }

        // ④ 特效：黑索（后背 → 手）＋ 目标身上裹黑雾 ＋ 手周围的黑气 ✓
        cord(caster);
        shroud(victim);
        if (tickCount % 2 == 0) {
            burst(position(), 0.45D, 6);
        }
    }

    /** 收工：t5 先捏碎，再消失 ✓。 */
    private void finish(GripStats stats) {
        LivingEntity victim = resolve(victimId);
        LivingEntity caster = resolve(casterId);
        if (victim != null && victim.isAlive() && stats.crush() > 0.0F) {
            float dealt = crush(victim, caster, stats.crush());
            dealtTotal += dealt;
        }
        if (dealtTotal > 0.0F) {
            LivingEntity owner = resolve(casterId);
            if (owner instanceof net.minecraft.server.level.ServerPlayer player) {
                player.displayClientMessage(Component.literal(String.format(
                        "§5[TN-C] §r黑夜之手 · 共捏碎 §c%.1f§r 点生命%s",
                        dealtTotal,
                        pulledTotal > 0.01D ? String.format(" · 已拖回 §d%.1f§r 格", pulledTotal) : ""))
                        .withStyle(net.minecraft.ChatFormatting.RESET), true);
            }
        }
        LOGGER.info("TN-C: night hand grip end tier={} squeeze={} dealt={} pulled={}",
                tier, squeezes, dealtTotal, String.format("%.2f", pulledTotal));
        discard();
    }

    /** 压制：把目标的水平速度压住 ⇒ 读感是"被攥住动不了"✓（不挂原版减速效果，免得冒药水粒子 ✗）。 */
    private void hold(LivingEntity victim) {
        Vec3 delta = victim.getDeltaMovement();
        double dampedX = delta.x * HELD_DRAG;
        double dampedZ = delta.z * HELD_DRAG;
        if (Math.abs(dampedX - delta.x) > 1.0E-4D || Math.abs(dampedZ - delta.z) > 1.0E-4D) {
            victim.setDeltaMovement(dampedX, delta.y, dampedZ);
            victim.hurtMarked = true;      // 服务端改了速度必须告诉客户端，否则看起来没被抓住 ✓
        }
    }

    /**
     * 捏一下。
     *
     * <p>⚠️ 这里**必须先把受伤窗口清 0** ✗ —— 和 {@code TNDarkSacrificeMechanics} 的燃血
     * 同一个原因：{@code hurt()} 在 {@code invulnerableTime > 0}（刚被打过/刚被烧过）时
     * 会被**静默丢掉**，表现就是"有时掉有时不掉" ✗（作者 10-09 抓到过这个现象）。
     */
    private float squeeze(LivingEntity victim, LivingEntity caster, float amount) {
        return dealDamage(victim, caster, amount, "squeeze");
    }

    /** t5 松手前那一下 ✓。 */
    private float crush(LivingEntity victim, LivingEntity caster, float amount) {
        burst(victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), 1.6D, 60);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.9F, 0.65F);
        }
        return dealDamage(victim, caster, amount, "crush");
    }

    /** 真正扣血（统一出口，方便日后要加"永不致死"之类的规则 ✓）。 */
    private float dealDamage(LivingEntity victim, LivingEntity caster, float amount, String kind) {
        if (amount <= 0.0F) {
            return 0.0F;
        }
        float before = victim.getHealth();
        victim.invulnerableTime = 0;
        boolean landed = victim.hurt(level().damageSources().indirectMagic(this, caster), amount);
        float dealt = landed ? Math.max(0.0F, before - victim.getHealth()) : 0.0F;
        LOGGER.debug("TN-C: night hand {} target={} amount={} landed={} dealt={}",
                kind, victim.getName().getString(), amount, landed, dealt);
        return dealt;
    }

    /**
     * 把被抓住的目标朝施法者拖 ✓（作者 2026-10-10："抓住敌人手再把敌人拉回来"）。
     *
     * <p>为什么用"间隔冲量"而不是每 tick 设速度：生物的 {@code travel()} 每 tick 都会按
     * 自己的意向重设速度 ⇒ 每 tick 设会被它抢走 ✗；间隔给一次较大冲量则确实能把人拖走 ✓
     * （与击退同一类效果）。{@code hurtMarked} 不能省 ✗（否则服务端改了速度客户端不知道 ✓）。
     *
     * @return 实际拽动了多远（格）
     */
    private double pullToward(LivingEntity victim, LivingEntity caster, double strength) {
        Vec3 delta = caster.position().subtract(victim.position());
        if (delta.lengthSqr() < PULL_STOP_DISTANCE * PULL_STOP_DISTANCE) {
            return 0.0D;                            // 已经拉到手边 ⇒ 别再抖 ✓
        }
        double moved = Math.min(strength, Math.max(0.0D, delta.length() - PULL_STOP_DISTANCE));
        Vec3 pull = delta.normalize().scale(moved).add(0.0D, PULL_LIFT, 0.0D);
        victim.setDeltaMovement(victim.getDeltaMovement().add(pull));
        victim.hurtMarked = true;
        return moved;
    }

    // ------------------------------------------------------------------
    //  特效（作者本次明确："别啥都看不见"，所以这部分是加量的 ✓）
    // ------------------------------------------------------------------

    /**
     * <b>黑索</b>：从施法者的<b>后背</b>到当前手的位置拉一条黑线 ✓
     * （作者 2026-10-10：「要那种从我的后背伸出来的感觉……可以做一个黑线连接着手腕跟后背」）。
     *
     * <p>为什么还是粒子而不是真几何线：那要自己写世界空间渲染
     * （{@code RenderLevelStageEvent} ＋ 自建 RenderType），风险与工作量都大得多 ✗；
     * 而**密到 {@link #CORD_SPACING} 格间距的粒子本来就成线** ✓
     * （与 {@code TNDarkHandCord} 同一套观感 ✓ —— 区别只是那条线是给"飞出去的手"用的，
     * 本实体自己就是那只手 ✓）。
     */
    private void cord(LivingEntity caster) {
        if (!(level() instanceof ServerLevel server) || caster == null) {
            return;
        }
        Vec3 from = backPoint(caster);
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

    /** 施法者后背那一点（沿视线反方向外移 {@link #BACK_OFFSET} 格、身高 65% 处）✓。 */
    private Vec3 backPoint(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        if (back.lengthSqr() < 1.0E-6D) {
            back = new Vec3(0.0D, 0.0D, 1.0D);
        }
        return caster.position()
                .add(0.0D, caster.getBbHeight() * 0.65D, 0.0D)
                .add(back.normalize().scale(BACK_OFFSET));
    }

    /** 目标身上裹一层黑雾 ⇒ 站在旁边的人**看得见**"他被什么抓住了" ✓。 */
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

    /** 一小团黑气 ＋ 一点灵魂火（手的"存在感"✓）。 */
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
     * <p>⚠️ 粒子 id **不能写死** ✗（铁律第十三条：{@code assets/<ns>/particles/<id>.json} 存在
     * 证明不了任何事，必须是真 {@code ParticleType}）；而且
     * {@code ParticleType} 本身**不是** {@code ParticleOptions}（只有 {@code SimpleParticleType} 才是）
     * ⇒ 必须先 {@code instanceof} 再赋值，直接写三元式**编译不过** ✗
     * （同一个坑见 {@code TNDarkDrainEntity.blood()} ✓）。
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

    /** 档位：从法术 id 反查目录（拿不到就退到 t1）✓。 */
    private static int tierOf(ResourceLocation spellId) {
        if (spellId == null) {
            return FALLBACK_TIER;
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(spellId);
        return entry == null || entry.tier() <= 0 ? FALLBACK_TIER : entry.tier();
    }

    /**
     * 抓谁：<b>命中点附近最近的敌人</b> ✓。
     *
     * <p>为什么用"命中点附近"就够：引擎是在**命中点**生成本实体的 ✓
     * （{@code SPAWN} 的语义就是把实体放在被打中的地方）⇒ 受害者本来就在脚下 ✓。
     * 这与 {@code TNDarkDrainEntity.findVictim} 的**兜底那条路**同源 ✓；
     * 那边多了一条"优先取玩家瞄准的那只"（{@code TNDarkAimMechanics.recentTargetId}）——
     * 本实体**故意不接它** ✗：那条 API 所在的文件此刻正被另一个会话改着（协作铁律：
     * 别把新代码挂到正在被别人写的文件上 ✗），而引擎已经把我们放在命中点，
     * "最近那只"与"瞄的那只"在实际手感里是同一只 ✓。
     */
    private LivingEntity findVictim(LivingEntity caster) {
        double radius = 3.0D;
        AABB box = new AABB(
                getX() - radius, getY() - radius, getZ() - radius,
                getX() + radius, getY() + radius, getZ() + radius);
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != caster && !e.isSpectator()
                        && !(caster instanceof Player owner && e.isAlliedTo(owner)));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : found) {
            double distance = candidate.distanceToSqr(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 本模组 id（保留给日志/调试，和别的法术实体一致 ✓）。 */
    public static ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "night_grip");
    }
}
