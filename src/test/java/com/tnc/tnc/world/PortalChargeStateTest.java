package com.tnc.tnc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalChargeStateTest {
    @Test
    void chargeCompletesAfterEightyTicksInItsSourcePortal() {
        PortalChargeState charge = new PortalChargeState(true, 100L);
        assertEquals(PortalChargeState.Decision.CHARGING,
                charge.evaluate(true, true, false, 179L));
        assertEquals(PortalChargeState.Decision.COMPLETE,
                charge.evaluate(true, true, false, 180L));
    }

    @Test
    void ritualPhasesDoNotReleaseBeforeLasersHaveConverged() {
        assertEquals(0, PortalRitualTiming.convergence(19));
        assertEquals(0, PortalRitualTiming.release(59));
        assertEquals(1, PortalRitualTiming.convergence(40));
        assertEquals(1, PortalRitualTiming.release(79));
        assertEquals(0, PortalRitualTiming.release(-100));
        assertEquals(1, PortalRitualTiming.convergence(1000));
    }

    @Test
    void leavingEvenOnCompletionTickNeverTeleports() {
        assertEquals(PortalChargeState.Decision.CANCEL,
                new PortalChargeState(true, 100).evaluate(true, false, false, 180));
    }

    @Test
    void leavingTheSourcePortalCancelsImmediately() {
        PortalChargeState charge = new PortalChargeState(false, 200L);
        assertEquals(PortalChargeState.Decision.CANCEL,
                charge.evaluate(true, false, false, 201L));
        assertEquals(PortalChargeState.Decision.CANCEL,
                charge.evaluate(true, true, false, 201L));
    }

    @Test
    void deathCancelsEvenWhenThePlayerRemainsInside() {
        PortalChargeState charge = new PortalChargeState(true, 300L);
        assertEquals(PortalChargeState.Decision.CANCEL,
                charge.evaluate(false, true, false, 301L));
    }
}
