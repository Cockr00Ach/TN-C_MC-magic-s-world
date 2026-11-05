package com.tnc.tnc.magic;

import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Earth-repainted weapon models used by the earth spells.
 *
 * <p>They are copies of the held weapon models (sword, katana, halberd, scythe, hammer)
 * re-textured with stone palettes and recentred into the 0..16 item space, with their
 * long axis on +Z — the flight axis the projectile renderer expects. Keep the list in
 * sync with {@code tools/gen_earth_weapons.py} and {@code TNDirectSpellModels}.</p>
 */
public final class EarthWeaponModels {
    public static final List<String> MODELS = List.of(
            "earth_wpn_dragon_sword",
            "earth_wpn_frost_katana",
            "earth_wpn_sky_halberd",
            "earth_wpn_doom_scythe",
            "earth_wpn_earthshaker_hammer",
            "earth_wpn_crimson_warblade",
            "earth_wpn_star_rapier");

    /** Length in blocks of the flying weapon; longer than the old 3.4 block spear. */
    public static final float FLIGHT_LENGTH = 5.2F;
    /** Width multiplier of the flying weapon; thicker than the old 0.42 block spear. */
    public static final float FLIGHT_WIDTH = 2.8F;

    private EarthWeaponModels() {}

    public static boolean isWeapon(String model) {
        return model != null && model.startsWith("earth_wpn_");
    }

    public static String pick(RandomSource random) {
        return MODELS.get(random.nextInt(MODELS.size()));
    }
}
