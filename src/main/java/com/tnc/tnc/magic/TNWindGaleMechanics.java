package com.tnc.tnc.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.casting.SpellCast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ★ <b>范围风链（风刃）的 Java 机制</b> —— t3+ 改成"召唤龙卷风"（2026-10-10）✓
 *
 * <p>作者原话：「风刃这样吧，你做个龙卷风的模型，<b>t3 开始释放风刃会召唤出一个一直在旋转的
 * 龙卷风，然后持续向外发射风刃</b>」✓
 *
 * <h2>为什么必须动 Java</h2>
 * 引擎的 {@code SPAWN} impact 动作只能生成一个<b>写死的</b>实体 id ✗
 * （已 {@code javap} 确认字段：{@code entity_type_id / time_to_live_seconds / delay_ticks /
 * location_offset_*} —— <b>没有 tier、没有"按档变化"的字段</b> ✗）。
 * 而作者要的是"t3/t4/t5 各不一样"（寿命 8/12/16 秒、每轮 3/5/7 发、大小 1.0/1.3/1.6 倍）✓
 * ⇒ 只能让 Java 来认档 ✓。
 *
 * <p>做法和黑夜之手**完全一样** ✓（那是本仓已验证的模板）：法术 JSON 改成
 * {@code release.target.type = "SELF"}（不再产生引擎投射物 ✗），Java 在
 * {@code SPELL_CAST} 里认 {@code spell.group == "gale"} ＋ {@code spell.learn.tier} ⇒ 召唤 ✓。
 *
 * <h2>t1 / t2 不动</h2>
 * t1「疾风刃」/ t2「强风刃」**照旧**是引擎投射物（月牙直接飞出去 ✓）——
 * 那两档本来就是"一刀"，不是"召唤" ✓（作者只说了 t3 开始 ✓）。
 * 所以这里只管 {@code tier >= 3} ✓，JSON 也只改了 t3/t4/t5 ✓。
 */
public final class TNWindGaleMechanics {

    private static final Logger LOGGER = LoggerFactory.getLogger("tnc/wind_gale");

    /**
     * 范围风链的分组名 ✓（五个法术 JSON 里都写着它 —— 不写死 id，
     * 加档/改名都不用动这里 ✓，和 {@code TNDarkHandMechanics.HAND_GROUP} 同一口径）。
     */
    public static final String GALE_GROUP = "gale";

    /** 从第几档开始改成"召唤龙卷风" ✓（作者指定 t3 ✓）。 */
    public static final int TORNADO_FROM_TIER = 3;

    /** 龙卷风召唤在施法者身前多远 ✓。 */
    private static final double SPAWN_DISTANCE = 3.0D;

    /** 施法者正前方找不到地面时，最多往下/往上找多少格 ✓。 */
    private static final int GROUND_SEARCH = 4;

    static {
        // ★ 挂在引擎自己的事件上（不是 Forge 总线 ✗ —— @SubscribeEvent 会编译通过、运行时静默不触发）
        try {
            CombatEvents.SPELL_CAST.register(TNWindGaleMechanics::onSpellCast);
            LOGGER.info("TN-C: wind gale cast listener registered on SpellEngine SPELL_CAST");
        } catch (Throwable t) {
            // 引擎换版本/接口变了也不该让整个 mod 加载不了 ✓
            LOGGER.warn("TN-C: wind gale listener registration failed ({})", t.toString());
        }
    }

    private TNWindGaleMechanics() {
    }

    /**
     * 空方法，唯一作用是<b>把类加载起来</b> ✓（Java 类懒加载：静态块不跑，监听器就挂不上 ✗）。
     * 由 {@link TNEffects#register} 在模组构造期调用一次 ✓。
     */
    public static void ensureLoaded() {
        // 故意空着：类一被引用，上面的静态块就已经跑过了 ✓
    }

    /**
     * 施法那一刻：t3+ ⇒ 召唤一个龙卷风 ✓。
     *
     * <p>只看 {@code RELEASE} ✗（不是 {@code CHANNEL}）：蓄力阶段每 tick 都来一次事件，
     * 那样会一秒召唤出十几个龙卷风 ✗。
     *
     * <p>本事件**客户端也会收到** ⇒ 必须用 {@code instanceof ServerPlayer} 限定在服务端 ✓
     * （不然客户端也会"召唤"出一个只有自己看得见的龙卷风 ✗）。
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
        if (!GALE_GROUP.equals(spell.group) || spell.learn == null) {
            return;
        }
        if (spell.learn.tier < TORNADO_FROM_TIER) {
            return;                              // t1/t2 照旧走引擎投射物 ✓
        }
        Player caster = args.caster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        summonTornado(player, spell.learn.tier);
    }

    /**
     * 在施法者身前召唤龙卷风 ✓。
     *
     * <p>落地点的处理：先按视线方向取身前 {@link #SPAWN_DISTANCE} 格，
     * 然后**从该点向下找最多 {@link #GROUND_SEARCH} 格**的地面 ✓
     * —— 直接放在眼睛高度会让龙卷风浮在空中 ✗（模型底边是贴地的 ✓）；
     * 找不到地面（比如站在悬崖边）就用原高度 ✓，宁可浮一点也不掉进虚空 ✗。
     */
    private static void summonTornado(ServerPlayer player, int tier) {
        if (TNOrbEntities.WIND_TORNADO == null || !TNOrbEntities.WIND_TORNADO.isPresent()) {
            // 注册表不在（理论上不会）⇒ 别刷空指针 ✗
            return;
        }
        ServerLevel level = player.serverLevel();

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);       // 正上/正下看时给个兜底方向 ✓
        }
        flat = flat.normalize();

        int index = Math.max(3, Math.min(5, tier)) - 3;
        int count = TNTornadoEntity.TORNADO_COUNT[index];
        // 垂直方向（用来把 t5 的两个漩涡左右拉开 ✓）
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);

        // ⚠️ 距离必须跟着**尺寸**走 ✗：t5 的漩涡宽约 15 格，若还按老的 3 格摆，
        // 施法者会直接站在漩涡**里面**（而且被自己的风刃判定够到 ✗）。
        // 取 "基准 + 外壁半径" ⇒ 近侧外壁刚好擦着施法者身前 ✓。
        double forward = SPAWN_DISTANCE + TNTornadoEntity.spawnRadiusFor(tier);

        for (int i = 0; i < count; i++) {
            // count == 1 ⇒ offset 0 ✓；count == 2 ⇒ ±间距/2 ✓
            double offset = count == 1 ? 0.0D : (i == 0 ? -0.5D : 0.5D) * TNTornadoEntity.PAIR_SEPARATION;
            Vec3 at = player.position()
                    .add(flat.scale(forward))
                    .add(side.scale(offset));

            double y = groundY(level, at);
            TNTornadoEntity.summon(level, new Vec3(at.x, y, at.z), player, tier);
        }
    }

    /**
     * 从 {@code at} 往下找最多 {@link #GROUND_SEARCH} 格的地面 ✓，返回该放的高度。
     *
     * <p>直接放在眼睛高度会让龙卷风浮在空中 ✗（模型底边是贴地的 ✓）；
     * 找不到地面（比如站在悬崖边）就用原高度 ✓，宁可浮一点也不掉进虚空 ✗。
     */
    private static double groundY(ServerLevel level, Vec3 at) {
        net.minecraft.core.BlockPos.MutableBlockPos pos =
                new net.minecraft.core.BlockPos.MutableBlockPos(at.x, at.y, at.z);
        for (int i = 0; i <= GROUND_SEARCH; i++) {
            if (!level.getBlockState(pos).isAir()) {
                return pos.getY() + 1.0D;            // 站在实心方块顶上 ✓
            }
            pos.move(0, -1, 0);
        }
        return at.y;
    }
}
