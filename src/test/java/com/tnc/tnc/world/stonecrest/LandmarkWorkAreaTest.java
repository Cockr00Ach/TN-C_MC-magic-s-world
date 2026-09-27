package com.tnc.tnc.world.stonecrest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandmarkWorkAreaTest {
    @Test void reportedCathedralLocateMustNotPauseTheLastOneColumnTile() {
        // Actual save: origin z=235, depth=433. Rounded 16-cell tile previously reached z=683.
        assertFalse(LandmarkWorkArea.near(13264.5,89.9,672.5,13264,-42,667,13280,91,668));
        assertFalse(LandmarkWorkArea.near(13272.5,89.9,680.5,13264,-42,667,13280,91,668));
    }
    @Test void bodiesInsideOrAdjacentToWritesAreProtected() {
        assertTrue(LandmarkWorkArea.near(8,65,8,0,60,0,16,80,16));
        assertTrue(LandmarkWorkArea.near(17,65,8,0,60,0,16,80,16));
        assertFalse(LandmarkWorkArea.near(19,65,8,0,60,0,16,80,16));
    }
    @Test void FlyingAboveWorkDoesNotStallEntireStructure() {
        assertFalse(LandmarkWorkArea.near(8,180,8,0,60,0,16,90,16));
    }
}
