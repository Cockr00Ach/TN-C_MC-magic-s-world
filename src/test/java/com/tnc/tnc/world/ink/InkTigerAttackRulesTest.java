package com.tnc.tnc.world.ink;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkTigerAttackRulesTest {
    @Test void chargePrecedesShotAndReturn(){
        assertTrue(InkTigerAttackRules.RISE<InkTigerAttackRules.SPIT);
        assertTrue(InkTigerAttackRules.SPIT<InkTigerAttackRules.DESCEND);
        assertTrue(InkTigerAttackRules.DESCEND<InkTigerAttackRules.duration(1));
    }
    @Test void roarBallGrowsBeforeDetonationThenThreeSlashes(){
        assertEquals(0,InkTigerAttackRules.roarBallRadius(19));
        assertEquals(5.5,InkTigerAttackRules.roarBallRadius(90),.001);
        double last=0;int hits=0;
        for(int i=0;i<=150;i++){
            double r=InkTigerAttackRules.roarBallRadius(i);assertTrue(r>=last);last=r;
            if(InkTigerAttackRules.slashAt(i)){hits++;assertTrue(i>InkTigerAttackRules.DETONATE);}
        }
        assertEquals(3,hits);
    }
    @Test void ghostHasWindupAndFivefoldScale(){
        assertEquals(5,InkTigerAttackRules.GHOST_SCALE);
        assertTrue(InkTigerAttackRules.GHOST_RELEASE>=20);
        assertTrue(InkTigerAttackRules.GHOST_RELEASE<InkTigerAttackRules.duration(2));
    }
    @Test void bodyAndGhostScaleTogetherWhileDamagePhasesStayUnchanged(){
        assertEquals(4,InkTigerAttackRules.BODY_MULTIPLIER);
        assertEquals(4.6,InkTigerAttackRules.BODY_SCALE,.001);
        assertEquals(23,InkTigerAttackRules.GHOST_SCALE*InkTigerAttackRules.BODY_SCALE,.001);
        assertTrue(InkTigerAttackRules.SUCTION_ACCELERATION>=.15);
    }
}
