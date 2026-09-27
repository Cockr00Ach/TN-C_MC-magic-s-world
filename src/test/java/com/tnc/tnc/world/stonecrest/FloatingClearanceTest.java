package com.tnc.tnc.world.stonecrest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FloatingClearanceTest {
    @Test void towerAboveIslandIsNotGroundUnderIt() {
        var c=new FloatingClearance(80,151);
        c.obstacle(249,54,100);
        assertEquals(132,c.choose(132));
    }
    @Test void overlappingTowerSelectsLowerHeightWithoutChangingXZ() {
        var c=new FloatingClearance(80,151);
        c.obstacle(249,54,140);
        assertEquals(104,c.choose(132));
    }
    @Test void fullHeightObstacleLeavesNoUnsafeFallback() {
        var c=new FloatingClearance(80,151);
        for(int y=0;y<320;y++) c.obstacle(y,0,164);
        assertEquals(Integer.MIN_VALUE,c.choose(132));
    }
    @Test void emptyRangeAndNearbySea() {
        assertEquals(Integer.MIN_VALUE,new FloatingClearance(80,70).choose(132));
        var c=new FloatingClearance(80,151);c.obstacle(62,0,164);
        assertEquals(132,c.choose(132));
    }
}
