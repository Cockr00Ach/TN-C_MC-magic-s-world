package com.tnc.tnc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkyPortalSearchTest {
    @Test void safeSearchExtendsWellBeyondOldSpawnRadiusAndHasNoDuplicateCandidates(){
        var offsets=SkyIslandManager.portalSearchOffsets();
        assertEquals(56,offsets.size());assertEquals(offsets.size(),new java.util.HashSet<>(offsets).size());
        assertTrue(offsets.stream().anyMatch(p->Math.abs(p.getX())>=1536));
        assertTrue(offsets.stream().anyMatch(p->Math.abs(p.getZ())>=1536));
    }
}
