package com.tnc.tnc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkyhavenReadinessTest {
 @Test void minimumWarmupCannotBeSkippedByEarlyQuietFrames(){assertFalse(SkyhavenReadiness.ready(9999,49,9,40));assertTrue(SkyhavenReadiness.ready(10000,49,9,40));}
 @Test void elapsedTimeNeverReplacesMissingTerrainOrMeshes(){assertFalse(SkyhavenReadiness.ready(90000,48,9,40));assertFalse(SkyhavenReadiness.ready(90000,49,8,40));}
 @Test void slowDeviceCanFinishAfterTerrainIsActuallyReady(){assertFalse(SkyhavenReadiness.ready(20000,49,9,0));assertTrue(SkyhavenReadiness.ready(45000,49,9,0));}
 @Test void lowServerViewDistanceUsesOnlyChunksItActuallySends(){assertTrue(SkyhavenReadiness.ready(15000,9,9,9,40));assertFalse(SkyhavenReadiness.ready(90000,8,9,9,40));}
}
