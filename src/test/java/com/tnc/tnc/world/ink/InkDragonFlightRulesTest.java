package com.tnc.tnc.world.ink;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonFlightRulesTest {
    @Test void cullingBoundsContainLongTailInEveryDirection(){
        Vec3 at=new Vec3(20,40,-30);
        for(int yaw=0;yaw<360;yaw+=15){AABB b=InkDragonFlightRules.bounds(at,yaw);
            for(Vec3 p:new Vec3[]{new Vec3(-11,5,0),new Vec3(63,5,0),new Vec3(30,2,4)})
                assertTrue(b.contains(at.add(p.add(0,1,0).scale(InkDragonCombatRules.BODY_SCALE).yRot((float)Math.toRadians(90-yaw)))));
        }
    }
    @Test void summonHeadIsInsideUsualForwardFieldOfView(){
        double height=InkDragonFlightRules.SUMMON_RISE+(InkDragonFlightRules.MOUTH.y+1)*InkDragonCombatRules.BODY_SCALE-1.62;
        double forward=InkDragonFlightRules.SUMMON_DISTANCE+InkDragonFlightRules.MOUTH.x*InkDragonCombatRules.BODY_SCALE;
        assertTrue(Math.toDegrees(Math.atan2(height,forward))<25,"No need to look almost straight up to find a new dragon");
    }
}
