package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 那几条<b>数据层表达不出来</b>的法术行为，全放这里。
 *
 * <h2>为什么必须写代码</h2>
 * 引擎（SpellEngine 0.15.12）的 JSON 只有 6 种动作：DAMAGE / HEAL / STATUS_EFFECT /
 * FIRE / SPAWN / TELEPORT，而且：
 * <ul>
 *   <li>没有 mana 池 —— "回一半蓝"只能我们自己扣/加（我们是 mana 的所有者）；</li>
 *   <li>没有"绕着自己转的光环"字段，{@code SpellCloud} 也不会跟着玩家走；</li>
 *   <li>没有"沿路留下伤害"的字段；</li>
 *   <li>冷却 {@code cost.cooldown_duration} 只能靠 haste 做除法，<b>永远到不了 0</b>。</li>
 * </ul>
 * 所以这四条用"效果当开关 + 每 tick 检查"的方式实现：
 * 法术 JSON 负责挂效果（{@link TNEffects}），效果在身时这里的 tick 逻辑就开始干活。
 *
 * <p><b>粒子</b>：优先用包里的"真电弧"（{@code spell_engine:electric_arc_b} 等，见 {@link #arc()}），
 * 沿锯齿线撒出来才像闪电；一个都找不到才退回原版 {@link ParticleTypes#ELECTRIC_SPARK}（小亮点 ✗）。
 * <b>原版闪电实体不能用</b>：{@code LightningBolt} 不管 {@code visualOnly} 都会播 10000 音量的雷声 ✗。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TnSpellMechanics {

    /** 自己的 logger（TNMod 的那个是 private ✗）。 */
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/spellvisuals");

    // ---------------- 可调数值（想改手感只动这里） ----------------

    /** 环绕雷球：几颗球、半径多大、多久电一次、电多少。 */
    private static final int ORBIT_COUNT = 4;
    private static final double ORBIT_RADIUS = 1.4D;
    private static final int ORBIT_ZAP_INTERVAL = 10;   // tick（0.5 秒）
    private static final double ORBIT_RADIUS_HIT = 3.5D;
    private static final float ORBIT_DAMAGE = 5.0F;

    /** 极速雷风：拖尾多久留一次、判定多大、伤害多少。 */
    private static final int WIND_TRAIL_INTERVAL = 2;
    private static final double WIND_HIT_RADIUS = 1.6D;
    private static final float WIND_DAMAGE = 4.0F;
    private static final double WIND_MIN_MOVE = 0.02D;   // 站着不动就不留痕迹

    /** 闪电登神：无冷却期间每隔几 tick 清一次冷却。 */
    private static final int ASCENSION_CLEAR_INTERVAL = 5;

    // ---- 2026-09-22：雷速链 5 档的"看得见"表现（作者要求：自身增益也要有体现）----

    /** t1 雷速：跑动时脚底电花的间隔 / 判定"在动"的最小位移。 */
    private static final int HASTE_SPARK_INTERVAL = 4;
    private static final double HASTE_MIN_MOVE = 0.01D;

    /** t2 闪电移位：起点/落点爆散的粒子数与扩散。 */
    private static final int BLINK_BURST_COUNT = 28;
    private static final double BLINK_BURST_SPREAD = 0.6D;
    /** 位移超过这个平方距离才算"真的传送了"（否则原地放不该炸两下）。 */
    private static final double BLINK_MIN_DISTANCE_SQR = 4.0D;

    /** t4 闪电降低冷却：回蓝后的"表演窗口"（tick）与电环。 */
    private static final int RECHARGE_FLOURISH_TICKS = 60;      // 3 秒
    private static final int RECHARGE_RING_COUNT = 14;
    private static final double RECHARGE_RING_RADIUS = 1.3D;

    /** t5 闪电登神：全身电弧密度 / 环绕电光 / 雷印。 */
    private static final int ASCENSION_ARC_INTERVAL = 2;
    private static final int ASCENSION_ARC_COUNT = 2;
    private static final int ASCENSION_ORBIT_COUNT = 3;
    private static final double ASCENSION_ORBIT_RADIUS = 1.05D;
    private static final int ASCENSION_MARK_LIFE = 40;          // 雷印存活 tick
    private static final double ASCENSION_MARK_STEP = 1.0D;     // 走多远落一枚
    private static final int ASCENSION_MARK_MAX = 10;           // 最多同时留几枚

    /** 回蓝的表演窗口：uuid -> 结束时刻（只在内存里，不需要存档） */
    private static final Map<UUID, Long> RECHARGE_UNTIL = new ConcurrentHashMap<>();
    /** 雷印：uuid -> 一串（位置, 到期时刻） */
    private static final Map<UUID, List<SparkMark>> MARKS = new ConcurrentHashMap<>();
    /** 上一个雷印的位置：uuid -> 坐标 */
    private static final Map<UUID, Vec3> LAST_MARK = new ConcurrentHashMap<>();
    /**
     * 神级大雷球的"贴粒子窗口"：uuid -> 结束时刻。
     *
     * <p>为什么要自己贴：法术 JSON 的 {@code travel_particles} 在这颗球上**不跟随** ✗
     * （作者 2026-09-22："他的粒子特效怎么不跟随雷球呢"）—— 所以改成 Java 侧每 tick 找到
     * **自己的** {@code SpellProjectile}（引擎的投射物实体，有 {@code getOwner()} ✓）在它位置上画环 ✓。
     */
    private static final Map<UUID, Long> BIG_BALL_UNTIL = new ConcurrentHashMap<>();
    /** 贴粒子窗口（tick）：球掉得慢，给足 20 秒 ✓ */
    private static final int BIG_BALL_WINDOW = 400;
    /** 大雷球的环半径（格）＝球半径（scale 75 × 0.375 ÷ 2 ≈ 14）✓ */
    private static final double BIG_BALL_RING = 14.0D;
    /** 每 tick 画几个点（每点 4 颗粒子：水平环紫/黄 ＋ 竖直环紫/黄）✓ */
    private static final int BIG_BALL_RING_POINTS = 72;

    /** 一枚留在原地的雷印。 */
    private record SparkMark(Vec3 pos, long expireAt) {
    }

    // ---- 电弧粒子：优先用包里的"真电弧"，拿不到才退回原版小亮点 ----
    //
    // 背景（作者 2026-09-22 反馈"怎么是星星，丑死了"）：原版 ParticleTypes.ELECTRIC_SPARK
    // 就是个小亮点、END_ROD 是白色星点 —— 都不是闪电 ✗。原版闪电**实体**也不能用：
    // LightningBolt 不管 visualOnly 都会播 10000 音量的雷声（读源码确认 ✗）。
    // 所以走"借用包里现成的电弧粒子 + 自己画锯齿雷线"这条路 ✓。
    //
    // 为什么要"按顺序找"：这些粒子分别来自不同 mod，哪个被删了都不该让法术崩。
    // 只在第一次使用时解析一次（注册表在构造期还没填满，静态初始化会拿不到 ✗）。
    private static final String[] ARC_CANDIDATES = {
            "spell_engine:electric_arc_b",          // 引擎自带的电弧（法术本体用的就是它）★首选
            "spell_engine:electric_arc_a",
            "alexscaves:tesla_bulb_lightning",      // 特斯拉电弧
            "aquamirae:electric",
            "jerotes:chain_lightning_display",      // 链状闪电
            "berserker_rpg:small_thunder",
    };
    /** 解析结果缓存：null = 还没找过。 */
    private static net.minecraft.core.particles.ParticleOptions arcCache = null;
    /** 最终用的是哪个（给日志/文档看）。 */
    private static String arcSource = "(未解析)";

    /** 取电弧粒子（找不到就退回原版 ELECTRIC_SPARK，并留一行日志说明）。 */
    public static net.minecraft.core.particles.ParticleOptions arc() {
        if (arcCache != null) {
            return arcCache;
        }
        for (String id : ARC_CANDIDATES) {
            try {
                net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id);
                if (key == null) {
                    continue;
                }
                net.minecraft.core.particles.ParticleType<?> type =
                        net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(key);
                if (type instanceof net.minecraft.core.particles.ParticleOptions options) {
                    arcCache = options;
                    arcSource = id;
                    LOGGER.info("TN-C: 雷速特效使用电弧粒子 {}", id);
                    return arcCache;
                }
            } catch (Throwable ignored) {
                // 某个 mod 的粒子类型怪：跳过，继续试下一个
            }
        }
        arcCache = ParticleTypes.ELECTRIC_SPARK;
        arcSource = "(退回原版 ELECTRIC_SPARK)";
        LOGGER.warn("TN-C: 没找到任何电弧粒子，雷速特效退回原版小亮点");
        return arcCache;
    }

    /** 只查不用：给日志/自检查询当前会用哪个粒子。 */
    public static String arcSourceName() {
        arc();      // 触发一次解析
        return arcSource;
    }

    /**
     * 画一条<b>锯齿雷线</b>（从 from 到 to，沿路撒 N 个电弧粒子 + 随机抖动）。
     *
     * <p>这是"闪电感"的关键：零散的点看起来是星星 ✗，沿一条抖动的线连起来才像电弧 ✓。
     * 用 {@code count=1, speed=0} 精确落点，避免原版把粒子随机撒开。
     */
    public static void arcLine(ServerLevel level, Vec3 from, Vec3 to, int points, double jitter) {
        if (points < 2) {
            points = 2;
        }
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            double x = from.x + (to.x - from.x) * t + (level.random.nextDouble() - 0.5D) * jitter;
            double y = from.y + (to.y - from.y) * t + (level.random.nextDouble() - 0.5D) * jitter;
            double z = from.z + (to.z - from.z) * t + (level.random.nextDouble() - 0.5D) * jitter;
            level.sendParticles(arc(), x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 以 center 为中心、半径 radius 的粗糙"电球"（爆炸/闪现用）。 */
    public static void arcBall(ServerLevel level, Vec3 center, int points, double radius) {
        for (int i = 0; i < points; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double b = (level.random.nextDouble() - 0.5D) * Math.PI;
            double r = radius * (0.5D + level.random.nextDouble() * 0.5D);
            level.sendParticles(arc(),
                    center.x + Math.cos(a) * Math.cos(b) * r,
                    center.y + Math.sin(b) * r,
                    center.z + Math.sin(a) * Math.cos(b) * r,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private TnSpellMechanics() {
    }

    // ------------------------------------------------------------------
    //  施法瞬间的额外效果
    // ------------------------------------------------------------------

    /**
     * 法术真的放出去之后调用（由 SPELL_CAST 钩子在扣完魔力后调）。
     *
     * @param spellId 法术 id
     * @param data    玩家数据（回蓝要改它）
     */
    public static void onSpellCast(ServerPlayer player, ResourceLocation spellId, MagicStoneData data) {
        String path = spellId.getPath();

        // 火系那几条（自爆扣最大生命 10%）在自己的类里
        TNFireMechanics.onSpellCast(player, spellId, data);

        // 闪电降低冷却：恢复一半蓝量（上限的一半）
        if (path.equals("lightning_recharge")) {
            int half = Math.max(1, data.getMaxMana() / 2);
            data.addMana(half);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§b[TN-C] §r冷却归位：魔力 +" + half), true);
            // 表现：一圈电环 + 3 秒的上升电流（t4）
            rechargeRing(player);
            long now = player.level().getGameTime();
            RECHARGE_UNTIL.put(player.getUUID(), now + RECHARGE_FLOURISH_TICKS);
        }

        // 闪电移位：起点与落点各炸一圈电花（t2）
        if (path.equals("lightning_blink")) {
            blinkBurst(player);
        }

        // 传说级 / 神级雷球：脚下留下魔法阵 ✓（作者 2026-09-22 指定）
        // tier 4 = 传说级、tier 5 = 神级（见 Element.tierName）
        if (path.equals("explosive_thunder_orb")) {
            spawnMagicCircle(player, 12.8D, 170);
        } else if (path.equals("cataclysm_thunder_orb")) {
            spawnMagicCircle(player, 20.0D, 260);
            // 大雷球的粒子不跟随 ✗ -> 自己开一个窗口，每 tick 在球的位置画环 ✓
            BIG_BALL_UNTIL.put(player.getUUID(), player.level().getGameTime() + BIG_BALL_WINDOW);
        } else if (path.equals("orbiting_thunder_orb")) {
            // 环绕雷球（现在是 t4）：作者要求"同款魔法阵" ✓
            spawnMagicCircle(player, 8.0D, 200);
        }
    }

    /**
     * 在玩家脚下铺一张魔法阵（{@link TNMagicCircleEntity}，纯表现实体 ✓）。
     *
     * @param radius 半径（格）
     * @param life   存在多少 tick
     */
    private static void spawnMagicCircle(ServerPlayer player, double radius, int life) {
        ServerLevel level = player.serverLevel();
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle == null) {
            return;
        }
        circle.configure(radius, life);
        // 贴在他站的那一层：往下找一个不是空气的方块，铺在它上面 ✓
        net.minecraft.core.BlockPos pos = player.blockPosition();
        int guard = 0;
        while (guard++ < 8 && pos.getY() > level.getMinBuildHeight()
                && level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
        }
        circle.moveTo(player.getX(), pos.getY() + 0.04D, player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(circle);
    }

    // ------------------------------------------------------------------
    //  每 tick 的持续行为
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        long time = player.level().getGameTime();

        // 环绕雷球：现在是**真实体**（TNThunderOrbEntity），这里只负责"维持数量" ✓
        // 作者 2026-09-22：「环绕雷球的技能，变为我们的实体雷球啊」
        if (has(player, TNEffects.ORBITING_THUNDER_ORB)) {
            maintainOrbs(player);
        } else {
            removeOrbs(player);
        }
        if (has(player, TNEffects.LIGHTNING_WIND)) {
            windTrail(player, time);
        }
        // 雷速：脚底电花（在动才明显）—— 最低成本的存在感
        if (has(player, TNEffects.LIGHTNING_HASTE)) {
            hasteSparks(player, time);
        }
        // 闪电登神：全身电弧 + 环绕电光 + 走过留雷印
        if (has(player, TNEffects.LIGHTNING_ASCENSION)) {
            ascensionAura(player, time);
        } else {
            // buff 没了就把"上一个雷印位置"清掉，免得下次上 buff 时先补一枚
            LAST_MARK.remove(player.getUUID());
        }
        // 回蓝的 3 秒表演 + 地上的雷印（两者都可能没有，函数内部自己判断）
        rechargeFlourish(player, time);
        sparkMarks(player, time);
        // 神级大雷球：粒子贴到球上（引擎自己的 travel_particles 不跟随 ✗）
        followBigBall(player, time);
        // 无冷却：雷系"闪电登神"与风系"风神降临"（5 级）都给。
        // 风系用专属标记 wind_god 判断 —— 只有 5 级发它，所以 4 级"超级风速"
        // 不会再蹭到无冷却（之前借用共用的 wind_speed_iii 时就会蹭到）。
        boolean windGod = TNWindMechanics.WIND_GOD.isPresent()
                && player.hasEffect(TNWindMechanics.WIND_GOD.get());
        boolean noCooldown = has(player, TNEffects.LIGHTNING_ASCENSION) || windGod;
        if (noCooldown && time % ASCENSION_CLEAR_INTERVAL == 0) {
            clearOurCooldowns(player);
        }
    }

    /**
     * 环绕雷球：<b>维持 {@link TNThunderOrbEntity#COUNT} 个真实体球</b>绕着玩家转 ✓。
     *
     * <p>位置、电击、轨迹全在实体自己身上（{@link TNThunderOrbEntity#tick()}）——
     * 这里只做"点名"：缺哪个槽位就补一个、多出来的清掉。
     *
     * <p>为什么要按槽位而不是"数量对就行"：数量对但槽位重复时，两颗球会**重叠在同一角度**上 ✗，
     * 看着像少了一颗。
     */
    private static void maintainOrbs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<TNThunderOrbEntity> near = level.getEntitiesOfClass(TNThunderOrbEntity.class,
                player.getBoundingBox().inflate(12.0D));
        boolean[] taken = new boolean[TNThunderOrbEntity.COUNT];
        for (TNThunderOrbEntity orb : near) {
            if (!player.getUUID().equals(orb.ownerId())) {
                continue;
            }
            int slot = orb.slot();
            if (slot >= 0 && slot < taken.length && !taken[slot]) {
                taken[slot] = true;
            } else {
                orb.discard();      // 重复槽位 / 越界：清掉，下面会补正确的
            }
        }
        for (int slot = 0; slot < taken.length; slot++) {
            if (taken[slot]) {
                continue;
            }
            TNThunderOrbEntity orb = TNOrbEntities.THUNDER_ORB.get().create(level);
            if (orb == null) {
                continue;
            }
            orb.bind(player.getUUID(), slot);
            orb.setPos(player.getX(), player.getY() + TNThunderOrbEntity.HEIGHT, player.getZ());
            level.addFreshEntity(orb);
        }
    }

    /** buff 掉了：把这个玩家的球都清掉（实体自己也会查 buff，这里是双保险 ✓）。 */
    private static void removeOrbs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (TNThunderOrbEntity orb : level.getEntitiesOfClass(TNThunderOrbEntity.class,
                player.getBoundingBox().inflate(16.0D))) {
            if (player.getUUID().equals(orb.ownerId())) {
                orb.discard();
            }
        }
    }

    /** 极速雷风：移动时在身后留下会扎人的雷电痕迹。 */
    private static void windTrail(ServerPlayer player, long time) {
        if (time % WIND_TRAIL_INTERVAL != 0) {
            return;
        }
        Vec3 moved = player.position().subtract(player.xOld, player.yOld, player.zOld);
        if (moved.length() < WIND_MIN_MOVE) {
            return;     // 站着不动就不留
        }
        ServerLevel level = player.serverLevel();
        // 身后的电弧尾巴：从上一 tick 的位置连到当前位置（只撒点看不出"闪电" ✗）
        Vec3 from = new Vec3(player.xOld, player.yOld + 0.9D, player.zOld);
        Vec3 to = new Vec3(player.getX(), player.getY() + 0.9D, player.getZ());
        arcLine(level, from, to, 6, 0.14D);
        if (time % (WIND_TRAIL_INTERVAL * 3) == 0) {
            // 偶尔在身上再蹦一条短弧，跑动时更"带电"
            arcLine(level, randomBodyPoint(player), randomBodyPoint(player), 4, 0.1D);
        }

        AABB box = player.getBoundingBox().inflate(WIND_HIT_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity target : targets) {
            if (!isEnemy(player, target) || target.distanceTo(player) > WIND_HIT_RADIUS) {
                continue;
            }
            // 同一个敌人不要每 2 tick 被扎一次：靠 hurt 的无敌帧天然限流
            zap(level, player, target, WIND_DAMAGE);
        }
    }

    /**
     * 闪电登神：把<b>我们自己的</b>法术冷却清掉。
     *
     * <p>只清 tnc: 的法术：别的 mod 的冷却不该被我们的 buff 影响。
     * 引擎类在 try/catch 里引用，没装引擎时这段直接跳过（软依赖）。
     */
    private static void clearOurCooldowns(ServerPlayer player) {
        try {
            Impl.clearCooldowns(player);
        } catch (Throwable error) {
            // 引擎不在 / API 变了：静默跳过，"无冷却"失效但不该拖垮整局游戏
        }
    }

    // ------------------------------------------------------------------
    //  雷速链 5 档的表现（2026-09-22）
    // ------------------------------------------------------------------

    /**
     * <b>t1 雷速</b>：跑动时脚下不断跳出<b>小电弧</b>（两脚之间来回蹦）；站着不动只偶尔闪一下。
     *
     * <p>2026-09-22 改：原来是零散的小亮点（作者："怎么是星星，丑死了 ✗"），
     * 现在改成"脚到脚"的短锯齿线 —— 同样几个粒子，观感从"星星"变成"电弧" ✓。
     */
    private static void hasteSparks(ServerPlayer player, long time) {
        if (time % HASTE_SPARK_INTERVAL != 0) {
            return;
        }
        Vec3 moved = player.position().subtract(player.xOld, player.yOld, player.zOld);
        boolean moving = moved.length() > HASTE_MIN_MOVE;
        ServerLevel level = player.serverLevel();
        if (!moving) {
            arcBall(level, new Vec3(player.getX(), player.getY() + 0.15D, player.getZ()), 3, 0.35D);
            return;
        }
        double side = 0.22D;
        for (int i = 0; i < 2; i++) {
            double ox = (i == 0 ? -side : side);
            Vec3 a = new Vec3(player.getX() + ox, player.getY() + 0.06D, player.getZ());
            Vec3 b = new Vec3(player.getX() - ox, player.getY() + 0.06D, player.getZ());
            arcLine(level, a, b, 6, 0.12D);
        }
    }

    /**
     * <b>t2 闪电移位</b>：起点与落点各炸一团电弧 + 短促传送音，
     * 中间再画一条贯穿两端的<b>雷线</b>（一闪而过，最能看出"这是闪电"）。
     *
     * <p>用 {@code xOld/yOld/zOld} 拿起点 —— 施法这一 tick 玩家已经落到终点了 ✗。
     */
    private static void blinkBurst(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 now = player.position().add(0.0D, 1.0D, 0.0D);
        Vec3 was = new Vec3(player.xOld, player.yOld + 1.0D, player.zOld);
        boolean teleported = now.distanceToSqr(was) > BLINK_MIN_DISTANCE_SQR;

        if (teleported) {
            arcBall(level, was, BLINK_BURST_COUNT, BLINK_BURST_SPREAD);
            level.sendParticles(ParticleTypes.FLASH, was.x, was.y, was.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            arcLine(level, was, now, 26, 0.55D);
            level.playSound(null, was.x, was.y, was.z, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.45F, 1.7F);
        }
        arcBall(level, now, BLINK_BURST_COUNT, BLINK_BURST_SPREAD);
        level.sendParticles(ParticleTypes.FLASH, now.x, now.y, now.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.25F);
    }

    /** <b>t4 闪电降低冷却</b>：脚下一圈电弧环（首尾相接）+ 一声晶鸣。 */
    private static void rechargeRing(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double cx = player.getX();
        double cy = player.getY() + 0.15D;
        double cz = player.getZ();
        for (int i = 0; i < RECHARGE_RING_COUNT; i++) {
            double a1 = i * (Math.PI * 2.0D / RECHARGE_RING_COUNT);
            double a2 = a1 + Math.PI * 2.0D / RECHARGE_RING_COUNT;
            Vec3 p1 = new Vec3(cx + Math.cos(a1) * RECHARGE_RING_RADIUS, cy, cz + Math.sin(a1) * RECHARGE_RING_RADIUS);
            Vec3 p2 = new Vec3(cx + Math.cos(a2) * RECHARGE_RING_RADIUS, cy, cz + Math.sin(a2) * RECHARGE_RING_RADIUS);
            arcLine(level, p1, p2, 4, 0.08D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.9F);
    }

    /** <b>t4</b>：施法后 3 秒内，绕着身体螺旋上升的短电弧（"魔力回来了"的观感）。 */
    private static void rechargeFlourish(ServerPlayer player, long time) {
        Long until = RECHARGE_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (time > until) {
            RECHARGE_UNTIL.remove(player.getUUID());
            return;
        }
        if (time % 2 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double climb = ((until - time) % 20) / 20.0D;      // 0..1 循环上升
        double a = (time % 40) / 40.0D * Math.PI * 2.0D;
        double y = player.getY() + 0.2D + climb * 1.9D;
        Vec3 from = new Vec3(player.getX() + Math.cos(a) * 0.6D, y, player.getZ() + Math.sin(a) * 0.6D);
        Vec3 to = new Vec3(player.getX() + Math.cos(a + 1.2D) * 0.45D, y + 0.12D,
                player.getZ() + Math.sin(a + 1.2D) * 0.45D);
        arcLine(level, from, to, 5, 0.08D);
    }

    /**
     * <b>t5 闪电登神</b>：身上来回跳的<b>电弧</b> + 三颗环绕电光 +
     * 每走一格留一枚<b>雷印</b>（地上的一小团电弧，存活 40 tick）。
     *
     * <p>环绕电光故意比"环绕雷球"（4 颗、r=1.4、会电人）小一圈快一点，
     * 让玩家一眼能分清"这是登神的外观"还是"那段会扎人的光环" ✓
     */
    private static void ascensionAura(ServerPlayer player, long time) {
        ServerLevel level = player.serverLevel();
        double phase = (time % 40) / 40.0D * Math.PI * 2.0D;
        for (int i = 0; i < ASCENSION_ORBIT_COUNT; i++) {
            double a1 = phase * 2.0D + i * (Math.PI * 2.0D / ASCENSION_ORBIT_COUNT);
            double a2 = a1 + 0.5D;
            Vec3 p1 = new Vec3(player.getX() + Math.cos(a1) * ASCENSION_ORBIT_RADIUS,
                    player.getY() + 0.9D + Math.sin(a1 * 2.0D) * 0.35D,
                    player.getZ() + Math.sin(a1) * ASCENSION_ORBIT_RADIUS);
            Vec3 p2 = new Vec3(player.getX() + Math.cos(a2) * ASCENSION_ORBIT_RADIUS,
                    player.getY() + 0.9D + Math.sin(a2 * 2.0D) * 0.35D,
                    player.getZ() + Math.sin(a2) * ASCENSION_ORBIT_RADIUS);
            arcLine(level, p1, p2, 3, 0.05D);
        }
        if (time % ASCENSION_ARC_INTERVAL == 0) {
            // 身上：两点之间蹦一条电弧（比撒点更像"电在身上跳" ✓）
            for (int i = 0; i < ASCENSION_ARC_COUNT; i++) {
                arcLine(level, randomBodyPoint(player), randomBodyPoint(player), 5, 0.1D);
            }
        }
        // 雷印：每走一格落一枚
        Vec3 pos = player.position();
        Vec3 last = LAST_MARK.get(player.getUUID());
        if (last == null || last.distanceTo(pos) >= ASCENSION_MARK_STEP) {
            LAST_MARK.put(player.getUUID(), pos);
            List<SparkMark> list = MARKS.computeIfAbsent(player.getUUID(), key -> new ArrayList<>());
            list.add(new SparkMark(pos, time + ASCENSION_MARK_LIFE));
            while (list.size() > ASCENSION_MARK_MAX) {
                list.remove(0);
            }
        }
    }

    /** 身上的随机一点（画电弧用）。 */
    private static Vec3 randomBodyPoint(ServerPlayer player) {
        double a = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double r = 0.3D + player.getRandom().nextDouble() * 0.3D;
        double y = player.getY() + 0.25D + player.getRandom().nextDouble() * 1.45D;
        return new Vec3(player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r);
    }

    /** 地上的雷印：在原地噼啪一小会儿，然后自己消失。 */
    private static void sparkMarks(ServerPlayer player, long time) {
        List<SparkMark> list = MARKS.get(player.getUUID());
        if (list == null || list.isEmpty()) {
            return;
        }
        list.removeIf(mark -> mark.expireAt() < time);
        if (list.isEmpty()) {
            MARKS.remove(player.getUUID());
            return;
        }
        if (time % 3 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (SparkMark mark : list) {
            // 每枚雷印是地上的一小团电弧（不是一堆星星 ✗）
            arcBall(level, new Vec3(mark.pos().x, mark.pos().y + 0.08D, mark.pos().z), 3, 0.28D);
        }
    }


    /** 真正碰 SpellEngine 类的部分：只在引擎存在时被加载。 */
    private static final class Impl {

        static void clearCooldowns(ServerPlayer player) {
            // 冷却管理器挂在玩家身上（SpellEngine 的 PlayerEntityMixin 让 Player 实现了
            // SpellCasterEntity 接口），拿实例只能走这个接口
            net.spell_engine.internals.SpellCooldownManager manager =
                    ((net.spell_engine.internals.casting.SpellCasterEntity) player).getCooldownManager();
            for (SpellCatalog.Entry entry : SpellCatalog.all()) {
                // remove 内部会发包给客户端，不然客户端那边还在冷却、法术根本按不出去
                manager.remove(entry.id());
            }
        }

        /**
         * 在<b>自己那颗大雷球</b>的位置上画环 ✓（引擎的投射物实体，认主人即可）。
         *
         * <p>窗口只有 20 秒，所以不会误伤别的法术的投射物 ✗ —— 而且只认 `getOwner() == player` ✓。
         */
        static void ringOnOwnProjectiles(ServerPlayer player, long time) {
            for (net.spell_engine.entity.SpellProjectile projectile
                    : player.serverLevel().getEntitiesOfClass(net.spell_engine.entity.SpellProjectile.class,
                            player.getBoundingBox().inflate(96.0D))) {
                if (projectile.getOwner() != player) {
                    continue;
                }
                ring(player.serverLevel(), projectile.position(), BIG_BALL_RING, BIG_BALL_RING_POINTS, time);
            }
        }
    }

    // ------------------------------------------------------------------
    //  小工具
    // ------------------------------------------------------------------

    /**
     * 神级大雷球：在自己那颗球的位置上每 tick 画一圈粒子 ✓。
     *
     * <p>引擎类的引用包在 try/catch 里（软依赖 ✓）：引擎不在时这一段直接跳过，不影响别的法术。
     */
    private static void followBigBall(ServerPlayer player, long time) {
        Long until = BIG_BALL_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (time > until) {
            BIG_BALL_UNTIL.remove(player.getUUID());
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        try {
            Impl.ringOnOwnProjectiles(player, time);
        } catch (Throwable ignored) {
            // 引擎不在 / API 变了：静默跳过（只是少一层粒子外观）
        }
    }

    /**
     * 在 center 处画一圈粒子（"球的边缘一圈"）✓：水平环 ＋ 一个慢慢转的竖直环。
     *
     * <h2>2026-09-22 第二版（作者："粒子特效跟随是跟随了，但是效果非常差，跟前面完全没法比"）</h2>
     * 第一版只用了 {@code witch} ＋ {@code electric_spark}（都是小亮点 ✗）＋ 零速度、零抖动 ——
     * 看起来就是"一圈静止的点" ✗。这次三处改进：
     * <ol>
     *   <li>改用 {@link #arc()}：**包里那套真电弧粒子**（和法术 JSON 里好看的是同一批 ✓）；</li>
     *   <li>给每颗粒子**一点向外/向上的速度**（不再死死钉在一个点上 ✓）；</li>
     *   <li>角度加**随机抖动** ＋ 每 3 个点才画一次竖直环（不然太规整、太费 ✗）。</li>
     * </ol>
     *
     * <p>供环绕雷球实体与"大雷球贴粒子"共用 ✓。
     */
    public static void ring(ServerLevel level, Vec3 center, double radius, int points, long time) {
        double tilt = (time % 200) / 200.0D * Math.PI * 2.0D;
        net.minecraft.core.particles.ParticleOptions arcParticle = arc();
        for (int i = 0; i < points; i++) {
            double jitter = (level.random.nextDouble() - 0.5D) * (Math.PI * 2.0D / points) * 0.9D;
            double a = i * (Math.PI * 2.0D / points) + jitter;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double r = radius * (0.96D + level.random.nextDouble() * 0.08D);
            // 水平环：电弧（主）＋ 紫点、黄点各一，都带一点向外的速度 ✓
            level.sendParticles(arcParticle, center.x + cos * r, center.y, center.z + sin * r,
                    1, 0.05D, 0.05D, 0.05D, 0.02D);
            level.sendParticles(ParticleTypes.WITCH, center.x + cos * r, center.y, center.z + sin * r,
                    1, 0.05D, 0.05D, 0.05D, 0.03D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + cos * r, center.y, center.z + sin * r,
                    1, 0.05D, 0.05D, 0.05D, 0.03D);
            // 竖直环：每 3 个点画一次（够看出"球在转"，又不至于粒子翻倍 ✗）
            if (i % 3 == 0) {
                double vx = center.x + cos * r * Math.cos(tilt);
                double vy = center.y + sin * r;
                double vz = center.z + cos * r * Math.sin(tilt);
                level.sendParticles(arcParticle, vx, vy, vz, 1, 0.05D, 0.05D, 0.05D, 0.02D);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, vx, vy, vz,
                        1, 0.05D, 0.05D, 0.05D, 0.03D);
            }
        }
    }

    private static boolean has(ServerPlayer player, net.minecraftforge.registries.RegistryObject<net.minecraft.world.effect.MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 敌人 = 不是自己、不是队友（简单判据：不是玩家、也不是驯服过的宠物）。 */
    private static boolean isEnemy(Player player, LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        if (target instanceof Mob mob && mob.getTarget() == player) {
            return true;
        }
        return !(target instanceof Player);
    }

    /** 造成一次魔法伤害（归属给施法者，好让击杀统计/掉落正常）。 */
    private static void zap(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
        target.hurt(level.damageSources().indirectMagic(player, player), amount);
    }
}
