package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.entity.SpellProjectile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ★ <b>以伤换伤链的"索敌"</b>：**玩家得瞄住敌人**才锁定，锁定的那个会**高亮**，
 * 血爪只追这只被锁定的敌人 ✓
 *
 * <p>作者 2026-10-10：「**可以有血爪，但是我想要那个就是我瞄敌人敌人身上会高亮的那种索敌释放，
 * 不是范围我直接开技能然后他自己去找敌人，是要玩家瞄住敌人的那种，有点像雷击的那种瞄**」。
 *
 * <h2>为什么不能靠"取消施法"来做"必须瞄中"</h2>
 * 引擎的 {@code CombatEvents.SPELL_CAST} 是**只能通知、不能取消**的
 * （{@code Event<T>} 只有 {@code register/invoke}，javap 确认 ✗）。所以做法是：
 * <b>施法那一刻用准星射线找敌人</b>（就是雷击 t3 用的 {@code player.pick} ✓），
 * 找到才**锁定**它；找不到就**不锁定** ⇒ 血爪没有追踪目标 ⇒
 * 效果上就是"没瞄准就抓不到人" ✓，而不是"自己去找最近的敌人" ✗。
 *
 * <h2>锁定的落地方式</h2>
 * {@code SpellProjectile} 自带 {@code setFollowedTarget(Entity)} / {@code getFollowedTarget()}
 * （javap 确认 ✓）—— 把"我瞄的那个人"塞给它，引擎的追踪就**只认这一只** ✓。
 * 血爪是施法后下一 tick 才生成的，所以这边留一个 {@link #LOCK_TICKS} 的短窗口等它出现 ✓。
 *
 * <h2>高亮</h2>
 * 用原版的**发光描边**（{@code setGlowingTag}）：它是同步字段 ⇒
 * **服务端一设，所有客户端都看得到** ✓，而且不需要自己写渲染 ✓。
 * 锁定期间 + 瞄准期间都会亮；松开准星/过期就灭 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNDarkAimMechanics {

    /** 只认这一条链（与生成器里写的 group 一致 ✓）。 */
    private static final String SACRIFICE_GROUP = "dark_sacrifice";

    /** 准星射线长度（和雷击的 STRIKE_RANGE 一个量级 ✓）。 */
    private static final double AIM_RANGE = 32.0D;

    /** 还在瞄准时，高亮每 tick 续这么久 ⇒ 一直瞄就一直亮，松手约半秒后灭 ✓。 */
    private static final int HIGHLIGHT_TICKS = 10;

    /** 施法之后等血爪生成的窗口（血爪下一 tick 就出现，14 很宽裕）✓。 */
    private static final int LOCK_TICKS = 14;

    /** 玩家 → 锁定的目标（entityId），带过期时间 ✓。 */
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    /** 被高亮的实体（entityId）→ 高亮到哪个 gameTime（到期就熄灭）✓。 */
    private static final Map<Integer, Long> GLOWING = new HashMap<>();

    /**
     * ★ 最近一次锁定的目标 —— **给 `tnc:drain`（抽血结算实体）用** ✓。
     *
     * <p>为什么要有它：抽血实体是自己去"命中点附近找最近的活体"的（引擎只把**施法者**
     * 交给它，不给目标 ✗）。一旦它没找到人，就**立刻 discard ⇒ 一点血都不撒** ✗
     * （作者 2026-10-10："**敌人自身也要爆一地的血粒子怎么没加**"）。
     * 现在把"玩家瞄的那一只"直接告诉它 ⇒ 既保证抽对人也保证撒到血 ✓。
     *
     * <p>存活 {@link #RECENT_TICKS} 要**盖住整个抽取过程**（{@code CHANNEL_TICKS = 60}）✓。
     */
    private static final Map<UUID, Lock> RECENT = new HashMap<>();

    private static final int RECENT_TICKS = 80;

    /**
     * 这个玩家最近锁定/瞄准的目标 entityId；没有或过期就 -1 ✓。
     *
     * @param now 调用方自己的 {@code level.getGameTime()}（本类不持有 level ✓）
     */
    public static int recentTargetId(UUID playerId, long now) {
        Lock lock = RECENT.get(playerId);
        if (lock == null) {
            return -1;
        }
        if (now > lock.until()) {
            RECENT.remove(playerId);
            return -1;
        }
        return lock.targetId();
    }

    private record Lock(int targetId, long until) {
    }

    static {
        // 挂引擎自己的事件上（不是 Forge 事件 ✗ —— 见 DarkFogMechanics 的注释）
        try {
            CombatEvents.SPELL_CAST.register(TNDarkAimMechanics::onSpellCast);
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("TN-C/aim")
                    .warn("TN-C: dark aim listener registration failed ({})", t.toString());
        }
    }

    private TNDarkAimMechanics() {
    }

    /** 施法那一刻：瞄到敌人才锁定（就是"雷击的瞄"那套 {@code player.pick} ✓）。 */
    private static void onSpellCast(CombatEvents.SpellCast.Args args) {
        if (args == null || !(args.caster() instanceof ServerPlayer player)) {
            return;
        }
        SpellInfo info = args.spell();
        Spell spell = info == null ? null : info.spell();
        if (spell == null || !SACRIFICE_GROUP.equals(spell.group)) {
            return;
        }
        LivingEntity aimed = aimedEnemy(player);
        if (aimed == null) {
            return;                     // ★ 没瞄到人 ⇒ 不锁定 ✓（不是"自己去找敌人" ✗）
        }
        ServerLevel level = player.serverLevel();
        LOCKS.put(player.getUUID(), new Lock(aimed.getId(), level.getGameTime() + LOCK_TICKS));
        RECENT.put(player.getUUID(), new Lock(aimed.getId(), level.getGameTime() + RECENT_TICKS));
        highlight(level, aimed);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        expireGlow(level, now);
        applyLock(level, player, now);

        // ★ 瞄准期间就亮（拿着法杖时）：作者要的就是"我瞄敌人敌人身上会高亮" ✓
        if (isHoldingWand(player)) {
            LivingEntity aimed = aimedEnemy(player);
            if (aimed != null) {
                highlight(level, aimed);
            }
        }
    }

    /** 准星指到的那个敌人；指到方块/空气就返回 null ✓。 */
    private static LivingEntity aimedEnemy(ServerPlayer player) {
        HitResult hit = player.pick(AIM_RANGE, 0.0F, false);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player && living.isAlive()) {
            return living;
        }
        return null;
    }

    /**
     * 把刚生成的血爪绑到"我瞄的那个人"身上 ✓。
     *
     * <p>★★ 2026-10-10 修正（作者："**以伤换伤没有找谁啊，黑夜之手有**"）：
     * 原来这里**只认 {@link #LOCKS}** —— 而 LOCKS 只在 {@code SPELL_CAST} 回调里写入，
     * 如果那条事件在服务端没触发（或时机不对），就**永远锁不上** ✗，
     * 表现就是"血爪不找人"✗。
     *
     * <p>现在改成**双保险**：血爪刚生成那一 tick，如果手上没有锁，
     * **自己再射线一次**（玩家这时准星基本还指着刚才瞄的敌人 ✓）⇒
     * 不再依赖施法事件 ✓。没瞄到人就**不锁定** ✓（不是"自己去找最近的敌人" ✗）。
     */
    private static void applyLock(ServerLevel level, ServerPlayer player, long now) {
        Lock lock = LOCKS.get(player.getUUID());
        LivingEntity locked = null;
        if (lock != null) {
            if (now > lock.until()) {
                LOCKS.remove(player.getUUID());
            } else {
                Entity target = level.getEntity(lock.targetId());
                if (target instanceof LivingEntity living && living.isAlive()) {
                    locked = living;
                } else {
                    LOCKS.remove(player.getUUID());
                }
            }
        }
        for (SpellProjectile bolt : level.getEntitiesOfClass(SpellProjectile.class,
                player.getBoundingBox().inflate(24.0D))) {
            Spell spell = bolt.getSpell();
            if (spell == null || !SACRIFICE_GROUP.equals(spell.group)) {
                continue;
            }
            if (bolt.getOwner() != player || bolt.getFollowedTarget() != null) {
                continue;
            }
            // ★ 兜底：施法事件没给我们锁 ⇒ 就现在瞄一次 ✓
            LivingEntity target = locked != null ? locked : aimedEnemy(player);
            if (target == null) {
                return;                 // ★ 没瞄到人 ⇒ 不锁定 ✓（"不是范围自动找敌人" ✗）
            }
            // ★ 只追这一只（不是引擎默认的"最近的敌人"）✓
            bolt.setFollowedTarget(target);
            highlight(level, target);
            // ★ 同时记进 RECENT：抽血实体据此确定"该抽谁、该在谁身上爆血" ✓
            RECENT.put(player.getUUID(), new Lock(target.getId(), now + RECENT_TICKS));
            LOCKS.remove(player.getUUID());
            return;
        }
    }

    private static void highlight(ServerLevel level, LivingEntity entity) {
        entity.setGlowingTag(true);
        GLOWING.put(entity.getId(), level.getGameTime() + HIGHLIGHT_TICKS);
    }

    /** 到期就熄灭（否则满地图的怪会一直亮 ✗）。 */
    private static void expireGlow(ServerLevel level, long now) {
        if (GLOWING.isEmpty()) {
            return;
        }
        var it = GLOWING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now <= entry.getValue()) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null) {
                entity.setGlowingTag(false);
            }
            it.remove();
        }
    }

    private static boolean isHoldingWand(ServerPlayer player) {
        return isWand(player.getMainHandItem()) || isWand(player.getOffhandItem());
    }

    /** 与 {@code SpellEngineBridge} 同一套判定（本模组法杖 或 元素法杖）✓。 */
    private static boolean isWand(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!TNMod.WAND.isPresent()) {
            return false;
        }
        return stack.is(TNMod.WAND.get())
                || stack.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand;
    }
}
