package com.tnc.tnc.magic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 魔力硬拦截的判定（"没魔力就放不出来"）。
 *
 * <p>纯逻辑放在这里（不引用任何 SpellEngine 类），Mixin 只是薄薄一层适配器：
 * `SpellHelper.attemptCasting` 的 HEAD 处调 {@link #shouldBlock}，
 * 返回 true 就把这次施法判定为失败。
 *
 * <p>只在**服务端**拦：客户端可以照常预测（动画/引导条），服务端说不行就不行。
 */
public final class ManaGate {

    /**
     * Mixin 到底有没有注入成功。
     *
     * <p>{@code SpellHelperManaGateMixin} 的静态初始化块会把它置为 true ——
     * 而 Mixin 只有在真正加载那个类（= 注入成功）时才会跑静态块。
     * 所以这个标志是<b>在游戏里判断"硬拦截是否生效"的唯一可靠办法</b>：
     * 用 `/tnc engine` 看。
     */
    private static volatile boolean mixinApplied;

    /** 拦截计数器：光看"注入成功"不够，看它真的拦过才算数。 */
    private static final java.util.concurrent.atomic.AtomicInteger blockedCount = new java.util.concurrent.atomic.AtomicInteger();
    /** 其中因为"还没在魔法石里解锁"被拦的次数。 */
    private static final java.util.concurrent.atomic.AtomicInteger unlearnedCount = new java.util.concurrent.atomic.AtomicInteger();
    /** 其中因为"手上没拿法杖"被拦的次数。 */
    private static final java.util.concurrent.atomic.AtomicInteger noWandCount = new java.util.concurrent.atomic.AtomicInteger();
    /** 判定被调用过多少次（= Mixin 真的执行到了我们的代码）。 */
    private static final java.util.concurrent.atomic.AtomicInteger gateChecks = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile String lastBlockedSpell = "（还没拦过）";

    /** 一次施法判定的结果。 */
    public enum Decision {
        /** 放行。 */
        ALLOW,
        /** 这个法术还没在魔法石里解锁。 */
        NOT_LEARNED,
        /** 魔力不够。 */
        NOT_ENOUGH_MANA,
        /** 手上没有法杖。 */
        NO_WAND
    }

    private ManaGate() {
    }

    // ------------------------------------------------------------------
    //  "放行了、但引擎没让放"的哑火检测（作者 2026-09-29："释放过的法术怎么无法再释放了"）
    //  ★ 2026-09-29 恢复：这一整块在 commit 1b31dea4 的回滚里丢了（作者："全部恢复"）
    // ------------------------------------------------------------------

    /**
     * 闸门刚**放行**的那次施法：uuid -> (法术, 游戏刻) ✓
     *
     * <h2>为什么需要它</h2>
     * 引擎的 `attemptCasting` 过了之后，`performSpell` **还会自己再判一次**，而它有一条
     * 很容易踩的规则（反编译确认）：<b>非引导型法术的 RELEASE 必须带"蓄力进度 ≥ 1.0"</b>
     * —— 也就是"读条没满就松手"时，引擎会**静默地什么都不做** ✗（不报错、不扣蓝、不发事件）。
     * 作者实测（日志 19:00）：唯一成功的一次是 **按住 2.26 秒**，之后 25 次全是 **0.1~0.2 秒的点按**
     * ⇒ 一次都没放出来，而界面上毫无提示 ✗。
     *
     * <p>所以这里记一笔"我放行了"，{@link TnSpellMechanics} 的每 tick 逻辑负责看它有没有
     * 在 ~1.5 秒内变成真的施法（{@code SPELL_CAST} 会给 {@link #noteCastHappened}）：
     * 没有就提示玩家"读条没满/被取消"，而不是让他对着空气按半天 ✗。
     */
    private static final java.util.Map<java.util.UUID, Pending> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 一次"已放行、尚未确认"的施法。 */
    public record Pending(ResourceLocation spell, long at) {
    }

    static void noteAllowed(ServerPlayer player, ResourceLocation spell) {
        PENDING.put(player.getUUID(), new Pending(spell, player.level().getGameTime()));
    }

    /** 真的放出去了（引擎发了 SPELL_CAST）⇒ 这笔就不算哑火 ✓ */
    public static void noteCastHappened(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /**
     * 每 tick 检查：放行了却没放出来 ⇒ 给玩家一句话 ✓（每个玩家最多提示一次，不刷屏 ✓）。
     *
     * @param patienceTicks 多久没动静算哑火（30 tick = 1.5 秒足够读条 ✓）
     */
    public static void checkSilentFizzle(ServerPlayer player, int patienceTicks) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - pending.at() < patienceTicks) {
            return;
        }
        PENDING.remove(player.getUUID());
        // 措辞中性：哑火有可能是**引擎在客户端把自己的读条取消了**，不一定怪玩家按得短 ✗
        player.displayClientMessage(Component.literal(
                "§c[TN-C] 这次没放出来 §7（读条被打断 / 冷却未清 / 引擎没接受这次释放）"), true);
        // ★ 一次到位的"为什么哑火"转储：把引擎 performSpell 里那几条判定全查一遍写进日志 ✓
        org.apache.logging.log4j.LogManager.getLogger("TN-C/gate").info(
                "TN-C: FIZZLE {} {}", pending.spell().getPath(), engineState(player, pending.spell()));
    }

    /**
     * 把"这次为什么可能没放出来"一次查完（引擎侧的全部判据）✓
     *
     * <p>引擎 `ClientPlayerEntityMixin.updateSpellCast()` 会在下面任一条不成立时**取消客户端读条** ⇒
     * 发出的 RELEASE 进度 < 1 ⇒ 服务端按"没蓄满"静默丢弃 ✗：
     * <ol>
     *   <li>{@code player.isAlive()}</li>
     *   <li>{@code player.getMainHandItem().getItem() == 施法时那个物品} ← 副手施法会踩这个 ✗</li>
     *   <li>{@code !getCooldownManager().isCoolingDown(id)} ← 客户端冷却</li>
     *   <li>{@code !EntityActionsAllowed.isImpaired(player, CAST_SPELL)} ← 沉默/眩晕类效果 ✗</li>
     * </ol>
     */
    private static String engineState(ServerPlayer player, ResourceLocation spell) {
        StringBuilder sb = new StringBuilder();
        sb.append("alive=").append(player.isAlive());
        sb.append(" mainItem=").append(player.getMainHandItem().getItem());
        try {
            net.spell_engine.internals.SpellCooldownManager manager =
                    ((net.spell_engine.internals.casting.SpellCasterEntity) player).getCooldownManager();
            sb.append(" coolingDown=").append(manager.isCoolingDown(spell))
                    .append(" progress=").append(manager.getCooldownProgress(spell, 0.0F));
        } catch (Throwable t) {
            sb.append(" cooldown=unavailable(").append(t.getClass().getSimpleName()).append(')');
        }
        try {
            sb.append(" impaired=").append(net.spell_engine.api.effect.EntityActionsAllowed.isImpaired(
                    player, net.spell_engine.api.effect.EntityActionsAllowed.Player.CAST_SPELL, true));
        } catch (Throwable t) {
            sb.append(" impaired=unavailable(").append(t.getClass().getSimpleName()).append(')');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  纯逻辑（可单测、可自检）
    // ------------------------------------------------------------------

    /**
     * 判定一次施法。
     *
     * <p>顺序很重要：<b>先看法杖、再看有没有解锁、最后看魔力</b> ——
     * 没解锁的法术不该因为"魔力够"就放出来（书里可能还留着卷轴时代绑进去的法术）。
     *
     * @param hasWand 手上有没有法杖（由 {@link #shouldBlock} 从玩家双手取）
     */
    public static Decision evaluate(MagicStoneData data, SpellCatalog.Entry entry, boolean requireLearned,
                                    boolean requireWand, boolean hasWand) {
        if (requireWand && !hasWand) {
            org.apache.logging.log4j.LogManager.getLogger("TN-C/gate").info("TN-C: gate BLOCKED {} reason=NO_WAND", entry.id());
            return Decision.NO_WAND;
        }
        if (requireLearned && !data.hasLearned(entry.id())) {
            // ★ 2026-09-30 作者关闭了自动学习 ✗ —— 这里不再就地补学，老老实实拦住 ✓
            org.apache.logging.log4j.LogManager.getLogger("TN-C/gate").info(
                    "TN-C: gate BLOCKED {} reason=NOT_LEARNED", entry.id());
            return Decision.NOT_LEARNED;
        }
        if (data.getMana() < entry.manaCostFor(data.getMaxMana())) {
            org.apache.logging.log4j.LogManager.getLogger("TN-C/gate").info("TN-C: gate BLOCKED {} reason=NOT_ENOUGH_MANA {} / {}", entry.id(), data.getMana(), entry.manaCostFor(data.getMaxMana()));
            return Decision.NOT_ENOUGH_MANA;
        }
        return Decision.ALLOW;
    }

    /** 兼容旧调用（自检里用）：不带法杖要求的版本。 */
    public static Decision evaluate(MagicStoneData data, SpellCatalog.Entry entry, boolean requireLearned) {
        return evaluate(data, entry, requireLearned, false, true);
    }

    /**
     * 玩家手上有没有法杖（主手或副手都算）。
     *
     * <p>为什么不看引擎传给 {@code attemptCasting} 的那个 ItemStack：
     * 那个 stack 是引擎自己挑的（`performSpell` 传的是主手），
     * 而这个包里 {@code spellHotbarShowsOffhand: true} —— 副手拿法杖也该能放。
     * 直接看玩家双手更稳，也不依赖引擎传什么。
     */
    public static boolean hasWand(Player player) {
        net.minecraft.world.item.Item wand =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        com.tnc.tnc.magic.compat.SpellEngineBridge.WAND);
        return java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem()).anyMatch(s->!s.isEmpty()&&((wand!=null&&s.is(wand))||s.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand));
    }
    public static boolean hasWandFor(Player player,SpellCatalog.Entry entry){
        var legacy=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(com.tnc.tnc.magic.compat.SpellEngineBridge.WAND);
        return java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem()).anyMatch(s->{
            if(s.isEmpty())return false;if(legacy!=null&&s.is(legacy))return true;
            return s.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand w&&entry.tier()<=w.design.tier()&&(entry.independent()||entry.element()==w.design.element());
        });
    }

    // ------------------------------------------------------------------
    //  计数器 / 信号灯
    // ------------------------------------------------------------------

    public static void markMixinApplied() {
        mixinApplied = true;
    }

    public static boolean isMixinApplied() {
        return mixinApplied;
    }

    public static int blockedCount() {
        return blockedCount.get();
    }

    public static int unlearnedCount() {
        return unlearnedCount.get();
    }

    public static int noWandCount() {
        return noWandCount.get();
    }

    public static int gateChecks() {
        return gateChecks.get();
    }

    public static String lastBlockedSpell() {
        return lastBlockedSpell;
    }

    // ------------------------------------------------------------------
    //  给 Mixin 用的入口
    // ------------------------------------------------------------------

    /**
     * @return true = 拦住这次施法
     */
    public static boolean shouldBlock(Player player, ResourceLocation spellId) {
        // 先记账再判断：这个数只要涨，就说明 Mixin 真的把控制权交到我们手上了
        // （哪怕 player 是 null、或者不是我们的法术，也算"我们的代码被执行到了"）
        gateChecks.incrementAndGet();

        // 客户端不拦：交给服务端裁决（避免客户端预测与服务端不一致时闪一下）
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if(com.tnc.tnc.combat.DownedCombat.isDowned(serverPlayer))return true;
        // 只管 TN-C 自己的法术
        SpellCatalog.Entry entry = SpellCatalog.byId(spellId);
        if (entry == null) {
            return false;
        }
        MagicStoneData data = MagicStone.getOrNull(serverPlayer);
        if (data == null) {
            return false;
        }

        boolean supported=hasWandFor(serverPlayer,entry);
        // Casting is an explicit player request. Keep periodic repair and pure
        // evaluate free of purchases; only a real eligible cast may learn here.
        if(com.tnc.tnc.Config.requireLearnedToCast&&!data.hasLearned(entry.id())&&!data.isExplicitlyForgotten(entry.id())
                &&(!com.tnc.tnc.Config.requireWandToCast||supported)
                && false /* 2026-09-30 作者关闭自动学习: 不再就地补学, 直接走 NOT_LEARNED */){
            com.tnc.tnc.network.MagicStoneNetwork.syncTo(serverPlayer);
            com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(serverPlayer,SpellCatalog.effectiveIds(data));
            com.tnc.tnc.adventure.AdventureService.milestone(serverPlayer,"learned");
        }
        Decision decision = evaluate(data, entry, com.tnc.tnc.Config.requireLearnedToCast,
                com.tnc.tnc.Config.requireWandToCast, supported);
        if (decision == Decision.ALLOW) {
            // ★ 诊断（作者 2026-09-29："投篮没法再释放了"）：**放行也留一行**。
            //   反过来读更有用：玩家说"按了没反应"时，日志里**既没有 BLOCKED 也没有 ALLOW**
            //   ⇒ 说明客户端根本没把这次施法送上来（引擎自己的冷却/弹药判定拦在前面 ✗），
            //   而不是我们的闸门拦的（拦截一定会打出 reason=… ✓）。
            org.apache.logging.log4j.LogManager.getLogger("TN-C/gate").info(
                    "TN-C: gate ALLOW {} mana {} / {}", entry.id(), data.getMana(),
                    entry.manaCostFor(data.getMaxMana()));
            // 记一笔"我放行了"：1.5 秒内没有 SPELL_CAST 就提示"没放出来"（见 PENDING 的说明 ✓）
            noteAllowed(serverPlayer, spellId);
            return false;
        }

        blockedCount.incrementAndGet();
        if (decision == Decision.NOT_LEARNED) {
            unlearnedCount.incrementAndGet();
            lastBlockedSpell = spellId + "（未解锁）";
            serverPlayer.displayClientMessage(Component.literal(
                    "§c[TN-C] 还没在魔法石里解锁「" + entry.displayName() + "」，施法失败"), true);
        } else if (decision == Decision.NO_WAND) {
            noWandCount.incrementAndGet();
            lastBlockedSpell = spellId + "（没拿法杖）";
            serverPlayer.displayClientMessage(Component.literal(
                    "§c[TN-C] 需要手持能承载该元素与阶位的法器"), true);
        } else {
            int cost = entry.manaCostFor(data.getMaxMana());
            lastBlockedSpell = spellId + "（" + data.getMana() + " / " + cost + "）";
            serverPlayer.displayClientMessage(Component.literal(
                    "§c[TN-C] 魔力不足（" + data.getMana() + " / " + cost + "），施法失败"), true);
        }
        return true;
    }
}
