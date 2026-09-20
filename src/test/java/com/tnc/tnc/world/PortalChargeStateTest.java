package com.tnc.tnc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalChargeStateTest {
    @Test
    void chargeCompletesAfterFiftyTicksInItsSourcePortal() {
        PortalChargeState charge = new PortalChargeState(true, 100L);
        assertEquals(PortalChargeState.Decision.CHARGING,
                charge.evaluate(true, true, false, 149L));
        assertEquals(PortalChargeState.Decision.COMPLETE,
                charge.evaluate(true, true, false, 150L));
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
