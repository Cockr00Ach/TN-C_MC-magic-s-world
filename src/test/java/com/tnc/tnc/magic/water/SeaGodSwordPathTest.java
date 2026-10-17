package com.tnc.tnc.magic.water;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeaGodSwordPathTest {
    @Test void gatewaysAreFixedWithinTheActualDescentAndReadyBeforeRelease() {
        double previous=0;
        for(int i=0;i<SeaGodSwordRules.PATH_SEAL_COUNT;i++) {
            double height=SeaGodSwordRules.pathSealHeight(i);
            assertEquals(12,height-previous);
            assertTrue(height>0 && height<SeaGodSwordRules.DROP_HEIGHT);
            assertEquals(0,SeaGodSwordRules.pathSealOpacity(99,i));
            assertEquals(1,SeaGodSwordRules.pathSealOpacity(SeaGodSwordRules.DROP_TICK-1,i));
            assertEquals(0,SeaGodSwordRules.pathSealOpacity(SeaGodSwordRules.END_TICK,i));
            double crossing=SeaGodSwordRules.DROP_TICK+Math.cbrt(1-height/SeaGodSwordRules.DROP_HEIGHT)
                    *(SeaGodSwordRules.IMPACT_TICK-SeaGodSwordRules.DROP_TICK);
            assertEquals(height,SeaGodSwordRules.tipHeight(crossing),1e-8);
            assertTrue(SeaGodSwordRules.pathSealOpacity(crossing,i)>.99F);
            previous=height;
        }
    }
}
