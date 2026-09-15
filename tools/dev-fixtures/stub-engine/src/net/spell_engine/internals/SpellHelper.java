package net.spell_engine.internals;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.internals.casting.SpellCast;

/**
 * 【开发用桩，不是真引擎！】
 *
 * <p>存在的唯一目的：让 dev 环境里也有一个类名叫
 * {@code net.spell_engine.internals.SpellHelper}、方法签名<b>和真引擎逐字节一致</b>的目标，
 * 好让我们能验证「TN-C 的 Mixin 到底能不能注入成功」。
 *
 * <h2>调用方向必须和真引擎一致（这一点踩过坑）</h2>
 * 真引擎是 {@code javap -c} 读出来的：
 * <pre>
 *   attemptCasting(...)            // 3 参：iconst_1 之后 invokestatic 转调 4 参
 *   attemptCasting(...,boolean)    // 4 参：真正干活的实现
 * </pre>
 * 所以 <b>3 参 → 4 参</b>。桩的第一版写反了（4 参转调 3 参），
 * 结果它验证的是"两个重载都注入"那套旧设计，跟真引擎对不上。
 * 现在桩严格照抄真引擎的方向，才有验证价值：
 * <b>TNC 只注入 4 参，而 3 参的调用者必须一样被拦到</b>。
 *
 * <p>真引擎是 Fabric mod，只在整合包里（经 Sinytra Connector 跑）。
 */
public class SpellHelper {

    /** 调用计数，方便日志里看出走的哪条路（桩自己用）。 */
    public static int callsThroughFourArg = 0;

    static {
        // 类一被初始化就自探针一次：走 3 参入口，验证它确实会汇聚到被注入的 4 参。
        // player 传 null 是有意的 —— ManaGate 会对非 ServerPlayer 放行，只记账不拦。
        attemptCasting(null, null, ResourceLocation.fromNamespaceAndPath("tnc", "spark"));
    }

    /** 3 参：纯转发（和真引擎一致）。 */
    public static SpellCast.Attempt attemptCasting(Player player, ItemStack stack, ResourceLocation spellId) {
        return attemptCasting(player, stack, spellId, true);
    }

    /** 4 参：真正的实现（TNC 的 Mixin 注入的就是这里）。 */
    public static SpellCast.Attempt attemptCasting(Player player, ItemStack stack, ResourceLocation spellId,
                                                   boolean flag) {
        callsThroughFourArg++;
        return SpellCast.Attempt.success();
    }
}
