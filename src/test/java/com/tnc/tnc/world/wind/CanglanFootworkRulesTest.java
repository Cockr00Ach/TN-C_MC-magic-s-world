package com.tnc.tnc.world.wind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CanglanFootworkRulesTest {
 @Test void spacingMovesWidenThenCloseTheGap(){
  double[] first=CanglanWarGodRules.stepOffset(6,0,1,0,false,1);
  assertEquals(14,Math.hypot(6+first[0],first[1]),1E-6);
  double[] second=CanglanWarGodRules.stepOffset(14,0,1,1,false,-1);
  assertEquals(20,Math.hypot(14+second[0],second[1]),1E-6);
  double[] close=CanglanWarGodRules.stepOffset(20,0,1,2,false,1);
  assertEquals(5.5,Math.hypot(20+close[0],close[1]),1E-6);
 }
 @Test void movementUsesACapInsteadOfAlwaysTakingFullDistance(){
  assertArrayEquals(new double[]{0,0},CanglanWarGodRules.stepOffset(18,0,1,0,false,1));
  for(float scale:new float[]{1,5})for(int stage=0;stage<3;stage++)for(double distance:new double[]{4,12,25,80,200}){
   double[] step=CanglanWarGodRules.stepOffset(distance,0,scale,stage,false,1);
   assertTrue(Math.hypot(step[0],step[1])<=75.00001);
   double[] dodge=CanglanWarGodRules.stepOffset(distance,0,scale,stage,true,-1);
   assertTrue(Math.hypot(dodge[0],dodge[1])<=50.00001);
  }
 }
 @Test void sweepSelectionIsLimitedToItsRealRange(){
  for(float scale:new float[]{1,5}){
   assertEquals(5,CanglanWarGodRules.choose(1,6*scale,scale));
   assertNotEquals(5,CanglanWarGodRules.choose(1,8*scale,scale));
  }
 }
 @Test void executionRequiresStrictlyBelowHalfIncludingGoldenHearts(){
  assertFalse(CanglanWarGodRules.executionHealth(20,0,20));
  assertFalse(CanglanWarGodRules.executionHealth(11,0,20));
  assertFalse(CanglanWarGodRules.executionHealth(10,0,20));
  assertFalse(CanglanWarGodRules.executionHealth(8,4,20));
  assertTrue(CanglanWarGodRules.executionHealth(9,0,20));
 }
}
