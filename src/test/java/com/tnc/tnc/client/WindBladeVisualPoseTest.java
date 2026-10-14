package com.tnc.tnc.client;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindBladeVisualPoseTest {
    @Test void bladePlaneKeepsItsLaunchNormalThroughoutEverySpin() {
        for(float yaw:new float[]{-170,0,65,180})for(float pitch:new float[]{-90,-35,0,60,90}) {
            var launch=WindBladeVisualPose.rotation(yaw,pitch,0).transform(new Vector3f(0,0,1));
            for(float spin:new float[]{0,.5F,45,180,359.9F,360,720}) {
                var normal=WindBladeVisualPose.rotation(yaw,pitch,spin).transform(new Vector3f(0,0,1));
                assertTrue(launch.distance(normal)<1E-5,"Spin must never tilt the plane away from launch direction");
            }
            var edge=WindBladeVisualPose.rotation(yaw,pitch,0).transform(new Vector3f(1,0,0));
            var spun=WindBladeVisualPose.rotation(yaw,pitch,90).transform(new Vector3f(1,0,0));
            assertEquals(0,edge.dot(spun),1E-5,"The edge still rotates within its fixed plane");
        }
    }
}
