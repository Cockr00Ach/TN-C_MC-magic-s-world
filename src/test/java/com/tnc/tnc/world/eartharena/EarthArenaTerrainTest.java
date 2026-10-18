package com.tnc.tnc.world.eartharena;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class EarthArenaTerrainTest {
    @Test void mountainReliefIsBoundedAndContinuousAcrossNegativeAndPositiveChunks() {
        int lowest = 999, highest = -999, largestStep = 0;
        for (int x = -2048; x <= 2048; x += 7) for (int z = -2048; z <= 2048; z += 11) {
            int h = EarthArenaTerrain.height(x, z, 20261018);
            lowest = Math.min(lowest, h); highest = Math.max(highest, h);
            largestStep = Math.max(largestStep, Math.abs(h - EarthArenaTerrain.height(x + 1, z, 20261018)));
            assertTrue(h > 90 && h < 400, "Terrain stays above any ocean and below the dimension ceiling");
        }
        assertTrue(highest - lowest > 170, "Broad mountain ranges have substantial relief");
        assertTrue(largestStep <= 8, "No discontinuity at chunk boundaries or vertical noise cliffs");
        assertNotEquals(EarthArenaTerrain.height(333, -157, 1), EarthArenaTerrain.height(333, -157, 2));
    }

    @Test void peaksAreLighterAndSolidMountainStrataUseTheEntireEarthPalette() {
        assertTrue(EarthArenaTerrain.shade(330, 330, 0) < EarthArenaTerrain.shade(130, 130, 0));
        var shades = new HashSet<Integer>();
        for (int top = 120; top <= 350; top += 5) for (int y = -64; y <= top; y++) {
            int shade = EarthArenaTerrain.shade(y, top, 17);
            assertTrue(shade >= 1 && shade <= 8);
            shades.add(shade);
        }
        assertEquals(8, shades.size());
    }
}
