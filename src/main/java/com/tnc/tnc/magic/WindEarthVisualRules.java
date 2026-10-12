package com.tnc.tnc.magic;
import java.util.Map;

/** Presentation only: original cloud radius, duration, and all 35 chain identities. */
public final class WindEarthVisualRules {
    public static final int WIND_FLIGHT=0, WIND_SUMMON=1, WIND_GALE=2, EARTH_SHOT=3,
            EARTH_MOVE=4, EARTH_MUD=5, EARTH_RING=6;
    public record Spec(int kind, int tier, double radius, int life, boolean followsOwner, String effects) {}
    private static final Map<String, Spec> SPECS=Map.ofEntries(
        Map.entry("all_things_grow", new Spec(6, 4, 2.400D, 400, true, "minecraft:absorption,minecraft:regeneration,minecraft:resistance")),
        Map.entry("earth_ball", new Spec(3, 2, 1.600D, 36, false, "")),
        Map.entry("earth_core_burst", new Spec(5, 5, 16.000D, 245, false, "tnc:gale_slow")),
        Map.entry("earth_flow", new Spec(5, 4, 13.000D, 245, false, "tnc:gale_slow")),
        Map.entry("earth_god_blessing", new Spec(6, 5, 2.650D, 500, true, "minecraft:absorption,minecraft:regeneration,minecraft:resistance")),
        Map.entry("earth_god_spear", new Spec(3, 5, 2.350D, 48, false, "")),
        Map.entry("earth_prison", new Spec(6, 2, 1.900D, 240, true, "minecraft:absorption,minecraft:resistance")),
        Map.entry("earth_rift", new Spec(4, 4, 7.000D, 284, false, "minecraft:slowness")),
        Map.entry("earth_ring", new Spec(6, 1, 1.650D, 200, true, "minecraft:absorption,minecraft:resistance")),
        Map.entry("earth_shot", new Spec(3, 1, 1.350D, 36, false, "")),
        Map.entry("earth_spike", new Spec(4, 2, 4.500D, 204, false, "minecraft:slowness")),
        Map.entry("earth_stir", new Spec(4, 1, 4.000D, 164, false, "minecraft:slowness")),
        Map.entry("gale", new Spec(2, 1, 1.350D, 36, false, "tnc:gale_slow,tnc:gale_haste")),
        Map.entry("giant_gale", new Spec(2, 4, 2.100D, 48, false, "tnc:gale_slow,tnc:gale_haste")),
        Map.entry("great_gale", new Spec(2, 2, 1.600D, 36, false, "tnc:gale_slow,tnc:gale_haste")),
        Map.entry("greater_wind_speed", new Spec(0, 3, 1.500D, 300, true, "tnc:wind_flight,tnc:wind_speed_ii,tnc:wind_power_i")),
        Map.entry("mire", new Spec(5, 3, 11.000D, 245, false, "tnc:gale_slow")),
        Map.entry("mud", new Spec(5, 1, 8.000D, 245, false, "tnc:gale_slow")),
        Map.entry("pit", new Spec(5, 2, 9.000D, 245, false, "tnc:gale_slow")),
        Map.entry("rock_burst", new Spec(4, 3, 5.500D, 244, false, "minecraft:slowness")),
        Map.entry("rock_cannon", new Spec(3, 3, 1.850D, 36, false, "")),
        Map.entry("rock_kingdom", new Spec(6, 3, 2.150D, 300, true, "minecraft:absorption,minecraft:resistance")),
        Map.entry("star_quake", new Spec(4, 5, 9.000D, 324, false, "minecraft:slowness")),
        Map.entry("super_wind_speed", new Spec(0, 4, 1.700D, 600, true, "tnc:wind_speed_iii,tnc:wind_power_ii")),
        Map.entry("ten_thousand_rocks", new Spec(3, 4, 2.100D, 48, false, "")),
        Map.entry("triple_wind_spirit", new Spec(1, 4, 2.400D, 48, false, "tnc:wind_spirit_ii")),
        Map.entry("vast_gale", new Spec(2, 3, 1.850D, 36, false, "tnc:gale_slow,tnc:gale_haste")),
        Map.entry("wind_field", new Spec(0, 1, 1.100D, 100, true, "tnc:wind_flight")),
        Map.entry("wind_god_descent", new Spec(0, 5, 1.900D, 600, true, "tnc:wind_speed_iii,tnc:wind_power_ii,tnc:wind_god")),
        Map.entry("wind_god_gale", new Spec(2, 5, 2.350D, 48, false, "tnc:gale_slow,tnc:gale_haste")),
        Map.entry("wind_orb", new Spec(1, 1, 1.650D, 36, false, "tnc:wind_orb_three")),
        Map.entry("wind_orb_swarm", new Spec(1, 2, 1.900D, 36, false, "tnc:wind_orb_five")),
        Map.entry("wind_speed", new Spec(0, 2, 1.300D, 100, true, "tnc:wind_flight,tnc:wind_speed_i")),
        Map.entry("wind_spirit", new Spec(1, 3, 2.150D, 36, false, "tnc:wind_spirit")),
        Map.entry("wind_spirit_lord", new Spec(1, 5, 2.650D, 48, false, "tnc:wind_spirit_lord"))
    );
    private WindEarthVisualRules() {}
    public static double spiritScale(int tier,int slot) {return tier==5?(slot==0?1.25:.85):1.0;}
    public static Spec find(String spell) {return SPECS.get(spell);}
    public static Map<String, Spec> all() {return SPECS;}
    public static float envelope(double age, int life) {
        double enter=Math.max(0,Math.min(1,age/8));
        double exit=Math.max(0,Math.min(1,(life-age)/16));
        return (float)(enter*enter*(3-2*enter)*exit*exit*(3-2*exit));
    }
}
