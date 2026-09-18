package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

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
