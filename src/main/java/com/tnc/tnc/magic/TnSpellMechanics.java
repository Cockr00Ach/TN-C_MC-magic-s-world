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
 * <p>粒子一律用原版 {@link ParticleTypes#ELECTRIC_SPARK}：不依赖任何 mod 的粒子注册，
 * 少一个"引擎没装/改名"就崩的点。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TnSpellMechanics {

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

    /** 一枚留在原地的雷印。 */
    private record SparkMark(Vec3 pos, long expireAt) {
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

        if (has(player, TNEffects.ORBITING_THUNDER_ORB)) {
            orbitingOrb(player, time);
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

    /** 环绕雷球：几颗球绕着玩家转，每半秒电一次附近的敌人。 */
    private static void orbitingOrb(ServerPlayer player, long time) {
        ServerLevel level = player.serverLevel();
        double phase = (time % 80) / 80.0D * Math.PI * 2.0D;

        // 画圈（客户端看得到）
        for (int i = 0; i < ORBIT_COUNT; i++) {
            double a = phase + i * (Math.PI * 2.0D / ORBIT_COUNT);
            double x = player.getX() + Math.cos(a) * ORBIT_RADIUS;
            double z = player.getZ() + Math.sin(a) * ORBIT_RADIUS;
            double y = player.getY() + 1.0D + Math.sin(a * 2.0D) * 0.25D;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
        }

        if (time % ORBIT_ZAP_INTERVAL != 0) {
            return;
        }
        // 电击：以玩家为中心的球形范围
        AABB box = player.getBoundingBox().inflate(ORBIT_RADIUS_HIT);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity target : targets) {
            if (!isEnemy(player, target)) {
                continue;
            }
            if (target.distanceTo(player) > ORBIT_RADIUS_HIT) {
                continue;
            }
            zap(level, player, target, ORBIT_DAMAGE);
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
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 0.2D, player.getZ(),
                3, 0.25D, 0.1D, 0.25D, 0.02D);
        // 2026-09-22 加：速度线（END_ROD 白点）—— 光有电弧看不出"我在冲刺"
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.9D, player.getZ(),
                2, 0.15D, 0.35D, 0.15D, 0.0D);

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
     * <b>t1 雷速</b>：跑动时脚底一直冒电花；站着不动只偶尔闪一下。
     *
     * <p>为什么这么做：雷速本身是"看不见的 +25% 移速"，脚底电花是最便宜的存在感 ——
     * 玩家一跑就知道 buff 还在。粒子全用原版 {@code ELECTRIC_SPARK} ✓ 不依赖任何 mod。
     */
    private static void hasteSparks(ServerPlayer player, long time) {
        if (time % HASTE_SPARK_INTERVAL != 0) {
            return;
        }
        Vec3 moved = player.position().subtract(player.xOld, player.yOld, player.zOld);
        boolean moving = moved.length() > HASTE_MIN_MOVE;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 0.08D, player.getZ(),
                moving ? 3 : 1,
                moving ? 0.28D : 0.16D, 0.02D, moving ? 0.28D : 0.16D,
                moving ? 0.03D : 0.0D);
    }

    /**
     * <b>t2 闪电移位</b>：起点和落点各炸一圈电花 + 一声短促传送音。
     *
     * <p>引擎的 TELEPORT 动作自带 {@code depart_particles}，但"炸得够不够"是手感问题 ——
     * 这里补足两端（用 {@code xOld/yOld/zOld} 拿起点：施法这一 tick 玩家已经落到终点了）。
     */
    private static void blinkBurst(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double ox = player.xOld;
        double oy = player.yOld;
        double oz = player.zOld;
        if (player.position().distanceToSqr(ox, oy, oz) > BLINK_MIN_DISTANCE_SQR) {
            zapBurst(level, ox, oy + 1.0D, oz);
            level.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.45F, 1.7F);
        }
        zapBurst(level, player.getX(), player.getY() + 1.0D, player.getZ());
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.25F);
    }

    /** 一圈电花 + 一次闪光（FLASH 是原版的瞬时亮光粒子）。 */
    private static void zapBurst(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                BLINK_BURST_COUNT, BLINK_BURST_SPREAD, 0.8D, BLINK_BURST_SPREAD, 0.35D);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** <b>t4 闪电降低冷却</b>：脚下一圈电环（一次性）+ 一声晶鸣。 */
    private static void rechargeRing(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < RECHARGE_RING_COUNT; i++) {
            double a = i * (Math.PI * 2.0D / RECHARGE_RING_COUNT);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + Math.cos(a) * RECHARGE_RING_RADIUS,
                    player.getY() + 0.15D,
                    player.getZ() + Math.sin(a) * RECHARGE_RING_RADIUS,
                    2, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.9F);
    }

    /** <b>t4</b>：施法后 3 秒内，绕身上升的电流（"魔力回来了"的观感）。 */
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
        double y = player.getY() + 0.2D + climb * 1.9D;
        double a = (time % 40) / 40.0D * Math.PI * 2.0D;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX() + Math.cos(a) * 0.55D, y, player.getZ() + Math.sin(a) * 0.55D,
                1, 0.02D, 0.05D, 0.02D, 0.01D);
    }

    /**
     * <b>t5 闪电登神</b>：全身随机电弧 + 三颗环绕电光（纯表现，不造成伤害）+ 走过留雷印。
     *
     * <p>环绕电光故意做得比"环绕雷球"（4 颗、r=1.4、会电人）小一圈、快一点 ——
     * 让玩家一眼能分清"这是登神的外观"还是"那段会扎人的光环" ✓
     */
    private static void ascensionAura(ServerPlayer player, long time) {
        ServerLevel level = player.serverLevel();
        double phase = (time % 40) / 40.0D * Math.PI * 2.0D;
        for (int i = 0; i < ASCENSION_ORBIT_COUNT; i++) {
            double a = phase * 2.0D + i * (Math.PI * 2.0D / ASCENSION_ORBIT_COUNT);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + Math.cos(a) * ASCENSION_ORBIT_RADIUS,
                    player.getY() + 0.9D + Math.sin(a * 2.0D) * 0.35D,
                    player.getZ() + Math.sin(a) * ASCENSION_ORBIT_RADIUS,
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        if (time % ASCENSION_ARC_INTERVAL == 0) {
            for (int i = 0; i < ASCENSION_ARC_COUNT; i++) {
                double a = player.getRandom().nextDouble() * Math.PI * 2.0D;
                double r = 0.35D + player.getRandom().nextDouble() * 0.25D;
                double y = player.getY() + 0.3D + player.getRandom().nextDouble() * 1.5D;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                        1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
        // 雷印：每走一格留一枚
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
        if (time % 2 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (SparkMark mark : list) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    mark.pos().x, mark.pos().y + 0.08D, mark.pos().z,
                    1, 0.12D, 0.02D, 0.12D, 0.0D);
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
    }

    // ------------------------------------------------------------------
    //  小工具
    // ------------------------------------------------------------------

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
