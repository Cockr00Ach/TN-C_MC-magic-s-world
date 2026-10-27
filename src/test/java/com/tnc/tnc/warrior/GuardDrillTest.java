package com.tnc.tnc.warrior;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardDrillTest {
    @Test void advancedTiersTightenCounterWindowWithoutChangingRequiredBlocks(){
        var enemy=UUID.randomUUID();
        for(int mode=0;mode<3;mode++){
            var d=new GuardDrill(mode);d.block(enemy,0);d.block(enemy,10);d.block(enemy,20);
            int window=PracticeDifficulty.window(mode);
            assertTrue(d.counter(enemy,20+window-1));assertFalse(d.counter(enemy,20+window));
            assertEquals(3,d.blocks());assertEquals(window-1,d.reaction(20+window-1));
            assertTrue(PracticeDifficulty.windup(mode)<PracticeDifficulty.period(mode));
        }
    }
    @Test void assessmentPenalizesMissesAndSlowCountersWithBoundedScores(){
        assertEquals(100,PracticeDifficulty.score(0,0));assertEquals(80,PracticeDifficulty.score(1,10));
        assertEquals(0,PracticeDifficulty.score(Integer.MAX_VALUE,Integer.MAX_VALUE));
        var d=new GuardDrill(2);d.miss();assertEquals(1,d.misses());assertEquals(2,d.mode());
        assertEquals("炉火纯青",PracticeDifficulty.grade(95));assertEquals("仍需磨练",PracticeDifficulty.grade(20));
    }
    @Test void requiresThreeSpacedBlocksAndMatchingTimelyCounter() {
        var d = new GuardDrill(); var enemy = UUID.randomUUID();
        assertFalse(d.counter(enemy, 0));
        assertTrue(d.block(enemy, 0));
        assertFalse(d.block(enemy, 1));
        assertTrue(d.block(enemy, 10));
        assertFalse(d.counter(enemy, 11));
        assertTrue(d.block(enemy, 20));
        assertFalse(d.counter(UUID.randomUUID(), 30));
        assertTrue(d.counter(enemy, 99));
        assertEquals(1,d.remaining(99));
        assertFalse(d.counter(enemy, 100));
        assertEquals(0,d.remaining(100));
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
