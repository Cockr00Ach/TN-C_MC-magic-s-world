package com.tnc.tnc.warrior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrainingGroundRulesTest {
    @Test
    void fourPadsAreSeparatedAndInsideTheDeck() {
        int cx = 100, cz = 200;
        for (int pad = 0; pad < TrainingGroundRules.PAD_COUNT; pad++) {
            int px = TrainingGroundRules.padX(pad, cx), pz = TrainingGroundRules.padZ(pad, cz);
            assertTrue(TrainingGroundRules.padContains(pad, cx, cz, px + 0.5, pz + 0.5),
                "pad centre belongs to its own pad");
            assertTrue(Math.abs(px - cx) + TrainingGroundRules.PAD_HALF <= TrainingGroundRules.HALF,
                "pad fits inside the deck");
            for (int other = pad + 1; other < TrainingGroundRules.PAD_COUNT; other++) {
                int ox = TrainingGroundRules.padX(other, cx), oz = TrainingGroundRules.padZ(other, cz);
                assertTrue(Math.abs(px - ox) > TrainingGroundRules.PAD_HALF * 2
                        || Math.abs(pz - oz) > TrainingGroundRules.PAD_HALF * 2,
                    "pads never overlap");
                assertFalse(TrainingGroundRules.padContains(pad, cx, cz, ox + 0.5, oz + 0.5),
                    "a point inside one pad is not inside another");
            }
        }
    }

    @Test
    void deckBoundsRejectTheVoidAndTheSky() {
        int cx = 100, cz = 200, deck = 120;
        assertTrue(TrainingGroundRules.contains(cx, deck, cz, cx + 0.5, deck + 1, cz + 0.5));
        assertTrue(TrainingGroundRules.contains(cx, deck, cz, cx + TrainingGroundRules.HALF, deck + 1, cz + 0.5));
        assertFalse(TrainingGroundRules.contains(cx, deck, cz, cx + TrainingGroundRules.HALF + 3, deck + 1, cz + 0.5));
        assertFalse(TrainingGroundRules.contains(cx, deck, cz, cx + 0.5, deck - 20, cz + 0.5));
        assertFalse(TrainingGroundRules.contains(cx, deck, cz, cx + 0.5, deck + 40, cz + 0.5));
    }

    @Test
    void invalidPositionsCannotEscapeTheDeck() {
        int cx = 100, cz = 200, deck = 120;
        assertFalse(TrainingGroundRules.contains(cx, deck, cz, Double.NaN, deck + 1, cz + 0.5));
        assertFalse(TrainingGroundRules.contains(cx, deck, cz, cx + 0.5, deck + 1, Double.NEGATIVE_INFINITY));
        assertFalse(TrainingGroundRules.contains(cx, deck, cz, cx + 0.5, Double.NaN, cz + 0.5));
    }
}
