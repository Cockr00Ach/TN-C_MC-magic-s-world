package com.tnc.tnc.light;

import com.tnc.tnc.magic.MagicStoneData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「升到光法 t5 就永久有翅膀」（作者 2026-11-07）的**规则**自检 ✓。
 *
 * <p>为什么值得单独测：这条规则是**看档位**的（{@code SpellCatalog.topLearned}），
 * 档位差一档就白送翅膀 ✗（t4 的"天使降临"也已经有翅膀了 ✓，但只有限时 ✓），
 * 而"升到 t5 之后还看不见翅膀"是最难从代码里一眼看出来的 ✗（要进游戏、把光耀点满、再飞一次 ✗）。
 */
class LightWingOwnersTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tnc", path);
    }

    @Test
    void emptyStoneHasNoWings() {
        assertFalse(LightWingOwners.hasPermanentWings(new MagicStoneData()), "什么都没学就有永久翅膀 ✗");
        assertFalse(LightWingOwners.hasPermanentWings((MagicStoneData) null), "空指针不该炸 ✗");
    }

    @Test
    void t5GraceGivesPermanentWings() {
        MagicStoneData data = new MagicStoneData();
        assertTrue(data.learn(id("angel_mercy")), "学不会 angel_mercy（光耀 t5）✗ —— id 写错了？");
        assertTrue(LightWingOwners.hasPermanentWings(data), "光耀点满 t5 却没有永久翅膀 ✗");
    }

    @Test
    void t4GraceIsNotEnough() {
        MagicStoneData data = new MagicStoneData();
        assertTrue(data.learn(id("angel_descent")), "学不会 angel_descent（光耀 t4）✗");
        assertFalse(LightWingOwners.hasPermanentWings(data), "t4 就白送永久翅膀了 ✗（那 t5 就没意义了）");
        data.learn(id("angel_mercy"));
        assertTrue(LightWingOwners.hasPermanentWings(data), "补上 t5 之后还是没翅膀 ✗");
    }

    @Test
    void otherLightChainsDoNotCount() {
        MagicStoneData data = new MagicStoneData();
        data.learn(id("radiant_barrage"));   // 光线 t5
        data.learn(id("archangel"));         // 召唤 t5
        data.learn(id("light_mirror_t5"));   // 光镜 t5
        assertFalse(LightWingOwners.hasPermanentWings(data),
                "光线/召唤/光镜点满也送翅膀了 ✗ —— 翅膀本来是光耀链给的");
    }
}
