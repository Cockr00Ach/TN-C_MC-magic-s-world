package com.tnc.tnc.magic.compat;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;

/**
 * 「直接放一个 TN-C 法术」—— {@code /tnc cast <法术>} 的背后实现。
 *
 * <h2>为什么需要这个命令</h2>
 * 正常情况下施法要：解锁 → 拿到法术书 → 选中书 → 按对键（槽位 1 是右键，2-9 是数字键）。
 * 环节多，而且作者的雷系法术书里同时有<b>他们自己的 5 个法术</b>（池子里一共 10 个），
 * 随手一按很可能放到的是<b>别人的法术</b> —— 那种情况下 TN-C 故意不扣魔力（只管自己的法术），
 * 于是看起来就像"魔力没扣"。
 *
 * <p>所以测试需要一个<b>不依赖书和键位</b>的入口：这个命令直接调引擎的
 * {@code SpellHelper.performSpell}，走的是和真实施法完全相同的路径
 * （它内部同样会先调 {@code attemptCasting}，所以<b>硬拦截照样生效</b>）。
 *
 * <p>引擎类只在 {@link Impl} 里引用，没引擎时不会被执行到。
 */
public final class SpellEngineCaster {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一次直接施法的结果，给命令拿去显示。 */
    public record Result(boolean enginePresent, int manaBefore, int manaAfter, String error) {

        public boolean manaChanged() {
            return manaAfter != manaBefore;
        }

        public int spent() {
            return manaBefore - manaAfter;
        }
    }

    private SpellEngineCaster() {
    }

    /**
     * 直接施放一个法术（服务端）。
     *
     * <p>不检查"解锁没解锁"—— 那是 {@code ManaGate} 在引擎内部自己会拒的事，
     * 正好可以用来验证硬拦截。
     */
    public static Result cast(ServerPlayer player, ResourceLocation spellId) {
        MagicStoneData data = MagicStone.getOrNull(player);
        int before = data == null ? -1 : data.getMana();
        if (!SpellEngineBridge.enginePresent()) {
            return new Result(false, before, before, "没有装 SpellEngine");
        }
        try {
            Impl.perform(player, spellId);
        } catch (Throwable error) {
            LOGGER.warn("TN-C: /tnc cast failed for {}", spellId, error);
            return new Result(true, before, data == null ? -1 : data.getMana(), error.toString());
        }
        return new Result(true, before, data == null ? -1 : data.getMana(), null);
    }

    // ------------------------------------------------------------------
    //  真正碰引擎类的部分
    // ------------------------------------------------------------------

    private static final class Impl {

        static void perform(ServerPlayer player, ResourceLocation spellId) {
            // 走引擎真正的施放入口。它在内部会：
            //   1) 先调 attemptCasting(...)  —— 我们的硬拦截就在那里，魔力不够/没解锁会直接 return
            //   2) 扣弹药、算命中、放粒子音效、上冷却
            //   3) 最后触发 CombatEvents.SPELL_CAST —— 我们的魔力钩子在那里扣魔力
            // 所以这一条命令能一次性验完"硬拦截"和"扣魔力"两件事。
            net.spell_engine.internals.SpellHelper.performSpell(
                    player.level(),
                    player,
                    spellId,
                    List.of(),
                    net.spell_engine.internals.casting.SpellCast.Action.RELEASE,
                    1.0F);
        }
    }
}
