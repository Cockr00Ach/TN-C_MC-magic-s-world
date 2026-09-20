package com.tnc.tnc.mixin;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestBushPropertyGuardTest {
    @Test
    void leavesStateUnchangedAndSkipsWriterWhenPropertyIsMissing() {
        Object airState = new Object();
        AtomicBoolean writerCalled = new AtomicBoolean(false);

        Object result = ConquestBushPropertyGuard.writeIfPresent(airState, false, state -> {
            writerCalled.set(true);
            return new Object();
        });

        assertSame(airState, result);
        assertFalse(writerCalled.get());
    }

    @Test
    void delegatesToWriterWhenPropertyExists() {
        Object bushState = new Object();
        Object updatedState = new Object();
        AtomicBoolean writerCalled = new AtomicBoolean(false);

        Object result = ConquestBushPropertyGuard.writeIfPresent(bushState, true, state -> {
            writerCalled.set(true);
            assertSame(bushState, state);
            return updatedState;
        });

        assertSame(updatedState, result);
        assertTrue(writerCalled.get());
    }
}
