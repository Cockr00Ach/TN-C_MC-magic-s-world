package com.tnc.tnc.magic;

import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Earth-repainted weapon models used by the earth spells.
 *
 * <p>They are copies of the held weapon models (swords, dao, katana, spears, halberds,
 * scythe, staff, mace) re-textured with stone palettes and recentred into the 0..16
 * item space, with their long axis on +Z — the flight axis the projectile renderer
 * expects. Keep the list in sync with {@code tools/gen_earth_weapons.py} and
 * {@code TNDirectSpellModels}.</p>
 */
public final class EarthWeaponModels {
    public static final List<String> MODELS = List.of(
            "earth_wpn_dragon_sword",
            "earth_wpn_frost_katana",
            "earth_wpn_sky_halberd",
            "earth_wpn_doom_scythe",
            "earth_wpn_earthshaker_hammer",
            "earth_wpn_crimson_warblade",
            "earth_wpn_star_rapier",
            "earth_wpn_crescent_guandao",
            "earth_wpn_fangtian_ji",
            "earth_wpn_han_iron_jian",
            "earth_wpn_iron_jian_mace",
            "earth_wpn_monk_spade",
            "earth_wpn_qimei_staff",
            "earth_wpn_red_tassel_spear",
            "earth_wpn_ring_pommel_dao",
            "earth_wpn_willow_leaf_dao",
            "earth_wpn_yue_bronze_jian");

    /** Length in blocks of the flying weapon; longer than the old 3.4 block spear. */
    public static final float FLIGHT_LENGTH = 5.2F;
    /** Width multiplier of the flying weapon; thicker than the old 0.42 block spear. */
    public static final float FLIGHT_WIDTH = 2.8F;

    private EarthWeaponModels() {}

    public static boolean isWeapon(String model) {
        return model != null && model.startsWith("earth_wpn_");
    }

    /** Boss variants: same geometry, black-and-gold material, spawned as {@code stone_lancer_earth_wpn_dark_*}. */
    public static boolean isBossWeapon(String model) {
        return model != null && model.startsWith("stone_lancer_earth_wpn_");
    }

    public static String pick(RandomSource random) {
        return MODELS.get(random.nextInt(MODELS.size()));
    }

    /** Returns {@code earth_wpn_dark_<name>}; the boss entity prefixes {@code stone_lancer_}. */
    public static String pickBoss(RandomSource random) {
        return "earth_wpn_dark_" + MODELS.get(random.nextInt(MODELS.size())).substring("earth_wpn_".length());
    }

    /** Full model ids of the black-and-gold copies packed for the Stone Lancer attacks. */
    public static java.util.List<String> bossModels() {
        return MODELS.stream().map(name -> "stone_lancer_earth_wpn_dark_" + name.substring("earth_wpn_".length())).toList();
    }
}
