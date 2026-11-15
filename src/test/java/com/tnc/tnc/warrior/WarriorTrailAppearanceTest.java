package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 刀光配色：<b>每把武器都得是不同颜色</b>，而且灌注只改亮芯、不改外壳。
 *
 * <p>这条要求很容易在"加一把新武器时忘了写配色"里悄悄丢掉 —— 忘写就会落到 {@code default}
 * 的默认蓝，看起来像两三把武器共用一种刀光。所以这里把 16 个武器 id 钉死，任何一个撞色都会红。
 */
class WarriorTrailAppearanceTest {

    /** 和 {@link WarriorTrailAppearance#of} 里的 case 一一对应；加武器时两边一起改。 */
    private static final String[] WEAPONS = {
            "yue_bronze_jian", "willow_leaf_dao", "crescent_guandao", "fangtian_ji",
            "red_tassel_spear", "dragon_sword", "frost_katana", "star_rapier",
            "crimson_warblade", "doom_scythe", "earthshaker_hammer", "sky_halberd",
            "greatsword", "hammer", "spear", "wind_relic_spear",
            // 2026-11-14：以前落到默认蓝的那批，现在各有本色
            "ring_pommel_dao", "han_iron_jian", "bronze_dao", "bronze_dagger",
            "bronze_halberd", "bronze_greatsword", "bronze_spear", "monk_spade",
            "iron_jian_mace", "qimei_staff"};

    @Test
    void everyWeaponHasItsOwnShellColour() {
        int fallback = WarriorTrailAppearance.of("no_such_weapon", WarriorWeaponFamily.LONGSWORD, "").shell();
        Set<Integer> shells = new HashSet<>();
        for (String weapon : WEAPONS) {
            int shell = WarriorTrailAppearance.of(weapon, WarriorWeaponFamily.LONGSWORD, "").shell();
            assertNotEquals(fallback, shell,
                    "武器 " + weapon + " 没有自己的配色，落到了默认蓝 #" + Integer.toHexString(fallback));
            shells.add(shell);
        }
        // 少一个：长枪和风遗物长枪是同一把枪的两种外观，故意共用一套配色
        assertEquals(WEAPONS.length - 1, shells.size(),
                "除了 spear / wind_relic_spear 这一对，其余武器不能撞色");
        assertEquals(WarriorTrailAppearance.of("spear", WarriorWeaponFamily.SPEAR, "").shell(),
                WarriorTrailAppearance.of("wind_relic_spear", WarriorWeaponFamily.SPEAR, "").shell(),
                "两把枪共用同一套长枪配色是设计如此");
    }

    @Test
    void unknownWeaponFallsBackButStillHasAColour() {
        WarriorTrailAppearance.Style fallback =
                WarriorTrailAppearance.of("no_such_weapon", WarriorWeaponFamily.GREATSWORD, "");
        assertEquals(0x70B7ED, fallback.shell(), "没有配色的武器落到默认蓝");
        assertTrue(fallback.thickness() > 0, "厚度必须有效");
    }

    @Test
    void infusionOnlyChangesTheCoreNotTheShell() {
        int plain = WarriorTrailAppearance.of("dragon_sword", WarriorWeaponFamily.LONGSWORD, "").shell();
        for (String element : new String[]{"wind", "fire", "water", "ice", "lightning", "earth", "light", "dark"}) {
            WarriorTrailAppearance.Style style =
                    WarriorTrailAppearance.of("dragon_sword", WarriorWeaponFamily.LONGSWORD, element);
            assertEquals(plain, style.shell(), element + " 灌注不该改外壳色（否则所有灌注武器都长一样）");
            assertNotEquals(plain, style.core(), element + " 灌注要改亮芯颜色");
        }
    }

    @Test
    void eachFamilyHasItsOwnThickness() {
        double sword = WarriorTrailAppearance.of("x", WarriorWeaponFamily.LONGSWORD, "").thickness();
        double great = WarriorTrailAppearance.of("x", WarriorWeaponFamily.GREATSWORD, "").thickness();
        double hammer = WarriorTrailAppearance.of("x", WarriorWeaponFamily.HAMMER, "").thickness();
        double spear = WarriorTrailAppearance.of("x", WarriorWeaponFamily.SPEAR, "").thickness();
        assertTrue(hammer > great && great > sword && sword > spear,
                "厚度梯子应该是 锤 > 重剑 > 长剑 > 枪：" + hammer + "/" + great + "/" + sword + "/" + spear);
    }
}
