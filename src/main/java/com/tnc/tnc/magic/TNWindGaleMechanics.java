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
 * 而作者要的是"t3/t4/t5 各不一样"（寿命 8/12/16 秒、龙卷风 2/5/8 倍、刃 5/10/20 倍、
 * 每轮 2/3/3 发 ✓）⇒ 只能让 Java 来认档 ✓。
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

    /**
     * ★ <b>落地</b>：从落脚点往下最多找多少格 ✓。
     *
     * <p>⚠️ 原来是 <b>4</b> ✗ —— 作者 2026-10-10 第二次反馈：「**龙卷风要落地，就是让它自己去找
     * 最低的平面**」✗。4 格太浅：站在坡上、或者朝下前方放，落点比脚下低好几格 ⇒
     * 找 4 格没找到 ⇒ 落回原高度 ⇒ 龙卷风**浮在半空中** ✗。
     * 现在一路往下找**真正的地面**（上限 64 格，或到世界底部为止 ✓）。
     */
    private static final int GROUND_SEARCH = 64;

    /**
     * 落地面之上要留出的**净空**（格）✓。
     *
     * <p>为什么要有 ✗：下面第一块有碰撞的方块可能就是"洞里的一块石头"⇒
     * 直接站上去会把龙卷风**塞进一格高的缝**里 ✗（模型 2~16 格高，穿模、看着像插进地里 ✗）。
     * 要求上方连着两格空 ⇒ 只会落在**开阔的平面**上 ✓。
     */
    private static final int GROUND_CLEARANCE = 2;

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
     * 然后**一路往下找最低的那个能站的地面** ✓（草/花/水/树叶都穿过去，见 {@link #groundY}）
     * —— 直接放在眼睛高度会让龙卷风浮在空中 ✗（模型底边是贴地的 ✓）；
     * 一路找不到地面（悬崖/虚空）就用原高度 ✓，宁可浮一点也不掉进虚空 ✗。
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
     * 从 {@code at} 往下找**最低的那个能站的地面** ✓，返回该放的高度（方块顶面 ＋1 ✓）。
     *
     * <p>规则（尽量简单可预期 ✓）：
     * <ol>
     *   <li>一路往下扫，最多 {@link #GROUND_SEARCH} 格、或者扫到世界底部为止 ✓；</li>
     *   <li>第一块**有碰撞**的方块才算地面 ⇒ 空气/草/花/雪片/水都**穿过去** ✓
     *       （原来用的是 {@code !isAir()} ✗ ⇒ 一丛草、一朵花都能把龙卷风**架在半空** ✗）；</li>
     *   <li>**树叶不算地面** ✗ —— 站在树冠上的龙卷风很怪，要落到树底下的真地面 ✓；</li>
     *   <li>地面之上必须连着 {@link #GROUND_CLEARANCE} 格净空 ✓（免得塞进洞里 ✗）；</li>
     *   <li>一路都没找到（悬崖/虚空）⇒ 落回原高度 ✓ —— 宁可浮着，也别掉进虚空 ✗。</li>
     * </ol>
     */
    private static double groundY(ServerLevel level, Vec3 at) {
        net.minecraft.core.BlockPos.MutableBlockPos pos =
                new net.minecraft.core.BlockPos.MutableBlockPos(at.x, at.y, at.z);
        int bottom = level.getMinBuildHeight();
        for (int i = 0; i <= GROUND_SEARCH && pos.getY() >= bottom; i++) {
            if (isStandableGround(level, pos)) {
                return pos.getY() + 1.0D;            // 站在方块顶面 ✓
            }
            pos.move(0, -1, 0);
        }
        return at.y;
    }

    /** 这一格算不算"能站的地面" ✓（见 {@link #groundY} 的五条规则）。 */
    private static boolean isStandableGround(ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
            return false;                        // 树叶不算地面 ✗
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return false;                        // 没碰撞 = 穿过去 ✓（空气/草/花/水）
        }
        for (int i = 1; i <= GROUND_CLEARANCE; i++) {
            net.minecraft.core.BlockPos above = pos.above(i);
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                return false;                    // 上面堵着 ⇒ 不是开阔平面 ✗
            }
        }
        return true;
    }
}
