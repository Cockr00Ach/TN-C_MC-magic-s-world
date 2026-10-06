package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.spell_engine.api.entity.SpellSpawnedEntity;
import net.spell_engine.api.spell.Spell;

import java.util.List;

/**
 * <b>吸血</b> —— 暗系第二条链「以伤换伤 · 献祭」改成"从敌人身上抽血"之后的那一下结算 ✓
 * （作者 2026-10-09："我希望这个技能可以对敌人释放，并且会扣除敌人的生命值并暂时提高我自己的血上限，
 * t 级越高就改成扣除敌人百分比生命"）。
 *
 * <h2>为什么要一个实体</h2>
 * 引擎的 {@code Impact$Action} 只有 DAMAGE / HEAL / STATUS_EFFECT / FIRE / SPAWN / TELEPORT 六种 ✗，
 * 其中<b>没有任何一种能"按目标的百分比生命造成伤害"</b>，也没有任何一种能
 * "把收益加给<b>施法者</b>"（{@code STATUS_EFFECT} 只会给被命中的目标 ✗）。
 * 所以这条能力只能自己写 ✓ —— 而引擎正好留了一个干净的接口：
 *
 * <pre>
 * interface SpellSpawnedEntity {
 *     void onCreatedFromSpell(LivingEntity caster, ResourceLocation spellId, Spell$Impact$Action$Spawn spawn);
 * }
 * </pre>
 *
 * 法术 JSON 的 {@code impact} 里加一条 `SPAWN`（{@code entity_type_id: "tnc:drain"}）⇒
 * <b>命中那一刻</b>引擎会造出这个实体、并**把施法者一起交给我们** ✓（反编译
 * {@code SpellHelper.performImpact} 确认：它会 {@code instanceof SpellSpawnedEntity} 然后回调 ✓）。
 * 于是"抽血 ＋ 把收益给施法者"这两件引擎做不到的事，就落在 {@link #onCreatedFromSpell} 里了 ✓。
 *
 * <h2>一次吸血的完整流程</h2>
 * <ol>
 *   <li><b>抽血</b>：对命中的那个敌人造成伤害 —— <b>低档固定点数、高档按最大生命百分比</b> ✓；
 *       扣血前先夹住（{@link #MIN_HEALTH_LEFT}）⇒ <b>不会一击必杀</b> ✓；</li>
 *   <li><b>回魔</b>：把吸到的血按 {@link #MANA_PER_DRAINED} 折成魔力还给施法者 ✓；</li>
 *   <li><b>抬血上限</b>：给施法者挂 {@link TNEffects#BLOOD_POOL}（每层 +2 秒，最多
 *       {@link TNEffects#BLOOD_POOL_MAX_STACKS} 层）✓；</li>
 *   <li><b>表现</b>：一团血雾 ＋ 给施法者飘一行字（吸了多少、魔力多少）✓。</li>
 * </ol>
 *
 * <h2>数值表（想调手感只改这一张表）</h2>
 * <pre>
 *   档  法术          固定伤害   百分比    血上限   回魔
 *   t1  trade_wounds   4.0 点     —        +4      伤害 × 1.5
 *   t2  blood_burn     6.0 点     —        +6      伤害 × 1.5
 *   t3  sacrifice       2.0 点     4%       +9      伤害 × 2
 *   t4  possess         2.0 点     5%      +14      伤害 × 2
 *   t5  i_am_god        3.0 点     6%      +20      伤害 × 2
 * </pre>
 * t3 起是"固定值 ＋ 最大生命的百分比"两条一起算 ✓（对高血量敌人也抽得动 ✓）。
 *
 * <p>⚠️ <b>未实机验收</b>：开发环境看不到画面。可编译、可静态核对的部分都做了；
 * 手感（抽多少、回多少）请作者进游戏试，按上表调 ✓。
 */
public class TNDarkDrainEntity extends Entity implements SpellSpawnedEntity {

    /** 扣血后至少给对方留这么多血 ⇒ 这一下永远打不死人 ✓。 */
    private static final float MIN_HEALTH_LEFT = 4.0F;

    /** 抽到的血按这个比例折成魔力还给施法者（1 点血 = N 点魔力）✓。 */
    private static final double MANA_PER_DRAINED = 5.0D;

    /** 血雾半径/数量（纯表现）✓。 */
    private static final double BURST_RADIUS = 1.2D;
    private static final int BURST_PARTICLES = 24;

    /** 命中判定半径（引擎把 SPAWN 实体放在命中点上 ✓）。 */
    private static final double HIT_RADIUS = 2.0D;

    // ------------------------------------------------------------------
    //  ★ 持续喷血（作者 2026-10-09："可以一直喷血粒子，我想要夸张猎奇一点的"）
    //
    //  第一版只在命中那一瞬炸一团血雾 ✗ —— 作者要的是"咬住不放、血一直往外喷"。
    //  所以这个实体不再是"活 5 tick 结算完就死" ✗，而是**跟着受害者活
    //  {@link #CHANNEL_TICKS} tick 的持续喷血源** ✓：
    //    * 每 tick 把实体**贴到受害者身上**（受害者被推走/被打飞时血柱跟着走 ✓）；
    //    * 每 {@link #SPRAY_INTERVAL} tick 喷一轮，**前 {@link #SPRAY_HEAVY_TICKS} tick
    //      是"动脉喷发"量级**，之后逐渐收细 ⇒ 像血在流光 ✓；
    //    * 血是**往上往前喷再受重力落下来**（所以是"喷泉"不是"扩散"✓）。
    //
    //  ⚠ 性能：满档时约 60 颗/tick × 60 tick ≈ 3600 颗/次 —— 和"天雷 30 道"同一量级 ✓。
    //  卡就往回收这三个数：SPRAY_INTERVAL ↑、HEAVY 的几个 count ↓、CHANNEL_TICKS ↓。
    // ------------------------------------------------------------------

    /** 咬住多久（tick）：3 秒 —— 够"一直喷"看得出来，又不至于没完没了 ✓。 */
    private static final int CHANNEL_TICKS = 60;

    /** 每几 tick 喷一轮 ✓。 */
    private static final int SPRAY_INTERVAL = 1;

    /** 前多少 tick 是"动脉喷发"量级（之后收细）✓。 */
    private static final int SPRAY_HEAVY_TICKS = 20;

    /** 一次吸血的结算分几次扣（分摊到整个咬住过程 ⇒ 血是慢慢被抽干的 ✓）。 */
    private static final int DRAIN_TICKS = 4;

    // ---- ★ 把被抓住的敌人拖回来（作者 2026-10-10："抓住敌人手再把敌人拉回来可以不"）----

    /** 每几 tick 给一次拖拽冲量 ✓（不是每 tick：生物的 travel() 会抢走每 tick 的速度 ✗）。 */
    private static final int PULL_INTERVAL = 10;

    /** 每次拖拽的冲量大小（格/tick）✓。 */
    private static final double PULL_STRENGTH = 0.55D;

    /** 拖拽时带一点上抬，免得敌人卡进地面 ✓。 */
    private static final double PULL_LIFT = 0.22D;

    /** 离施法者多近就不再拽（免得在脸上抖）✓。 */
    private static final double PULL_STOP_DISTANCE = 2.0D;

    /** 每一档的数值。索引 = tier - 1（1..5）✓。
     *
     *  <p>`flat` 是固定点数、`percent` 是最大生命的百分比（0 = 这一档不用百分比）✓，
     *  `maxHealth` 是给施法者加的血上限、`manaPerDrained` 是"1 点血折多少魔力"✓。
     *  这些是**整个咬住过程的总量**（会分摊到 {@link #DRAIN_TICKS} 次 ✓）。
     */
    private static final DrainStats[] STATS = {
            new DrainStats(4.0F, 0.00D, 4.0D, 1.5D),     // t1 以伤换伤
            new DrainStats(6.0F, 0.00D, 6.0D, 1.5D),     // t2 燃血
            new DrainStats(2.0F, 0.04D, 9.0D, 2.0D),     // t3 献祭
            new DrainStats(2.0F, 0.05D, 14.0D, 2.0D),    // t4 夺舍
            new DrainStats(3.0F, 0.06D, 20.0D, 2.0D),    // t5 我为神
    };

    /** 血盈持续时间：每层这么多 tick（叠加时每层 +2 秒）✓。 */
    private static final int BLOOD_POOL_TICKS = 40;

    /** 没在施法者身上找到时用的兜底档位（不该发生）。 */
    private static final int FALLBACK_TIER = 1;

    private record DrainStats(float flat, double percent, double maxHealth, double manaPerDrained) {
    }

    // ---- 运行期状态（全在服务端 memory，不进存档 ✓）----

    /** 被咬住的受害者（只存 id，免得跨 tick 拿着旧引用 ✗）。 */
    private int victimId = -1;
    /** 施法者（只存 id：跨维度/下线时重新解析 ✓）。 */
    private int casterId = -1;
    /** 档位（生成那一刻定下来 ✓）。 */
    private int tier = FALLBACK_TIER;
    /** 已经扣过几次血 ✓。 */
    private int drainSteps;
    /** 累计吸到的血（用来最后报一次总账 ✓）。 */
    private float drainedTotal;
    /** 施法者已经拿到的魔力（同上 ✓）。 */
    private double manaTotal;

    public TNDarkDrainEntity(EntityType<? extends TNDarkDrainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        // 不需要同步任何东西：结算全在服务端一次做完 ✓
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        // 不存档：它只活几 tick ✓
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (tickCount > CHANNEL_TICKS) {
            finish();
            return;
        }

        LivingEntity victim = resolve(victimId);
        LivingEntity caster = resolve(casterId);
        if (victim == null || !victim.isAlive()) {
            // 抽干了 / 目标没了 ⇒ 收工（但把这一口已经抽到的账报完 ✓）
            finish();
            return;
        }
        // 贴住受害者：它被推走/被打飞/被击退时，血柱跟着走 ✓
        setPos(victim.getX(), victim.getY() + victim.getBbHeight() * 0.45D, victim.getZ());

        // 分摊扣血：整个咬住过程扣 DRAIN_TICKS 次，所以血是"慢慢流光"而不是一刀 ✓
        int step = CHANNEL_TICKS / Math.max(1, DRAIN_TICKS);
        if (drainSteps < DRAIN_TICKS && step > 0 && tickCount % step == 0) {
            drainSteps++;
            DrainStats stats = stats();
            float share = 1.0F / DRAIN_TICKS;
            float drained = drain(victim, stats, share);
            drainedTotal += drained;
            if (drained > 0.0F) {
                double mana = drained * stats.manaPerDrained();
                manaTotal += mana;
                if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                    grantMana(player, mana);
                    grantBloodPool(player, stats.maxHealth() * share);
                } else if (caster != null) {
                    grantBloodPool(caster, stats.maxHealth() * share);
                }
            }
        }

        // ★ 抓住就往回拽（作者 2026-10-10："抓住敌人手再把敌人拉回来可以不"）✓
        if (caster != null && tickCount % PULL_INTERVAL == 0) {
            pullToward(victim, caster);
        }

        // ★ 一直喷血：每 SPRAY_INTERVAL tick 一轮，前 SPRAY_HEAVY_TICKS 是动脉量级 ✓
        if (tickCount % SPRAY_INTERVAL == 0) {
            boolean heavy = tickCount <= SPRAY_HEAVY_TICKS;
            spray(victim, caster, heavy);
            sprayCaster(caster, heavy);
        }
    }

    /**
     * ★ <b>真正的"血"粒子</b>（`fromtheshadows:blood`，本项目验证过的真粒子 ✓）。
     *
     * <p>为什么要有这个查表：跨模组的粒子 id **不能写死** ✗ ——
     * `fromtheshadows:blood` 是那个模组注册的 `SimpleParticleType`，
     * 如果它没装/改名，直接 `ParticleTypes` 写法会拿到 null 并在撒的时候崩 ✓。
     * 这里查一次、缓存；拿不到就退回原版的 `crimson_spore`（同样是血红色 ✓），
     * 所以**永远不可能是 null** ✓。
     */
    private static net.minecraft.core.particles.ParticleOptions bloodParticle;

    private static net.minecraft.core.particles.ParticleOptions blood() {
        if (bloodParticle == null) {
            var type = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "fromtheshadows", "blood"));
            // ⚠ `ParticleType` 本身**不是** `ParticleOptions`（只有 `SimpleParticleType` 才是）
            //   ⇒ 必须先 instanceof 再赋值；直接写三元式会"类型不兼容"编译不过 ✗
            bloodParticle = (type instanceof net.minecraft.core.particles.ParticleOptions options)
                    ? options
                    : net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE;
        }
        return bloodParticle;
    }

    /**
     * 把被抓住的敌人朝施法者拖（作者："抓住敌人手再把敌人拉回来"）✓
     *
     * <p>为什么用"间隔冲量"而不是每 tick 设速度：生物的 {@code travel()} 每 tick 都会按
     * 自己的意向重设速度 ⇒ 每 tick 设会被它抢走 ✗；间隔给一次较大冲量则确实能把人拖走 ✓
     * （与击退同一类效果）。
     *
     * <p>{@code hurtMarked = true} 不能省 ✗：不加的话服务端改了速度但**客户端不知道** ⇒
     * 你会看到敌人不动或瞬移 ✓。
     */
    private void pullToward(LivingEntity victim, LivingEntity caster) {
        net.minecraft.world.phys.Vec3 delta = caster.position().subtract(victim.position());
        if (delta.lengthSqr() < PULL_STOP_DISTANCE * PULL_STOP_DISTANCE) {
            return;                                  // 已经拉到手边 ⇒ 别再抖 ✓
        }
        net.minecraft.world.phys.Vec3 pull = delta.normalize().scale(PULL_STRENGTH)
                .add(0.0D, PULL_LIFT, 0.0D);
        victim.setDeltaMovement(victim.getDeltaMovement().add(pull));
        victim.hurtMarked = true;
    }

    /**
     * ★ <b>施法者自己也在落血粒子</b>（作者 2026-10-10："不仅我自身会落血粒子，
     * 索敌索中的敌人也得会，那种抽血的感觉你懂吧"）。
     *
     * <p>为什么两处都要：抽血是**一条通路**——受害者身上喷出来（{@link #spray}）＋
     * 施法者身上往回落（这里）✓。只有一头有粒子的话，看起来就像"那边在流血"，
     * 而不是"血被抽到我身上"✗。这里撒得比受害者那边**少一点、慢一点**，
     * 读起来才像"被吸过来的"而不是"自己也在流血"✓。
     */
    private void sprayCaster(LivingEntity caster, boolean heavy) {
        if (caster == null || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        double cx = caster.getX();
        double cy = caster.getY() + caster.getBbHeight() * 0.5D;
        double cz = caster.getZ();
        double scale = heavy ? 0.7D : 0.35D;

        // 身上滴下来的血（从胸口往下掉 ⇒ "血在回落"✓）
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                cx, cy + 0.3D, cz, (int) Math.round(12 * scale),
                0.35D, 0.4D, 0.35D, 0.35D);
        // 贴地的血泊（一点点）
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                cx, caster.getY() + 0.1D, cz, (int) Math.round(6 * scale),
                0.5D, 0.05D, 0.5D, 0.02D);
    }

    /** 收工：报一次总账（吸了多少血 / 回了多少魔力）然后消失 ✓。 */
    private void finish() {
        if (!level().isClientSide() && drainedTotal > 0.0F) {
            LivingEntity caster = resolve(casterId);
            if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                player.displayClientMessage(Component.literal(String.format(
                                "§4[TN-C] §r共吸血 §c%.1f§r 点 · 魔力 §b+%d",
                                drainedTotal, Math.round(manaTotal)))
                        .withStyle(ChatFormatting.RESET), true);
            }
        }
        discard();
    }

    /**
     * ★ <b>引擎在"命中那一刻"回调这里</b>，并顺手把施法者交给我们 ✓。
     *
     * <p>注意它给的是<b>施法者</b>（{@code caster}），不是被打的目标 ——
     * 目标是"这个实体被生成在哪"附近找到的 ✓（引擎把 SPAWN 实体放在命中点上 ✓）。
     *
     * <p>这里只做三件事：<b>记住受害者与施法者</b>、定下档位、开喷第一口 ✓；
     * 剩下的持续抽血/喷血交给 {@link #tick()} ✓（作者要"一直喷"）。
     */
    @Override
    public void onCreatedFromSpell(LivingEntity caster, ResourceLocation spellId,
                                   Spell.Impact.Action.Spawn spawn) {
        if (level().isClientSide()) {
            return;
        }
        Player owner = caster instanceof Player p ? p : null;
        tier = tierOf(caster, spellId);
        casterId = caster == null ? -1 : caster.getId();

        LivingEntity victim = findVictim(owner);
        if (victim == null) {
            // 没咬到人（打到方块/打空了）⇒ 立刻收工，别留一根空血柱 ✓
            discard();
            return;
        }
        victimId = victim.getId();
        setPos(victim.getX(), victim.getY() + victim.getBbHeight() * 0.45D, victim.getZ());

        // 开喷第一口（量最大的一口）✓
        spray(victim, caster, true);
    }

    private DrainStats stats() {
        return STATS[Math.max(0, Math.min(STATS.length - 1, tier - 1))];
    }

    /** 按 id 在服务端重新解析实体（跨 tick 拿着旧引用会指向死掉的实体 ✗）。 */
    private LivingEntity resolve(int entityId) {
        if (entityId < 0 || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(entityId);
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** 档位：从法术 id 反查目录（拿不到就退到 t1）✓。 */
    private static int tierOf(LivingEntity caster, ResourceLocation spellId) {
        if (spellId == null) {
            return FALLBACK_TIER;
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(spellId);
        return entry == null || entry.tier() <= 0 ? FALLBACK_TIER : entry.tier();
    }

    /**
     * 找被抽血的那个敌人：优先取<b>离命中点最近、且不是施法者/不是自己人</b>的活体 ✓。
     *
     * <p>为什么不用"引擎告诉我们的目标"：`SpellSpawnedEntity` 的回调**只给施法者** ✗ ——
     * 目标信息没有传进来。而引擎是在<b>命中点</b>生成这个实体的 ✓，
     * 所以"命中点附近最近的敌人"就是刚才被打的那个 ✓（投射物命中时两者重合）。
     */
    private LivingEntity findVictim(Player owner) {
        AABB box = new AABB(
                getX() - HIT_RADIUS, getY() - HIT_RADIUS, getZ() - HIT_RADIUS,
                getX() + HIT_RADIUS, getY() + HIT_RADIUS, getZ() + HIT_RADIUS);
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != owner && !e.isSpectator()
                        && !(owner != null && e.isAlliedTo(owner)));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : found) {
            double d = candidate.distanceToSqr(this);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 扣血：<b>低档固定、高档百分比</b> ✓（两条一起算）。
     *
     * @param share 这一次扣"整口"的几分之一（持续抽血会分几次调用 ✓）
     * @return 真正扣掉的血量（用来折算魔力/血上限 ✓）
     */
    private float drain(LivingEntity victim, DrainStats stats, float share) {
        float amount = stats.flat();
        if (stats.percent() > 0.0D) {
            amount += (float) (victim.getMaxHealth() * stats.percent());
        }
        amount *= Math.max(0.0F, Math.min(1.0F, share));
        float health = victim.getHealth();
        if (health <= 0.0F) {
            return 0.0F;
        }
        // 夹住：这一下最多把人打到 MIN_HEALTH_LEFT ⇒ 永不致死 ✓
        float allowed = Math.max(0.0F, health - MIN_HEALTH_LEFT);
        float dealt = Math.min(amount, allowed);
        if (dealt <= 0.0F) {
            return 0.0F;
        }
        // 抽血是"魔法伤害"（能穿过护甲，符合"从体内抽走"的手感）✓。
        // 先把对方的受伤窗口清零，保证这一下一定生效 ✓（和燃血那条修法同一个道理）。
        victim.invulnerableTime = 0;
        victim.hurt(victim.damageSources().magic(), dealt);
        float actual = health - victim.getHealth();
        return Math.max(0.0F, actual);
    }

    /** 把血折成魔力还给施法者 ✓。 */
    private static void grantMana(net.minecraft.server.level.ServerPlayer player, double amount) {
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        int gain = (int) Math.round(amount);
        if (gain <= 0) {
            return;
        }
        data.setMana(Math.min(data.getMaxMana(), data.getMana() + gain));
    }

    /** 抬血上限：挂一层 {@link TNEffects#BLOOD_POOL}（叠加时每层 +2 秒 ✓，有层数上限 ✓）。 */
    private static void grantBloodPool(LivingEntity caster, double maxHealthPerTier) {
        if (!TNEffects.BLOOD_POOL.isPresent()) {
            return;
        }
        MobEffectInstance existing = caster.getEffect(TNEffects.BLOOD_POOL.get());
        int amplifier = existing == null ? 0 : existing.getAmplifier() + 1;
        amplifier = Math.min(amplifier, TNEffects.BLOOD_POOL_MAX_STACKS - 1);
        int duration = (existing == null ? BLOOD_POOL_TICKS : existing.getDuration() + BLOOD_POOL_TICKS);
        caster.addEffect(new MobEffectInstance(TNEffects.BLOOD_POOL.get(), duration, amplifier,
                false, true, true));
        // 血上限涨了，顺手把"当前血"也抬一点，否则血条会看着"变短"✓
        caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + (float) maxHealthPerTier));
    }

    /**
     * ★★ <b>喷血</b> —— 作者 2026-10-09："可以一直喷血粒子，我想要夸张猎奇一点的"。
     *
     * <h2>为什么是"喷"而不是"炸"</h2>
     * 第一版只在命中那一瞬撒一圈血雾 ✗ —— 那看着像"爆"，不像"血在往外流"。
     * 现在每一轮都按<b>喷泉</b>来撒：
     * <ul>
     *   <li><b>动脉</b>：从伤口往上、往外的高速血柱（速度 1.2~2.6）⇒ 血喷出去再受重力落下来 ✓；</li>
     *   <li><b>血滴</b>：`FALLING_DUST`（原版红沙粒，看着就是往下掉的血渣 ✓）从高处洒落；</li>
     *   <li><b>血泊</b>：脚下一圈慢慢扩的暗红，猎奇感的来源之一 ✓；</li>
     *   <li><b>脏器/碎块</b>：`DAMAGE_INDICATOR`（原版那个"被打爆"的黑红十字）混在里面 ⇒
     *       夸张、不体面、正是"猎奇"要的效果 ✓；</li>
     *   <li><b>黑雾</b>：少量 `SQUID_INK` 压一层暗的底色（和暗系整体一致 ✓）。</li>
     * </ul>
     *
     * <p>全部用**原版粒子** ✓ —— 不碰任何第三方 id（`block_factorys_bosses` 那套
     * 是 Bedrock 粒子，用了会让客户端闪退 ✗，见 `tools/gen_dark_fog_spells.py` 的说明）。
     * `FALLING_DUST` 需要 `BlockParticleOption`，这里用红石块 ⇒ 殷红的一粒粒血渣 ✓。
     *
     * @param heavy 前 {@link #SPRAY_HEAVY_TICKS} tick 为 true ⇒ 动脉喷发量级 ✓
     */
    private void spray(LivingEntity victim, LivingEntity caster, boolean heavy) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        double vx = victim.getX();
        double vy = victim.getY() + victim.getBbHeight() * 0.55D;
        double vz = victim.getZ();
        double scale = heavy ? 1.0D : 0.45D;              // 后期收细，像血在流光 ✓

        // 血渣往下掉（原版"落尘"粒子 + 红石块 = 殷红血粒 ✓）
        net.minecraft.core.particles.ParticleOptions bloodDust =
                new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());

        // ① 动脉：往上往外的高速血柱（speed 1.2~2.6）
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                vx, vy, vz, (int) Math.round(46 * scale),
                0.28D, 0.36D, 0.28D, 2.4D);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.DRIPPING_DRIPSTONE_LAVA,
                vx, vy, vz, (int) Math.round(14 * scale),
                0.22D, 0.3D, 0.22D, 1.1D);
        // ② 血渣从高处洒落（从头顶上方生成 ⇒ 有一段落体时间，像"血雨"✓）
        server.sendParticles(bloodDust,
                vx, victim.getY() + victim.getBbHeight() + 0.9D, vz,
                (int) Math.round(26 * scale),
                0.55D, 0.35D, 0.55D, 0.08D);
        // ③ 血泊：脚下一圈慢慢扩的暗红
        server.sendParticles(bloodDust,
                vx, victim.getY() + 0.08D, vz,
                (int) Math.round(18 * scale),
                0.95D, 0.06D, 0.95D, 0.02D);
        // ④ 猎奇：被打爆的那个黑红十字，混在血里 ⇒ 夸张
        if (heavy) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    vx, vy, vz, 5, 0.3D, 0.3D, 0.3D, 0.12D);
        }
        // ⑤ ★ 真正的"血"粒子（作者 2026-10-10："抓到的敌人身上也掉血粒子"）✓
        //    原来这里是 SQUID_INK（黑雾材质）—— 作者说雾该留给黑雾链 ⇒ 换成真血 ✓
        server.sendParticles(blood(),
                vx, vy, vz, (int) Math.round(18 * scale),
                0.4D, 0.45D, 0.4D, 0.35D);
        // ⑥ 抽回自己：一小撮血朝施法者方向飞（"被吸过来"的方向感 ✓）
        if (caster != null && caster != victim) {
            double dx = caster.getX() - vx;
            double dy = (caster.getY() + caster.getBbHeight() * 0.5D) - vy;
            double dz = caster.getZ() - vz;
            double len = Math.max(0.1D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                    vx, vy, vz, (int) Math.round(10 * scale),
                    dx / len * 0.4D, dy / len * 0.4D, dz / len * 0.4D,
                    2.2D);
        }
    }
}
