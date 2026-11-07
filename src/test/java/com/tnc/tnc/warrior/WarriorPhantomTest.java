package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorPhantomTest {
    @Test void peaksMatchSolidWeaponAndReachGrowth(){
        assertEquals(3,WarriorPhantom.scale(0,200));
        assertEquals(5.5,WarriorPhantom.scale(5000,8000));
        assertEquals(8,WarriorPhantom.scale(10000,10000));
        assertEquals(8,1+WarriorPhantom.MAX_GROWTH);
    }
    @Test void talentChangesNeverShrinkTheSameAbsoluteProgress(){
        for(int cap:new int[]{0,200,1000,3000,6000,10000})assertEquals(4.5,WarriorPhantom.scale(3000,cap),1e-8);
    }
    @Test void invalidPointsClampAndGrowthNeverReverses(){
        assertEquals(3,WarriorPhantom.scale(Integer.MIN_VALUE,0));
        assertEquals(8,WarriorPhantom.scale(Integer.MAX_VALUE,0));
        double previous=3;
        for(int points=0;points<=10000;points++){
            double next=WarriorPhantom.scale(points,0);assertTrue(next>=previous && next<=8);previous=next;
        }
    }
}
