package com.tnc.tnc.boss;

/** Frequent giant water-orb shots alternate with three less frequent, fully telegraphed special spells. */
public final class LeviathanSpellRules {
    public static final String BASIC = "leviathan_water_orb";
    private static final String[] SPECIALS = {"dragon_roar", "sea_god_crypt", "dragon_ruin"};
    private LeviathanSpellRules() {}

    public static String next(int basicCasts, int specialCooldown, int specialIndex) {
        return basicCasts>=3 && specialCooldown<=0
                ? SPECIALS[Math.floorMod(specialIndex,SPECIALS.length)] : BASIC;
    }

    public static int windup(String spell, boolean phaseTwo) {
        int ticks=switch(spell) { case "dragon_ruin" -> 36; case "sea_god_crypt" -> 28;
            case "dragon_roar" -> 20; default -> 14; };
        return phaseTwo?Math.max(10,ticks-4):ticks;
    }

    public static int recovery(boolean phaseTwo) { return phaseTwo?22:30; }
    public static int specialCooldown(String spell, boolean phaseTwo) {
        return spell.equals("dragon_ruin")?300:phaseTwo?220:260;
    }
}
