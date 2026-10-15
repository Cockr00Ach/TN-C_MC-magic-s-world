package com.tnc.tnc.light;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightBeamAimTest {
    @Test void worldDirectionSurvivesMinecraftYawAndPitchConversion() {
        for(Vec3 direction:new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(1,.3,-.7),new Vec3(-.6,-.4,1),new Vec3(0,1,0)}) {
            Vec3 actual=Vec3.directionFromRotation(TNLightBeamMechanics.pitchFor(direction),TNLightBeamMechanics.yawFor(direction));
            assertTrue(direction.normalize().distanceTo(actual)<.001,"World X direction must never be mirrored");
        }
    }
    @Test void fanFollowsNewLookWithoutCollapsingTheThreeRays() {
        for(Vec3 look:new Vec3[]{new Vec3(0,0,1),new Vec3(1,.3,0),new Vec3(0,1,0),new Vec3(0,-1,0)}) {
            var expected=TNLightBeamMechanics.fanDirections(look.normalize(),3,14);
            for(int i=0;i<3;i++)assertTrue(expected.get(i).distanceTo(LightBeamAim.direction(look,LightBeamAim.offset(i,3,14)))<1E-6);
            assertTrue(LightBeamAim.direction(look,-7).distanceTo(LightBeamAim.direction(look,7))>.2);
        }
    }
    @Test void singleRayAlwaysUsesExactLookEvenWithUnusedSpread() {
        Vec3 look=new Vec3(-.7,.4,.2).normalize();
        assertEquals(0,LightBeamAim.offset(0,1,90));
        assertTrue(look.distanceTo(LightBeamAim.direction(look,0))<1E-6);
    }
}
