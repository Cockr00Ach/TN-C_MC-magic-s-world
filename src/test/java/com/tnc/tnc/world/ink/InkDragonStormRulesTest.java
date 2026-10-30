package com.tnc.tnc.world.ink;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonStormRulesTest {
    @Test void allPillarsGiveAtLeastTwoSecondsToDodgeAfterLock(){for(int i=0;i<7;i++)assertTrue(InkDragonStormRules.hitTick(i)-InkDragonStormRules.LOCK>=40);}
    @Test void eruptionRisesInLessThanASecondThenFormsOrb(){
        assertTrue(InkDragonEruptionRules.TOP-InkDragonEruptionRules.RISE<20);
        assertEquals(0,InkDragonEruptionRules.rise(InkDragonEruptionRules.RISE));
        assertEquals(1,InkDragonEruptionRules.rise(InkDragonEruptionRules.TOP));
        double previous=0;for(int age=0;age<180;age++){double next=InkDragonEruptionRules.rise(age);assertTrue(next>=previous);previous=next;}
        assertTrue(InkDragonEruptionRules.ORB>InkDragonEruptionRules.TOP);
        assertTrue(InkDragonEruptionRules.hidden(35));assertFalse(InkDragonEruptionRules.hidden(45));
    }
    @Test void sixOuterPillarsLeaveGapsAroundTheCenter(){for(int i=1;i<7;i++)assertEquals(30,Math.hypot(InkDragonStormRules.pillarX(i),InkDragonStormRules.pillarZ(i)),1e-9);}
}
