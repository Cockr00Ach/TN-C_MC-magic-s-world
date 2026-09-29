package com.tnc.tnc.adventure;

/** Pure rules; no vanilla XP, time-of-day or client supplied rewards. */
public final class AdventureRules {
    public static final int MAX_LEVEL = 100;
    public static final long MAX_COINS = 1_000_000_000L;
    public static final long BOARD_PERIOD = 36_000;
    private AdventureRules() {}

    public static long levelCost(int level) { return 50L + 10L * level + (long) level * level; }
    public static long xpAtLevel(int level) {
        long xp = 0;
        for (int l = 1; l < Math.min(MAX_LEVEL, Math.max(1, level)); l++) xp += levelCost(l);
        return xp;
    }
    public static int level(long xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= levelCost(level)) xp -= levelCost(level++);
        return level;
    }
    public static int normalGrade(int rank) { return Math.max(0, Math.min(5, rank)); }
    public static boolean canAccept(int rank, int grade, int active, int higherActive) {
        return active < 3 && grade >= 0 && grade <= normalGrade(rank) + 1
                && (grade <= normalGrade(rank) || higherActive == 0);
    }
    public static int reputation(int reward, int rank, int grade) {
        int gap = normalGrade(rank) - grade;
        return gap >= 2 ? 0 : gap == 1 ? reward / 2 : reward;
    }
    public static String money(long copper) {
        return copper / 10_000 + "金 " + copper / 100 % 100 + "银 " + copper % 100 + "铜";
    }
}
