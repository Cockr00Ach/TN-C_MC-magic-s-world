package com.tnc.tnc.world.wind;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanglanExecutionHealthTest {
    @Test void finisherNeverInjectsExtremeOrNonFiniteDamage(){
        for(float hp:new float[]{0,1,9,40,Float.MAX_VALUE,Float.NaN,Float.POSITIVE_INFINITY})
            for(float shield:new float[]{0,10,Float.MAX_VALUE,Float.NaN})
                for(float max:new float[]{0,20,40,1024,Float.MAX_VALUE,Float.NaN}){
                    float damage=CanglanExecutionHealth.damage(hp,shield,max);
                    assertTrue(Float.isFinite(damage)&&damage>=64&&damage<=1_000_000);
                    assertTrue(Float.isFinite(damage*1000));
                }
        assertTrue(CanglanExecutionHealth.damage(19,0,40)>40*8);
    }
    @Test void invalidGuardDoesNotResurrectLegitimateFiniteZeroHealth(){
        assertFalse(CanglanExecutionHealth.invalid(0,0));assertFalse(CanglanExecutionHealth.invalid(1,0));
        assertTrue(CanglanExecutionHealth.invalid(Float.NaN,0));assertTrue(CanglanExecutionHealth.invalid(4,Float.NaN));
        assertTrue(CanglanExecutionHealth.invalid(Float.POSITIVE_INFINITY,1));
        assertEquals(40,CanglanExecutionHealth.restoredHealth(40,false));assertEquals(1,CanglanExecutionHealth.restoredHealth(40,true));
        assertEquals(20,CanglanExecutionHealth.restoredHealth(Float.NaN,false));
    }
}
