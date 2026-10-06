package com.tnc.tnc.magic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.casting.SpellCast;

/**
 * <b>黑夜之手链的"代价"端</b> —— 反噬 ✓
 * （作者 2026-10-10：参考《Re:Zero》486 的不可视之手 —— 那只手的设定里，
 * 「用一次就要还一次债」是它最核心的一味：头痛、吐血、自我磨损 ✓）。
 *
 * <h2>为什么要有它</h2>
 * 重做前这条链**只有冷却、零代价** ✗ —— 也就是说它和"不可视之手"在体验上毫无关系 ✓。
 * 不可视之手在原作里是**越用越靠近魔女**的能力：威力大、射程短、而且每一发都在烧持有者自己 ✓。
 * 本次重做的两半是配套的：
 * <ul>
 *   <li><b>抓取端</b>（{@link TNNightGripEntity}）：手抓住目标不放、拽回来、捏碎 ✓；</li>
 *   <li><b>代价端</b>（本类）：每放一次，<b>施法者自己掉一截血</b> ＋ t3 起挂一层"头痛" ✓。</li>
 * </ul>
 *
 * <h2>为什么用 {@code SPELL_CAST} 而不是改法术 JSON</h2>
 * 引擎的 {@code Impact$Action} 只有六种（DAMAGE / HEAL / STATUS_EFFECT / FIRE / SPAWN / TELEPORT）✗，
 * 其中**没有任何一种能"按施法者最大生命的百分比扣他自己的血"** ✓
 * —— 但引擎留了一个公开的施法事件：{@code CombatEvents.SPELL_CAST} ✓。
 *
 * <p>⚠️ <b>它<b>不是</b> Forge 事件</b> ✗：用法是 {@code SPELL_CAST.register(listener)}，
 * 挂 {@code @SubscribeEvent} 会**编译通过、运行时静默不触发** ✗（本仓铁律）。
 * 同一个挂法见 {@link DarkFogMechanics} / {@link TNDarkAimMechanics} / {@code SpellEngineManaHook} ✓。
 *
 * <h2>反噬数值（想调手感只改这两张表）</h2>
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
 * t1 是安全档（对应那条链的 t1「血之烙印」也不燃血 ✓）。
 *
 * <p>⚠️ 用的是 {@code hurt(..., MAGIC, ...)} 而不是 {@code setHealth()} ✗ ——
 * {[@code setHealth} 绕开原版的受伤窗口（{@code invulnerableTime}），在"刚被打过"的玩家身上
 * 会被**静默丢掉** ⇒ 表现成"有时扣有时不扣" ✗（作者 2026-10-09 抓到过这个现象 ✓）。
 * 现在这样还白拿三样原版反馈：**屏幕红闪 ＋ 受伤音效 ＋ 正确的死亡判定** ✓
 * —— 作者本次特意强调过"别啥都看不见"，这三样正是最直接的"我付了代价"的可见信号 ✓。
 *
 * <p>⚠️ <b>未实机验收</b>（开发环境看不到画面）：可静态核对的部分都核了，手感请作者进游戏试 ✓。
 */
public final class TNDarkHandMechanics {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/nighthand");

    /** 黑夜之手链的分组名（五个法术 JSON 里都写着它 ✓）—— 不写死 id，加档/改名都不用动这里 ✓。 */
    public static final String HAND_GROUP = "dark_hand";

    /** 每档扣"自己最大生命"的百分比 ✓（索引 = tier − 1；0 = 这一档不反噬）。 */
    private static final double[] BACKLASH_PERCENT = {0.00D, 0.02D, 0.03D, 0.05D, 0.08D};

    /** 每档的"头痛"时长（tick，虚弱一档；0 = 不挂）✓。 */
    private static final int[] HEADACHE_TICKS = {0, 0, 10, 30, 60};

    /** 反噬**永远不会**把施法者扣到这个值以下 ✓（与燃血同一条保底规则）。 */
    private static final float MIN_HEALTH_LEFT = 1.0F;

    static {
        // ★ 挂在引擎自己的事件上（不是 Forge 总线 ✗，见类注释）
        try {
            CombatEvents.SPELL_CAST.register(TNDarkHandMechanics::onSpellCast);
            LOGGER.info("TN-C: night hand backlash listener registered on SpellEngine SPELL_CAST");
        } catch (Throwable t) {
            // 引擎换版本/接口变了也不该让整个 mod 加载不了 ✓
            LOGGER.warn("TN-C: night hand listener registration failed ({})", t.toString());
        }
    }

    private TNDarkHandMechanics() {
    }

    /**
     * 空方法，唯一作用是<b>把类加载起来</b>（Java 类懒加载：静态块不跑，监听器就挂不上 ✗）。
     *
     * <p>由 {@link TNEffects#register} 在模组构造期调用一次 ✓ —— 这个坑项目里踩过好几次
     * （见 {@code 当前文档} 铁律 ⑨：靠静态块 ⇒ 静默不注册）。
     */
    public static void ensureLoaded() {
        // 故意空着：类一被引用，上面的静态块就已经跑过了 ✓
    }

    /**
     * 施法那一刻：收"代价" ✓。
     *
     * <p>只看 {@code RELEASE} ✗（不是 {@code CHANNEL}）：蓄力阶段每 tick 都会来一次事件，
     * 在蓄力里扣血会**一秒钟扣十几次** ✗（{@code SpellHelper.performSpell} 两个阶段都会发事件 ✓）。
     *
     * <p>注意本事件**客户端也会收到**（{@code TNSpellClientVisuals} 就在客户端侧接它做震屏 ✓）
     * ⇒ 必须用 {@code instanceof ServerPlayer} 把自己限定在服务端 ✓，否则两边的玩家各扣一次 ✗。
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
        backlash(player, spell.learn.tier);
    }

    /**
     * 扣施法者自己的血 ＋ 挂"头痛" ✓（每次施法结算一次，**与有没有抓到人无关** ✓
     * —— "用了就要付"，这正是这条链的手感核心 ✓）。
     */
    private static void backlash(ServerPlayer player, int tier) {
        int index = Math.max(1, Math.min(BACKLASH_PERCENT.length, tier)) - 1;

        double percent = BACKLASH_PERCENT[index];
        float before = player.getHealth();
        float actual = 0.0F;
        boolean landed = false;
        if (percent > 0.0D) {
            float cost = (float) (player.getMaxHealth() * percent);
            // ★ 先夹住：无论 cost 多大，都要给他留 MIN_HEALTH_LEFT 点 ⇒ 反噬**永不致死** ✓
            float allowed = Math.max(0.0F, before - MIN_HEALTH_LEFT);
            actual = Math.min(cost, allowed);
            if (actual > 0.0F) {
                // 清掉受伤窗口：否则"刚被打过"的那一下会被原版静默丢掉 ✗（见类注释）
                player.invulnerableTime = 0;
                landed = player.hurt(player.damageSources().magic(), actual);
            }
        }

        int headache = HEADACHE_TICKS[index];
        if (headache > 0) {
            // "头痛"先用原版「虚弱」顶（不新注册效果：零风险；要专门做一个 tnc:dark_migraine 也行 ✓）
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, headache, 0, false, false));
        }

        if (percent > 0.0D) {
            player.displayClientMessage(Component.literal(String.format(
                    "§5[TN-C] §r反噬 —— 它烧的是你的血 §c−%.1f", actual)), true);
        }
        // ★ 留一行日志（验收：grep `TN-C: night hand backlash` ✓）——
        //   它一次性说明"扣了多少、有没有真的落地、留了几点血、头痛多久" ✓
        LOGGER.info("TN-C: night hand backlash tier={} hp {} -> {} (cost={} landed={} floor={} headache={}t)",
                tier, before, player.getHealth(), String.format("%.2f", actual), landed,
                MIN_HEALTH_LEFT, headache);
    }
}
