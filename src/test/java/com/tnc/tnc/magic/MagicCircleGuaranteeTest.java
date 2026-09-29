package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「雷系三条链的 t4 / t5 一定有魔法阵」这条规则的守卫测试 ✓
 *
 * <p>作者 2026-09-29 原话：「保证 t4 和 t5 施法的时候会有魔法阵，雷的三条链」。
 *
 * <p>为什么要有这个测试：这条规则以前是**逐个法术 id 写 if** 的 ✗ ——
 * 主链 t4/t5 有、雷球 t4/t5 有，而**雷速链 t4「闪电降低冷却」/ t5「闪电登神」根本没有** ✗，
 * 而且这种「少一张」在游戏里不会报错、只会「看着少点什么」，很容易再漏 ✗。
 * 现在改成「链 ＋ 档位」的规则（{@link TnSpellMechanics#needsGuaranteedCircle}），
 * 这个测试把**三条链各两张、一共六张**钉死：谁把规则改坏、或者给这三条链加了新的 t4/t5
 * 却绕开了规则，这里立刻红 ✓。
 *
 * <p>纯规则测试：不碰引擎、不碰 Minecraft（和其它 magic 包的测试一样 ✓）。
 */
class MagicCircleGuaranteeTest {

    /** 雷系三条链（主链 / 雷球 / 雷速）✓ */
    private static final SpellCatalog.Chain[] LIGHTNING_CHAINS = {
            SpellCatalog.Chain.CORE, SpellCatalog.Chain.ORB, SpellCatalog.Chain.SPEED
    };

    private static List<SpellCatalog.Entry> entriesOf(SpellCatalog.Chain chain) {
        List<SpellCatalog.Entry> list = new ArrayList<>();
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            if (entry.chain() == chain) {
                list.add(entry);
            }
        }
        return list;
    }

    @Test
    void everyLightningChainHasBothTierFourAndTierFive() {
        for (SpellCatalog.Chain chain : LIGHTNING_CHAINS) {
            List<SpellCatalog.Entry> chainEntries = entriesOf(chain);
            assertTrue(chainEntries.stream().anyMatch(e -> e.tier() == 4),
                    chain + " 缺少 t4（这条链没有 t4 的话，「保证阵」就无从谈起）");
            assertTrue(chainEntries.stream().anyMatch(e -> e.tier() == 5),
                    chain + " 缺少 t5");
        }
    }

    @Test
    void allSixHighTierLightningSpellsGetTheGuaranteedCircle() {
        int guarded = 0;
        for (SpellCatalog.Chain chain : LIGHTNING_CHAINS) {
            for (SpellCatalog.Entry entry : entriesOf(chain)) {
                if (entry.tier() < 4) {
                    continue;
                }
                assertTrue(TnSpellMechanics.needsGuaranteedCircle(entry, false),
                        entry.id() + "（" + chain + " t" + entry.tier()
                                + "）没有拿到保证阵 —— 这正是「雷速 t4/t5 少一张阵」那次的形状");
                guarded++;
            }
        }
        assertEquals(6, guarded, "雷系三条链的 t4/t5 一共应该是 6 个");
    }

    @Test
    void legacySpellsNeverGetASecondCircle() {
        // 自己已经铺过阵的那几个（雷暴 / 天雷 / 超级无敌大雷球 / 神在投篮）传 alreadySpawned = true
        for (SpellCatalog.Chain chain : LIGHTNING_CHAINS) {
            for (SpellCatalog.Entry entry : entriesOf(chain)) {
                if (entry.tier() >= 4) {
                    assertFalse(TnSpellMechanics.needsGuaranteedCircle(entry, true),
                            entry.id() + " 会铺第二张阵（同一个法术脚下两张阵 ✗）");
                }
            }
        }
    }

    @Test
    void lowTiersAndOtherChainsAreLeftAlone() {
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            boolean lightning = false;
            for (SpellCatalog.Chain chain : LIGHTNING_CHAINS) {
                if (entry.chain() == chain) {
                    lightning = true;
                    break;
                }
            }
            if (!lightning) {
                assertFalse(TnSpellMechanics.needsGuaranteedCircle(entry, false),
                        entry.id() + " 不是雷系三条链，不该拿到保证阵");
            } else if (entry.tier() < 4) {
                assertFalse(TnSpellMechanics.needsGuaranteedCircle(entry, false),
                        entry.id() + " 是 t" + entry.tier()
                                + "，低档不做阵（作者 2026-09-27：低档也铺 ＝「随便哪个魔法都有了」✗）");
            }
        }
        assertFalse(TnSpellMechanics.needsGuaranteedCircle(null, false), "null 条目不该抛异常");
    }
}
