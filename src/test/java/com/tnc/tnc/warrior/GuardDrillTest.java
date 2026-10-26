package com.tnc.tnc.warrior;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardDrillTest {
    @Test void requiresThreeSpacedBlocksAndMatchingTimelyCounter() {
        var d = new GuardDrill(); var enemy = UUID.randomUUID();
        assertFalse(d.counter(enemy, 0));
        assertTrue(d.block(enemy, 0));
        assertFalse(d.block(enemy, 1));
        assertTrue(d.block(enemy, 10));
        assertFalse(d.counter(enemy, 11));
        assertTrue(d.block(enemy, 20));
        assertFalse(d.counter(UUID.randomUUID(), 30));
        assertTrue(d.counter(enemy, 100));
        assertFalse(d.counter(enemy, 101));
        assertFalse(d.counter(enemy, 19));
    }
    @Test void freshBlockRefreshesExpiredWindowAndChangesCounterTarget() {
        var d = new GuardDrill(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        d.block(a, 0); d.block(a, 10); d.block(a, 20);
        assertFalse(d.counter(a, 500));
        assertTrue(d.block(b, 500));
        assertEquals(3, d.blocks());
        assertFalse(d.counter(a, 501));
        assertTrue(d.counter(b, 501));
        assertFalse(d.block(null, 600));
    }
}
