package com.tnc.tnc.dungeon.leyline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LeylinePuzzleRulesTest {
    @Test void wrongNotesResetInsteadOfAccumulatingUnrelatedHits() {
        assertEquals(1,LeylinePuzzleRules.nextNote(0,1));
        assertEquals(0,LeylinePuzzleRules.nextNote(1,6));
        assertEquals(0,LeylinePuzzleRules.nextNote(0,3));
        assertEquals(3,LeylinePuzzleRules.nextNote(LeylinePuzzleRules.nextNote(LeylinePuzzleRules.nextNote(0,1),3),6));
    }
    @Test void onlyTwoDistinctStatuesCanEverBeMelted() {
        int melted=0;
        for(int i:new int[]{2,2,4,0,5}) if(LeylinePuzzleRules.mayThaw(melted,i)) melted|=1<<i;
        assertEquals((1<<2)|(1<<4),melted);
        assertFalse(LeylinePuzzleRules.mayThaw(0,-1)); assertFalse(LeylinePuzzleRules.mayThaw(0,6));
    }
    @Test void mirrorStepsNeverWrapBoardEdgesAndRequireActualMovement() {
        assertEquals(4,LeylinePuzzleRules.moveMirror(4,1,0));
        assertEquals(20,LeylinePuzzleRules.moveMirror(20,0,1));
        assertEquals(0,LeylinePuzzleRules.moveMirror(0,-1,0));
        assertEquals(7,LeylinePuzzleRules.moveMirror(12,0,-1));
        assertEquals(11,LeylinePuzzleRules.moveMirror(12,-1,0));
    }
    @Test void opticalWindowIsExactlyThreeByThreeAndDoesNotWrapRows() {
        int count=0; for(int i=0;i<150;i++) if(LeylinePuzzleRules.lensCovers(7,4,i)) count++;
        assertEquals(9,count); assertFalse(LeylinePuzzleRules.lensCovers(0,1,14));
    }
    @Test void greenTemperatureBoundariesAndSunOrbitalDirectionAreStable() {
        assertFalse(LeylinePuzzleRules.temperatureSafe(359)); assertTrue(LeylinePuzzleRules.temperatureSafe(360));
        assertTrue(LeylinePuzzleRules.temperatureSafe(620)); assertFalse(LeylinePuzzleRules.temperatureSafe(621));
        assertEquals(0,LeylinePuzzleRules.nextHour(3,true)); assertEquals(3,LeylinePuzzleRules.nextHour(0,false));
    }
    @Test void pawnToxicityImprovesOddsButKeepsRiskAndScaleHasFiniteDetoxBudget() {
        for(int roll=0;roll<100;roll++) if(LeylinePuzzleRules.rarePawn(5,roll)) assertTrue(LeylinePuzzleRules.rarePawn(75,roll));
        assertFalse(LeylinePuzzleRules.rarePawn(100,99)); assertEquals(90,LeylinePuzzleRules.scaleToxicity(18));
    }
}
