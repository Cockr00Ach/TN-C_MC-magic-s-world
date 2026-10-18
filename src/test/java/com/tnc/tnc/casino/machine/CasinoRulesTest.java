package com.tnc.tnc.casino.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CasinoRulesTest {
    @Test void rouletteEveryOrdinaryBetHasEighteenWinnersAndZeroAlwaysLoses() {
        int red=0;
        for(int n=1;n<=36;n++) if(CasinoRules.red(n)) red++;
        assertEquals(18,red);
        for(int bet=0;bet<6;bet++) {
            int won=0,total=0;
            for(int n=0;n<=36;n++) { int pay=CasinoRules.roulette(n,bet,1);if(pay>0)won++;total+=pay; }
            assertEquals(18,won);assertEquals(36,total);assertEquals(0,CasinoRules.roulette(0,bet,1));
        }
        assertEquals(36,CasinoRules.roulette(0,6,1));
        assertEquals(0,CasinoRules.roulette(1,6,1));
    }
    @Test void diceExhaustiveProbabilitiesAndTriplesNeverWinOrdinaryBets() {
        int[] totals=new int[5];
        for(int a=1;a<=6;a++)for(int b=1;b<=6;b++)for(int c=1;c<=6;c++)
            for(int bet=0;bet<5;bet++) totals[bet]+=CasinoRules.dice(a,b,c,bet,1);
        assertArrayEquals(new int[]{210,210,210,210,144},totals);
        for(int a=1;a<=6;a++)for(int bet=0;bet<4;bet++)assertEquals(0,CasinoRules.dice(a,a,a,bet,1));
    }
    @Test void cardsUseThirteenRanksTiesRefundAndOppositeBetsAreSymmetric() {
        int[] sums={0,0};
        for(int a=1;a<=13;a++)for(int b=1;b<=13;b++)for(int bet=0;bet<2;bet++)sums[bet]+=CasinoRules.cards(a,b,bet,1);
        assertArrayEquals(new int[]{169,169},sums);
        assertEquals(2,CasinoRules.cards(13,1,0,1));assertEquals(0,CasinoRules.cards(1,13,0,1));
        assertEquals(1,CasinoRules.cards(7,7,0,1));
    }
    @Test void wheelAndSlotsHaveExplicitStableReturnBudgets() {
        assertEquals(12,CasinoRules.WHEEL_MULTIPLIERS.length);
        assertEquals(11,java.util.Arrays.stream(CasinoRules.WHEEL_MULTIPLIERS).sum());
        int total=0;
        for(int a=0;a<6;a++)for(int b=0;b<6;b++)for(int c=0;c<6;c++)total+=CasinoRules.slots(a,b,c,1);
        assertEquals(183,total); // 90 pairs + 93 triple returns, among 216 equiprobable outcomes.
        assertEquals(50,CasinoRules.slots(5,5,5,1));
    }
    @Test void malformedResultsAndEmptyStakesDoNotCreateCoins() {
        assertEquals(0,CasinoRules.roulette(-1,0,1));assertEquals(0,CasinoRules.roulette(37,0,1));
        assertEquals(0,CasinoRules.dice(0,2,3,0,1));assertEquals(0,CasinoRules.cards(14,1,0,1));
        assertEquals(0,CasinoRules.slots(6,6,6,1));assertEquals(0,CasinoRules.slots(1,1,1,0));
    }
}
