package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tnc.tnc.warrior.WarriorCodex.Clause;
import com.tnc.tnc.warrior.WarriorCodex.Entry;
import com.tnc.tnc.warrior.WarriorCodex.Gate;
import com.tnc.tnc.warrior.WarriorCodex.Progress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 长剑连招目录的硬约束。
 *
 * <p>目录是"按键 → 招式"的唯一映射，一旦两条顺序互为前缀/后缀，同一串输入就会
 * 命中两招，玩家永远说不清自己放的是哪个。所以这里把不变量写成测试：
 * id/槽位唯一、顺序互不包含、每条都能在图鉴里找到已实装条目、解锁只认图鉴门槛。
 */
class WarriorCombosTest {

    private static Progress progress(int talent, int points) {
        return new Progress(talent, points, 0, 0, false);
    }

    @Test
    void idsAndSlotsAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> familySlots = new HashSet<>();
        for (WarriorSkill skill : WarriorCombos.all()) {
            assertTrue(ids.add(skill.id()), "重复的连招 id: " + skill.id());
            assertTrue(familySlots.add(skill.family() + "#" + skill.slot()),
                    "同一把武器内重复的槽位: " + skill.family() + " / " + skill.slot());
            assertFalse(skill.sequence().isEmpty(), skill.id() + " 没有输入顺序");
            assertTrue(skill.length() >= 2, skill.id() + " 至少两段，单键不算连招");
        }
    }

    @Test
    void noSequenceIsPrefixOrSuffixOfAnother() {
        for (WarriorWeaponFamily family : WarriorWeaponFamily.values()) {
            List<WarriorSkill> all = WarriorCombos.forFamily(family);
            for (int i = 0; i < all.size(); i++) {
                for (int j = 0; j < all.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    WarriorSkill aSkill = all.get(i);
                    WarriorSkill bSkill = all.get(j);
                    // 同一把武器内不允许"一招是另一招的前缀"：否则玩家分不清打的是哪招。
                    String a = aSkill.sequenceLabel();
                    String b = bSkill.sequenceLabel();
                    assertFalse(a.startsWith(b), a + " 以 " + b + " 开头，同一串输入会命中两招");
                    assertFalse(a.endsWith(b), a + " 以 " + b + " 结尾，同一串输入会命中两招");
                }
            }
        }
    }

    @Test
    void holdComboCarriesHoldMarkersAndGuardWindow() {
        WarriorSkill stance = WarriorCombos.byId("sword_combo_4");
        required(stance);
        assertEquals("sword_combo_4", stance.codexId());
        assertEquals(WarriorChain.DEFENSE, stance.chain());
        assertEquals("EQE", stance.sequenceLabel(), "顺序标签保持纯字母，便于反解");
        assertEquals("EQE+", stance.compactLabel(), "长按段用 + 标出来");
        assertEquals(List.of(false, false, true), stance.holds());
        assertTrue(stance.anyHold());
        assertTrue(stance.guardTicks() > 0, "振刀要给自己一个短时架势");
        assertTrue(stance.interrupt() && stance.staggerTicks() > 0, "振刀要打断并僵直");
        assertEquals(1500, WarriorCombos.temperRequirement(stance));
    }

    private static void required(WarriorSkill skill) {
        assertNotNull(skill, "目录里必须有这条连招");
    }

    @Test
    void everyWeaponFamilyCoversTheRequiredChains() {
        assertTrue(WarriorCombos.forFamily(WarriorWeaponFamily.LONGSWORD).size() >= 3,
                "长剑至少要有突击/攻击/防御三条链");
        for (WarriorWeaponFamily family : WarriorWeaponFamily.values()) {
            List<WarriorSkill> skills = WarriorCombos.forFamily(family);
            if (skills.isEmpty()) {
                continue; // 还没做内容的武器家族允许留空
            }
            Set<WarriorChain> chains = new HashSet<>();
            for (WarriorSkill skill : skills) {
                // 同一条链可以有多招（比如防御链：长连逆鳞斩 + 短连长按振刀）
                chains.add(skill.chain());
                assertNotNull(skill.animation(), skill.id() + " 没有动作");
            }
            assertTrue(chains.contains(WarriorChain.ASSAULT), family + " 缺突击链");
            assertTrue(chains.contains(WarriorChain.ATTACK), family + " 缺攻击链");
            assertTrue(chains.stream().anyMatch(WarriorChain::isDefensive),
                    family + " 必须有防御链或闪避链 —— 保命链是每把武器的必需品");
        }
    }

    @Test
    void chainBehavioursMatchTheirRoles() {
        WarriorSkill assault = WarriorCombos.forFamily(WarriorWeaponFamily.LONGSWORD).stream()
                .filter(skill -> skill.chain() == WarriorChain.ASSAULT).findFirst().orElseThrow();
        assertTrue(assault.displaces(), "长剑的突击链必须带位移");
        assertTrue(assault.dash() >= 1.0 && assault.dash() <= 3.0, "位移太短没有意义、太长会穿模");

        WarriorSkill attack = WarriorCombos.forFamily(WarriorWeaponFamily.LONGSWORD).stream()
                .filter(skill -> skill.chain() == WarriorChain.ATTACK).findFirst().orElseThrow();
        assertTrue(attack.damageMultiplier() > assault.damageMultiplier(),
                "攻击链的伤害必须高于突击链，否则玩家没有站桩的理由");

        WarriorSkill defense = WarriorCombos.forFamily(WarriorWeaponFamily.LONGSWORD).stream()
                .filter(skill -> skill.role() == WarriorComboRole.BREAKER).findFirst().orElseThrow();
        assertTrue(defense.interrupt(), "防御链必须能打断施法/连招");
        assertTrue(defense.staggerTicks() > 0, "防御链必须带僵直");
        assertTrue(defense.dash() > 0 && defense.dash() <= 0.5,
                "防御链只往前垫半步：既要能贴上，又不能把目标推走");
    }

    @Test
    void familyDefaultDisplacementMatchesWeaponTraits() {
        assertTrue(WarriorWeaponFamily.LONGSWORD.assaultDisplacement(), "长剑的突击链要位移");
        assertFalse(WarriorWeaponFamily.GREATSWORD.assaultDisplacement(), "重剑用范围换位移");
        assertFalse(WarriorWeaponFamily.HAMMER.assaultDisplacement(), "锤子用范围换位移");
        assertTrue(WarriorWeaponFamily.BOW.assaultDisplacement(), "弓的突击链是闪避");
        assertTrue(WarriorWeaponFamily.SPEAR.assaultDisplacement(), "长枪靠突进贴身，要位移");
    }

    @Test
    void everyComboHasAnImplementedCodexEntry() {
        for (WarriorSkill skill : WarriorCombos.all()) {
            Entry entry = WarriorCodex.byId(skill.codexId());
            assertNotNull(entry, skill.id() + " 在图鉴里找不到条目 " + skill.codexId());
            assertTrue(entry.implemented(), skill.id() + " 的图鉴条目还标着规划中");
            assertEquals(expectedCodexWeapon(skill.family()), entry.weapon(),
                    skill.id() + " 挂错了武器页签");
            assertNotNull(WarriorCombos.temperRequirement(skill), skill.id() + " 没有磨砺门槛");
        }
    }

    /** 家族 → 图鉴页签：重剑/锤挂在"重武器"（AXE）页，弓挂在"弓"页。 */
    private static WarriorCodex.Weapon expectedCodexWeapon(WarriorWeaponFamily family) {
        return switch (family) {
            case LONGSWORD -> WarriorCodex.Weapon.SWORD;
            case GREATSWORD, HAMMER -> WarriorCodex.Weapon.AXE;
            case BOW -> WarriorCodex.Weapon.BOW;
            case SPEAR -> WarriorCodex.Weapon.POLEARM;
        };
    }

    @Test
    void weaponTraitsAreExpressedByTheChains() {
        List<WarriorSkill> greatswords = WarriorCombos.forFamily(WarriorWeaponFamily.GREATSWORD);
        List<WarriorSkill> hammers = WarriorCombos.forFamily(WarriorWeaponFamily.HAMMER);
        List<WarriorSkill> bows = WarriorCombos.forFamily(WarriorWeaponFamily.BOW);
        assertEquals(6, greatswords.size(), "重剑要三条链 + 架势 + 两招扩写");
        assertEquals(6, hammers.size(), "战锤要三条链 + 架势 + 两招扩写");
        assertEquals(6, bows.size(), "长弓要三条链 + 三招扩写");
        assertEquals(5, WarriorCombos.forFamily(WarriorWeaponFamily.SPEAR).size(), "长枪要四条链 + 一条架势");

        // 重剑/锤的突击链不做位移，用范围换突进
        assertTrue(greatswords.stream().filter(skill -> skill.chain() == WarriorChain.ASSAULT)
                .allMatch(skill -> skill.dash() <= 1.0), "重剑的突击链位移要小，主要靠范围");
        assertTrue(hammers.stream().filter(skill -> skill.chain() == WarriorChain.ASSAULT)
                .allMatch(skill -> skill.dash() <= 1.0), "战锤的突击链位移要小，主要靠范围");

        // 弓：攻击/突击/闪避都是远程，闪避带无敌+后撤
        assertTrue(bows.stream().filter(s -> s.slot()!=1 && s.slot()!=2).allMatch(WarriorSkill::ranged), "长弓三条链都该是射击");
        WarriorSkill evade = bows.stream().filter(skill -> skill.chain() == WarriorChain.EVADE)
                .findFirst().orElseThrow();
        assertTrue(evade.evades() && evade.backsteps(), "长弓的闪避链要无敌 + 后撤");
        assertTrue(bows.stream().filter(skill -> skill.chain() == WarriorChain.ASSAULT)
                .findFirst().orElseThrow().displaces(), "长弓的突击链要向前突进");
    }

    @Test
    void everyComboMovesThePlayer() {
        for (WarriorSkill skill : WarriorCombos.all()) {
            assertTrue(skill.slot()<2 || skill.dash() > 0 || skill.backstep() > 0,
                    skill.id() + " 没有位移：用户要求每一招都得让人动起来");
        }
    }

    @Test
    void unlockFollowsCodexGatesOnly() {
        WarriorSkill opener = WarriorCombos.byId("sword_combo_1");
        assertNotNull(opener);
        assertEquals(200, WarriorCombos.temperRequirement(opener));
        assertFalse(WarriorCombos.unlocked(opener, progress(3, 199)), "差 1 点磨砺也不能解锁");
        assertTrue(WarriorCombos.unlocked(opener, progress(3, 200)));

        WarriorSkill spin = WarriorCombos.byId("sword_combo_2");
        assertEquals(1000, WarriorCombos.temperRequirement(spin));
        assertFalse(WarriorCombos.unlocked(spin, progress(3, 999)));
        assertTrue(WarriorCombos.unlocked(spin, progress(3, 1000)));

        // 没有天赋的空白角色（0 磨砺）任何连招都不该开放
        assertFalse(WarriorCombos.unlocked(opener, Progress.EMPTY));
        assertTrue(WarriorCombos.unlocked(WarriorCombos.byId("sword_combo_1"), progress(1, 200)));
    }

    @Test
    void codexGateValueMatchesHelper() {
        for (WarriorSkill skill : WarriorCombos.all()) {
            Entry entry = WarriorCodex.byId(skill.codexId());
            int gate = -1;
            for (Clause clause : entry.gates()) {
                if (clause.gate() == Gate.TEMPER) {
                    gate = clause.value();
                }
            }
            assertEquals(gate, WarriorCombos.temperRequirement(skill),
                    skill.id() + " 的磨砺门槛与图鉴条件不一致");
        }
    }

    @Test
    void slotsFitTheTwoPageSpellLayout() {
        for (WarriorSkill skill : WarriorCombos.all()) {
            assertTrue(skill.slot() >= 0 && skill.slot() < WarriorSkill.LOADOUT_SLOTS,
                    skill.id() + " 的槽位越界");
            assertEquals(0, skill.page(), "三条起手连招都放在第一页");
            assertEquals(skill.slot(), skill.column());
        }
        List<WarriorSkill> page0 = WarriorCombos.page(0);
        assertEquals(9, page0.size());
        assertEquals("sword_combo_1", page0.get(0).id());
        assertEquals("sword_combo_4", page0.get(1).id());
        assertEquals("sword_combo_3", page0.get(2).id());
        assertEquals("sword_combo_2", page0.get(3).id());
        assertEquals("sword_combo_5", page0.get(4).id());
        assertEquals("sword_combo_6", page0.get(5).id());
        assertNull(page0.get(6));
        for (WarriorSkill slot : WarriorCombos.page(1)) {
            assertNull(slot, "第二页暂时没有长剑连招");
        }
        assertEquals(WarriorCombos.byId("sword_combo_4"), WarriorCombos.bySlot(1));
    }

    @Test
    void lookupsRoundTrip() {
        for (WarriorSkill skill : WarriorCombos.all()) {
            assertEquals(skill, WarriorCombos.byId(skill.id()));
            assertEquals(skill, WarriorCombos.byCodexId(skill.codexId()));
            assertEquals(skill, WarriorCombos.bySlot(skill.family(), skill.slot()));
        }
        assertNull(WarriorCombos.byId("nope"));
        assertNull(WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD, 17));
    }

    @Test
    void sequenceLabelsAreReadableAndParseBack() {
        for (WarriorSkill skill : WarriorCombos.all()) {
            String label = skill.sequenceLabel();
            assertEquals(skill.sequence(), WarriorMove.parse(label),
                    skill.id() + " 的界面连招串必须能反解回同一个顺序");
            assertTrue(label.matches("[QEZ]+"), "连招串只该由 Q/E/Z 组成：" + label);
        }
        assertEquals("QZE", WarriorCombos.byId("sword_combo_1").sequenceLabel());
        assertEquals("QEZ", WarriorCombos.byId("sword_combo_2").sequenceLabel());
        assertEquals("EQZ", WarriorCombos.byId("sword_combo_3").sequenceLabel());
    }
}
