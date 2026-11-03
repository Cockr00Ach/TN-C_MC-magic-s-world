package com.tnc.tnc.warrior.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorMotionBlendTest {
    @Test void enterAndReleaseEaseWithoutDroppingAContinuouslyHeldPose(){
        assertEquals(0,WarriorMotionBlend.weight(0,20,false));
        assertEquals(.5f,WarriorMotionBlend.weight(1,20,false));
        assertEquals(1,WarriorMotionBlend.weight(20,20,false));
        assertEquals(.5f,WarriorMotionBlend.weight(21.5f,20,false));
        assertEquals(0,WarriorMotionBlend.weight(23,20,false));
        assertEquals(1,WarriorMotionBlend.weight(50000,20,true));
    }
    @Test void angleBlendingUsesShortWayAndCompletedTurnsNeverRewind(){
        float d=(float)(Math.PI/180);
        assertEquals(180*d,WarriorMotionBlend.angle(170*d,-170*d,.5f),.0001);
        assertEquals(0,WarriorMotionBlend.root(720,.5f));
        assertEquals(-5,WarriorMotionBlend.root(710,.5f));
        assertEquals(710,WarriorMotionBlend.root(710,1));
    }
}
