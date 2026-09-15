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
            return Decision.NO_WAND;
        }
        if (requireLearned && !data.hasLearned(entry.id())) {
            return Decision.NOT_LEARNED;
        }
        if (data.getMana() < entry.manaCost()) {
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
        if (wand == null) {
            return false;
        }
        return player.getMainHandItem().is(wand) || player.getOffhandItem().is(wand);
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
        // 只管 TN-C 自己的法术
        SpellCatalog.Entry entry = SpellCatalog.byId(spellId);
        if (entry == null) {
            return false;
        }
        MagicStoneData data = MagicStone.getOrNull(serverPlayer);
        if (data == null) {
            return false;
        }

        Decision decision = evaluate(data, entry, com.tnc.tnc.Config.requireLearnedToCast,
                com.tnc.tnc.Config.requireWandToCast, hasWand(serverPlayer));
        if (decision == Decision.ALLOW) {
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
                    "§c[TN-C] 手上要拿着法杖才能施法"), true);
        } else {
            lastBlockedSpell = spellId + "（" + data.getMana() + " / " + entry.manaCost() + "）";
            serverPlayer.displayClientMessage(Component.literal(
                    "§c[TN-C] 魔力不足（" + data.getMana() + " / " + entry.manaCost() + "），施法失败"), true);
        }
        return true;
    }
}
