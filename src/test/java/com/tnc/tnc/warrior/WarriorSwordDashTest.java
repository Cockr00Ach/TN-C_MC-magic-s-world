package com.tnc.tnc.warrior;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorSwordDashTest {
 @Test void nineRealThrustsChangeYawByFortyDegreesAndContinueThroughElevatedTarget(){
  Vec3 from=new Vec3(0,0,-5),anchor=new Vec3(0,3,0);double previous=0;
  for(int i=0;i<9;i++){
   var plan=WarriorSwordDash.plan(from,anchor,0,i,p->true);assertNotNull(plan);
   double yaw=Math.toDegrees(Math.atan2(-plan.direction().x,plan.direction().z));
   if(i>0)assertEquals(40,wrap(yaw-previous),.00001);
   previous=yaw;double along=anchor.subtract(plan.strikeFrom()).dot(plan.direction());
   assertTrue(along>0&&along<plan.strikeTo().distanceTo(plan.strikeFrom()));
   assertTrue(anchor.distanceTo(plan.strikeFrom().add(plan.direction().scale(along)))<.03);
   assertEquals(from,plan.approach().get(0));assertEquals(plan.strikeFrom(),plan.approach().get(plan.approach().size()-1));
   from=plan.strikeTo();
  }
 }
 @Test void raisedPlatformIsApproachedAboveTheLedgeInsteadOfRequiringGroundAlongTheAirRoute(){
  Vec3 from=new Vec3(0,0,-5),anchor=new Vec3(0,3,3);
  var plan=WarriorSwordDash.plan(from,anchor,0,0,p->p.z< -1||p.y>=3);assertNotNull(plan);
  for(int i=1;i<plan.approach().size();i++)assertTrue(WarriorSwordDash.clear(plan.approach().get(i-1),plan.approach().get(i),p->p.z< -1||p.y>=3));
  assertTrue(plan.strikeTo().z>3);assertTrue(plan.strikeFrom().y>=3);
 }
 @Test void aSolidWallCannotBeBypassedByTeleportingToAFreeEndpoint(){
  var plan=WarriorSwordDash.plan(new Vec3(0,0,-6),Vec3.ZERO,0,0,p->p.z< -2||p.z> -1);
  assertNull(plan);
 }
 private static double wrap(double x){return Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(x)),Math.cos(Math.toRadians(x))));}
}
