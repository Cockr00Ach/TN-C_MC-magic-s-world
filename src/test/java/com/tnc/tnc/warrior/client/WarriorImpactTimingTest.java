package com.tnc.tnc.warrior.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorImpactTimingTest {
 @Test void impactHoldsTwoPoseTicksThenCatchesUpWithoutChangingTheCastEnd(){
  for(double t=0;t<=2;t+=.125)assertEquals(0,t-WarriorImpactTiming.lag(t),.00001);
  double previous=0;for(double t=2;t<=8;t+=.125){double visual=t-WarriorImpactTiming.lag(t);assertTrue(visual>=previous);assertTrue(visual<=t);previous=visual;}
  assertEquals(5,5-WarriorImpactTiming.lag(5),.00001);assertEquals(0,WarriorImpactTiming.lag(Double.NaN));
 }
}
