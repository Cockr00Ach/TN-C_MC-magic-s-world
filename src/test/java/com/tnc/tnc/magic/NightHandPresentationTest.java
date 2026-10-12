package com.tnc.tnc.magic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NightHandPresentationTest {
    @Test void completedGatePrecedesReleaseAndHasAnObservableHold() {
        for (int age = 0; age <= NightHandPresentation.PREPARE_TICKS; age++) assertFalse(NightHandPresentation.ready(age));
        assertEquals(1, NightHandPresentation.formation(NightHandPresentation.FORM_TICKS));
        assertTrue(NightHandPresentation.PREPARE_TICKS - NightHandPresentation.FORM_TICKS >= 4);
        assertTrue(NightHandPresentation.ready(NightHandPresentation.PREPARE_TICKS + 1));
    }
    @Test void gateFormationIsContinuousMonotonicAndClamped() {
        float previous = 0;
        for (int frame = -10; frame <= 160; frame++) {
            float value = NightHandPresentation.formation(frame / 10.0F);
            assertTrue(value >= previous && value >= 0 && value <= 1);
            previous = value;
        }
    }
}
