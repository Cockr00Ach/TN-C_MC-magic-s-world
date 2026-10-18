package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarthModelLayoutTest {
    @Test void everyEarthSpellUsesBoundedSolidModels() {
        int count=0;
        for(var spec:WindEarthVisualRules.all().values())if(spec.kind()>=3) {
            var pieces=EarthModelLayout.create(spec.kind(),spec.tier(),spec.radius());
            assertFalse(pieces.isEmpty());assertTrue(pieces.size()<=32);
            for(var p:pieces) {
                assertTrue(p.width()>0&&p.height()>0);
                assertTrue(Double.isFinite(p.x()+p.y()+p.z()+p.yaw()));
                assertTrue(p.model().startsWith("earth_"));
            }
            count++;
        }
        assertEquals(20,count);
    }
    @Test void chainsHaveTheirOwnRecognisableModels() {
        assertTrue(EarthModelLayout.create(4,3,5.5).stream().anyMatch(p->p.model().equals("earth_pillar")));
        assertTrue(EarthModelLayout.create(5,5,8).stream().anyMatch(p->p.model().equals("earth_mud_plate")));
        assertTrue(EarthModelLayout.create(6,4,2.4).stream().anyMatch(p->p.model().equals("earth_root")));
        assertTrue(EarthModelLayout.create(3,1,1.35).stream().allMatch(p->p.model().equals("earth_clod")));
    }
    @Test void particlesRemainBoundedAtEveryTier() {
        for(int tier=1;tier<=5;tier++) {
            assertTrue(EarthSpellParticles.count(tier,true)<=18);
            assertTrue(EarthSpellParticles.count(tier,false)<=7);
        }
    }
}
