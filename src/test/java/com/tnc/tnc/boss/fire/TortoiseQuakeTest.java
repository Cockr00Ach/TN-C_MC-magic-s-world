package com.tnc.tnc.boss.fire;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TortoiseQuakeTest {
    @Test void everyGroundBlockIsDrawnOnceAtEveryWaveAge() {
        for (double age = 0; age < TortoiseQuake.LIFETIME; age += 0.5D) {
            var cells = TortoiseQuake.cells(-12.35D, 7.8D, age);
            assertTrue(cells.size() <= 1024, "single wave must fit the render budget");
            var seen = new HashSet<Long>();
            for (var cell : cells) assertTrue(seen.add(cell.key()), "duplicate ground block");
        }
    }

    @Test void nextPartialFrameAlreadyHasItsNewLeadingCells() {
        for (int age = 0; age < 40; age++) {
            var sampled = new HashSet<Long>();
            for (var cell : TortoiseQuake.cells(0.3D, -0.7D, age)) sampled.add(cell.key());
            for (var cell : TortoiseQuake.cells(0.3D, -0.7D, age + 0.5D)) {
                if (TortoiseQuake.visible(age + 0.5D, cell.distance())) assertTrue(sampled.contains(cell.key()));
            }
        }
    }

    @Test void fixedBlockRisesPeaksFallsAndSettlesContinuously() {
        double at = TortoiseQuake.arrival(12.0D);
        assertEquals(0, TortoiseQuake.lift(at - 3, 12), 1E-9);
        assertTrue(TortoiseQuake.lift(at - 1.5D, 12) > 0.4D);
        assertEquals(1.05D, TortoiseQuake.lift(at, 12), 1E-9);
        assertTrue(TortoiseQuake.lift(at + 3, 12) < 0.5D);
        assertEquals(0, TortoiseQuake.lift(at + 11, 12), 1E-9);
        double previous = 0;
        for (double age = 0; age <= 55; age += 0.025D) {
            double height = TortoiseQuake.lift(age, 12);
            assertTrue(Math.abs(height - previous) < 0.02D, "height pops between frames");
            assertTrue(height >= 0 && height <= 1.05D);
            previous = height;
        }
    }

    @Test void crestMatchesServerRadiusAndLastRowHasTimeToLand() {
        for (int tick = 1; tick <= 40; tick++) {
            assertEquals(1.05D, TortoiseQuake.lift(tick, TortoiseQuake.radius(tick)), 1E-8);
        }
        assertTrue(TortoiseQuake.lift(43, 22) > 0);
        assertEquals(0, TortoiseQuake.lift(51, 22));
        assertTrue(TortoiseQuake.cells(0, 0, 51).isEmpty());
        assertEquals(0, TortoiseQuake.lift(40, 24));
    }

    @Test void trailingTroughDarkensThenReturnsToOriginalSurface() {
        double at = TortoiseQuake.arrival(10);
        assertTrue(TortoiseQuake.shade(at + 8, 10) < 0.8D);
        assertEquals(1, TortoiseQuake.shade(at + 11, 10));
    }

    @Test void lateTrackingAndUnsynchronisedWorldClocksKeepElapsedProgress() {
        var playback = new TortoiseQuake.Playback();
        playback.receive(240000, 26, 7);
        assertEquals(26, playback.age(7));
        assertEquals(27.5D, playback.age(8.5D));
        playback.receive(240000, 28, 9);
        assertEquals(28, playback.age(9));
    }

    @Test void unchangedMetadataDoesNotResetOrReplayExpiredWaves() {
        var playback = new TortoiseQuake.Playback();
        playback.receive(12, 0, 100);
        playback.receive(12, 0, 101);
        assertEquals(1, playback.age(101));
        assertTrue(playback.age(160) > TortoiseQuake.LIFETIME);
        playback.receive(12, 0, 160);
        assertTrue(playback.age(160) > TortoiseQuake.LIFETIME);
        playback.receive(-1, -1, 161);
        assertEquals(-1, playback.age(161));
    }

    @Test void simultaneousBossCastsAndNextCastAreIndependent() {
        var first = new TortoiseQuake.Playback();
        var second = new TortoiseQuake.Playback();
        first.receive(40, 10, 3);
        second.receive(40, 0, 3);
        assertEquals(11, first.age(4));
        assertEquals(1, second.age(4));
        first.receive(90, 0, 4);
        assertEquals(0, first.age(4));
        assertEquals(1, second.age(4));
    }
}
