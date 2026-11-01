package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InkDrainRulesTest {
    @Test void nearRoarRecoversTheFlyingPose(){assertEquals(0,InkDragonDrainRules.pitch(0));assertEquals(60,InkDragonDrainRules.pitch(80));assertEquals(0,InkDragonDrainRules.pitch(165));}
    @Test void drinkingAdvancesExactlyOneQuarterOfTicks(){int advances=0;for(int i=0;i<128;i++)if(InkDragonDread.advances(i))advances++;assertEquals(32,advances);}
    @Test void retimingPreservesCastProgressAndIsReversible(){
        long now=1000,start=980;int length=80;
        long slowed=InkSpellTimeBridge.retimedStart(now,start,4);int longer=InkSpellTimeBridge.retimedLength(length,4);
        assertEquals((now-start)/(double)length,(now-slowed)/(double)longer,1e-9);
        assertEquals(start,InkSpellTimeBridge.retimedStart(now,slowed,.25));assertEquals(length,InkSpellTimeBridge.retimedLength(longer,.25));
        assertEquals(1,InkSpellTimeBridge.retimedLength(1,.25));
    }
}
