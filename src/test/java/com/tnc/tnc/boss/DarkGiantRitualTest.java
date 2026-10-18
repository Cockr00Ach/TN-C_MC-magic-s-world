package com.tnc.tnc.boss;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DarkGiantRitualTest {
    @Test void ascentIsFastMonotonicAndEndsAboveTheLockedLanding(){
        Vec3 home=new Vec3(10,64,10),hover=new Vec3(90,108,-20);double y=home.y;
        for(int tick=0;tick<=DarkGiantSkyCast.RISE_TICKS;tick++){
            var at=DarkGiantSkyCast.risePosition(home,hover,tick);assertTrue(at.y>=y);y=at.y;
        }
        assertEquals(hover,DarkGiantSkyCast.risePosition(home,hover,12));
        assertEquals(hover,DarkGiantSkyCast.risePosition(home,hover,80));
    }
    @Test void siphonHasCappedSpeedAndStopsOutsideTheBossCollisionBox(){
        var far=DarkGiantSiphonControl.velocity(Vec3.ZERO,24,56,new Vec3(90,0,0));
        assertEquals(-.14,far.x,.0001);assertEquals(.11,far.y,.0001);
        var near=DarkGiantSiphonControl.velocity(Vec3.ZERO,24,56,new Vec3(14,6.72,0));
        assertEquals(0,near.lengthSqr(),.0001);
        assertTrue(DarkGiantSiphonControl.velocity(Vec3.ZERO,24,56,new Vec3(20,20,0)).y<0);
    }
}
