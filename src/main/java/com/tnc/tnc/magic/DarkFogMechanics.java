package com.tnc.tnc.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.casting.SpellCast;

/**
 * <b>黑雾链</b>（暗系第四条链：黑雾 / 领域）的接线 —— 那条链上"数据层表达不出来"的部分。
 *
 * <h2>为什么需要它</h2>
 * 五档法术本体仍然是普通 JSON（伤害/减益/粒子都由引擎结算 ✓），Java 只补两件引擎没有的事：
 * <ol>
 *   <li><b>边界法阵</b>：{@code SPAWN} 动作只能指定实体类型、<b>不能带半径</b> ✗
 *       （{@code Spell$Impact$Action$Spawn} 只有 {@code entity_type_id / time_to_live_seconds /
 *       delay_ticks / placement} ✓，反编译确认），而 {@link TNMagicCircleEntity} 的半径是运行时
 *       {@code configure(...)} 写进去的 ⇒ 只能在这里铺 ✓。半径取
 *       {@code volume.combinedRadius(...)} —— <b>和引擎真正结算伤害用的那个算法同一个</b> ✓
 *       （所以"看到的圈"和"吃伤害的范围"永远一致，不会各说各话 ✗）；</li>
 *   <li><b>把法阵交给雾托管</b>：找到刚生成的 {@link DarkFogCloudEntity} 交给它
 *       （{@code claimBoundaryCircle}）⇒ 雾走到哪，圈跟到哪 ✓（t3 起雾本身也跟着施法者走 ✓）。</li>
 * </ol>
 *
 * <h2>★ 事件怎么订阅（这里有个坑）</h2>
 * 引擎<b>不是</b>用 Forge 事件总线，而是自带一套：
 * {@code CombatEvents.SPELL_CAST} 是 {@code net.spell_engine.api.event.Event<SpellCast>}，
 * 用法是 {@code SPELL_CAST.register(listener)} ✓（{@code @SubscribeEvent} 挂 Forge 总线
 * <b>收不到</b> ✗ —— 编译期不会报错，运行时静默不触发）。
 * 所以这里在<b>静态块</b>里注册（类一被加载就挂上，早于任何施法 ✓）。
 *
 * <h2>为什么判据是 {@code spell.group == "dark_fog"}</h2>
 * 不写死五个法术 id ✓ —— 以后往链里加第六档、或换名字，这里都不用动；
 * "这条链属于雾"这件事本来就写在数据里（{@code "group": "dark_fog"}）✓。
 */
public final class DarkFogMechanics {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/fog");

    /** 黑雾链的分组名（五个法术 JSON 里都写着它 ✓）。 */
    public static final String FOG_GROUP = "dark_fog";

    /** 找不到 {@code volume.radius} 时的兜底半径（不该发生；兜底只为不留 NPE ✗）。 */
    private static final double FALLBACK_RADIUS = 5.0D;

    /** 法阵比雾多活这么久（tick）—— 雾散的那一刻圈还在，玩家才看得到"领域结束" ✓。 */
    private static final int CIRCLE_TAIL_TICKS = 20;

    /** 交给雾托管的搜索半径（格）：雾就生成在施法者身上，8 格足够 ✓。 */
    private static final double CLAIM_RADIUS = 8.0D;

    static {
        // ★ 挂在引擎自己的事件上（不是 Forge 总线 ✗，见类注释）
        try {
            CombatEvents.SPELL_CAST.register(DarkFogMechanics::onSpellCast);
            LOGGER.info("TN-C: dark fog listener registered on SpellEngine SPELL_CAST");
        } catch (Throwable t) {
            // 引擎换了版本/接口变了也不该让整个 mod 加载不了 ✓
            LOGGER.warn("TN-C: dark fog listener registration failed ({})", t.toString());
        }
    }

    private DarkFogMechanics() {
    }

    /**
     * 空方法，唯一的作用是<b>把类加载起来</b>（Java 类懒加载：静态块不跑，监听器就挂不上 ✗）。
     *
     * <p>由 {@link TNEffects#register} 在模组构造期调用一次 ✓ —— 这个坑项目里踩过好几次
     * （{@code DeferredRegister} 靠静态块静默不注册，见 {@code 当前状态.md} 铁律 ⑨）。
     */
    public static void ensureLoaded() {
        // 故意空着：类一被引用，上面的静态块就已经跑过了 ✓
    }

    /**
     * 施法那一刻：铺边界法阵 + 交给雾托管 ✓。
     *
     * <p>只看 {@code RELEASE}（不是 {@code CHANNEL}）：黑雾是"按住蓄力、松手放出去"，
     * 蓄力阶段每 tick 都会来一次事件 —— 在蓄力里铺阵会铺出一堆 ✗
     * （{@code SpellHelper.performSpell} 两个阶段都会发事件 ✓）。
     */
    private static void onSpellCast(CombatEvents.SpellCast.Args args) {
        if (args == null || args.action() != SpellCast.Action.RELEASE) {
            return;
        }
        SpellInfo info = args.spell();
        if (info == null || info.spell() == null || !FOG_GROUP.equals(info.spell().group)) {
            return;
        }
        Player caster = args.caster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        double radius = radiusOf(info.spell());
        int life = lifeTicksOf(info.spell());
        int circleLife = life + CIRCLE_TAIL_TICKS;

        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle == null) {
            LOGGER.warn("TN-C: fog circle could not be created (entity type missing?)");
            return;
        }
        circle.configure(radius, circleLife, TNMagicCircleEntity.STYLE_DARK);
        Vec3 at = groundNear(level, player.position());
        circle.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(circle);

        boolean claimed = false;
        for (DarkFogCloudEntity fog : level.getEntitiesOfClass(DarkFogCloudEntity.class,
                player.getBoundingBox().inflate(CLAIM_RADIUS), DarkFogCloudEntity::isDarkFog)) {
            fog.claimBoundaryCircle(circle, radius, circleLife);
            claimed = true;
            break;
        }
        // ★ 一定留一行日志（验收：grep `TN-C: fog circle` ✓）——
        //   它同时说明"圈铺在哪、多大、有没有被雾接管"✓
        LOGGER.info("TN-C: fog circle spell={} r={} life={} claimedByFog={} at={}",
                info.id(), radius, circleLife, claimed, at);
    }

    /** 雾的半径（和引擎结算伤害用的是同一个算法 ✓）。 */
    private static double radiusOf(Spell spell) {
        try {
            Spell.Release.Target.Cloud cloud = cloudOf(spell);
            if (cloud != null && cloud.volume != null) {
                // ⚠ 这里**故意传 null**：`combinedRadius` 只有在**真的**有
                //   `extra_radius`（按法术强度放大）时才会去读那个参数
                //   —— 它内部是 `if (extraRadius.power_coefficient != 0)` 那个分支才用 ✗。
                //   我们的五档都没有 extra_radius ⇒ 传 null 是合法的 ✓。
                //   （第一版把 NPE 当"查不到"打了 WARN，其实那不是错误：
                //    除非将来给某档加了 extra_radius，否则这条 WARN 是噪音 ✗）
                return cloud.volume.combinedRadius(null);
            }
        } catch (Throwable t) {
            // 真出问题（比如以后加了 extra_radius）也不能让施法崩掉 ⇒ 兜底 + 一次警告 ✓
            LOGGER.warn("TN-C: fog radius lookup failed ({}), falling back to {}",
                    t.toString(), FALLBACK_RADIUS);
        }
        return FALLBACK_RADIUS;
    }

    /** 雾的存在时长（tick）—— 法阵要跟它对齐 ✓。 */
    private static int lifeTicksOf(Spell spell) {
        try {
            Spell.Release.Target.Cloud cloud = cloudOf(spell);
            if (cloud != null && cloud.time_to_live_seconds > 0.0F) {
                return Math.round(cloud.time_to_live_seconds * 20.0F);
            }
        } catch (Throwable t) {
            LOGGER.warn("TN-C: fog life lookup failed ({})", t.toString());
        }
        return 15 * 20;
    }

    private static Spell.Release.Target.Cloud cloudOf(Spell spell) {
        if (spell.release == null || spell.release.target == null) {
            return null;
        }
        return spell.release.target.cloud;
    }

    /**
     * 把法阵按到施法者脚下那一层（和 {@code TnSpellMechanics.spawnMagicCircle} 同一套做法 ✓）：
     * 往下找一个不是空气的方块、铺在它上面 —— 免得站在台阶边时法阵浮在半空 ✗。
     */
    private static Vec3 groundNear(ServerLevel level, Vec3 at) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at.x, at.y, at.z);
        int guard = 0;
        while (guard++ < 8 && pos.getY() > level.getMinBuildHeight()
                && level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY() + 0.04D, at.z);
    }
}
