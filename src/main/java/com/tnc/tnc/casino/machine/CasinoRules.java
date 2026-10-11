package com.tnc.tnc.casino.machine;

/** Integer-only rules. Returns include the stake; randomness belongs to the server. */
public final class CasinoRules {
    private CasinoRules() {}
    public static final String[] ROULETTE_BETS = {"红色", "黑色", "单数", "双数", "小号 1–18", "大号 19–36", "绿零"};
    public static final String[] DICE_BETS = {"小 4–10", "大 11–17", "单数（豹子除外）", "双数（豹子除外）", "豹子"};
    public static final String[] CARD_BETS = {"玩家牌大", "庄家牌大"};
    public static final int[] WHEEL_MULTIPLIERS = {0, 1, 0, 2, 0, 1, 0, 4, 0, 2, 0, 1};

    public static boolean red(int number) {
        return switch (number) {
            case 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36 -> true;
            default -> false;
        };
    }

    public static int roulette(int number, int bet, int stake) {
        if (number < 0 || number > 36 || stake <= 0) return 0;
        if (bet == 6) return number == 0 ? stake * 36 : 0;
        if (number == 0) return 0;
        boolean won = switch (bet) {
            case 0 -> red(number);
            case 1 -> !red(number);
            case 2 -> number % 2 == 1;
            case 3 -> number % 2 == 0;
            case 4 -> number <= 18;
            case 5 -> number >= 19;
            default -> false;
        };
        return won ? stake * 2 : 0;
    }

    public static int dice(int a, int b, int c, int bet, int stake) {
        if (a < 1 || a > 6 || b < 1 || b > 6 || c < 1 || c > 6 || stake <= 0) return 0;
        boolean triple = a == b && b == c;
        if (bet == 4) return triple ? stake * 24 : 0;
        if (triple) return 0;
        int sum = a + b + c;
        boolean won = switch (bet) {
            case 0 -> sum >= 4 && sum <= 10;
            case 1 -> sum >= 11 && sum <= 17;
            case 2 -> sum % 2 == 1;
            case 3 -> sum % 2 == 0;
            default -> false;
        };
        return won ? stake * 2 : 0;
    }

    public static int cards(int player, int dealer, int bet, int stake) {
        if (player < 1 || player > 13 || dealer < 1 || dealer > 13 || stake <= 0 || bet < 0 || bet > 1) return 0;
        if (player == dealer) return stake;
        return ((bet == 0) == (player > dealer)) ? stake * 2 : 0;
    }

    public static int slots(int a, int b, int c, int stake) {
        if (a < 0 || a >= 6 || b < 0 || b >= 6 || c < 0 || c >= 6 || stake <= 0) return 0;
        if (a == b && b == c) return stake * new int[]{3,4,6,10,20,50}[a];
        return a == b || a == c || b == c ? stake : 0;
    }
}
