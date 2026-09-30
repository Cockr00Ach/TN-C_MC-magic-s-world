package com.tnc.tnc.magic;

import com.tnc.tnc.Config;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔法石自检（不依赖界面，命令行/服务器启动都能跑）。
 *
 * <p>设计原则：**除了"capability 挂没挂上"这一项，其它全部在临时的
 * {@link MagicStoneData} 上跑** —— 绝不碰玩家自己的数据，所以随时可以重复执行。
 *
 * <p>用途：
 * <ul>
 *   <li>专用服务器启动时自动跑一遍（把结果打到日志里，用来验证运行期行为）</li>
 *   <li>游戏内 {@code /tnc selftest} 手动跑（玩家报问题时用来排查）</li>
 * </ul>
 */
public final class MagicStoneSelfTest {

    /** 一条检查结果。 */
    public record Check(String name, boolean passed, String detail) {
    }

    private MagicStoneSelfTest() {
    }

    /**
     * @param capabilityHolder 用来验证 capability 的玩家（可以是假玩家）；传 null 就跳过那一项
     */
    public static List<Check> run(net.minecraft.world.entity.player.Player capabilityHolder) {
        List<Check> checks = new ArrayList<>();

        // 0. capability 到底挂上没挂上（这是唯一一项碰真实玩家的）
        if (capabilityHolder != null) {
            boolean attached = MagicStone.getOrNull(capabilityHolder) != null;
            checks.add(new Check("capability attached",
                    attached,
                    attached ? "MagicStoneData present on " + capabilityHolder.getName().getString() : "getCapability() returned empty"));
        }

        // 1. 配置是否加载（后面几项都依赖它）
        boolean configReady = Config.pointThresholds != null && Config.learnCostPerTier != null;
        checks.add(new Check("config loaded", configReady,
                configReady ? "thresholds=" + Config.pointThresholds + " learnCost=" + Config.learnCostPerTier
                        : "Config.pointThresholds is null (config not loaded yet)"));
        if (!configReady) {
            return checks;
        }

        // 2. 初始值：七元素全 3 → 亲和力总和 21 → 上限 = 21×10 = 210，且初始魔力回满
        MagicStoneData data = new MagicStoneData();
        data.assignDefaultAffinities(3);
        data.recomputeMaxMana(0, 10, 10, 0);
        checks.add(new Check("affinity sum = 21", data.affinitySum() == 21, "sum=" + data.affinitySum()));
        checks.add(new Check("max mana = 210 @level 0", data.getMaxMana() == 210, "maxMana=" + data.getMaxMana()));
        checks.add(new Check("mana starts full", data.getMana() == data.getMaxMana(),
                "mana=" + data.getMana() + "/" + data.getMaxMana()));

        // 3. 等级成长：10 级 → 210 + 10×10 = 310，跨过两个档位
        data.recomputeMaxMana(10, 10, 10, 0);
        checks.add(new Check("max mana = 310 @level 10", data.getMaxMana() == 310, "maxMana=" + data.getMaxMana()));
        checks.add(new Check("points total = 2 @310", data.getPointsTotal(Config.pointThresholds) == 2,
                "total=" + data.getPointsTotal(Config.pointThresholds) + " thresholds=" + Config.pointThresholds));

        // 4. 法术目录：每个有法术的元素都是"每条链 5 级"（链数按文档要求 >= 3，现在水/土/暗是 4）
        int elementCount = 0;
        int chainTotal = 0;
        boolean chainsOk = true;
        StringBuilder chainSizes = new StringBuilder();
        for (Element element : Element.values()) {
            List<SpellCatalog.Chain> chains = SpellCatalog.chainsOf(element);
            if (chains.isEmpty()) {
                continue;
            }
            elementCount++;
            chainTotal += chains.size();
            // 链数不再写死 3：文档里水/土/暗都是 4 条，雷/火/风暂时 3 条
            chainsOk = chainsOk && chains.size() >= 3;
            chainSizes.append(element.cn()).append("(");
            for (SpellCatalog.Chain chain : chains) {
                int size = SpellCatalog.of(element, chain).size();
                chainSizes.append(chain.cn()).append('=').append(size).append(' ');
                chainsOk = chainsOk && size == SpellCatalog.maxTier();
            }
            chainSizes.append(") ");
        }
        int expected = chainTotal * SpellCatalog.maxTier();
        checks.add(new Check("chains are 5 tiers each (>=3 chains per element)",
                chainsOk && elementCount > 0 && SpellCatalog.all().size() - SpellCatalog.of(null).size() == expected,
                "elements=" + elementCount + " chains=" + chainTotal
                        + " total=" + SpellCatalog.all().size() + " expected=" + expected
                        + " [" + chainSizes.toString().trim() + "]"));

        // 5. 门槛顺序（全部在临时数据上跑）
        MagicStoneData fresh = new MagicStoneData();
        fresh.assignDefaultAffinities(3);
        fresh.recomputeMaxMana(10, 10, 10, 0);   // 310 → 2 点

        SpellCatalog.Entry tier1 = SpellCatalog.all().get(0);   // 小闪电
        SpellCatalog.Entry tier2 = SpellCatalog.all().get(1);   // 雷场
        SpellCatalog.Entry tier4 = SpellCatalog.all().get(3);   // 雷暴（亲和力 3 学不到）

        checks.add(new Check("tier1 unlockable", MagicStoneLearning.check(fresh, tier1) == MagicStoneLearning.Result.OK,
                MagicStoneLearning.check(fresh, tier1).name()));
        checks.add(new Check("cannot skip to tier2", MagicStoneLearning.check(fresh, tier2) == MagicStoneLearning.Result.OUT_OF_ORDER,
                MagicStoneLearning.check(fresh, tier2).name()));
        checks.add(new Check("tier4 blocked by affinity", MagicStoneLearning.check(fresh, tier4) == MagicStoneLearning.Result.AFFINITY_TOO_LOW,
                "affinity(lightning)=" + fresh.getAffinity(Element.LIGHTNING) + " maxTier=" + fresh.maxTierFor(Element.LIGHTNING)
                        + " -> " + MagicStoneLearning.check(fresh, tier4).name()));

        // 6. 真解锁一次：扣点数、记已学、推进度
        MagicStoneLearning.Result unlockResult = MagicStoneLearning.unlock(fresh, tier1);
        boolean unlockOk = unlockResult == MagicStoneLearning.Result.OK
                && fresh.hasLearned(tier1.id())
                && fresh.getPointsSpent() == Config.learnCostForTier(1)
                && fresh.getProgress(Element.LIGHTNING) == 1;
        checks.add(new Check("unlock tier1 works", unlockOk,
                "result=" + unlockResult.name() + " learned=" + fresh.hasLearned(tier1.id())
                        + " spent=" + fresh.getPointsSpent() + " progress=" + fresh.getProgress(Element.LIGHTNING)));

        // 7. 解锁一级之后，二级应该只差"点数不足"（说明前面几道门都过了）
        MagicStoneLearning.Result afterUnlock = MagicStoneLearning.check(fresh, tier2);
        checks.add(new Check("tier2 now only lacks points", afterUnlock == MagicStoneLearning.Result.NOT_ENOUGH_POINTS,
                "available=" + fresh.getPointsAvailable(Config.pointThresholds)
                        + " cost=" + Config.learnCostForTier(2) + " -> " + afterUnlock.name()));

        // 7b. 三条链互不影响：主链学了 1 级，不该推进雷球链的进度
        SpellCatalog.Entry orbT1 = SpellCatalog.of(Element.LIGHTNING, SpellCatalog.Chain.ORB).get(0);
        SpellCatalog.Entry orbT2 = SpellCatalog.of(Element.LIGHTNING, SpellCatalog.Chain.ORB).get(1);
        boolean independent = fresh.getProgress(Element.LIGHTNING, SpellCatalog.Chain.ORB) == 0
                && MagicStoneLearning.check(fresh, orbT2) == MagicStoneLearning.Result.OUT_OF_ORDER;
        checks.add(new Check("chains are independent", independent,
                "CORE=" + fresh.getProgress(Element.LIGHTNING, SpellCatalog.Chain.CORE)
                        + " ORB=" + fresh.getProgress(Element.LIGHTNING, SpellCatalog.Chain.ORB)
                        + " 直接学 " + orbT2.displayName() + " -> " + MagicStoneLearning.check(fresh, orbT2).name()));

        // 7c. **高阶替换低阶**：同一链学了 2 级，法杖里就只剩 2 级（不是两个都挂）
        MagicStoneData chainData = new MagicStoneData();
        chainData.assignDefaultAffinities(5);
        chainData.recomputeMaxMana(0, 10, 10, 0);
        chainData.addBonusPoints(50);
        MagicStoneLearning.unlock(chainData, orbT1);
        MagicStoneLearning.unlock(chainData, orbT2);
        java.util.List<net.minecraft.resources.ResourceLocation> effective = SpellCatalog.effectiveIds(chainData);
        boolean replaced = effective.contains(orbT2.id()) && !effective.contains(orbT1.id());
        checks.add(new Check("high tier replaces low (wand content)", replaced,
                "已学 ORB 1+2 级 -> 法杖内容="
                        + effective.stream().map(net.minecraft.resources.ResourceLocation::getPath).toList()));

        // 8. NBT 往返
        MagicStoneData copy = new MagicStoneData();
        copy.deserializeNBT(fresh.serializeNBT());
        boolean nbtOk = copy.affinitySum() == fresh.affinitySum()
                && copy.getMaxMana() == fresh.getMaxMana()
                && copy.getMana() == fresh.getMana()
                && copy.getPointsSpent() == fresh.getPointsSpent()
                && copy.getProgress(Element.LIGHTNING) == fresh.getProgress(Element.LIGHTNING)
                && copy.getLearned().size() == fresh.getLearned().size()
                && copy.hasLearned(tier1.id());
        checks.add(new Check("NBT round-trip", nbtOk,
                "affinity=" + copy.affinitySum() + " maxMana=" + copy.getMaxMana() + " learned=" + copy.getLearned().size()));

        // 8b. 配装（loadout）：2 页 × 9 槽 = 18（2026-09-29 用户拍板）
        //
        // 这一批断言守的是几个真会翻车的点：
        //   ① 学法"只填空槽、绝不覆盖玩家手配的"——否则玩家排好的键位会被顶掉
        //   ② 同链升档要替换该链的键，但**别的链、别的元素**一律不动
        //   ③ 配键列表只给"每条链当前拥有的最高档"（没学过的不出现）
        //   ④ 同一法术只能占一个槽 ——否则"3 个键全绑雷暴"刷冷却
        //   ⑤ 槽位定长、不挤位，写进法杖的顺序 = 键位顺序
        //   ⑥ 切页/遗忘之后法杖内容跟着变（忘了的法术不许留在槽里当死键）
        MagicStoneData loadout = new MagicStoneData();
        loadout.assignDefaultAffinities(5);          // 亲和 5 → 前四级都能学
        loadout.recomputeMaxMana(0, 10, 10, 0);
        loadout.addBonusPoints(200);
        java.util.List<SpellCatalog.Entry> learnable = new java.util.ArrayList<>();
        for (SpellCatalog.Chain chain : SpellCatalog.Chain.values()) {
            java.util.List<SpellCatalog.Entry> ofChain = SpellCatalog.of(Element.LIGHTNING, chain);
            if (!ofChain.isEmpty()) {
                learnable.add(ofChain.get(0));       // 每条链的第一级
            }
        }
        for (SpellCatalog.Entry entry : learnable) {
            MagicStoneLearning.unlock(loadout, entry);
        }
        checks.add(new Check("loadout: slot count = 2 pages x 9",
                MagicStoneData.LOADOUT_SLOTS == 18 && MagicStoneData.PAGE_COUNT == 2
                        && MagicStoneData.SLOTS_PER_PAGE == 9,
                "pages=" + MagicStoneData.PAGE_COUNT + " perPage=" + MagicStoneData.SLOTS_PER_PAGE
                        + " total=" + MagicStoneData.LOADOUT_SLOTS));
        checks.add(new Check("loadout: learning fills slots in order, no duplicates",
                loadout.loadoutCount() == loadout.getLearned().size()
                        && loadout.getSlot(0) != null && loadout.getSlot(1) != null && loadout.getSlot(2) != null,
                "bound=" + loadout.loadoutCount() + " learned=" + loadout.getLearned().size()
                        + " slot0=" + loadout.getSlot(0)));

        // ① 玩家手配的槽不许被新学的**别的链**的法术覆盖
        java.util.List<SpellCatalog.Entry> fireChain = SpellCatalog.of(Element.FIRE, SpellCatalog.Chain.BALL);
        net.minecraft.resources.ResourceLocation manual = loadout.getSlot(0);
        loadout.setSlot(13, manual);                 // 手动挪到第 2 页的某个槽
        boolean manualKept = loadout.getSlot(13) != null && loadout.getSlot(0) == null;
        loadout.setSlot(0, manual);
        int freeBefore = loadout.firstFreeSlot();
        if (!fireChain.isEmpty()) {
            MagicStoneLearning.unlock(loadout, fireChain.get(0));
        }
        boolean filledFreeOnly = freeBefore >= 0 && manual.equals(loadout.getSlot(0))
                && loadout.getSlot(freeBefore) != null;
        checks.add(new Check("loadout: manual binding survives a new spell", manualKept && filledFreeOnly,
                "freeBefore=" + freeBefore + " slot0=" + loadout.getSlot(0)
                        + " slot" + freeBefore + "=" + loadout.getSlot(freeBefore)));

        // ② 配键列表 = 每条链的最高档；同链升档后列表换成新档、旧档不再可绑
        java.util.List<SpellCatalog.Entry> topsBefore = SpellCatalog.chainTopAssignable(loadout, Element.LIGHTNING);
        boolean topsAreChainTops = !topsBefore.isEmpty();
        for (SpellCatalog.Entry top : topsBefore) {
            if (!top.id().equals(SpellCatalog.topLearned(loadout, Element.LIGHTNING, top.chain()).id())) {
                topsAreChainTops = false;
            }
        }
        checks.add(new Check("loadout: bind list = each chain's current top only", topsAreChainTops,
                "lightning tops=" + topsBefore.size() + " " + topsBefore.stream()
                        .map(SpellCatalog.Entry::displayName).toList()));

        // ②b 同链升档：该链的键被换成新档，别的链不动
        SpellCatalog.Entry coreT1 = SpellCatalog.of(Element.LIGHTNING, SpellCatalog.Chain.CORE).get(0);
        SpellCatalog.Entry coreT2 = SpellCatalog.of(Element.LIGHTNING, SpellCatalog.Chain.CORE).get(1);
        SpellCatalog.Entry orbT1Self = SpellCatalog.of(Element.LIGHTNING, SpellCatalog.Chain.ORB).get(0);
        int coreSlot = -1;
        int orbSlot = -1;
        for (int i = 0; i < MagicStoneData.LOADOUT_SLOTS; i++) {
            if (coreT1.id().equals(loadout.getSlot(i))) coreSlot = i;
            if (orbT1Self.id().equals(loadout.getSlot(i))) orbSlot = i;
        }
        MagicStoneLearning.unlock(loadout, coreT2);
        boolean upgraded = coreSlot >= 0 && coreT2.id().equals(loadout.getSlot(coreSlot))
                && orbSlot >= 0 && orbT1Self.id().equals(loadout.getSlot(orbSlot))
                && !SpellCatalog.canBind(loadout, coreT1.id());
        checks.add(new Check("loadout: same-chain upgrade swaps that chain's key only", upgraded,
                "coreSlot=" + coreSlot + " -> " + loadout.getSlot(coreSlot)
                        + " orbSlot=" + orbSlot + " kept=" + (orbSlot >= 0 && orbT1Self.id().equals(loadout.getSlot(orbSlot)))));

        // ③ 同一法术只能占一个槽
        net.minecraft.resources.ResourceLocation boundNow = loadout.getSlot(coreSlot >= 0 ? coreSlot : 0);
        int second = loadout.firstFreeSlot();
        boolean movedNotCopied = false;
        if (second >= 0 && boundNow != null) {
            loadout.setSlot(second, boundNow);
            movedNotCopied = boundNow.equals(loadout.getSlot(second))
                    && !boundNow.equals(loadout.getSlot(coreSlot >= 0 ? coreSlot : 0));
            loadout.setSlot(coreSlot >= 0 ? coreSlot : 0, boundNow);
        }
        checks.add(new Check("loadout: one spell occupies exactly one slot", movedNotCopied,
                "moved to " + second + " from " + coreSlot));

        // ③ 法杖内容 = 定长 9 项、按槽位顺序（不排序、不挤位）
        java.util.List<net.minecraft.resources.ResourceLocation> page0 = loadout.pageSpellIds(0);
        boolean fixedLengthAndOrder = page0.size() == MagicStoneData.SLOTS_PER_PAGE
                && java.util.Objects.equals(page0.get(0), loadout.getSlot(0))
                && java.util.Objects.equals(page0.get(1), loadout.getSlot(1))
                && page0.get(MagicStoneData.SLOTS_PER_PAGE - 1) == loadout.getSlot(MagicStoneData.SLOTS_PER_PAGE - 1);
        checks.add(new Check("loadout: page ids are fixed-length and slot-ordered", fixedLengthAndOrder,
                "page0=" + page0.size() + " first=" + page0.get(0)));

        // ④ NBT 往返：配装和页号都要活下来（不然一重登键位就全空了）
        MagicStoneData loadoutCopy = new MagicStoneData();
        loadoutCopy.deserializeNBT(loadout.serializeNBT());
        boolean loadoutNbt = loadoutCopy.loadoutCount() == loadout.loadoutCount()
                && java.util.Objects.equals(loadoutCopy.getSlot(0), loadout.getSlot(0))
                && loadoutCopy.getLoadoutPage() == loadout.getLoadoutPage()
                && java.util.Objects.equals(loadoutCopy.getSlot(MagicStoneData.SLOTS_PER_PAGE - 1),
                        loadout.getSlot(MagicStoneData.SLOTS_PER_PAGE - 1));
        checks.add(new Check("loadout: survives NBT round-trip", loadoutNbt,
                "bound=" + loadoutCopy.loadoutCount() + " page=" + loadoutCopy.getLoadoutPage()));

        // ⑤ 切页循环
        int pageBefore = loadout.getLoadoutPage();
        int pageAfter = loadout.cycleLoadoutPage();
        boolean pageOk = pageAfter == (pageBefore + 1) % MagicStoneData.PAGE_COUNT
                && loadout.cycleLoadoutPage() == pageBefore;
        checks.add(new Check("loadout: page cycles", pageOk,
                "page " + pageBefore + " -> " + pageAfter));

        // ⑥ 清空槽
        int nonEmpty = -1;
        for (int i = 0; i < MagicStoneData.LOADOUT_SLOTS; i++) {
            if (loadout.getSlot(i) != null) {
                nonEmpty = i;
                break;
            }
        }
        boolean clearOk = nonEmpty >= 0 && loadout.clearSlot(nonEmpty) && loadout.getSlot(nonEmpty) == null;
        checks.add(new Check("loadout: clearing a slot empties it", clearOk, "cleared slot " + nonEmpty));

        // ⑦ 遗忘的链法术必须一起从配装里消失（否则法杖上留一个按不动的死键 ✗）
        net.minecraft.resources.ResourceLocation forgotten = null;
        for (int i = 0; i < MagicStoneData.LOADOUT_SLOTS; i++) {
            if (loadout.getSlot(i) != null && SpellCatalog.byId(loadout.getSlot(i)) != null) {
                forgotten = loadout.getSlot(i);
                break;
            }
        }
        boolean forgetClears = forgotten != null && loadout.forget(forgotten) && !loadout.isBound(forgotten);
        checks.add(new Check("loadout: forgetting removes it from the hotbar", forgetClears,
                "forgot=" + forgotten));

        // 9. 施法魔力消耗表（按等级递增）
        int[] manaCosts = new int[5];
        boolean ascending = true;
        for (int tier = 1; tier <= 5; tier++) {
            manaCosts[tier - 1] = Config.manaCostForTier(tier);
            if (tier > 1 && manaCosts[tier - 1] < manaCosts[tier - 2]) {
                ascending = false;
            }
        }
        checks.add(new Check("mana cost table ascends", ascending && manaCosts[0] > 0, java.util.Arrays.toString(manaCosts)));

        // 10. 扣魔力：够就扣、不够就拒绝（并且不改数据）
        MagicStoneData manaData = new MagicStoneData();
        manaData.assignDefaultAffinities(3);
        manaData.recomputeMaxMana(0, 10, 10, 0);
        boolean deductOk = manaData.spendMana(20) && manaData.getMana() == manaData.getMaxMana() - 20;
        boolean refuseOk = !manaData.spendMana(999_999) && manaData.getMana() == manaData.getMaxMana() - 20;
        checks.add(new Check("spendMana deducts / refuses", deductOk && refuseOk,
                "mana=" + manaData.getMana() + " / " + manaData.getMaxMana()));

        // 11. 魔力恢复为正、且随上限增长（不然高级法术永远放不出来）
        //     显示的是**每秒**真实速度（= 每周期回的量 × 20 ÷ 周期）。
        //     直接打印 manaRegenFor 会把"每周期回多少"说成"/s"，误导人。
        double regenSmall = Config.manaRegenPerSecondFor(210);
        double regenBig = Config.manaRegenPerSecondFor(2100);
        checks.add(new Check("mana regen positive & scales", regenSmall > 0 && regenBig >= regenSmall,
                String.format("上限 210 → %.1f/s，上限 2100 → %.1f/s（周期 %d tick）",
                        regenSmall, regenBig, Config.manaRegenIntervalTicks)));

        // 12. 每一级法术都放得起（最贵的法术不能超过初始魔力上限）
        int worst = 0;
        for (int tier = 1; tier <= 5; tier++) {
            worst = Math.max(worst, Config.manaCostForTier(tier));
        }
        checks.add(new Check("every tier affordable at 210", worst <= 210,
                "最贵 " + worst + " 魔力 vs 初始上限 210"));

        // 12b. Check the selected cost mode; older saves can retain proportional costs.
        int costAtBaseline = Config.manaCostForTier(1, Config.manaCostBaselineMaxMana);
        int costAt620 = Config.manaCostForTier(1, 620);
        int expectedAt620=Config.manaCostScalesWithMaxMana?Math.max(1,(int)Math.round(costAtBaseline*620.0/Math.max(1,Config.manaCostBaselineMaxMana))):costAtBaseline;
        boolean configuredCost = costAtBaseline == Config.manaCostForTier(1) && costAt620 == expectedAt620;
        checks.add(new Check("mana cost follows configured fixed/proportional mode", configuredCost,
                "上限 " + Config.manaCostBaselineMaxMana + " → " + costAtBaseline
                        + "，上限 620 → " + costAt620
                        + "（占上限 " + String.format("%.1f", costAt620 * 100.0 / 620) + "%）"));

        // 13/14. 拦截判定（纯逻辑，不碰玩家数据）
        MagicStoneData gateData = new MagicStoneData();
        gateData.assignDefaultAffinities(3);
        gateData.recomputeMaxMana(0, 10, 10, 0);
        SpellCatalog.Entry gateSpell = SpellCatalog.all().get(0);
        boolean unlearnedBlocked = ManaGate.evaluate(gateData, gateSpell, true) == ManaGate.Decision.NOT_LEARNED;
        boolean unlearnedAllowed = ManaGate.evaluate(gateData, gateSpell, false) == ManaGate.Decision.ALLOW;
        checks.add(new Check("gate blocks unlearned spells", unlearnedBlocked && unlearnedAllowed,
                "要求解锁时=" + (unlearnedBlocked ? "拦" : "放") + "，不要求时=" + (unlearnedAllowed ? "放" : "拦")));

        gateData.learn(gateSpell.id());
        boolean learnedAllowed = ManaGate.evaluate(gateData, gateSpell, true) == ManaGate.Decision.ALLOW;
        gateData.setMana(0);
        boolean poorBlocked = ManaGate.evaluate(gateData, gateSpell, true) == ManaGate.Decision.NOT_ENOUGH_MANA;
        checks.add(new Check("gate allows learned / blocks no-mana", learnedAllowed && poorBlocked,
                "解锁+有魔力=" + (learnedAllowed ? "放" : "拦") + "，解锁+没魔力=" + (poorBlocked ? "拦" : "放")));

        // 15/16. 施法扣魔力的算术（ManaCharge，纯逻辑 —— 每一条边界都跑一遍）
        //     这段原来是写在直接引用 SpellEngine 的内部类里的，dev 环境一行都跑不到。
        int cost = gateSpell.manaCost();

        MagicStoneData chargeData = new MagicStoneData();
        chargeData.assignDefaultAffinities(3);
        chargeData.recomputeMaxMana(0, 10, 10, 0);
        chargeData.learn(gateSpell.id());

        chargeData.setMana(cost + 5);
        ManaCharge.Result rich = ManaCharge.apply(chargeData, gateSpell);
        boolean richOk = rich.outcome() == ManaCharge.Outcome.DEDUCTED
                && rich.spent() == cost
                && rich.after() == cost + 5 - cost
                && chargeData.getMana() == cost + 5 - cost;
        checks.add(new Check("charge deducts exactly the spell cost", richOk,
                "魔力 " + rich.before() + " → " + rich.after() + "（消耗 " + cost + "）"));

        chargeData.setMana(cost);
        ManaCharge.Result exact = ManaCharge.apply(chargeData, gateSpell);
        boolean exactOk = exact.outcome() == ManaCharge.Outcome.DEDUCTED
                && exact.after() == 0 && chargeData.getMana() == 0 && exact.shortfall() == 0;
        checks.add(new Check("charge at exactly the cost leaves 0 (not exhausted)", exactOk,
                "魔力 " + exact.before() + " → " + exact.after()
                        + "（" + (exact.exhausted() ? "判成力竭=错" : "正常扣除") + "）"));

        // 差 1 点：这是硬拦截失效时的兜底路径，必须清零 + 报出差额
        chargeData.setMana(cost - 1);
        ManaCharge.Result poor = ManaCharge.apply(chargeData, gateSpell);
        boolean poorOk = poor.outcome() == ManaCharge.Outcome.EXHAUSTED
                && poor.after() == 0 && chargeData.getMana() == 0 && poor.shortfall() == 1;
        checks.add(new Check("charge short by 1 -> exhausted, mana zeroed", poorOk,
                "魔力 " + poor.before() + " → " + poor.after() + "，差 " + poor.shortfall()));

        chargeData.setMana(0);
        ManaCharge.Result broke = ManaCharge.apply(chargeData, gateSpell);
        boolean brokeOk = broke.outcome() == ManaCharge.Outcome.EXHAUSTED
                && broke.after() == 0 && broke.spent() == 0 && broke.shortfall() == cost;
        checks.add(new Check("charge at zero mana -> exhausted, nothing spent", brokeOk,
                "魔力 0 → " + broke.after() + "，差 " + broke.shortfall()));

        // 17. 纯逻辑不能越界（魔力永远不能在 [0, maxMana] 之外）
        boolean bounded = rich.after() >= 0 && poor.after() >= 0 && broke.after() >= 0
                && rich.after() <= chargeData.getMaxMana() && broke.after() <= chargeData.getMaxMana();
        checks.add(new Check("charge never leaves [0, maxMana]", bounded,
                "maxMana=" + chargeData.getMaxMana()));

        // 15. 引擎接线：装了引擎的话，Mixin 必须真的注入
        //     （dev 环境没引擎时跳过；整合包里这项就是"硬拦截到底生不生效"的判据。
        //      gateChecks 放在说明里 —— 它在整合包里要等玩家真施法过才会 > 0）
        if (com.tnc.tnc.magic.compat.SpellEngineBridge.enginePresent()) {
            // 只"初始化"引擎的 SpellHelper 类，不调用它的逻辑 —— 对真引擎无害，
            // 但能让 dev 桩（它在静态块里探针一次）把 gateChecks 打上去
            try {
                Class.forName("net.spell_engine.internals.SpellHelper", true,
                        MagicStoneSelfTest.class.getClassLoader());
            } catch (Throwable ignored) {
                // 引擎在但类加载不到：下面那项检查会如实报出来
            }
            boolean applied = ManaGate.isMixinApplied();
            int gateRuns = ManaGate.gateChecks();
            checks.add(new Check("engine wiring (mixin applied)", applied,
                    "mixinApplied=" + applied + " gateChecks=" + gateRuns
                            + (applied ? "" : "（false = 注入没成功，硬拦截失效）")));
        }


        // 4b. 领域魔法（§12.F）：七个元素各一条，且 NBT 往返不丢
        boolean specialsOk = SpellCatalog.specials().size() == Element.values().length;
        StringBuilder specialNames = new StringBuilder();
        for (SpellCatalog.Special special : SpellCatalog.specials()) {
            specialNames.append(special.element().cn()).append(' ');
        }
        checks.add(new Check("special magic: one domain per element",
                specialsOk, "count=" + SpellCatalog.specials().size()
                        + " elements=" + Element.values().length
                        + " [" + specialNames.toString().trim() + "]"));

        MagicStoneData specialData = new MagicStoneData();
        specialData.grantSpecial(Element.LIGHTNING);
        specialData.grantSpecial(Element.DARK);
        MagicStoneData reloaded = new MagicStoneData();
        reloaded.deserializeNBT(specialData.serializeNBT());
        boolean specialNbtOk = reloaded.hasSpecial(Element.LIGHTNING)
                && reloaded.hasSpecial(Element.DARK)
                && !reloaded.hasSpecial(Element.FIRE)
                && reloaded.specialElements().size() == 2;
        checks.add(new Check("special magic survives NBT round-trip",
                specialNbtOk, "granted=2 reloaded=" + reloaded.specialElements().size()));
        return checks;
    }

    /** 把结果打到日志（服务器启动时用）。 */
    public static void log(Logger logger, String context, List<Check> checks) {
        int passed = 0;
        for (Check check : checks) {
            if (check.passed()) {
                passed++;
            }
        }
        logger.info("TN-C magic stone self-test [{}]: {}/{} passed", context, passed, checks.size());
        for (Check check : checks) {
            if (check.passed()) {
                logger.info("  [ok]   {} : {}", check.name(), check.detail());
            } else {
                logger.warn("  [FAIL] {} : {}", check.name(), check.detail());
            }
        }
    }
}
