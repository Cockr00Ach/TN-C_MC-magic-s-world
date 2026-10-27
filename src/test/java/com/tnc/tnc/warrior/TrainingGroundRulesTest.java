package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrainingGroundRulesTest {
    @Test void adjacentAndWrappedPlotsNeverSharePracticeSpace(){
        for(int id:new int[]{0,1,255,256,65535}){
            double x=TrainingGroundRules.centerX(id)+.5,z=TrainingGroundRules.centerZ(id)+.5;
            assertTrue(TrainingGroundRules.contains(id,x,2,z));
            assertFalse(TrainingGroundRules.contains(id,x+11,2,z));
            if(id<65535)assertFalse(TrainingGroundRules.contains(id+1,x,2,z));
        }
        assertThrows(IllegalArgumentException.class,()->TrainingGroundRules.centerX(-1));
        assertThrows(IllegalArgumentException.class,()->TrainingGroundRules.centerZ(65536));
    }
    @Test void invalidPositionsCannotEscapeThePersonalPlot(){
        double x=TrainingGroundRules.centerX(0)+.5,z=TrainingGroundRules.centerZ(0)+.5;
        assertFalse(TrainingGroundRules.contains(0,Double.NaN,2,z));
        assertFalse(TrainingGroundRules.contains(0,x,-10,z));
        assertFalse(TrainingGroundRules.contains(0,x,9,z));
        assertFalse(TrainingGroundRules.contains(0,x,2,Double.POSITIVE_INFINITY));
    }
}
