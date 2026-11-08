package com.tnc.tnc.fish;

/** Pure rules shared by the item, drops and server-side anvil settlement. */
public final class FishSwordRules {
    public static final int LIVING_DURABILITY = 250, METAL_DURABILITY = 750;
    public static final int LIVING_DAMAGE = 5, METAL_DAMAGE = 9;
    public static final float LIVING_SPEED = 2.4f, METAL_SPEED = 1.4f;
    public static final int INGOT_CHANCE = 500, BLOCK_CHANCE = 3000;
    public static final int ROLL_BOUND = 10000;
    private FishSwordRules() {}
    public static int dropChance(int looting) { return 200 + 50 * Math.max(0, Math.min(3, looting)); }
    public static boolean succeeds(int roll, int chance) { return roll >= 0 && roll < chance; }
    public static int convertedDamage(int damage, int oldMax, int newMax) {
        if (oldMax <= 0 || newMax <= 0) throw new IllegalArgumentException("Invalid durability");
        return Math.min(newMax - 1, (int)((long)Math.max(0, damage) * newMax / oldMax));
    }
}
