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

    // ------------------------------------------------------------------
    //  主链雷法的"劈"（2026-09-27 作者："像这种雷击，就应该有闪电劈敌人啊"）
    // ------------------------------------------------------------------

    /** 雷击（t3）的准星射线长度（格）。 */
    private static final double STRIKE_RANGE = 32.0D;
    /** 闪电从多高的天上劈下来（视觉）✓ */
    private static final double STRIKE_HEIGHT = 12.0D;

    /** 雷场（t2）：只在第 20 / 60 tick 各劈一轮 ⇒ 场里的敌人**正好各挨两下** ✓（作者原话） */
    private static final int[] FIELD_STRIKE_TICKS = {20, 60};
    private static final int FIELD_WINDOW = 100;
    private static final double FIELD_RADIUS = 14.0D;   // 2026-09-27 作者："范围也太小了" -> 6 -> 14
    private static final float FIELD_DAMAGE = 5.0F;

    /** 雷暴（t4）：每 15 tick 一轮，每轮最多 3 个目标，一直劈到 buff 结束 ✓（作者："劈到时间结束"） */
    private static final int STORM_INTERVAL = 15;
    private static final int STORM_WINDOW = 200;
    private static final double STORM_RADIUS = 14.0D;
    private static final float STORM_DAMAGE = 4.0F;
    private static final int STORM_TARGETS_PER_WAVE = 3;

    /**
     * 神在投篮（t5）：施法那一刻，**你正在瞄的那个敌人脚下**铺一张大阵 ＋ 三尊神 ＋ 一颗慢速大雷球 ✓
     *
     * <p>★★ 2026-09-29 恢复（作者："全部恢复"）：这一组数值是作者**逐个验收过**的
     * （3.2i-4：球的体积 ×3、三尊神环绕半径拉大、高度降三格、落地才炸、爆炸不击飞），
     * 被 commit `1b31dea4` 的回滚换回了 9-27 的旧值（神 12 / 高 +6 / 球 20 / 下落 1.5 / 没有落地爆炸 ✗），
     * 这里按验收过的值改回来 ✓。
     */
    private static final int DIVINE_WINDOW = 40;
    private static final double DIVINE_RADIUS = 20.0D;
    /** t5 顺带把天变黑（打雷下雨）多少 tick ✓ */
    private static final int DIVINE_WEATHER_TICKS = 200;
    /** 大雷球落地那一下：伤害与半径（球**落地才炸** ✓ 作者 2026-09-29） */
    private static final float DIVINE_BALL_DAMAGE = 25.0F;
    private static final double DIVINE_BALL_BLAST = 8.0D;
    /** 神留 120 tick（6 秒）—— 作者 2026-09-30："持续时间可以短一点" ✓（原来 200 = 10 秒）；球落地后还留 40 tick（2 秒）✓ */
    private static final int DIVINE_GOD_LIFE = 120;
    private static final int DIVINE_BALL_LIFE = 40;
    /** 球下落速度（格/tick）：0.5 = 慢慢砸下来，看得清 ✓ */
    private static final double DIVINE_BALL_FALL = 0.5D;
    /**
     * 三尊神：体积 ×10 ⇒ 线性 26 ✓；环绕半径 **30 格**；悬停高度 **10 格** ✓
     *
     * <p>2026-09-30 作者："神的格数太高了，改成 20 格高吧" → 然后又"三个 god 的高度降低到 10 格吧，
     * 间距可以拉大点" ⇒ 27 → 20 → **10** 格 ✓；半径 22 → **30** 格 ✓（间距拉大 ✓）。
     */
    private static final double DIVINE_GOD_SCALE = 26.0D;
    private static final double DIVINE_GOD_RADIUS = 30.0D;
    private static final double DIVINE_GOD_HEIGHT = 10.0D;
    /** 大雷球：体积 ×3 ⇒ 线性 29 ✓ */
    private static final double DIVINE_BALL_SCALE = 29.0D;
    /** 球模型自身的半径（格）—— 用来把球心抬起来，免得半个球埋进地里 ✗ */
    private static final double DIVINE_BALL_MODEL_BLOCKS = 13.0D / 16.0D;
    /** 锚点脚下那张阵（半径 15 格、200 tick）✓ */
    private static final double DIVINE_CIRCLE_RADIUS = 15.0D;
    private static final int DIVINE_CIRCLE_LIFE = 200;
    /**
     * 锚点选取的"瞄准锥"：只认与准星夹角 ≤ 这个度数的敌人 ✓
     *
     * <p>为什么要锥：旧的"范围内第一个敌人"会把村民/动物/剧情 NPC 也算进去（{@code isEnemy}
     * 只排除玩家自己 ✗），于是三尊神和大雷球常常生成在**身后/墙后** ⇒ 玩家什么都看不见 ✗。
     */
    private static final double DIVINE_AIM_DEGREES = 35.0D;
    private static final Map<UUID, Long> DIVINE_UNTIL = new ConcurrentHashMap<>();
    /** 这一次施法是否已经铺过球了（防止窗口内每 tick 重复铺 ✓）。 */
    private static final java.util.Set<UUID> DIVINE_FIRED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 「雷系三条链 t4 / t5 一定有魔法阵」的保底阵尺寸 ✓
     * （作者 2026-09-29："保证 t4 和 t5 施法的时候会有魔法阵，雷的三条链"）
     *
     * <p>只用于**前面没自己铺过阵**的那几个（雷速链 t4「闪电降低冷却」/ t5「闪电登神」——
     * 自增益类 ⇒ 阵铺在自己脚下 ✓）。主链那两张（10.5 / 14.0）、雷球那两张
     * （20 在准星落点 / 锚点那张）都保持原样 ✓。
     */
    private static final double MAGIC_CIRCLE_R4 = 12.0D;
    private static final int MAGIC_CIRCLE_L4 = 190;
    private static final double MAGIC_CIRCLE_R5 = 15.0D;
    private static final int MAGIC_CIRCLE_L5 = 220;

    /**
     * ★★ 跟随神（2026-09-29 作者："我已登神，我希望玩家背后会出现 god 的模型跟随"）
     *
     * <p>登神（{@code LIGHTNING_ASCENSION} = t5「闪电登神」给的 buff）期间，
     * 玩家背后**恒有一尊 {@code lightning_god} 模型的雷神跟着** ✓ ——
     * 用的是和 t5「神在投篮」天上那三尊**完全同一个模型**（{@code projectile/lightning_god}）✓。
     */
    private static final int GOD_FOLLOWER_COUNT = 1;
    /** 跟在背后多少格（离玩家中心）✓ */
    private static final double GOD_FOLLOWER_BACK = 3.4D;
    /** 抬高多少格（贴地悬停感）✓ */
    private static final double GOD_FOLLOWER_UP = 1.1D;
    /** 尺寸：模型原生 1.63×1.13×0.94 格 ⇒ 5.0 倍 ≈ **5.6 格高** ✓（t5 那三尊是 26 ≈ 29 格 ✗ 当跟随者太大） */
    private static final double GOD_FOLLOWER_SCALE = 5.0D;
    /** 多尊时的角间隔（单尊用不到 ✓） */
    private static final double GOD_FOLLOWER_ARC_DEGREES = 34.0D;

    /**
     * ★★ 登神链（雷速链 {@code SPEED}）t1..t5 的「<b>万雷归体</b>」
     * （2026-09-29 作者："登神链他们从 t1 到 t5，根据等级，释放之后应该有无数雷粒子从外部向角色汇集，
     * 等级越高越多"）
     *
     * <p>做法：**释放**那一刻开一个窗口，窗口内每 tick 在玩家周围的球壳上随机取点，
     * 给每颗粒子一个"**指向玩家**"的初速度（{@code sendParticles(..., count = 0, vx, vy, vz, 0)}：
     * 原版在 {@code count == 0} 时把这三个数当**定向速度**用 ✓，不是随机散布 ✗）
     * ⇒ 看上去就是"雷电从四面八方被吸进身体" ✓。
     */
    private record Gather(long start, long until, int tier) {
    }

    private static final Map<UUID, Gather> GATHER = new ConcurrentHashMap<>();
    /** 每个档位：窗口 tick / 每 tick 颗数 / 起始半径（格）✓（索引 = tier-1） */
    private static final int[] GATHER_TICKS = {40, 55, 70, 90, 120};
    private static final int[] GATHER_PER_TICK = {3, 6, 10, 16, 24};
    private static final double[] GATHER_RADIUS = {6.0D, 9.0D, 12.0D, 16.0D, 21.0D};
    /** 颗粒子飞几个 tick 到身上 ✓ */
    private static final double GATHER_TRAVEL_TICKS = 7.0D;
    /** 收束：窗口末尾粒子从更近的地方来 ✓ */
    private static final double GATHER_CLOSE_IN = 0.45D;
    /** 登神 buff 期间常驻的"余波"倍率（t5 登神 12 秒 > 窗口 6 秒 ✓；0 = 关掉） */
    private static final double ASCENDED_GATHER_MUL = 0.35D;

    /** 谁在雷场 / 雷暴的窗口里（UUID -> 结束时的 gameTime）。 */
    private static final Map<UUID, Long> FIELD_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> STORM_UNTIL = new ConcurrentHashMap<>();

    /**
     * 一道"从天上劈下来"的闪电（纯视觉）：<b>竖着</b>一条电弧 ＋ 落点炸一圈 ＋ 白光 ✓。
     *
     * <p>为什么不做成投射物模型：那样每一道都要一个实体 ＋ 一次发射，
     * 而这里要的是"啪一下就劈完了" ✓ —— 直接用我们已有的 {@link #arcLine}（环绕雷球的电击就是它，
     * 作者认可过观感 ✓）＋ {@link #arcBall} 拼出来，零新实体、零新渲染器 ✓。
     */
    private static void strikeVisual(ServerLevel level, Vec3 at, int points, double spread) {
        // 2026-09-27 author: "I gave you the lightning model, why are field/storm/strike using
        // particles?" -> spawn the real bolt (his flash model, rendered by
        // TNLightningStrikeRenderer) so it looks exactly like the heavenly thunder strike.
        // `spread` doubles as the size knob: 1.5 -> 2.25x, 2.4 (the big strike) -> 3.6x.
        TNLightningStrikeEntity bolt = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        if (bolt != null) {
            bolt.configure(spread * 3.0D, 6, at.y, spread >= 2.0D ? 9.0D : 3.0D);   // x2 size (author) + fall from 24 blocks up
            bolt.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            level.addFreshEntity(bolt);
        }
        arcLine(level, at.add(0.0D, STRIKE_HEIGHT, 0.0D), at.add(0.0D, 0.2D, 0.0D), points, spread);
        arcBall(level, at, 18, 1.2D);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.6D, at.z, 2, 0.1D, 0.1D, 0.1D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.4D, at.z,
                30, 0.35D, 0.7D, 0.35D, 0.5D);
    }

    /** 劈一个敌人：天上一条电弧 ＋ 落点爆一圈 ＋ 真的扣血 ✓。 */
    private static void strikeEnemy(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
        Vec3 at = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        strikeVisual(level, at, 16, 1.5D);
        zap(level, player, target, damage);
        level.playSound(null, at.x, at.y, at.z,
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** 雷击（t3）：在**准星落点**劈一道"超级大雷"（更大更粗 ✗ 视觉；伤害由法术 JSON 给 ✓）。 */
    private static void bigStrike(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.HitResult hit = player.pick(STRIKE_RANGE, 0.0F, false);
        Vec3 at = hit.getLocation();
        strikeVisual(level, at, 30, 4.8D);   // x2 again (author 2026-09-27) -> 14.4x bolt
        level.playSound(null, at.x, at.y, at.z,
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    /** 雷场（t2）：第 20 / 60 tick 各劈一轮范围内的敌人 ⇒ 每个敌人挨两下 ✓。 */
    

    private static void tickLightningField(ServerPlayer player, long time) {
        Long until = FIELD_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (time > until || !has(player, TNEffects.LIGHTNING_FIELD)) {
            FIELD_UNTIL.remove(player.getUUID());
            return;
        }
        long age = time - (until - FIELD_WINDOW);
        ServerLevel level = null;
        for (int offset : FIELD_STRIKE_TICKS) {
            if (age != offset) {
                continue;
            }
            if (level == null) {
                level = player.serverLevel();
            }
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(FIELD_RADIUS))) {
                if (isEnemy(player, target) && target.distanceTo(player) <= FIELD_RADIUS) {
                    strikeEnemy(level, player, target, FIELD_DAMAGE);
                }
            }
        }
    }

    /**
     * 神在投篮（t5）：**你正在瞄的那个敌人 / 准星落点**脚下，铺一张大阵 ＋ 三尊环绕的神 ＋ 一颗慢速大雷球 ✓
     *
     * <p>锚点规则（2026-09-29 修，作者："我的模型呢，球的模型跟 god 的模型嘞，没看见"）：
     * <ul>
     *   <li>① 20 格内"敌对生物"里与准星夹角最小的那只（≤ {@link #DIVINE_AIM_DEGREES}）——"我看着谁就投谁" ✓</li>
     *   <li>② 一只都没有 → 准星落点（一定在视线方向上 ✓）</li>
     * </ul>
     * 旧代码取的是"**遍历顺序里第一个** isEnemy 的目标"✗ —— 而 isEnemy 把村民、动物、剧情 NPC
     * 全算成敌人（只排除玩家自己）✗，于是锚点常常落在**身后/墙后**：三尊神、大雷球、魔法阵全生成在背后，
     * 玩家什么都看不见，却"确实有伤害" ✗。
     *
     * <p>三尊神的数值是作者验收过的：体积 ×10（线性 26）、环绕半径 22 格、悬停 27 格、**朝向圆心** ✓
     * （模型的**脸在 −Z** ⇒ 朝向用原版那套 yaw = atan2(−dx, dz) ✓）。
     */
    private static void tickDivineShot(ServerPlayer player, long time) {
        Long until = DIVINE_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (time > until) {
            DIVINE_UNTIL.remove(player.getUUID());
            DIVINE_FIRED.remove(player.getUUID());
            return;
        }
        if (!DIVINE_FIRED.add(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // 锚点：**玩家正在瞄的那个敌人**优先，其次准星落点 ✓
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F).normalize();
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        double bestDot = Math.cos(Math.toRadians(DIVINE_AIM_DEGREES));
        net.minecraft.world.phys.Vec3 anchor = null;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DIVINE_RADIUS))) {
            if (!isDivineTarget(player, target) || target.distanceTo(player) > DIVINE_RADIUS) {
                continue;
            }
            net.minecraft.world.phys.Vec3 to = target.position()
                    .add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            if (to.lengthSqr() < 1.0E-4D) {
                continue;                       // 站在我身上：不算"瞄着他" ✓
            }
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                anchor = target.position();
            }
        }
        if (anchor == null) {
            anchor = aimPoint(player);
        }
        // ① 三尊神：围成一个大圆、每一尊都**朝圆心看** ✓
        //    悬停高度 = fallTo + FALL_HEIGHT（实体 tick 里定的 ✓）⇒ 想悬停在 DIVINE_GOD_HEIGHT
        //    就得把 fallTo 设成「目标高度 − FALL_HEIGHT」✓
        for (int i = 0; i < 3; i++) {
            double ang = i * (Math.PI * 2.0D / 3.0D) + (time % 628) * 0.01D;
            TNLightningStrikeEntity god = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
            if (god == null) {
                continue;
            }
            god.asGod();
            double gx = anchor.x + Math.cos(ang) * DIVINE_GOD_RADIUS;
            double gz = anchor.z + Math.sin(ang) * DIVINE_GOD_RADIUS;
            // 朝向圆心 ✓ —— ★ 约定：look = (−sin yaw, +cos yaw)（见 getViewVector）
            //   ⇒ 想面向 (dx, dz) 必须 yaw = atan2(−dx, dz) ✓
            //   （2026-09-30 实机反馈"朝向反了"：原来写成 atan2(dx, dz)，x 分量正好反 ✗）
            float yaw = (float) Math.toDegrees(Math.atan2(gx - anchor.x, anchor.z - gz));
            god.configure(DIVINE_GOD_SCALE, DIVINE_GOD_LIFE,
                    anchor.y + DIVINE_GOD_HEIGHT - TNLightningStrikeEntity.FALL_HEIGHT, 3.0D);
            god.moveTo(gx, anchor.y + DIVINE_GOD_HEIGHT, gz, yaw, 0.0F);
            level.addFreshEntity(god);
        }
        // ② 一颗雷霆大球：**慢慢**砸下来，**落地那一下**才结算伤害/爆炸 ✓（作者："应该等球落地"）
        //    球心要抬起"球半径"那么多，否则半个球埋进地里 ✗（而爆心仍然是地面上的锚点 ✓）
        TNLightningStrikeEntity ball = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        if (ball != null) {
            double ballRadius = DIVINE_BALL_MODEL_BLOCKS * DIVINE_BALL_SCALE / 2.0D;
            ball.asBall();
            ball.setFallSpeed(DIVINE_BALL_FALL);
            ball.configure(DIVINE_BALL_SCALE, DIVINE_BALL_LIFE, anchor.y + ballRadius, 12.0D);
            ball.setLandImpact(player, DIVINE_BALL_DAMAGE, DIVINE_BALL_BLAST);
            ball.moveTo(anchor.x, anchor.y + ballRadius, anchor.z, 0.0F, 0.0F);
            level.addFreshEntity(ball);
        }
        // 锚点脚下那张阵：半径 15 格（球现在 23.6 格直径 ⇒ 8 格的阵整个压在球底下、看不见 ✗）
        spawnMagicCircleAt(level, anchor, DIVINE_CIRCLE_RADIUS, DIVINE_CIRCLE_LIFE);
    }

    /**
     * "神在投篮"要认的目标 ✓（包内共享给实体侧不需要；这里就一处用）
     *
     * <p>比 {@link #isEnemy} 更严：原版 {@code Enemy} 或**正在打玩家**的怪才算 ——
     * 村民 / 动物 / 剧情 NPC 不算 ✗（它们以前会被当成"我瞄着的敌人"，把三尊神引到背后去 ✗）。
     */
    private static boolean isDivineTarget(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return false;
        }
        if (target instanceof net.minecraft.world.entity.monster.Enemy) {
            return true;
        }
        return target instanceof Mob mob && mob.getTarget() == player;
    }

    /** 雷暴（t4）：每 {@link #STORM_INTERVAL} tick 劈几个敌人，**一直劈到 buff 结束** ✓。 */
    private static void tickLightningStorm(ServerPlayer player, long time) {
        Long until = STORM_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (time > until || !has(player, TNEffects.LIGHTNING_STORM)) {
            STORM_UNTIL.remove(player.getUUID());
            return;
        }
        if (time % STORM_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        java.util.List<LivingEntity> targets = new java.util.ArrayList<>(level.getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(STORM_RADIUS)));
        targets.removeIf(t -> !isEnemy(player, t) || t.distanceTo(player) > STORM_RADIUS);
        if (targets.isEmpty()) {
            // 没敌人也别让"雷暴"哑掉：在半空随机劈一道，纯视觉 ✓
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double r = 2.0D + level.random.nextDouble() * (STORM_RADIUS - 2.0D);
            strikeVisual(level, player.position().add(Math.cos(a) * r, 0.2D, Math.sin(a) * r), 12, 1.4D);
            return;
        }
        java.util.Collections.shuffle(targets);
        int n = Math.min(STORM_TARGETS_PER_WAVE, targets.size());
        for (int i = 0; i < n; i++) {
            strikeEnemy(level, player, targets.get(i), STORM_DAMAGE);
        }
    }

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
        if(com.tnc.tnc.combat.DownedCombat.isDowned(player))return;
        String path = spellId.getPath();

        // 真的放出去了 ⇒ 消掉"放行了却没放出来"的哑火待定项 ✓（见 ManaGate.PENDING）
        ManaGate.noteCastHappened(player);

        // 火系那几条（自爆扣最大生命 10%）在自己的类里
        TNFireMechanics.onSpellCast(player, spellId, data);
        com.tnc.tnc.magic.water.TNWaterSpellEntity.cast(player, spellId);
        com.tnc.tnc.magic.water.TNWaterFieldEntity.cast(player, spellId);
        com.tnc.tnc.magic.water.ChaosSilence.cast(player, spellId);
        // 火球链（火系第 1 条链）：自有投射物。命中时要精确知道「是哪个法术、打了多少」
        // 才能挂焚身，所以不走引擎的 PROJECTILE —— 引擎不会把法术 id 告诉伤害事件。
        // 不是本链的法术它返回 false，所以这里无脑调一遍是安全的（和水系同一个约定）。
        com.tnc.tnc.magic.fire.TNFireBoltEntity.cast(player, spellId);

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

        // ★ 查一次目录条目：下面的"魔法阵保证"按 **链 + 档位** 判 ✓（不写死法术 id）
        SpellCatalog.Entry castEntry = SpellCatalog.byId(spellId);
        // 这一格里"某个分支已经自己铺过阵了吗"——铺过的（阵摆在该在的位置：脚下 / 准星落点 / 锚点）
        // 就不该再补一张，否则同一个法术脚下会出现两张阵 ✗
        boolean circleOwn = false;

        // 传说级 / 神级雷球：脚下留下魔法阵 ✓（作者 2026-09-22 指定）
        // tier 4 = 传说级、tier 5 = 神级（见 Element.tierName）
        if (path.equals("explosive_thunder_orb")) {
            // (无魔法阵：爆炸雷球现在是 t3，不做 ✗)
        } else if (path.equals("cataclysm_thunder_orb")) {
            spawnMagicCircleAt(player.serverLevel(), aimPoint(player), 20.0D, 260);
            circleOwn = true;                             // 阵在**准星落点**（作者要的"敌人脚下"✓）
            // 大雷球的粒子不跟随 ✗ -> 自己开一个窗口，每 tick 在球的位置画环 ✓
            BIG_BALL_UNTIL.put(player.getUUID(), player.level().getGameTime() + BIG_BALL_WINDOW);
        } else if (path.equals("god_descent")) {
            // 神在投篮（t5）：**开窗口** —— 真正的三尊神 / 大雷球 / 锚点魔法阵由 tickDivineShot 摆 ✓
            // 2026-09-29 修（作者："t5 施法总是有问题"）：这里原来只有一张脚下的阵 ✗，
            //   DIVINE_UNTIL 从头到尾**没有任何地方写过** ⇒ tickDivineShot 形同死代码：
            //   按下去只有引擎那一下冲击波，三尊神和大雷球一颗都不出现 ✗。
            DIVINE_FIRED.remove(player.getUUID());       // 新的一次施法 -> 允许再铺一次 ✓
            DIVINE_UNTIL.put(player.getUUID(), player.level().getGameTime() + DIVINE_WINDOW);
            spawnMagicCircle(player, 8.0D, 200);          // 自己脚下一张小的 ＋ 锚点一张大的 ✓
            circleOwn = true;
            // 施法那一刻天就黑下来 ✓（作者 2026-09-29：t5 = 雷雨天 + 三尊神 + 一颗慢速大雷球）
            // ⚠️ 只在"现在没打雷"时才改天：水法那份天气是**带租约**的（10 秒后要把原来的天气还回去 ✗），
            //    玩家自己 /weather 的结果同理 —— 不该被一个 t5 盖掉 ✓。
            ServerLevel castLevel = player.serverLevel();
            if (!castLevel.isThundering()) {
                castLevel.setWeatherParameters(0, DIVINE_WEATHER_TICKS, true, true);
            }
        }

        // 主链雷法（group = primary，5 档）：**释放的那一刻**就在脚下铺一张魔法阵 ✓
        // 作者 2026-09-27："魔法阵出现的不是很流畅，我建议是释放出魔法的那一刻就出现，然后逐渐淡化消失"
        //   ＋ "我说改的可是**主链**的雷法啊" —— 主链原来是**没有**魔法阵的 ✗（只有雷球链有）。
        // 时机：这个回调挂在引擎的 SPELL_CAST 上，且 `action == CHANNEL`（起手蓄力）在上面已经 return ✗
        //       —— 所以这里**就是"放出去"的那一 tick** ✓，不用再加延迟。
        // 尺寸/时长按档位递增：越高级的雷法，阵越大、留得越久 ✓（数值都在这一张表里，好调）
        //   阵的出场是**瞬时满亮度**、随后**平滑淡出**（渲染器负责，见 TNMagicCircleRenderer）✓
        else if (path.equals("spark")) {
            // (无魔法阵：t1 不做 -- 作者 2026-09-27：低档也铺 = "随便哪个魔法都有了" ✗)          // t1 电花
        } else if (path.equals("lightning_field")) {
            // (无魔法阵：t2 不做 ✗)         // t2 电场
            // 雷场：开一个 100 tick 的窗口，第 20 / 60 tick 各劈一轮 ⇒ 场内敌人各挨两下 ✓
            FIELD_UNTIL.put(player.getUUID(), player.level().getGameTime() + FIELD_WINDOW);
        } else if (path.equals("lightning_strike")) {
            // (无魔法阵：t3 不做 ✗)         // t3 雷击
            bigStrike(player);                            // 准星落点劈一道"超级大雷" ✓
        } else if (path.equals("lightning_storm")) {
            spawnMagicCircle(player, 10.5D, 170);        // t4 雷暴
            circleOwn = true;
            // 雷暴：窗口 200 tick，期间每 15 tick 劈一轮，一直劈到结束 ✓
            STORM_UNTIL.put(player.getUUID(), player.level().getGameTime() + STORM_WINDOW);
        } else if (path.equals("heavenly_thunder")) {
            spawnMagicCircle(player, 14.0D, 210);        // t5 天雷
            circleOwn = true;
        }

        // ★★ 保证：**雷系三条链（主链 CORE / 雷球 ORB / 雷速 SPEED）的 t4、t5 一定有魔法阵** ✓
        //    （作者 2026-09-29："保证 t4 和 t5 施法的时候会有魔法阵，雷的三条链"）
        //    前面几个分支已经按"阵该摆在哪"自己铺过了（雷暴/天雷在自己脚下、超级无敌大雷球在准星落点、
        //    神在投篮在脚下一张＋锚点一张 ✓）—— 这里补的是**还没铺过**的，也就是雷速链那两个：
        //      t4「闪电降低冷却」、t5「闪电登神」（自增益类 ⇒ 阵铺在自己脚下 ✓）。
        //    ★ 判据是 **chain + tier**，不是法术名单 ⇒ 以后加档 / 改名 / 换 id 都自动有 ✓
        //      （"保证"靠规则，不靠记性 —— 上次就是逐个 id 写 if，才漏了雷速链 ✗）
        if (needsGuaranteedCircle(castEntry, circleOwn)) {
            boolean god = castEntry.tier() >= 5;
            double radius = god ? MAGIC_CIRCLE_R5 : MAGIC_CIRCLE_R4;
            int life = god ? MAGIC_CIRCLE_L5 : MAGIC_CIRCLE_L4;
            spawnMagicCircle(player, radius, life);
            LOGGER.info("TN-C: 魔法阵（按链补）{} chain={} tier={} r={} life={}",
                    spellId, castEntry.chain(), castEntry.tier(), radius, life);
        }

        // ★★ 登神链（雷速链）t1..t5：释放后"万雷归体" —— 从四周向角色汇集，档位越高越多 ✓
        //    判据同样用**链**（见 startGather）✓
        if (castEntry != null && castEntry.chain() == SpellCatalog.Chain.SPEED) {
            startGather(player, castEntry.tier());
        }

        // ★★ 光系第二条链（光耀）：范围治疗 + 队友减伤 + 光系法阵 + 天使 + 转晴 + 怪物停手 ✓
        //    数值全在 light/TNLightChainMechanics 的一张表里 ✓；这里只转发 ✓
        if (com.tnc.tnc.light.TNLightChainMechanics.isLightChainSpell(path)) {
            com.tnc.tnc.light.TNLightChainMechanics.onSpellCast(player, path);
        }

        // ★★ 光系第三条链（光线）：向前数道彩色光线 / 天上开阵垂直落下的光柱 ✓
        //    （作者 2026-10-01："光线 — 大光线 — 巨大光线 — 圣光天降 — 五光十射" ✓）
        //    数值全在 light/TNLightBeamMechanics 的一张表里 ✓；每 tick 的推进见 tickPlayer ✓
        if (com.tnc.tnc.light.TNLightBeamMechanics.isLightBeamSpell(path)) {
            com.tnc.tnc.light.TNLightBeamMechanics.onSpellCast(player, path);
        }

        // ★★ 暗龙（暗系第五条链）2026-10-04 按作者要求**整条删了** ✗
        //    （"把龙法术都删了吧，包括光龙和暗龙" ✓ ⇒ 这里不再派发 ✓：
        //      法术 json / 法杖池条目都删了 ✓，Java 那条链的代码留着但**没有入口** ✓）

        // ★★ 暗系第三条链（召唤）：小恶魔 / 暗卫 / 暗之统领 / 暗之国王 / 邪神 ✓
        //    （作者 2026-10-04："暗魔法的召唤流没实装吗" ✓ —— 这五个法术原来挂的是
        //      **风系占位效果** ✗，现在真的把召唤物叫出来 ✓）
        if (com.tnc.tnc.dark.TNDarkSummonChain.isDarkSummonSpell(path)) {
            com.tnc.tnc.dark.TNDarkSummonChain.onSpellCast(player, path);
        }
    }

    /**
     * 雷系三条链 ✓ —— 主链 {@code CORE}（基础雷法）、雷球 {@code ORB}、雷速 {@code SPEED}。
     *
     * <p>"t4/t5 一定有魔法阵"这条规则就靠它判（见 {@code onSpellCast}）✓；
     * 火/水/风/土/暗那几条链**不在**这里 —— 它们的表现各自另做 ✗。
     */
    private static boolean isLightningChain(SpellCatalog.Chain chain) {
        return chain == SpellCatalog.Chain.CORE
                || chain == SpellCatalog.Chain.ORB
                || chain == SpellCatalog.Chain.SPEED;
    }

    /**
     * <b>纯规则</b>（可单测 ✓）：这一档雷法要不要补一张"保证阵"？
     *
     * <p>= 雷系三条链 ＋ 档位 ≥ 4 ＋ 前面没有分支自己铺过。
     * 抽成独立函数只为一件事：让 {@code MagicCircleGuaranteeTest} 能把"六张阵一张不少"钉在测试里 ✓
     * —— 靠人记 id 就是漏掉雷速链 t4/t5 的原因 ✗，靠规则 + 测试才叫"保证" ✓。
     */
    static boolean needsGuaranteedCircle(SpellCatalog.Entry entry, boolean alreadySpawned) {
        return !alreadySpawned && entry != null
                && entry.tier() >= 4 && isLightningChain(entry.chain());
    }

    /**
     * 在玩家脚下铺一张魔法阵（{@link TNMagicCircleEntity}，纯表现实体 ✓）。
     *
     * @param radius 半径（格）
     * @param life   存在多少 tick
     */
    /**
     * 在**指定坐标**铺魔法阵（作者 2026-09-27：雷球的阵要出现在"敌人脚下"，才有瞄准的感觉 ✓）。
     *
     * <p>★ 2026-09-30 改成 public：黑暗衍的「天打五雷轰」也要在他目标脚下铺一张 ✓
     * （作者："他的法术怎么感觉一般啊，他没有天打五雷轰吗" ⇒ 给他配上阵，和玩家 t5 同款 ✓）。
     */
    public static void spawnMagicCircleAt(ServerLevel level, Vec3 at, double radius, int life) {
        TNMagicCircleEntity circle = TNOrbEntities.MAGIC_CIRCLE.get().create(level);
        if (circle == null) {
            return;
        }
        circle.configure(radius, life);
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at.x, at.y, at.z);
        int guard = 0;
        while (guard++ < 8 && pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
        }
        circle.moveTo(at.x, pos.getY() + 0.04D, at.z, 0.0F, 0.0F);
        level.addFreshEntity(circle);
        // ★ 铺阵一定留一行日志（作者 2026-09-29："保证 t4/t5 有魔法阵"）——
        //   验收时 grep `TN-C: spawn circle` 就知道这一张到底铺没铺、铺在哪、多大 ✓
        LOGGER.info("TN-C: spawn circle at {} r={} life={} (aim point)", pos, radius, life);
    }

    /**
     * 准星落点（用来把魔法阵铺在"瞄准的地方"✓）。
     *
     * <p>2026-09-29：对着**天空**放的时候（射线 32 格没打到任何方块）以前会返回"天上那个点" ✗
     * —— 于是大雷球停在半空、魔法阵铺在云里 ✗（作者："模型没看见"）。现在退回
     * <b>身前 4 格的地面</b>（只取视线方向的水平分量，高度用自己脚底）✓。
     */
    private static Vec3 aimPoint(ServerPlayer player) {
        net.minecraft.world.phys.HitResult hit = player.pick(STRIKE_RANGE, 0.0F, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            Vec3 flat = player.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
            if (flat.lengthSqr() < 1.0E-4D) {
                return player.position();               // 垂直朝上/朝下：就落在自己脚下 ✓
            }
            return player.position().add(flat.normalize().scale(4.0D));
        }
        return hit.getLocation();
    }

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
        // ★ 铺阵一定留一行日志（见 spawnMagicCircleAt 的说明）✓
        LOGGER.info("TN-C: spawn circle at {} r={} life={} (caster feet)", pos, radius, life);
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
        tickPlayer(player);
    }

    /**
     * 第二个入口：服务端每 tick 遍历所有玩家 ✓
     *
     * <p>★ 2026-09-29 实机（3.2i-9 那次）：<b>{@code PlayerTickEvent} 在我们的 mod 上一个世界整局都没进来过</b>
     * （`TN-C/spellvisuals: player-tick driver alive` 那行只在换世界后才第一次出现 ✗），
     * 而同一段时间 {@code ServerTickEvent} 是好的 ✓。两条都挂上、谁活着谁驱动 ✓ ——
     * {@link #tickPlayer} 内部有"一个玩家一 tick 只跑一次"的闸门（{@link #LAST_TICK_SEEN}），
     * 两个入口都来也不会跑两遍 ✗。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    /** 一个玩家一 tick 只处理一次的闸门：uuid -> 这一 tick 的 gameTime ✓ */
    private static final Map<UUID, Long> LAST_TICK_SEEN = new ConcurrentHashMap<>();

    /** 一个玩家一 tick 的全部持续行为（两个入口共用，幂等 ✓）。 */
    private static void tickPlayer(ServerPlayer player) {
        if (player.level().isClientSide()) {
            return;
        }
        long time = player.level().getGameTime();
        Long seen = LAST_TICK_SEEN.put(player.getUUID(), time);
        if (seen != null && seen == time) {
            return;                             // 这一 tick 已经由另一个入口跑过了 ✓
        }

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
        // 闪电登神：全身电弧 + 环绕电光 + 走过留雷印 + **背后跟着的神** ✓
        if (has(player, TNEffects.LIGHTNING_ASCENSION)) {
            ascensionAura(player, time);
            maintainGodFollower(player, time, false);
        } else {
            // buff 没了就把"上一个雷印位置"清掉，免得下次上 buff 时先补一枚
            LAST_MARK.remove(player.getUUID());
            removeGodFollower(player);
        }
        // 回蓝的 3 秒表演 + 地上的雷印（两者都可能没有，函数内部自己判断）
        rechargeFlourish(player, time);
        sparkMarks(player, time);
        // ★ 登神链的"万雷归体"（雷粒子从四周向角色汇集）✓
        tickGather(player, time);
        // 神级大雷球：粒子贴到球上（引擎自己的 travel_particles 不跟随 ✗）
        followBigBall(player, time);
        // ★★ 魔力恢复的第三个驱动入口（2026-09-29）：
        //    日志实锤 —— 换世界之后 `TN-C/mana` 一行都不打、魔力卡住不动 ✗，
        //    而**这个函数是活着的**（同一段 tick 里的别的日志照打 ✓）。所以把回魔挂到这条
        //    已验证活着的路上来；回魔内部有"每个周期只回一次"的闸门 ⇒ 多入口不会翻倍 ✓。
        MagicStone.tickManaRegen(player);
        // ★★ 光耀链的"能飞 + 有翅膀"（2026-10-01 起，2026-10-02 并进光耀 ✓）
        //    必须挂在这条**已证活着**的路上：原来挂在 TickEvent.PlayerTickEvent 上，
        //    而那个事件在本仓库一个世界整局都不进来 ✗ ⇒ 法术放得出来却飞不起来、也看不见翅膀 ✗✗
        //    （详见 light/TNLightChainMechanics 的类注释 ✓；函数幂等 ✓）
        //    ★ 作者 2026-10-02："把光魔法的飞行链删去，加入到光耀里，释放光耀法术就获得飞行" ✓
        //      ⇒ 判据从"光翼 buff"改成了"光耀 buff" ✓（方法名也跟着改了 ✓）
        com.tnc.tnc.light.TNLightChainMechanics.tickGraceFlight(player);
        // ★★ 光系第三条链（光线）：推进"正在射的光线"（画粒子 + 判伤 ✓）
        //    同样走这条**已证活着**的路 ✓ —— 挂死事件上的教训见 light/TNLightChainMechanics 类注释 ✓
        com.tnc.tnc.light.TNLightBeamMechanics.tick(player);
        // ★★ 自动补学**不许再挂在 tick 上** ✗✗
        //    （作者 2026-09-29 两次："为什么会自动学习啊，删去" → "遗忘了还自动学"）
        //    原来这里每 40 tick 跑一次 autoLearnUnlockedAndSync；而那个方法在
        //    commit 1b31dea4 里被改成了"**引擎里有这个法术就 data.learn()**"✗：
        //      ① 没有任何档位 / 前置 / 亲和力判据 ⇒ 开一局就**全学会** ✗
        //      ② MagicStoneData.learn() 里有一句 explicitlyForgotten.remove(spell) ⇒
        //         **忘掉的会被学回来、遗忘标记还被抹掉** ✗✗（这就是"遗忘了还自动学"）
        //    现在：tick 上不调它 ✓；方法本身也补上了遗忘/档位判据（见方法注释）——
        //    这样万一以后又被接回 tick，也不会全学、更不会撤销遗忘 ✓。
        // 雷场 / 雷暴：窗口内自己劈敌人（视觉＋伤害都在里面）✓
        tickLightningField(player, time);
        tickDivineShot(player, time);
        tickLightningStorm(player, time);
        // ★ "放行了却没放出来"的哑火提示（作者 2026-09-29："释放过的法术怎么无法再释放了"）：
        //   最常见的原因是**读条没满就松手**（引擎那条规则静默生效，界面上毫无反馈 ✗）✓
        ManaGate.checkSilentFizzle(player, 30);
        // ★★ 服务端冷却看门狗 —— **"放完一次就再也放不出来"的真因** ✓✓（2026-09-29 实测）
        //   引擎只在"正在施法"时才 update() 冷却管理器 ⇒ 服务端那次冷却永远停在 100% ✗，
        //   于是 attemptCasting 静默拒绝：日志里 `coolingDown=true progress=1.0` 就是它 ✓。
        tickServerCooldowns(player);
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
     * 每 tick 手动推一次引擎的<b>服务端</b>冷却管理器 ✓
     *
     * <h2>为什么必须自己推（2026-09-29 实机反编译确认）</h2>
     * 引擎的 {@code PlayerEntityMixin.tick_TAIL_SpellEngine} 里，服务端那一支**只在
     * {@code synchronizedSpellCastProcess != null}（正在施法）时才调
     * {@code getCooldownManager().update()}** ✗ —— 也就是"没在读条的时候冷却根本不走字"。
     * 后果：放完一个法术后冷却冻在 100%，下一次 {@code attemptCasting} 被引擎静默拒绝
     * （日志实证：`FIZZLE … coolingDown=true progress=1.0`）⇒ <b>每个法术一辈子只能放一次</b> ✗✗。
     * 客户端那半边同样的问题在 {@code TNSpellClientVisuals} 里补 ✓。
     *
     * <p>包在 try 里：引擎不在 / API 变了就退回原样（少一层保险），不影响别的逻辑 ✓。
     */
    private static void tickServerCooldowns(ServerPlayer player) {
        try {
            ((net.spell_engine.internals.casting.SpellCasterEntity) player)
                    .getCooldownManager().update();
        } catch (Throwable ignored) {
            // 引擎缺失：什么都不做 ✓
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
     *
     * <p>★ 2026-09-30：参数从 {@code ServerPlayer} 放宽到 {@link LivingEntity} ✓ ——
     * 黑暗衍（{@code tnc:yan_dark}）二阶段是**永久登神**的（作者："boss 二阶段是要一直开着
     * 闪电登神的"），但 aura 这套以前只挂在**玩家**的每 tick 逻辑上 ✗ ⇒ boss 身上有 buff、
     * 却一点电光都看不见 ✗。放宽之后由 boss 自己每 tick 调这个方法 ✓（见
     * {@code YanDarkBossEntity.aiStep}），玩家那边调用点一个字都不用改 ✓。
     */
    public static void ascensionAura(net.minecraft.world.entity.LivingEntity entity, long time) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        java.util.UUID id = entity.getUUID();
        double phase = (time % 40) / 40.0D * Math.PI * 2.0D;
        for (int i = 0; i < ASCENSION_ORBIT_COUNT; i++) {
            double a1 = phase * 2.0D + i * (Math.PI * 2.0D / ASCENSION_ORBIT_COUNT);
            double a2 = a1 + 0.5D;
            Vec3 p1 = new Vec3(entity.getX() + Math.cos(a1) * ASCENSION_ORBIT_RADIUS,
                    entity.getY() + 0.9D + Math.sin(a1 * 2.0D) * 0.35D,
                    entity.getZ() + Math.sin(a1) * ASCENSION_ORBIT_RADIUS);
            Vec3 p2 = new Vec3(entity.getX() + Math.cos(a2) * ASCENSION_ORBIT_RADIUS,
                    entity.getY() + 0.9D + Math.sin(a2 * 2.0D) * 0.35D,
                    entity.getZ() + Math.sin(a2) * ASCENSION_ORBIT_RADIUS);
            arcLine(level, p1, p2, 3, 0.05D);
        }
        if (time % ASCENSION_ARC_INTERVAL == 0) {
            // 身上：两点之间蹦一条电弧（比撒点更像"电在身上跳" ✓）
            for (int i = 0; i < ASCENSION_ARC_COUNT; i++) {
                arcLine(level, randomBodyPoint(entity), randomBodyPoint(entity), 5, 0.1D);
            }
        }
        // 雷印：每走一格落一枚
        Vec3 pos = entity.position();
        Vec3 last = LAST_MARK.get(id);
        if (last == null || last.distanceTo(pos) >= ASCENSION_MARK_STEP) {
            LAST_MARK.put(id, pos);
            List<SparkMark> list = MARKS.computeIfAbsent(id, key -> new ArrayList<>());
            list.add(new SparkMark(pos, time + ASCENSION_MARK_LIFE));
            while (list.size() > ASCENSION_MARK_MAX) {
                list.remove(0);
            }
        }
    }

    // ------------------------------------------------------------------
    //  ★ 跟随神（登神期间背后那尊）
    // ------------------------------------------------------------------

    /**
     * 登神期间，维持玩家背后的 {@link #GOD_FOLLOWER_COUNT} 尊跟随神 ✓
     * （作者 2026-09-29："我已登神，我希望玩家背后会出现 god 的模型跟随"）
     *
     * <p>做法和"环绕雷球"一致（同一条既成做法 ✓）：**实体自己负责跟随**，
     * 机制层只负责"缺了就补一尊"（每 10 tick 查一次，不用每 tick 遍历世界 ✗）。
     * 位置/朝向/粒子/消失条件全在 {@link TNLightningStrikeEntity#tick()} 里 ✓。
     *
     * <p>为什么不每 tick 重建：重建会**每 tick 重发一次生成包** ⇒ 客户端一直在"新实体出现"，
     * 模型会不停闪 ✗。所以只在**一尊都没有**的时候补 ✓。
     *
     * <p>★ 2026-09-30：参数放宽到 {@link net.minecraft.world.entity.LivingEntity} ✓ ——
     * 作者要的"登神外观"就是这个跟随神（"我想要的登神外观是那个 god 的模型在他背后跟随"），
     * 而黑暗衍二阶段是永久登神 ⇒ 它背后也要有一尊 ✓（{@code dark = true} 时用 {@code god_dark} ✓）。
     */
    public static void maintainGodFollower(net.minecraft.world.entity.LivingEntity owner, long time,
                                           boolean dark) {
        maintainGodFollower(owner, time, dark, GOD_FOLLOWER_SCALE);
    }

    /** 带尺寸的版本（boss 想用更大的跟随神时传 scale ✓）。 */
    public static void maintainGodFollower(net.minecraft.world.entity.LivingEntity owner, long time,
                                           boolean dark, double scale) {
        if (GOD_FOLLOWER_COUNT <= 0 || time % 10 != 0) {
            return;
        }
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        int alive = 0;
        for (TNLightningStrikeEntity god : level.getEntitiesOfClass(TNLightningStrikeEntity.class,
                owner.getBoundingBox().inflate(64.0D))) {
            if (god.isFollowerGod() && owner.getUUID().equals(god.followOwner())) {
                alive++;
            }
        }
        if (alive >= GOD_FOLLOWER_COUNT) {
            return;
        }
        double base = Math.toRadians(owner.getYRot());
        for (int i = alive; i < GOD_FOLLOWER_COUNT; i++) {
            // 多尊时按角度均分站在背后（单尊时 i=0 ⇒ 正后方 ✓）
            double spread = Math.toRadians((i - (GOD_FOLLOWER_COUNT - 1) / 2.0D) * GOD_FOLLOWER_ARC_DEGREES);
            double dir = base + spread;
            TNLightningStrikeEntity god = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
            if (god == null) {
                return;
            }
            god.asFollowerGod(owner.getUUID(), GOD_FOLLOWER_BACK, GOD_FOLLOWER_UP);
            if (dark) {
                god.asDark();                    // 黑暗衍背后那尊用 god_dark ✓
            }
            god.configure(scale, TNLightningStrikeEntity.FOLLOWER_MAX_AGE, owner.getY(), 0.0D);
            double gx = owner.getX() + Math.sin(dir) * GOD_FOLLOWER_BACK;
            double gz = owner.getZ() - Math.cos(dir) * GOD_FOLLOWER_BACK;
            // 朝向：和主人一致（原版约定 facing = (−sin yaw, cos yaw) ⇒ 直接用主人的 yaw ✓）
            god.moveTo(gx, owner.getY() + GOD_FOLLOWER_UP, gz, owner.getYRot(), 0.0F);
            level.addFreshEntity(god);
        }
    }

    /** 登神 buff 没了：把主人背后那几尊跟随神清掉 ✓（实体自己也会查 buff，这里是双保险 ✓）。 */
    public static void removeGodFollower(net.minecraft.world.entity.LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        for (TNLightningStrikeEntity god : level.getEntitiesOfClass(TNLightningStrikeEntity.class,
                owner.getBoundingBox().inflate(80.0D))) {
            if (god.isFollowerGod() && owner.getUUID().equals(god.followOwner())) {
                god.discard();
            }
        }
    }

    // ------------------------------------------------------------------
    //  ★ 万雷归体（登神链 t1..t5）
    // ------------------------------------------------------------------

    /**
     * ★ 单调时钟（单位 tick，50 ms 一格）—— **只给"跨世界也不能算错"的窗口用** ✓
     *
     * <p>为什么：3.2i-9 那个真因（回魔换世界永久停摆）就是"static 的 last 时间 ＋
     * 每存档各自的 {@code level.getGameTime()}"算出的**负数时间差** ✗。本类里那些旧窗口
     * （{@code FIELD_UNTIL} / {@code STORM_UNTIL} / {@code DIVINE_UNTIL} …）还在用 gameTime ✗，
     * 但它们都有 buff / 40 tick 窗口兜底 ✓；**新加的"万雷归体"窗口用这个** ✓。
     */
    private static long wallTicks() {
        return System.currentTimeMillis() / 50L;
    }

    /**
     * 登神链释放时开窗：接下来 {@link #GATHER_TICKS}[tier-1] tick 内"万雷归体" ✓。
     *
     * <p>判据用**链**而不是法术 id：{@code entry.chain() == Chain.SPEED} ⇒ 这条链
     * t1..t5 以后再加档也自动有特效 ✓，档位直接决定强度 ✓。
     */
    private static void startGather(ServerPlayer player, int tier) {
        int idx = Math.max(0, Math.min(GATHER_TICKS.length - 1, tier - 1));
        long now = wallTicks();
        GATHER.put(player.getUUID(), new Gather(now, now + GATHER_TICKS[idx], tier));
        LOGGER.info("TN-C: gather start tier={} ticks={} perTick={} radius={}",
                tier, GATHER_TICKS[idx], GATHER_PER_TICK[idx], GATHER_RADIUS[idx]);
    }

    /**
     * 每 tick：在玩家周围球壳上随机取点，给粒子一个**指向玩家**的初速度 ⇒ 雷电被"吸"进身体 ✓。
     *
     * <p>窗口结束后若玩家仍在**登神**状态，则按 {@link #ASCENDED_GATHER_MUL} 的倍率继续
     * （12 秒的登神期里一直有雷在往身上灌 ✓）；两者都没有就直接 return（零开销 ✓）。
     */
    private static void tickGather(ServerPlayer player, long time) {
        long now = wallTicks();                 // ★ 和 startGather 同一把尺子（单调时钟）✓
        Gather window = GATHER.get(player.getUUID());
        if (window != null && now > window.until()) {
            GATHER.remove(player.getUUID());
            closingBurst(player, window.tier());
            window = null;
        }
        boolean ascended = has(player, TNEffects.LIGHTNING_ASCENSION);
        if (window == null && !ascended) {
            return;
        }
        int tier = window != null ? window.tier() : GATHER_TICKS.length;
        double mul = window != null ? 1.0D : ASCENDED_GATHER_MUL;
        int idx = Math.max(0, Math.min(GATHER_TICKS.length - 1, tier - 1));
        ServerLevel level = player.serverLevel();
        // 窗口内越到后面，粒子从越近的地方来（"收束"）✓
        double closeIn = 1.0D;
        if (window != null) {
            double p = (now - window.start()) / (double) Math.max(1L, window.until() - window.start());
            closeIn = 1.0D - GATHER_CLOSE_IN * Math.min(1.0D, Math.max(0.0D, p));
        }
        int count = (int) Math.max(1L, Math.round(GATHER_PER_TICK[idx] * mul));
        double radius = GATHER_RADIUS[idx] * closeIn;
        double cx = player.getX();
        double cy = player.getY() + player.getBbHeight() * 0.55D;
        double cz = player.getZ();
        for (int i = 0; i < count; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double b = (level.random.nextDouble() - 0.5D) * Math.PI;
            double rr = radius * (0.7D + level.random.nextDouble() * 0.5D);
            double px = cx + Math.cos(a) * Math.cos(b) * rr;
            double py = cy + Math.sin(b) * rr * 0.8D;
            double pz = cz + Math.sin(a) * Math.cos(b) * rr;
            // count=0 ⇒ 原版把 xDist/yDist/zDist 当**定向速度**（不是随机散布）✓
            double vx = (cx - px) / GATHER_TRAVEL_TICKS;
            double vy = (cy - py) / GATHER_TRAVEL_TICKS;
            double vz = (cz - pz) / GATHER_TRAVEL_TICKS;
            level.sendParticles(arc(), px, py, pz, 0, vx, vy, vz, 0.0D);
            if ((i & 1) == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0, vx, vy, vz, 0.0D);
            }
        }
        // 身上那点"被灌入"的亮光（每 2 tick 一次，够亮又不糊视野 ✓）
        if (time % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 4, 0.35D, 0.6D, 0.35D, 0.06D);
        }
    }

    /** 窗口收尾：一圈雷电同时向内收 ＋ 一记白闪（"灌满了"的那一下 ✓）。 */
    private static void closingBurst(ServerPlayer player, int tier) {
        int idx = Math.max(0, Math.min(GATHER_TICKS.length - 1, tier - 1));
        ServerLevel level = player.serverLevel();
        double cx = player.getX();
        double cy = player.getY() + player.getBbHeight() * 0.55D;
        double cz = player.getZ();
        double radius = Math.max(2.0D, GATHER_RADIUS[idx] * 0.45D);
        int points = 16 + tier * 4;
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2.0D / points);
            double px = cx + Math.cos(a) * radius;
            double pz = cz + Math.sin(a) * radius;
            double py = cy + (level.random.nextDouble() - 0.5D) * 1.2D;
            level.sendParticles(arc(), px, py, pz, 0,
                    (cx - px) / 4.0D, (cy - py) / 4.0D, (cz - pz) / 4.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, cx, cy, cz, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.6F);
    }

    /** 身上的随机一点（画电弧用）✓ —— 玩家和 boss 共用（{@link #ascensionAura}）✓。 */
    private static Vec3 randomBodyPoint(net.minecraft.world.entity.LivingEntity entity) {
        double a = entity.getRandom().nextDouble() * Math.PI * 2.0D;
        double r = 0.3D + entity.getRandom().nextDouble() * 0.3D;
        double y = entity.getY() + 0.25D + entity.getRandom().nextDouble() * 1.45D;
        return new Vec3(entity.getX() + Math.cos(a) * r, y, entity.getZ() + Math.sin(a) * r);
    }

    /**
     * 地上的雷印：在原地噼啪一小会儿，然后自己消失 ✓。
     *
     * <p>★ 2026-09-30：和 {@link #ascensionAura} 一样放宽到 {@link LivingEntity} ✓
     * （boss 二阶段也要留雷印 ✓）。
     */
    public static void sparkMarks(net.minecraft.world.entity.LivingEntity entity, long time) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        List<SparkMark> list = MARKS.get(entity.getUUID());
        if (list == null || list.isEmpty()) {
            return;
        }
        list.removeIf(mark -> mark.expireAt() < time);
        if (list.isEmpty()) {
            MARKS.remove(entity.getUUID());
            return;
        }
        if (time % 3 != 0) {
            return;
        }
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
                ballShell(player.serverLevel(), projectile.position(), BIG_BALL_RING, time);
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

    /**
     * 在 center 处铺一层<b>球壳</b>粒子 ✓（大雷球专用，2026-09-22 第三版）。
     *
     * <h2>为什么不是 {@link #ring}</h2>
     * 作者原话："这个超级无敌大雷球还是不行啊……粒子特效也一般"。上一版只在球的赤道上画了
     * <b>一圈</b>粒子 ✗ —— 28 格的球配一圈线，看起来就是个细光环，不像一颗电球 ✗。
     * 这一版按<b>纬度</b>切 5 层水平环（越靠近赤道半径越大、点数越多 ✓），
     * 叠出来是一整层球壳 ✓；每层再用 {@link #ring} 自带的旋转经线，球就"转"起来了 ✓。
     *
     * <p>点数按半径算（{@code r * 4}）：半径 14 格时约 350 颗/tick，配 JSON 里
     * 的 travel_particles，够亮但不到卡的程度。
     */
    public static void ballShell(ServerLevel level, Vec3 center, double radius, long time) {
        for (int k = 0; k < 5; k++) {
            // -90°..+90° 取 5 个纬度（不含两极，两极半径趋 0、没意义 ✗）
            double phi = ((k + 0.5D) / 5.0D - 0.5D) * Math.PI;
            double r = radius * Math.cos(phi);
            if (r < 0.8D) {
                continue;
            }
            int points = (int) Math.max(12.0D, Math.round(r * 4.0D));
            ring(level, center.add(0.0D, radius * Math.sin(phi), 0.0D), r, points, time + k * 23L);
        }
    }

    /**
     * 把"档位已解锁"的链上法术自动补进魔法石 ✓ —— 作者改链/加档/换 id 之后不用手敲 /tnc learn。
     *
     * <p>规则（2026-09-29）：只补 {@code tier <= maxTierFor(元素)} 的条目，且**必须同链上一档已学会**
     * （保住 t1→t2→…→t5 的顺序 ✓，作者原话："不需要先学 t1t2 就可以学 t4t5" ✗ 就是这里漏了前置）。
     * 学会之后**立刻 {@link com.tnc.tnc.network.MagicStoneNetwork.MagicStoneNetwork#syncTo} 同步给客户端** ✗ —— 不同步的话客户端热键栏里
     * 根本没有这个法术，按下去等于没按（这正是"t5 放不出来、连 gate 都没被调用"的原因 ✓）。
     */
    private static void autoLearnUnlockedAndSync(ServerPlayer player) {
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null || !data.isInitialized()) {
            return;
        }
        boolean learned = false;
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            // ★★ 2026-09-29 作者："遗忘了还自动学" —— 这一版把判据补齐了 ✗✗
            //    上一版（commit 1b31dea4）写的是"引擎里有这个法术就 data.learn()"✗：
            //      ① 没有档位/前置/亲和力 ⇒ 一开档就**全学**（作者："为什么有自动全学了"）
            //      ② MagicStoneData.learn() 会 explicitlyForgotten.remove(spell) ⇒
            //         **把玩家明确忘掉的又学回来、连遗忘标记一起抹掉** ✗✗
            //    现在四条判据，缺一不可（和 MagicStoneLearning.check 的规则一致 ✓）：
            //      独立魔法（element == null，乱魔）跳过 —— maxTierFor(null) 会 NPE 崩服 ✗
            if (entry.independent() || entry.element() == null) {
                continue;
            }
            //      玩家**明确点过遗忘**的一律不许补 —— 遗忘是玩家的决定，任何自动路径都不能撤销 ✓
            if (data.isExplicitlyForgotten(entry.id())) {
                continue;
            }
            if (data.hasLearned(entry.id())) {
                continue;
            }
            //      亲和力允许的档位（等级不够不给）
            if (entry.tier() > data.maxTierFor(entry.element())) {
                continue;
            }
            //      这条链**已经走到过的档位**（= 修复，不是白送）——
            //      不花点数、也不越过玩家还没解锁的档，所以它只是"把记录补回来" ✓
            if (entry.tier() > data.getProgress(entry.element(), entry.chain())) {
                continue;
            }
            learned |= data.learn(entry.id());
        }
        if (learned) {
            com.tnc.tnc.network.MagicStoneNetwork.syncTo(player);
            com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player,SpellCatalog.effectiveIds(data));
            org.apache.logging.log4j.LogManager.getLogger("TN-C/learn")
                    .info("TN-C: auto-learn synced (chain spells added to magic stone)");
        }
    }
    private static boolean has(ServerPlayer player, net.minecraftforge.registries.RegistryObject<net.minecraft.world.effect.MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /**
     * 敌人判据（包内共享）✓
     *
     * <p>★ 2026-09-30：owner 从 {@code Player} 放宽到 {@link LivingEntity} ✓ ——
     * 黑暗衍（boss）的落雷/大雷球也要真的打人 ✓（原来只认玩家 ✗ ⇒ boss 的法术一点伤害都没有 ✗）。
     * 非玩家 owner **只打玩家** ✓ —— boss 之间 / 召唤物之间互相误伤不是我们要的观感 ✗。
     */
    public static boolean isEnemy(LivingEntity owner, LivingEntity target) {
        if (target == owner || !target.isAlive()) {
            return false;
        }
        if (!(owner instanceof Player player)) {
            return target instanceof Player;
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
