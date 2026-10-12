package com.tnc.tnc.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.casting.SpellCast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <b>黑夜之手链的"施法那一刻"</b>：把手甩出去 ＋ 收代价 ✓
 * （作者 2026-10-10：「暗魔法的黑夜之手可能要整个改一下」，参考《Re:Zero》486 的不可视之手 ✓）。
 *
 * <h2>为什么生成手这件事落在 Java，而不是法术 JSON 的 SPAWN</h2>
 * 重做的第一步曾经用 {@code impact} 里一条 {@code SPAWN} 让引擎在<b>命中那一刻</b>生成实体 ✓
 * （那条路本身是验证过的，见 {@code TNDarkDrainEntity}）。但作者 2026-10-10 追加的要求
 * 把那条路否掉了 ✗：
 * <ul>
 *   <li><b>「不能所有手都是直直飞出去，要有弯的弧线」</b> —— 引擎的投射物只能沿直线飞
 *       （{@code LaunchProperties} 只有 velocity/多发，javap 确认没有弧线/散射字段 ✗），
 *       而 {@code SPAWN} 是在**命中之后**才生成实体 ⇒ 拿到手时"飞行"这一段已经过去了 ✗；</li>
 *   <li>顺手也解决了"手一直在旋转 / 掌面不对"（那两件事都是引擎那套朝向逻辑造成的 ✗，
 *       见 {@link TNNightGripEntity} 的类注释）。</li>
 * </ul>
 * ⇒ 现在法术 JSON 改成 {@code release.target.type = "SELF"}（**不再产生引擎投射物** ✗），
 * 由本类在 {@code SPELL_CAST} 的 RELEASE 上自己把手甩出去 ✓。
 * 这一条同样是<b>已验证</b>的：本仓有 50 个 SELF 目标的法术（雷速、风场、血烙印…），
 * 它们都靠同一个事件扣魔力、全部正常工作 ✓。
 *
 * <h2>抓谁：瞄住谁就抓谁 ✓</h2>
 * 施法那一刻用<b>准星射线</b> {@code player.pick(32, 0, false)}（就是雷击 t3 用的那一套 ✓）：
 * <ul>
 *   <li>射到<b>活着的敌人</b> ⇒ 手朝它甩过去、抓住它 ✓；</li>
 *   <li>射到方块/空气 ⇒ 没有目标 ⇒ 手沿弧线飞到准星前方然后散掉 ✓
 *       （＝"没瞄到就抓不到人"的手感 ✓，作者 2026-10-10 要的就是这种"玩家瞄住敌人"的释放方式 ✓）。</li>
 * </ul>
 *
 * <h2>代价：反噬 ✓（每放一次都付，与有没有抓到人无关）</h2>
 * <pre>
 *   档  法术            扣自己最大生命   "头痛"（虚弱）
 *   t1  night_hand      —               —
 *   t2  night_raid      2%              —
 *   t3  night_embrace   3%              0.5 s
 *   t4  black_ruin      5%              1.5 s
 *   t5  slay_light      8%              3.0 s
 * </pre>
 * ★ <b>永不致死</b> ✓：扣血前先夹住，保底留 {@link #MIN_HEALTH_LEFT} 点 ——
 * 和「以伤换伤」那条链的燃血**同一条规则** ✓（作者当初确认过的保底 ✓）。
 *
 * <p>⚠️ "头痛"这一半**故意放在法术 JSON 里** ✗（每个档位文件各写自己的 duration）——
 * 因为 {@code impact} 数组必须非空（引擎的空列表用法在本整合包里**一例都没有**，
 * 不去发明未验证的语义 ✗）；而"按最大生命百分比扣血"引擎做不到，
 * 所以那一半留在本类里 ✓。两边分工：<b>JSON 管头痛，Java 管扣血</b> ✓。
 *
 * <p>⚠️ <b>未实机验收</b>（开发环境看不到画面）：手感请作者进游戏试 ✓。
 */
public final class TNDarkHandMechanics {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/nighthand");

    /** 黑夜之手链的分组名（五个法术 JSON 里都写着它 ✓）—— 不写死 id，加档/改名都不用动这里 ✓。 */
    public static final String HAND_GROUP = "dark_hand";

    /** 每档扣"自己最大生命"的百分比 ✓（索引 = tier − 1；0 = 这一档不反噬）。 */
    private static final double[] BACKLASH_PERCENT = {0.00D, 0.02D, 0.03D, 0.05D, 0.08D};

    /** 每档甩出几只手 ✓（作者：从背后伸出"好几只手"；档位越高越多 ✓）。**t5 不用它**（见下）。 */
    private static final int[] HANDS = {1, 2, 3, 5, 7};

    // ------------------------------------------------------------------
    //  ★ 2026-10-10 追加：t5 换形态 —— 「很多只大手从天而降砸向敌人，大手的掌心向下」
    // ------------------------------------------------------------------

    /** 从这一档起换成"天降"形态 ✓（作者：t5 变成很多个大手从天而降）。 */
    private static final int SKYFALL_TIER = 5;

    /** 天降同时落几只 ✓（作者："很多个大手"）。 */
    private static final int SKYFALL_HANDS = 7;

    /** 天降：在瞄准点多大范围里找敌人（一只敌人落一只手 ✓）。 */
    private static final double SKYFALL_RADIUS = 12.0D;

    /** 天降：敌人不够时，多出来的手撒在中心多大的一个圈上 ✓。 */
    private static final double SKYFALL_RING = 4.5D;

    /** 瞄准射线的长度（格）—— 与雷击 t3 的 {@code player.pick(32, 0, false)} 同一口径 ✓。 */
    private static final double AIM_RANGE = 32.0D;

    /** 反噬**永远不会**把施法者扣到这个值以下 ✓（与燃血同一条保底规则）。 */
    private static final float MIN_HEALTH_LEFT = 1.0F;

    static {
        // ★ 挂在引擎自己的事件上（不是 Forge 总线 ✗ —— @SubscribeEvent 会编译通过、运行时静默不触发）
        try {
            CombatEvents.SPELL_CAST.register(TNDarkHandMechanics::onSpellCast);
            LOGGER.info("TN-C: night hand cast listener registered on SpellEngine SPELL_CAST");
        } catch (Throwable t) {
            // 引擎换版本/接口变了也不该让整个 mod 加载不了 ✓
            LOGGER.warn("TN-C: night hand listener registration failed ({})", t.toString());
        }
    }

    private TNDarkHandMechanics() {
    }

    /**
     * 空方法，唯一作用是<b>把类加载起来</b>（Java 类懒加载：静态块不跑，监听器就挂不上 ✗）。
     * 由 {@link TNEffects#register} 在模组构造期调用一次 ✓。
     */
    public static void ensureLoaded() {
        // 故意空着：类一被引用，上面的静态块就已经跑过了 ✓
    }

    /**
     * 施法那一刻：甩手 ＋ 扣自己的血 ✓。
     *
     * <p>只看 {@code RELEASE} ✗（不是 {@code CHANNEL}）：蓄力阶段每 tick 都会来一次事件，
     * 在蓄力里甩手会甩出一堆、扣血也会一秒钟扣十几次 ✗。
     *
     * <p>本事件**客户端也会收到**（{@code TNSpellClientVisuals} 就在客户端侧接它做震屏 ✓）
     * ⇒ 必须用 {@code instanceof ServerPlayer} 限定在服务端 ✓。
     */
    private static void onSpellCast(CombatEvents.SpellCast.Args args) {
        if (args == null || args.action() != SpellCast.Action.RELEASE) {
            return;
        }
        SpellInfo info = args.spell();
        if (info == null || info.spell() == null) {
            return;
        }
        Spell spell = info.spell();
        if (!HAND_GROUP.equals(spell.group) || spell.learn == null) {
            return;
        }
        Player caster = args.caster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        int tier = Math.max(1, Math.min(BACKLASH_PERCENT.length, spell.learn.tier));

        backlash(player, tier);
        launch(player, tier);
    }

    /**
     * 把这一档的手放出去 ✓。
     *
     * <p>t1~t4：从施法者背后出发、各走各的弧线（见 {@link TNNightGripEntity#launch}）✓；
     * <b>t5：换成"很多只大手从天而降砸向敌人"</b> ✓（作者 2026-10-10：
     * 「t5 变成有很多个大手从天而降砸向敌人，**大手的掌心向下**」）。
     */
    private static void launch(ServerPlayer player, int tier) {
        ServerLevel level = player.serverLevel();
        if (TNOrbEntities.NIGHT_GRIP == null) {
            // 引擎不在时不生成（那个实体类型本身就在 ModList 守卫里 ✓）
            return;
        }
        int targetId = aimedTargetId(player);
        if (tier >= SKYFALL_TIER) {
            skyfall(player, level, targetId, tier);
            return;
        }
        int count = NightHandReleaseRules.count(tier);
        for (int i = 0; i < count; i++) {
            TNNightGripEntity hand = create(level);
            if (hand == null) {
                return;
            }
            hand.launch(player, targetId, tier, i, count);
            level.addFreshEntity(hand);
        }
        // ★ 一定留一行日志（验收：grep `TN-C: night hand cast` ✓）——
        //   它一次说清"这一发几只、有没有瞄到人、扣了多少血" ✓
        LOGGER.info("TN-C: night hand cast tier={} hands={} target={} owner={}",
                tier, count, targetId < 0 ? "none" : targetId, player.getName().getString());
    }

    /**
     * ★ t5：<b>很多只大手从天而降</b>，各自砸一个敌人 ✓
     * （作者 2026-10-10：「t5 变成有很多个大手从天而降砸向敌人，**大手的掌心向下**」）。
     *
     * <p>落点怎么分：以"瞄准点"为中心找最近的敌人，**一只敌人落一只手** ✓；
     * 敌人不够 {@link #SKYFALL_HANDS} 只 ⇒ 多出来的撒在中心一圈（免得白放 ✗）；
     * 一个敌人都没有 ⇒ 全部撒在准星前方那一带 ✓（"没瞄到人也砸得到地"）。
     *
     * <p>每只手都从落点上方 {@code SKYFALL_HEIGHT}（实体里那个常量）开始压下来，
     * 落地那一下就是范围重击 ✓ —— 掌心朝下由渲染器按模式决定 ✓。
     */
    private static void skyfall(ServerPlayer player, ServerLevel level, int targetId, int tier) {
        Vec3 center = aimPointOf(player, targetId);
        List<LivingEntity> victims = victimsNear(level, player, center);
        int spawned = 0;
        for (int i = 0; i < SKYFALL_HANDS; i++) {
            LivingEntity victim = i < victims.size() ? victims.get(i) : null;
            Vec3 landing = victim != null
                    ? groundNear(level, victim.position(), 3)
                    : groundNear(level, ringPoint(center, i - victims.size()), 24);
            TNNightGripEntity hand = create(level);
            if (hand == null) {
                return;
            }
            hand.launchSkyfall(player, victim == null ? -1 : victim.getId(), tier, i, SKYFALL_HANDS, landing);
            level.addFreshEntity(hand);
            spawned++;
        }
        LOGGER.info("TN-C: night hand skyfall tier={} hands={} onEnemies={} center={} owner={}",
                tier, spawned, victims.size(), center, player.getName().getString());
    }

    /** 新建一只（失败就统一在这里报一次警告 ✓）。 */
    private static TNNightGripEntity create(ServerLevel level) {
        TNNightGripEntity hand = TNOrbEntities.NIGHT_GRIP.get().create(level);
        if (hand == null) {
            LOGGER.warn("TN-C: night hand could not be created (entity type missing?)");
        }
        return hand;
    }

    /** 天降的中心：瞄到谁就落在谁那儿；没瞄到 ⇒ 准星前方（被墙截断）那个点 ✓。 */
    private static Vec3 aimPointOf(ServerPlayer player, int targetId) {
        Entity aimed = targetId < 0 ? null : player.serverLevel().getEntity(targetId);
        if (aimed != null) {
            return aimed.position();
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 view = player.getViewVector(1.0F);
        HitResult blockHit = player.pick(AIM_RANGE, 0.0F, false);
        double limit = blockHit.getType() == HitResult.Type.MISS
                ? AIM_RANGE
                : Math.max(0.0D, blockHit.getLocation().distanceTo(eye));
        return eye.add(view.scale(limit));
    }

    /** 中心附近最近的敌人（最多 {@link #SKYFALL_HANDS} 只，按距离排 ✓）。 */
    private static List<LivingEntity> victimsNear(ServerLevel level, ServerPlayer player, Vec3 center) {
        AABB box = new AABB(
                center.x - SKYFALL_RADIUS, center.y - SKYFALL_RADIUS, center.z - SKYFALL_RADIUS,
                center.x + SKYFALL_RADIUS, center.y + SKYFALL_RADIUS, center.z + SKYFALL_RADIUS);
        List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e != player && !e.isAlliedTo(player)));
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
        return found.size() > SKYFALL_HANDS ? found.subList(0, SKYFALL_HANDS) : found;
    }

    /** 多出来的手撒在中心一圈 ✓。 */
    private static Vec3 ringPoint(Vec3 center, int index) {
        double angle = (index % SKYFALL_HANDS) / (double) SKYFALL_HANDS * Math.PI * 2.0D;
        return center.add(Math.cos(angle) * SKYFALL_RING, 0.0D, Math.sin(angle) * SKYFALL_RING);
    }

    /**
     * 把落点按到地面上 ✓（往下找不是空气的那层，最多找 maxDrop 格 ✗ 免得掉进洞里没完）——
     * 和 {@code DarkFogMechanics.groundNear} 同一套做法 ✓。
     */
    private static Vec3 groundNear(ServerLevel level, Vec3 at, int maxDrop) {
        BlockPos pos = BlockPos.containing(at.x, at.y, at.z);
        int guard = 0;
        while (guard++ < maxDrop && pos.getY() > level.getMinBuildHeight()
                && level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY(), at.z);
    }

    /**
     * 玩家瞄住的那只敌人（没有就 -1）✓。
     *
     * <p>★ 走 {@link TNAimRay}（原版准星那一套：方块截断 ＋
     * {@code ProjectileUtil.getEntityHitResult} 实体射线）——
     * **不要用 {@code player.pick(...)} 判 {@code EntityHitResult}** ✗：
     * 那个方法只做方块射线，恒不返回实体命中（javap 实证，作者 2026-10-10 的
     * "无法瞄准敌人"就是它 ✗）。与雷击 t3、以及 {@code TNDarkAimMechanics} 的高亮
     * 现在是同一套判据 ✓ ⇒ 玩家看到"谁被高亮锁定"，抓的就是那一只 ✓。
     */
    private static int aimedTargetId(ServerPlayer player) {
        LivingEntity aimed = TNAimRay.aimedEnemy(player, AIM_RANGE);
        return aimed == null ? -1 : aimed.getId();
    }

    /**
     * 扣施法者自己的血 ✓（每次施法结算一次，**与有没有抓到人无关** ✓
     * —— "用了就要付"，这正是这条链的手感核心 ✓）。
     */
    private static void backlash(ServerPlayer player, int tier) {
        double percent = BACKLASH_PERCENT[Math.max(0, Math.min(BACKLASH_PERCENT.length - 1, tier - 1))];
        if (percent <= 0.0D) {
            return;
        }
        float before = player.getHealth();
        float cost = (float) (player.getMaxHealth() * percent);
        // ★ 先夹住：无论 cost 多大，都要给他留 MIN_HEALTH_LEFT 点 ⇒ 反噬**永不致死** ✓
        float allowed = Math.max(0.0F, before - MIN_HEALTH_LEFT);
        float actual = Math.min(cost, allowed);
        boolean landed = false;
        if (actual > 0.0F) {
            // 清掉受伤窗口：否则"刚被打过"的那一下会被原版静默丢掉 ✗（见类注释）
            player.invulnerableTime = 0;
            landed = player.hurt(player.damageSources().magic(), actual);
        }
        if (actual > 0.0F) {
            player.displayClientMessage(Component.literal(String.format(
                    "§5[TN-C] §r反噬 —— 它烧的是你的血 §c−%.1f", actual)), true);
        }
        LOGGER.info("TN-C: night hand backlash tier={} hp {} -> {} (cost={} landed={} floor={})",
                tier, before, player.getHealth(), String.format("%.2f", actual), landed, MIN_HEALTH_LEFT);
    }
}
