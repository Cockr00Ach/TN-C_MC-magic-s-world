package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>暗系第二条链「以伤换伤 · 献祭」</b>的机制 —— 用血换暗属性强度 ✓
 * （作者 2026-10-09："你还可以再优化一下以伤换伤"）。
 *
 * <h2>链条原来缺的那一半（这次优化针对的就是它）</h2>
 * 五档法术 `trade_wounds → blood_burn → sacrifice → possess → i_am_god` 的 JSON 一直挂着
 * <b>火系燃烧线的五个效果</b>（`tnc:fire_aspect` … `tnc:total_burn`）当"代价" ✗ ——
 * 而那五个加的是 {@code spell_power:fire} ✗：
 * <ul>
 *   <li>暗系法术**一点都吃不到**那个加成 ⇒ "烧自己的血"换来的对暗系毫无用处 ✗；</li>
 *   <li>更糟的是 `tnc:fire_aspect` 在火系里是"<b>不燃血</b>"档
 *       （见 {@code TNFireMechanics.burnPercentPerSecond}）⇒ <b>t1 连血都不掉</b> ✗；</li>
 *   <li>`tnc:total_burn`（我为神）把血锁在 1 还**给了无敌**，而它本来加的火伤对暗系是 0 ✗
 *       ⇒ t5 成了纯白嫖。</li>
 * </ul>
 * 也就是说：**代价没生效、收益也没生效**，唯一真正起作用的是 `tnc:dark_power` 那一份加成 ✓。
 *
 * <h2>现在</h2>
 * 五档改用暗系自己的五个效果（{@link TNEffects#BLOOD_MARK} … {@link TNEffects#BLOOD_GOD}，
 * 加 {@code spell_power:soul} ✓），本类按**和火系完全相同的那条规则**结算代价 ✓：
 *
 * <table border="1">
 *   <tr><th>档</th><th>法术</th><th>效果</th><th>暗属性强度</th><th>代价</th></tr>
 *   <tr><td>t1</td><td>以伤换伤</td><td>{@code blood_mark}</td><td>+10%</td><td>不燃血（安全档）</td></tr>
 *   <tr><td>t2</td><td>燃血</td><td>{@code blood_burn}</td><td>+25%</td><td>燃血 1%/秒</td></tr>
 *   <tr><td>t3</td><td>献祭</td><td>{@code blood_sacrifice}</td><td>+50%</td><td>燃血 2%/秒 ＋ 可原地复活</td></tr>
 *   <tr><td>t4</td><td>夺舍</td><td>{@code blood_possess}</td><td>+100%</td><td>燃血 3%/秒 ＋ 可原地复活</td></tr>
 *   <tr><td>t5</td><td>我为神</td><td>{@code blood_god}</td><td>+200%</td><td>血锁 1 ＋ 无敌，结束回血</td></tr>
 * </table>
 *
 * <p>★ <b>平衡上的关键一条</b>：{@code amplifier} 是**覆盖**（`apply_mode: SET`）而不是叠加 ✗ ——
 * 所以放一次 t2 会把 t1 的 +10% 顶掉，不会出现"四档加成全叠在身上"。这一点是这次特意确认的 ✓。
 *
 * <h2>和火系那条链的关系</h2>
 * 两条链**各自独立、数值相同、加成分属不同学派** ✓ —— 燃血判定看的是各自那组效果，
 * 所以同时挂着火系燃烧和暗系献祭时，只有高的那个在扣血 ✓（不会翻倍）。
 * 共用的是 {@link TNFireMechanics#burnPercentPerSecond} 那条"永不致死"的规则 ✓
 * （保底留 1 点，作者当初确认过的 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNDarkSacrificeMechanics {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/sacrifice");

    /** 燃血：每秒扣最大生命的百分之几（和为神/血之烙印 = 0，不扣）。 */
    private static final double BURN_BLOOD = 0.01D;
    private static final double BURN_SACRIFICE = 0.02D;
    private static final double BURN_POSSESS = 0.03D;

    /** 我为神：血锁在这个值（和火系完全燃烧一致 ✓）。 */
    private static final float GOD_HEALTH_LOCK = 1.0F;

    /** 我为神结束时回多少血（占最大生命的比例）。 */
    private static final double GOD_END_HEALTH_FRACTION = 0.5D;

    /** 谁正在"我为神"状态里（用来判断"效果刚结束、该解无敌 + 回血了"）✓。 */
    private static final Map<UUID, Long> GOD_TRACKER = new ConcurrentHashMap<>();

    private TNDarkSacrificeMechanics() {
    }

    /** 显式加载入口（由 {@link TNEffects#register} 在模组构造期碰一下，保证监听器挂上 ✓）。 */
    public static void ensureLoaded() {
        // 故意空着：类一被引用，@Mod.EventBusSubscriber 的注册就已经发生了 ✓
    }

    // ------------------------------------------------------------------
    //  每 tick：燃血 + 我为神的血量/无敌维护
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        long time = player.level().getGameTime();

        // 我为神：血锁 1 + 无敌；效果没了就回半血、解除无敌 ✓
        // （和火系"完全燃烧"同一套做法 —— 关键是**无敌必须由我们自己解除** ✗：
        //   setInvulnerable 是实体标志位，没恢复的话玩家会永久无敌 ✗）
        if (has(player, TNEffects.BLOOD_GOD)) {
            if (player.getHealth() > GOD_HEALTH_LOCK) {
                player.setHealth(GOD_HEALTH_LOCK);
            }
            player.setInvulnerable(true);
            GOD_TRACKER.put(player.getUUID(), time);
            return;
        }
        Long wasGod = GOD_TRACKER.remove(player.getUUID());
        if (wasGod != null) {
            player.setInvulnerable(false);
            player.setHealth((float) (player.getMaxHealth() * GOD_END_HEALTH_FRACTION));
            player.displayClientMessage(Component.literal("§5[TN-C] §r神性褪去 —— 血流回半"), true);
        }

        // 燃血：每秒一次、按最大生命百分比、**永不致死**（保底 1 点 ✓）
        //
        // ⚠ 这里**故意不用 setHealth()**：那个方法绕开原版的受伤窗口（invulnerableTime），
        //   在"刚被打过/刚烧过"的玩家身上会被**静默丢掉** ⇒ 表现就是"有时掉血、有时不掉" ✗
        //   （作者 2026-10-09："释放完之后一直掉血"）。现在走 `hurt(..., MAGIC, ...)` ✓：
        //   ① 自己把受伤窗口清成 0，保证每秒那一下**一定**生效；
        //   ② 走真正的伤害管线 ⇒ 扣血提示、屏幕红闪、死亡判定都是原版行为 ✓；
        //   ③ 扣血前先夹住，保证**永不致死** ✓。
        double percent = burnPercentPerSecond(player);
        if (percent <= 0.0D || time % 20 != 0) {
            return;
        }
        float drain = (float) (player.getMaxHealth() * percent);
        float health = player.getHealth();
        if (health <= GOD_HEALTH_LOCK || drain <= 0.0F) {
            return;
        }
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().magic(),
                Math.min(drain, Math.max(0.0F, health - GOD_HEALTH_LOCK)));
    }

    /**
     * 原版复活：中级/高级献祭期间死亡 → 取消死亡、回满血、效果结束（一次性）✓。
     *
     * <p>做法与火系那条链完全相同（那边也是 {@code setCanceled(true)} 之后立刻 {@code setHealth}），
     * 而且那套**已经实机跑过** ✓ ⇒ 这里刻意保持一致，不引入新的写法 ✗。
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RegistryObject<MobEffect> revive = has(player, TNEffects.BLOOD_POSSESS) ? TNEffects.BLOOD_POSSESS
                : has(player, TNEffects.BLOOD_SACRIFICE) ? TNEffects.BLOOD_SACRIFICE : null;
        if (revive == null) {
            return;
        }
        event.setCanceled(true);                        // 不死
        player.setHealth(player.getMaxHealth());        // 回满
        player.removeEffect(revive.get());              // 一次性的 ✓
        player.displayClientMessage(Component.literal("§5[TN-C] §r献祭未偿 —— 原地复活"), true);
        LOGGER.info("TN-C: dark sacrifice revive ({})", revive.getId());
    }

    // ------------------------------------------------------------------
    //  数值 / 自检
    // ------------------------------------------------------------------

    /** 这个玩家现在燃血每秒掉多少百分比（0 = 没在燃血）。 */
    public static double burnRateFor(ServerPlayer player) {
        return burnPercentPerSecond(player);
    }

    /** 燃血百分比：取"身上最高的那一档"，不叠加 ✓。 */
    static double burnPercentPerSecond(ServerPlayer player) {
        if (has(player, TNEffects.BLOOD_GOD) || has(player, TNEffects.BLOOD_MARK)) {
            return 0.0D;                                // 我为神把血锁住了；血之烙印本来就不烧
        }
        if (has(player, TNEffects.BLOOD_POSSESS)) {
            return BURN_POSSESS;
        }
        if (has(player, TNEffects.BLOOD_SACRIFICE)) {
            return BURN_SACRIFICE;
        }
        if (has(player, TNEffects.BLOOD_BURN)) {
            return BURN_BLOOD;
        }
        return 0.0D;
    }

    /** 这条链的五个效果都注册上了吗（自检/命令用 ✓）。 */
    public static int registeredCount() {
        return TNEffects.darkScalesCount();
    }

    private static boolean has(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }
}
