package com.tnc.tnc.magic;

/**
 * 「法术真的放出去之后，魔力怎么变」—— 纯逻辑，<b>不引用任何 SpellEngine 类</b>。
 *
 * <p>和 {@link ManaGate} 一样的分工：引擎适配层（{@code SpellEngineManaHook.Impl}）只负责
 * "听懂引擎的事件、发提示、同步界面"，而<b>数值怎么算放在这里</b>。
 *
 * <h2>为什么非要拆出来</h2>
 * 拆之前，这段算术写在直接引用 SpellEngine 类的内部类里 —— 于是 dev 环境
 * （没有引擎）**一行都跑不到**，只能等整合包实测。拆出来之后它是纯函数，
 * 启动自检就能把每种边界都跑一遍：够扣 / 刚好够 / 差一点 / 一点都没有。
 *
 * <p>这也是这个工程一贯的做法：能变成纯逻辑的就别留在适配层里
 * （见 {@code ManaGate} 与 {@link MagicStoneLearning}）。
 */
public final class ManaCharge {

    /** 放出去之后发生了什么。 */
    public enum Outcome {
        /** 魔力够，按法术消耗扣掉了。 */
        DEDUCTED,
        /** 魔力不够，硬放：魔力清零 + 力竭惩罚。 */
        EXHAUSTED
    }

    /**
     * @param outcome   结果
     * @param before    施法前魔力
     * @param after     施法后魔力
     * @param spent     实际扣掉多少（力竭时 = 把剩下的全扣光）
     * @param shortfall 差多少（够的时候是 0）
     */
    public record Result(Outcome outcome, int before, int after, int spent, int shortfall) {

        public boolean exhausted() {
            return outcome == Outcome.EXHAUSTED;
        }
    }

    private ManaCharge() {
    }

    /** 够不够放这一发（CHANNEL 阶段用来提前警告）。 */
    public static boolean canAfford(MagicStoneData data, SpellCatalog.Entry entry) {
        return data.getMana() >= entry.manaCostFor(data.getMaxMana());
    }

    /**
     * 把一次成功施法的魔力变化应用到玩家数据上。
     *
     * <p>注意这里<b>假设法术已经放出去了</b> —— 拦不拦是 {@link ManaGate} 的事。
     * 硬拦截生效时魔力不够根本走不到这里，所以 {@link Outcome#EXHAUSTED} 是
     * **硬拦截失效时的兜底**，正常情况不该出现。
     *
     * @return 这次变化的明细（调用方拿去发提示）
     */
    public static Result apply(MagicStoneData data, SpellCatalog.Entry entry) {
        // 消耗随上限等比放大 —— 和 ManaGate 的判定必须用同一个口径，
        // 否则会出现"拦截说够、扣费说不够"或者反过来
        int cost = entry.manaCostFor(data.getMaxMana());
        int before = data.getMana();

        if (before >= cost) {
            // 上面刚判过够，spendMana 必然成功；失败也只会是 cost<0 的畸形配置，那就不扣
            int spent = data.spendMana(cost) ? cost : 0;
            return new Result(Outcome.DEDUCTED, before, data.getMana(), spent, 0);
        }

        // 不够还想放：把剩下的全扣光（力竭）
        int shortfall = cost - before;
        data.setMana(0);
        return new Result(Outcome.EXHAUSTED, before, data.getMana(), before, shortfall);
    }

    /** 给玩家看的一句话（不含颜色码，颜色由调用方加）。 */
    public static String describe(Result result, SpellCatalog.Entry entry) {
        if (result.exhausted()) {
            return "魔力不足（差 " + result.shortfall() + "），勉强施法导致力竭";
        }
        return "魔力 -" + result.spent();
    }
}
