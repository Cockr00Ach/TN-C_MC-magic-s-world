package com.tnc.tnc.light;

import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.world.entity.player.Player;

/**
 * 谁"永久拥有光翼" ✓ —— 作者 2026-11-07：
 * <i>"升到光法 t5 的玩家可以永久有这个翅膀"</i>。
 *
 * <h2>为什么单独一个类</h2>
 * 翅膀的**来源**原来是唯一的：身上有 {@code tnc:light_wings} 这个 buff ✓
 * （由光耀链 t4+ 的法术发，再由 {@link TNLightChainMechanics#tickGraceFlight} 每 tick 续 ✓）。
 * 现在多了一条**永久**来源 ⇒ 判定被渲染器（客户端 ✓）和自检（纯逻辑 ✓）都要用，
 * 所以抽成这一份，不放进渲染器里 ✗（渲染器那份要被客户端类加载，测试不想碰 ✗）。
 *
 * <h2>规则</h2>
 * <b>光耀链</b>（{@link SpellCatalog.Chain#LIGHT_GRACE}）学到 t5（"天使的悲悯"✓）⇒ 永久翅膀 ✓。
 * 只认光耀链：翅膀本来就是光耀这条链给的 ✓（t4 的"天使降临"起就有 ✓），
 * 把光线／光镜／召唤点满不该白送翅膀 ✗。要放宽就改 {@link #PERMANENT_CHAINS} ✓。
 *
 * <p>⚠️ 只影响**看不看得见翅膀** ✓：飞行仍然要光耀 buff（{@code mayfly} 在 buff 上 ✓），
 * 作者说"永久有这个翅膀"，不是"永久无限飞" —— 真要把飞行也给成永久，改这里一处即可 ✓。
 */
public final class LightWingOwners {

    /** 永久翅膀需要的档位（光耀链 t5 = "天使的悲悯" ✓）。 */
    public static final int PERMANENT_TIER = 5;

    /** 哪几条链点满 t5 算"光法毕业"✓。 */
    private static final SpellCatalog.Chain[] PERMANENT_CHAINS = {
            SpellCatalog.Chain.LIGHT_GRACE,
    };

    private LightWingOwners() {
    }

    /**
     * 这个玩家是不是"永久光翼"✓（不看 buff ✓，纯看学过的光耀档位 ✓）。
     *
     * <p>客户端也调 ✓（{@link MagicStone#getOrNull} 在两端都返回同步过的那份数据 ✓）。
     */
    public static boolean hasPermanentWings(Player player) {
        if (player == null) {
            return false;
        }
        return hasPermanentWings(MagicStone.getOrNull(player));
    }

    /** 纯逻辑版 ✓（给单测用 ✓：{@link MagicStoneData} 不碰任何 Minecraft 运行时 ✓）。 */
    public static boolean hasPermanentWings(MagicStoneData data) {
        if (data == null) {
            return false;
        }
        for (SpellCatalog.Chain chain : PERMANENT_CHAINS) {
            SpellCatalog.Entry top = SpellCatalog.topLearned(data, Element.LIGHT, chain);
            if (top != null && top.tier() >= PERMANENT_TIER) {
                return true;
            }
        }
        return false;
    }
}
