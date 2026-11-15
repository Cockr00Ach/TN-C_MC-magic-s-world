package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FireFlamePaletteTest {
    @Test void emberSoulUsesDistinctColdBlueAndSlowerMotion() {
        var blue = FireFlamePalette.forTier(4);
        assertEquals(FireFlamePalette.GHOST, blue);
        assertTrue((blue.body() & 255) > (blue.body() >> 16 & 255));
        assertTrue(blue.motion() < FireFlamePalette.FLAME.motion());
        assertNotEquals(blue.fringe(), blue.core());
    }
    @Test void fireColorsFollowTheSpellIdentity() {
        assertEquals(FireFlamePalette.FLAME, FireFlamePalette.forEffect("SPARK", 1));
        assertEquals(FireFlamePalette.ION, FireFlamePalette.forEffect("SKYFIRE", 1));
        assertEquals(FireFlamePalette.ION, FireFlamePalette.forEffect("MARTYR", .45F));
        assertEquals(FireFlamePalette.GHOST, FireFlamePalette.forEffect("GHOST_BURST", 1));
        assertEquals(FireFlamePalette.FLAME, FireFlamePalette.forEffect("DETONATE", 1));
        assertEquals(FireFlamePalette.GHOST, FireFlamePalette.forEffect("FORM", .8F));
        assertEquals(FireFlamePalette.forIgnition(5), FireFlamePalette.forEffect("FORM", 1));
        assertNotEquals(FireFlamePalette.ION, FireFlamePalette.forEffect("FORM", 1));
        assertEquals(FireFlamePalette.forIgnition(4), FireFlamePalette.forEffect("SCORCHED", .8F));
    }
    @Test void hottestBurstKeepsItsOrangeBodyButDevelopsBlueWhiteSeams() {
        var hot = FireFlamePalette.forEffect("HEAT_BURST", 1);
        assertEquals(FireFlamePalette.FLAME.body(), hot.body());
        assertNotEquals(FireFlamePalette.FLAME.glow(), hot.glow());
        assertEquals(FireFlamePalette.FLAME, FireFlamePalette.forEffect("HEAT_BURST", .5F));
        assertEquals(1, FireFlamePalette.formTier(Float.NaN));
        assertEquals(5, FireFlamePalette.formTier(99));
    }
}
