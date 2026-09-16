package com.tnc.tnc.magic.compat;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.ManaCharge;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;

/**
 * 让 TN-C 的魔力值真正参与施法。
 *
 * <p>接的是引擎公开的施法事件 {@code CombatEvents.SPELL_CAST}（参数：
 * caster / spell / targets / action(CHANNEL|RELEASE) / progress）。
 *
 * <h2>和「硬拦截」的分工（重要）</h2>
 * <b>拦住魔力不足的那一发</b>靠的是 {@code SpellHelperManaGateMixin}（施法前判定，见 {@code ManaGate}）——
 * 那个 Mixin 生效时，魔力不够的法术根本进不到 {@code RELEASE} 阶段，
 * 所以下面那条「勉强施法」分支<b>正常情况下永远不会被走到</b>。
 *
 * <p>那为什么还留着它？因为硬拦截依赖 Mixin 注入成功，而 {@code required:false} 的配置
 * 一旦注入失败是<b>静默</b>失效的（游戏照常跑，只是拦不住）。这种时候本类就是唯一的防线：
 * <ul>
 *   <li><b>CHANNEL</b>（起手）：魔力不够就先用动作栏警告 —— 让玩家在放出去之前就知道；</li>
 *   <li><b>RELEASE</b>（真正放出去）：扣魔力；不够则降级为"勉强施法"（魔力清零 + 力竭 + 聊天栏说明）。</li>
 * </ul>
 * 也就是说这是一层<b>兜底</b>，不是主路径。{@code /tnc gatetest} 可以直接告诉你主路径灵不灵。
 *
 * <p>只处理 TN-C 自己的法术（`SpellCatalog` 里有的），不碰别的 mod 的法术。
 * 所有直接引用引擎类的代码都在 {@link Impl} 里，没引擎时不会被执行到。
 */
public final class SpellEngineManaHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 魔力不足硬放之后，"力竭"持续多久（tick）。 */
    private static final int EXHAUST_TICKS = 60;

    /**
     * 我们的监听器到底有没有挂上引擎的 {@code SPELL_CAST}。
     *
     * <p>为什么需要这个标志：注册那一步整段包在 try/catch 里（软依赖，注册失败不能拖垮 mod），
     * 于是**签名对不上、字段改名之类的错误会被静默吞掉**，表现就是"施法不扣魔力"而毫无提示。
     * 有了这个标志，{@code /tnc engine} 和启动探针就能如实报出来。
     *
     * <p>注意不能用引擎的 {@code Event.isListened()} 代替 —— 那是"有没有**任何**监听器"，
     * 这个包里 spellbladenext / ysjxteams 也都监听了同一个事件，所以它永远是 true。
     */
    private static volatile boolean registered;

    // ---- 诊断计数器（"魔力没扣"这种问题只能靠证据定位，不能靠猜）----
    private static final java.util.concurrent.atomic.AtomicInteger seen =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger ours =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger otherMod =
            new java.util.concurrent.atomic.AtomicInteger();
    private static volatile String lastSeen = "（还没收到过任何施法事件）";

    /** 引擎一共播报了多少次 SPELL_CAST。 */
    public static int seenCount() {
        return seen.get();
    }

    /** 其中属于 TN-C 法术的次数。 */
    public static int ourSpellCount() {
        return ours.get();
    }

    /** 其中被判定为"别的 mod 的法术"而放过的次数。 */
    public static int otherModCount() {
        return otherMod.get();
    }

    /** 最近一次播报的内容（法术 id + 阶段 + 施法者）。 */
    public static String lastSeen() {
        return lastSeen;
    }

    private SpellEngineManaHook() {
    }

    public static boolean isRegistered() {
        return registered;
    }

    public static void register() {
        if (!SpellEngineBridge.enginePresent()) {
            LOGGER.info("TN-C: SpellEngine not present -> mana hook not registered (dev environment?)");
            return;
        }
        try {
            Impl.register();
            registered = true;
        } catch (Throwable error) {
            // 软依赖：注册失败不应该拖垮整个 mod（比如装了个不完整的引擎替身）
            LOGGER.warn("TN-C: could not register the mana hook on SpellEngine: {}", error.toString());
        }
    }

    // ------------------------------------------------------------------
    //  真正碰引擎类的部分
    // ------------------------------------------------------------------

    private static final class Impl {

        static void register() {
            net.spell_engine.api.event.CombatEvents.SPELL_CAST.register(Impl::onSpellCast);
            LOGGER.info("TN-C: mana hook registered on SpellEngine SPELL_CAST");
        }

        static void onSpellCast(net.spell_engine.api.event.CombatEvents.SpellCast.Args args) {
            // 先记账再判断：哪怕后面因为种种原因不处理，也能从计数上看出"事件到底有没有来"
            seen.incrementAndGet();

            ResourceLocation spellId = args.spell().id();
            String casterName = args.caster() == null ? "null" : args.caster().getName().getString();
            SpellCatalog.Entry entry = SpellCatalog.byId(spellId);

            lastSeen = spellId + " · " + args.action() + " · " + casterName
                    + (entry == null ? "（不是 TN-C 法术 → 不扣魔力）" : "（TN-C 法术）");
            // 每次施法只打一行，频率很低；"魔力没扣"这类问题全靠这行定位
            LOGGER.info("TN-C: SPELL_CAST spell={} action={} caster={} ours={}",
                    spellId, args.action(), casterName, entry != null);

            // 只管我们自己的法术
            if (entry == null) {
                otherMod.incrementAndGet();
                return;
            }
            ours.incrementAndGet();

            // 服务端权威：只认服务端的玩家实体
            if (!(args.caster() instanceof ServerPlayer player) || player.level().isClientSide()) {
                return;
            }
            MagicStoneData data = MagicStone.getOrNull(player);
            if (data == null) {
                LOGGER.warn("TN-C: no magic stone data on {} - mana not charged", casterName);
                return;
            }

            int cost = entry.manaCostFor(data.getMaxMana());

            if (args.action() == net.spell_engine.internals.casting.SpellCast.Action.CHANNEL) {
                // 起手阶段：不够就先警告（只在进度刚开始时提示一次，避免刷屏）
                if (!ManaCharge.canAfford(data, entry) && args.progress() <= 0.05F) {
                    player.displayClientMessage(Component.literal(
                            "§c[TN-C] 魔力不足（" + data.getMana() + " / " + cost + "）§7—— 现在收手还来得及"), true);
                }
                return;
            }

            // 真正放出去了：数值怎么变交给纯逻辑层（ManaCharge），这里只负责提示与同步
            ManaCharge.Result result = ManaCharge.apply(data, entry);
            // 记下这次施法：接下来几秒不回魔，好让玩家在 HUD 上看得见刚扣掉的那一截
            MagicStone.markCast(player);
            // 数据层表达不出来的那几条（回蓝 / 光环 / 拖尾 / 无冷却）交给机制层
            com.tnc.tnc.magic.TnSpellMechanics.onSpellCast(player, spellId, data);
            if (result.exhausted()) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUST_TICKS, 0));
                player.sendSystemMessage(Component.literal("§c[TN-C] " + ManaCharge.describe(result, entry)));
            } else {
                player.displayClientMessage(Component.literal(
                        "§b" + ManaCharge.describe(result, entry)
                                + " §7→ " + data.getMana() + " / " + data.getMaxMana()), true);
            }
            // 让界面/HUD 立刻看到新魔力值
            MagicStoneNetwork.syncTo(player);
        }
    }
}
