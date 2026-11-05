package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 对拼定位：哪一招是进攻、哪一招是振刀、哪一招能破振刀，以及克制闭环。
 *
 * <p>规则（用户口述）：攻击链会被振刀架住；防御链里的"破架"专破振刀；
 * 进攻和破架互相中断，谁先造成连招伤害谁赢。
 */
class WarriorComboRoleTest {

    private static WarriorSkill skill(String id) {
        WarriorSkill found = WarriorCombos.byId(id);
        if (found == null) {
            throw new AssertionError("没有这条连招：" + id);
        }
        return found;
    }

    @Test
    void swordChainsSplitIntoOffenseStanceAndBreaker() {
        assertEquals(WarriorComboRole.OFFENSE, skill("sword_combo_1").role(), "破锋式是进攻");
        assertEquals(WarriorComboRole.OFFENSE, skill("sword_combo_2").role(), "旋转斩击是进攻");
        assertEquals(WarriorComboRole.BREAKER, skill("sword_combo_3").role(), "逆鳞斩是破架");
        assertEquals(WarriorComboRole.STANCE, skill("sword_combo_4").role(), "振刀是架势");
    }

    @Test
    void otherWeaponDefenseChainsAreBreakersAndBowsStayNeutral() {
        assertEquals(WarriorComboRole.BREAKER, skill("greatsword_combo_3").role(), "铁壁也是破架");
        assertEquals(WarriorComboRole.BREAKER, skill("hammer_combo_3").role(), "断招也是破架");
        for (String id : new String[]{"bow_combo_1", "bow_combo_2", "bow_combo_3"}) {
            assertEquals(WarriorComboRole.NEUTRAL, skill(id).role(), "弓不参与对拼：" + id);
        }
    }

    @Test
    void everyMeleeWeaponHasAStanceOfItsOwn() {
        // 振刀只是长剑的架势；重剑壁立、战锤沉渊同样是"架住别人"的架势，
        // 这样三把近战武器都凑齐了 进攻 / 架势 / 破架 三种定位。
        assertEquals(WarriorComboRole.STANCE, skill("greatsword_combo_4").role(), "壁立是架势");
        assertEquals(WarriorComboRole.STANCE, skill("hammer_combo_4").role(), "沉渊是架势");
        assertEquals(WarriorComboRole.OFFENSE, skill("greatsword_combo_1").role(), "破阵斩是进攻");
        assertEquals(WarriorComboRole.OFFENSE, skill("greatsword_combo_2").role(), "山崩是进攻");
        assertEquals(WarriorComboRole.OFFENSE, skill("hammer_combo_1").role(), "碎骨是进攻");
        assertEquals(WarriorComboRole.OFFENSE, skill("hammer_combo_2").role(), "震地是进攻");
        // 架势要真的给自己一个窗口，否则"架住"永远触发不了
        assertTrue(skill("greatsword_combo_4").guardTicks() > 0, "壁立要有架势窗口");
        assertTrue(skill("hammer_combo_4").guardTicks() > 0, "沉渊要有架势窗口");
    }

    @Test
    void counterRingIsClosed() {
        // 进攻被振刀架住
        assertEquals(true, WarriorComboRole.OFFENSE.parriedByStance());
        assertEquals(false, WarriorComboRole.BREAKER.parriedByStance());
        assertEquals(false, WarriorComboRole.STANCE.parriedByStance());
        // 破架能破振刀
        assertEquals(true, WarriorComboRole.BREAKER.breaksStance());
        assertEquals(false, WarriorComboRole.STANCE.breaksStance());
        // 进攻 ↔ 破架 互为对手
        assertEquals(WarriorComboRole.BREAKER, WarriorComboRole.OFFENSE.rival());
        assertEquals(WarriorComboRole.OFFENSE, WarriorComboRole.BREAKER.rival());
        assertEquals(WarriorComboRole.NEUTRAL, WarriorComboRole.STANCE.rival());
    }

    @Test
    void everySwordComboAnimatesWithItsOwnClip() {
        assertEquals(WarriorAnimation.SWORD_BREAKER, skill("sword_combo_1").animation());
        assertEquals(WarriorAnimation.SWORD_SPIN, skill("sword_combo_2").animation());
        assertEquals(WarriorAnimation.SWORD_COUNTER, skill("sword_combo_3").animation());
        assertEquals(WarriorAnimation.SWORD_STANCE, skill("sword_combo_4").animation());
        // 时长要和 Blockbench 里的 animation_length 对得上（秒 → tick，四舍五入）
        assertEquals(18, WarriorAnimation.SWORD_BREAKER.durationTicks());
        assertEquals(15, WarriorAnimation.SWORD_SPIN.durationTicks());
        assertEquals(20, WarriorAnimation.SWORD_STANCE.durationTicks());
        assertEquals(25, WarriorAnimation.SWORD_COUNTER.durationTicks());
    }
}
