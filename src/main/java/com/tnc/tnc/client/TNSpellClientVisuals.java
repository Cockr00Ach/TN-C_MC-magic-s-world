package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 雷速链的<b>第一人称表现</b>：速度越快，视野越拉 —— 让"快"这件事在屏幕上看得见。
 *
 * <h2>为什么这个最划算</h2>
 * 自身增益法术（SELF）在引擎里没有任何模型/投射物，光靠粒子很难传达"我现在很快"。
 * 而原版本身就用 FOV 表达速度（冲刺时会轻微拉伸视野）✓ ——
 * 我们只要在 Forge 的 {@link ComputeFovModifierEvent} 里再乘一个系数，
 * 不写 mixin、不画 UI，几行代码就有明显手感 ✓。
 *
 * <h2>数值</h2>
 * 取<b>最高的那一档</b>而不是相乘 ✗（否则三样叠加会拉到头晕）：
 * 雷速 +3%、极速雷风 +8%、登神 +5%。基础值来自 {@code event.getFovModifier()}
 * （原版已经把冲刺/弓弩蓄力等算进去了 ✓）。
 *
 * <p>挂在 <b>FORGE 总线 + Dist.CLIENT</b>：这是游戏事件、且只在客户端存在 ✓
 * （挂错总线会静默不生效，项目里踩过 —— 见 MagicStoneKeys 的注释）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNSpellClientVisuals {

    /** 雷速（t1）：轻微 */
    private static final float FOV_LIGHTNING_HASTE = 1.03F;
    /** 极速雷风（t3）：明显 */
    private static final float FOV_LIGHTNING_WIND = 1.08F;
    /** 闪电登神（t5）：中等（它已经有满身电弧了，FOV 不用太夸张） */
    private static final float FOV_LIGHTNING_ASCENSION = 1.05F;

    private TNSpellClientVisuals() {
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        float mult = 1.0F;
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_HASTE, FOV_LIGHTNING_HASTE));
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_WIND, FOV_LIGHTNING_WIND));
        mult = Math.max(mult, factor(player, TNEffects.LIGHTNING_ASCENSION, FOV_LIGHTNING_ASCENSION));
        if (mult != 1.0F) {
            event.setNewFovModifier(event.getFovModifier() * mult);
        }
    }

    /** 有该效果就返回它的系数，否则返回 1（不动原版数值）。 */
    private static float factor(Player player, RegistryObject<MobEffect> effect, float value) {
        return (effect.isPresent() && player.hasEffect(effect.get())) ? value : 1.0F;
    }
}
