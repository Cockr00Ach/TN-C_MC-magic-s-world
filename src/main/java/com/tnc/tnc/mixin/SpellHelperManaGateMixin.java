package com.tnc.tnc.mixin;

import com.tnc.tnc.magic.ManaGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「没魔力就放不出来」—— 注入 SpellEngine 自己的施法前判定。
 *
 * <h2>注入哪个方法（用 javap 对着真引擎核过，不是猜的）</h2>
 * {@code SpellHelper.attemptCasting} 有两个重载，字节码是这样的：
 *
 * <pre>
 *   attemptCasting(Player, ItemStack, ResourceLocation)          // 3 参：纯转发
 *       iconst_1
 *       invokestatic attemptCasting(...,Z)                       //   → 转调 4 参
 *
 *   attemptCasting(Player, ItemStack, ResourceLocation, boolean) // 4 参：真正的实现
 *       checkcast SpellCasterEntity
 *       SpellRegistry.getSpell(id)                               //   查注册表
 *       getCooldownManager().isCoolingDown(id)                   //   查冷却
 *       ammoForSpell(...)                                        //   查弹药
 *       Attempt.success() / .none() / .failOnCooldown(...)       //   给出结论
 * </pre>
 *
 * <p>所以<b>所有调用路径最终都汇聚到 4 参那个方法</b>：谁调 3 参，也会走到 4 参。
 * 只在这一个点注入就够，不需要两个都注入。
 *
 * <h2>为什么"少注入一个点"很重要</h2>
 * 本配置是 {@code required: false} + {@code defaultRequire: 1}：<b>任何一个注入点没对上，
 * Mixin 就会抛错，整份配置静默失效</b>（游戏照常启动，只是魔力再也拦不住施法）。
 * 也就是说每个注入点都是一条"静默失效"的路径 —— 注入点越少越安全，能覆盖全部调用路径就更没必要多留。
 *
 * <p>{@code remap = false}：目标类和方法都是引擎自己的名字（不是 MC 的），不需要重映射，
 * 也不需要 refmap —— 这也是为什么这个 Mixin 在 Connector 环境下是安全的。
 */
@Mixin(value = net.spell_engine.internals.SpellHelper.class, remap = false)
public class SpellHelperManaGateMixin {

    /*
     * Mixin 加载这个类（= 注入真的发生了）时才会执行 —— 拿它当"硬拦截已生效"的信号灯，
     * 游戏里用 /tnc engine 查。但它只证明"类被加载"，不证明"注入器对上了"；
     * 真正的硬证据是 ManaGate.gateChecks 涨了（见 /tnc gatetest）。
     */
    static {
        ManaGate.markMixinApplied();
    }

    /** 4 参重载：真正干活的实现，也是全部调用路径的汇聚点。 */
    private static final String TARGET =
            "attemptCasting(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/resources/ResourceLocation;Z)Lnet/spell_engine/internals/casting/SpellCast$Attempt;";

    @Inject(method = TARGET, at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnc$manaGate(Player player, ItemStack stack, ResourceLocation spellId, boolean checkAmmo,
                                     CallbackInfoReturnable<net.spell_engine.internals.casting.SpellCast.Attempt> cir) {
        // 在 HEAD 处直接给出"这次施法不成立"的结论，引擎自己的实现根本不会跑 ——
        // 这就是"施法前判定"的真拦截（而不是放出去之后再补扣）。
        if (ManaGate.shouldBlock(player, spellId)) {
            cir.setReturnValue(net.spell_engine.internals.casting.SpellCast.Attempt.none());
        }
    }
}
