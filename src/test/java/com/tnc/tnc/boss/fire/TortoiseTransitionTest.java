package com.tnc.tnc.boss.fire;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TortoiseTransitionTest {
 @Test void clockAndDuration() {
  assertEquals(160,TortoiseTransition.DURATION);
  assertEquals(-1F,TortoiseTransition.age(-1,6000,.5F));
  assertEquals(24.5F,TortoiseTransition.age(1000,1024,.5F));
  assertEquals(160F,TortoiseTransition.age(1000,1200,.5F));
  assertEquals(0F,TortoiseTransition.age(1000,999,0));
 }
 @Test void flashHidesOnlyModelSwapAndClearsBeforeShellOpening() {
  assertFalse(TortoiseTransition.phaseTwo(52.99F));
  assertTrue(TortoiseTransition.phaseTwo(53));
  assertTrue(TortoiseTransition.flash(53)>=.8F);
  assertEquals(0F,TortoiseTransition.flash(52));
  for(float age=55;age<=76;age+=.25F) assertEquals(0F,TortoiseTransition.flash(age));
  assertEquals(3,TortoiseTransition.FLASH_END-TortoiseTransition.FRACTURE_START);
 }
 @Test void silenceAndPulses() {
  for(int tick=40;tick<76;tick++) assertEquals(0F,TortoiseTransition.ambient(tick));
  assertEquals(TortoiseTransition.glow(40),TortoiseTransition.glow(41));
  assertNotEquals(TortoiseTransition.glow(41),TortoiseTransition.glow(42));
 }
 @Test void glowRebuildsAfterTheVisibleOpening() {
  assertEquals(.55F,TortoiseTransition.glow(0),.0001F);
  assertEquals(.15F,TortoiseTransition.glow(40),.0001F);
  assertTrue(TortoiseTransition.glow(70) > .6F);
  float previous=TortoiseTransition.glow(76);
  for(int tick=77;tick<=124;tick++) {float value=TortoiseTransition.glow(tick);assertTrue(value>=previous);previous=value;}
  assertEquals(1F,previous,.0001F);
 }
 @Test void shakeRemainsBrief() {
  assertEquals(0F,TortoiseTransition.shake(52.99F));
  assertEquals(.35F,TortoiseTransition.shake(53),.0001F);
  assertTrue(TortoiseTransition.shake(60)>0);
  assertEquals(0F,TortoiseTransition.shake(61));
 }
 @Test void ringWindowsFollowTheRetimedBeats() {
  assertEquals(0F,TortoiseTransition.shockRadius(52));
  assertTrue(TortoiseTransition.shockRadius(64)>0);
  assertEquals(0F,TortoiseTransition.shockRadius(77));
  assertEquals(0F,TortoiseTransition.scorchRadius(123));
  assertEquals(12F,TortoiseTransition.scorchRadius(156));
  assertEquals(0F,TortoiseTransition.scorchRadius(160));
 }
 @Test void emissionBudgetPreservesSilenceAndCapsBurst() {
  assertTrue(TortoiseTransition.particleBudget(20)>0);
  for(int age=40;age<53;age++) assertEquals(0,TortoiseTransition.particleBudget(age));
  assertEquals(48,TortoiseTransition.particleBudget(53));
  assertTrue(TortoiseTransition.particleBudget(90)>0);
  assertTrue(TortoiseTransition.particleBudget(130)>0);
  assertEquals(0,TortoiseTransition.particleBudget(140));
  for(float age=-1;age<162;age+=.25F) assertTrue(TortoiseTransition.particleBudget(age)<=48);
 }
 @Test void curvesStayFiniteAndBounded() {
  for(float age=-1;age<=161;age+=.125F) for(float value:new float[]{TortoiseTransition.glow(age),TortoiseTransition.ambient(age),TortoiseTransition.flash(age),TortoiseTransition.shake(age)}) assertTrue(Float.isFinite(value)&&value>=0&&value<=1);
 }
}
