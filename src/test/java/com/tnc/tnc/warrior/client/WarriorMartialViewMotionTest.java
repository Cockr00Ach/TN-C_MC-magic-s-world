package com.tnc.tnc.warrior.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorMartialViewMotionTest {
    @Test void eachSignatureTraversesTheViewAndReturnsWithoutMovingTheCamera() {
        for(String id:com.tnc.tnc.warrior.WarriorMartialArts.ids()) {
            if(id.equals("red_tassel_spear"))continue;
            var clip=WarriorComboAnimations.all().get(id+"_3");
            double min=1,max=-1;
            for(int i=0;i<=100;i++) {
                var r=WarriorMartialViewMotion.sample(clip,i/100f,false);
                var l=WarriorMartialViewMotion.sample(clip,i/100f,true);
                assertTrue(Double.isFinite(r.x()) && Math.abs(r.x())<=.38 && Math.abs(r.yaw())<=52,id);
                assertEquals(-r.x(),l.x(),1e-7);assertEquals(-r.roll(),l.roll(),1e-6);
                min=Math.min(min,r.x());max=Math.max(max,r.x());
            }
            assertTrue(max-min>.35,id+" must cross the view");
            assertEquals(0,WarriorMartialViewMotion.sample(clip,0,false).x(),1e-7);
            assertEquals(0,WarriorMartialViewMotion.sample(clip,1,false).yaw(),1e-7);
        }
        assertFalse(WarriorMartialViewMotion.supports("red_tassel_spear_3"));
    }
}
