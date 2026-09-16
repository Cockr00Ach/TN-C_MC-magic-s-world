package com.tnc.tnc.magic.compat;

import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.ManaGate;
import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 「没魔力就真的放不出来」的<b>实测探针</b> —— {@code /tnc gatetest} 的背后实现。
 *
 * <h2>为什么需要它</h2>
 * 编译通过、字节码描述符比对通过，都只说明"注入器<b>应该</b>能对上"，
 * 说明不了"运行时<b>真的</b>注入进去了"。而 {@code required:false} 的配置一旦注入失败是
 * <b>静默</b>的：游戏正常启动，只是魔力再也拦不住施法 —— 这种失败必须能当场看见。
 *
 * <p>所以这里直接调用引擎真正的施法前判定 {@code SpellHelper.attemptCasting}，
 * 然后看 {@link ManaGate} 的计数器有没有按预期变化。计数器是 Mixin 注入进去的代码里加的，
 * <b>它涨了就说明注入真的生效了</b>，这是没法伪造的证据。
 *
 * <h2>三级阶梯（每一步只让一个变量变化）</h2>
 * <ol>
 *   <li>魔力<b>够</b>但<b>没解锁</b> → 该被拦，且算在"未解锁"头上；</li>
 *   <li>已解锁但魔力<b>差一点</b> → 该被拦，且算在"魔力不足"头上；</li>
 *   <li>已解锁且魔力<b>刚好够</b> → 该<b>放行</b>。</li>
 * </ol>
 * 三步合起来才说明"判定真的在按魔力和解锁状态区分"，只测"拦住了"是不够的
 * （一个永远返回"拦"的实现也能通过第一步）。
 *
 * <h2>安全性：不碰真玩家的任何数据</h2>
 * 全部在 {@link FakePlayerFactory} 给的一次性假玩家上跑。
 * 假玩家的魔法石数据只在这一份内存对象里，改了就改了，不会写进任何存档。
 * （注意假玩家是按维度缓存的单例，所以每次跑之前都显式 {@code forget} 一次，避免上次的残留影响这次。）
 */
public final class ManaGateProbe {

    /** 一轮测试里的一条断言。 */
    public record Step(String name, boolean passed, String detail) {
    }

    /**
     * @param ran   探针是否真的跑起来了（引擎不在 / 法术不认识时为 false，不算失败）
     * @param steps 各步断言
     * @param note  结论或跳过原因
     */
    public record Report(boolean ran, List<Step> steps, String note) {

        public int passedCount() {
            int n = 0;
            for (Step step : steps) {
                if (step.passed()) {
                    n++;
                }
            }
            return n;
        }

        public boolean allPassed() {
            return ran && !steps.isEmpty() && passedCount() == steps.size();
        }
    }

    private ManaGateProbe() {
    }

    /**
     * 默认测哪个法术：目录里<b>最便宜</b>的那个 —— 消耗最小，最容易构造"差一点"的魔力。
     *
     * @return null = 法术目录是空的
     */
    public static ResourceLocation defaultSpell() {
        SpellCatalog.Entry cheapest = null;
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            if (cheapest == null || entry.manaCost() < cheapest.manaCost()) {
                cheapest = entry;
            }
        }
        return cheapest != null ? cheapest.id() : null;
    }

    /**
     * 跑一轮实测。默认测目录里的第一个法术。
     *
     * @param level   服务端维度（拿假玩家用）
     * @param spellId 要测的 TN-C 法术
     */
    public static Report run(ServerLevel level, ResourceLocation spellId) {
        if (!SpellEngineBridge.enginePresent()) {
            return new Report(false, List.of(),
                    "没装 SpellEngine，硬拦截无从测起（dev 环境属于正常情况）");
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(spellId);
        if (entry == null) {
            return new Report(false, List.of(),
                    "法术目录里没有 " + spellId + "（用 /tnc spells 看可测的法术）");
        }
        if (entry.manaCost() <= 0) {
            return new Report(false, List.of(),
                    "「" + entry.displayName() + "」的魔力消耗是 " + entry.manaCost()
                            + "，没法用「魔力差一点」来测，先给它配个正数消耗");
        }
        try {
            // 只有引擎真的在的时候才会走到这里，所以 Impl 里的引擎类才会被加载
            return Impl.run(level, entry);
        } catch (Throwable error) {
            return new Report(false, List.of(), "探针执行出错：" + error);
        }
    }

    // ------------------------------------------------------------------
    //  真正碰引擎类的部分
    // ------------------------------------------------------------------

    private static final class Impl {

        static Report run(ServerLevel level, SpellCatalog.Entry entry) {
            FakePlayer probe = FakePlayerFactory.getMinecraft(level);
            MagicStoneData data = MagicStone.getOrNull(probe);
            if (data == null) {
                return new Report(false, List.of(), "假玩家身上没挂到魔法石 capability（capability 注册有问题？）");
            }

            int cost = entry.manaCost();
            List<Step> steps = new ArrayList<>();
            // ---- 第 0 步：施法钩子到底挂上引擎了没有 ----
            // 注册那一步包在 try/catch 里（软依赖），所以"字段改名/签名对不上"会被静默吞掉，
            // 表现就是"施法不扣魔力"而毫无提示。这条断言把那个静默失败变成可见的 FAIL。
            steps.add(new Step("施法钩子已注册到引擎的 SPELL_CAST", SpellEngineManaHook.isRegistered(),
                    SpellEngineManaHook.isRegistered()
                            ? "SPELL_CAST 监听器已挂上（施法会按消耗扣魔力）"
                            : "没挂上 —— 施法不会扣魔力，多半是引擎 API 变了"));

            // 假玩家的上限设成消耗表的基准上限：这样"实际消耗"就等于表里的值，
            // 后面几条按表里的数字写的断言才成立（消耗会随上限等比放大）
            data.setMaxMana(Math.max(cost * 2, com.tnc.tnc.Config.manaCostBaselineMaxMana));
            data.forget(entry.id());
            cost = entry.manaCostFor(data.getMaxMana());

            // ---- 第 1 步：没拿法杖 → 拦住 ----
            // 这一条必须先测：不然后面"魔力够就该放行"会被"没法杖"这个原因拦掉，
            // 看起来像魔力判定坏了，实际是测试自己没准备好道具。
            probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            probe.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            data.setMana(cost);
            data.learn(entry.id());          // 解锁和魔力都给足，唯一缺的就是法杖
            int bWand0 = ManaGate.blockedCount();
            int nWand0 = ManaGate.noWandCount();
            boolean threwWand = attempt(probe, entry.id());
            steps.add(new Step("没拿法杖 → 拦住（算在「没法杖」头上）",
                    ManaGate.noWandCount() == nWand0 + 1 && ManaGate.blockedCount() == bWand0 + 1,
                    "blocked +" + (ManaGate.blockedCount() - bWand0)
                            + " · noWand +" + (ManaGate.noWandCount() - nWand0)
                            + (threwWand ? " · 引擎内部抛错(已忽略)" : "")));

            // 给假玩家一根法杖（后面的判定才有意义）。道具是一次性的，不影响真玩家。
            ItemStack wand = wandStack();
            if (wand.isEmpty()) {
                return new Report(true, steps, "拿不到法杖物品 —— 注册失败了？");
            }
            probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);

            // ---- 第 2 步：魔力够、但没解锁 ----
            // 这一步同时承担"注入到底生效没有"的举证责任：计数器涨了才算注入成功。
            data.forget(entry.id());
            data.setMana(cost);
            int g0 = ManaGate.gateChecks();
            int b0 = ManaGate.blockedCount();
            int u0 = ManaGate.unlearnedCount();
            boolean threw0 = attempt(probe, entry.id());
            int dg = ManaGate.gateChecks() - g0;

            boolean injected = dg > 0;
            steps.add(new Step("Mixin 已注入：判定代码真的被执行到", injected,
                    "gateChecks +" + dg));
            if (!injected) {
                // 再往下测没有意义：拦不住是因为压根没注入，不是因为判定逻辑
                return new Report(true, steps,
                        "Mixin 没生效 —— 事后扣魔力仍然有效，但拦不住魔力不足的那一发");
            }
            steps.add(new Step("魔力充足但未解锁 → 拦住（算在「未解锁」头上）",
                    ManaGate.blockedCount() == b0 + 1 && ManaGate.unlearnedCount() == u0 + 1,
                    "blocked +" + (ManaGate.blockedCount() - b0)
                            + " · unlearned +" + (ManaGate.unlearnedCount() - u0)
                            + (threw0 ? " · 引擎内部抛错(已忽略)" : "")));

            // ---- 第 2 步：已解锁、魔力差一点 ----
            data.learn(entry.id());
            data.setMana(cost - 1);
            int b1 = ManaGate.blockedCount();
            int u1 = ManaGate.unlearnedCount();
            boolean threw1 = attempt(probe, entry.id());
            steps.add(new Step("已解锁但魔力差 1 点 → 拦住（算在「魔力不足」头上）",
                    ManaGate.blockedCount() == b1 + 1 && ManaGate.unlearnedCount() == u1,
                    "魔力 " + (cost - 1) + " / " + cost
                            + " · blocked +" + (ManaGate.blockedCount() - b1)
                            + " · unlearned +" + (ManaGate.unlearnedCount() - u1)
                            + (threw1 ? " · 引擎内部抛错(已忽略)" : "")));

            // ---- 第 3 步：已解锁、魔力刚好够 ----
            // 只断言"没被拦"：引擎对假玩家还可能因为别的原因（没拿书/冷却）给出失败结论，
            // 那属于引擎的事，不影响"我们的闸门这次放行了"这个结论。
            data.setMana(cost);
            int b2 = ManaGate.blockedCount();
            boolean threw2 = attempt(probe, entry.id());
            steps.add(new Step("已解锁且魔力刚好够 → 放行（不再拦）",
                    ManaGate.blockedCount() == b2,
                    "魔力 " + cost + " / " + cost
                            + " · blocked +" + (ManaGate.blockedCount() - b2)
                            + (threw2 ? " · 引擎内部抛错(已忽略)" : "")));

            // 收尾：假玩家是缓存的单例，把学习状态清掉，免得影响下一次 gatetest
            data.forget(entry.id());
            probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            return new Report(true, steps, null);
        }

        /** 一根法杖（注册不到就是 empty）。 */
        private static ItemStack wandStack() {
            net.minecraft.world.item.Item wand = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(SpellEngineBridge.WAND);
            return wand == null ? ItemStack.EMPTY : new ItemStack(wand);
        }

        /**
         * 走<b>3 参</b>重载调用 —— 引擎里绝大多数调用点用的都是它。
         * 它内部会转调 4 参，所以这一步同时也证明了"3 参的调用者一样会被拦"。
         *
         * @return true = 引擎内部抛了错（对我们的计数器断言没有影响：我们的代码在 HEAD 先跑完了）
         */
        private static boolean attempt(FakePlayer probe, ResourceLocation spellId) {
            try {
                net.spell_engine.internals.SpellHelper.attemptCasting(probe, ItemStack.EMPTY, spellId);
                return false;
            } catch (Throwable error) {
                return true;
            }
        }
    }
}
