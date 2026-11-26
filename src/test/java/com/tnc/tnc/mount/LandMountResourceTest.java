package com.tnc.tnc.mount;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandMountResourceTest {
 @Test void unicornAccelerationRequiresContinuousForwardTravel() {
  int ticks = 0;
  assertEquals(1.5f, MountMotion.unicornSpeedMultiplier(ticks));
  for (int i = 0; i < 30; i++) ticks = MountMotion.advanceUnicornRun(ticks, true, true);
  assertEquals(2.0f, MountMotion.unicornSpeedMultiplier(ticks));
  for (int i = 0; i < 90; i++) ticks = MountMotion.advanceUnicornRun(ticks, true, true);
  assertEquals(60, ticks);
  assertEquals(2.5f, MountMotion.unicornSpeedMultiplier(ticks));
  assertEquals(0, MountMotion.advanceUnicornRun(ticks, false, true));
  assertEquals(0, MountMotion.advanceUnicornRun(ticks, true, false));
  assertEquals(1.5f, MountMotion.unicornSpeedMultiplier(MountMotion.advanceUnicornRun(ticks, false, false)));
 }

 @Test void predictionReconcilesWithoutReusingProgressAfterStop() {
  assertEquals(0, MountMotion.reconcileUnicornRun(0, 54));
  assertEquals(0, MountMotion.reconcileUnicornRun(54, 0));
  assertEquals(35, MountMotion.reconcileUnicornRun(35, 30));
  assertEquals(45, MountMotion.reconcileUnicornRun(48, 33));
  assertEquals(28, MountMotion.reconcileUnicornRun(25, 36));
 }

 @Test void runtimeSeatAnchorMatchesTheRidingProfile() throws Exception {
  double modelUnit = (0.65f * LandMounts.SIZE_MULTIPLIER) / 16.0;
  for (String id : List.of("rainbow_unicorn", "mountain_goat", "snow_wolf")) {
   var geometry = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/geo/mount/" + id + ".geo.json")))
     .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
   var marker = geometry.getAsJsonArray("bones").asList().stream().map(JsonElement::getAsJsonObject)
     .filter(bone -> bone.get("name").getAsString().equals("rider_seat")).findFirst().orElseThrow();
   assertEquals("body", marker.get("parent").getAsString());
   assertFalse(marker.has("cubes"), "The rider seat must be invisible");
   var pivot = marker.getAsJsonArray("pivot");
   var pose = MountRiderPose.forSpecies(id);
   assertEquals(pivot.get(1).getAsDouble() * modelUnit, pose.ridingOffset() + .35, 0.00001);
   assertEquals(pivot.get(2).getAsDouble() * modelUnit, pose.rearOffset(), 0.00001);
  }
 }

 @Test void measuredCadenceTracksSpeedAndCapsTheFastestCycle() {
  for (String id : List.of("rainbow_unicorn", "mountain_goat", "snow_wolf")) {
   var timing = MountMotion.Cadence.forSpecies(id).gallop();
   double speed = timing.distancePerCycle() / timing.clipTicks();
   assertEquals(1.0, timing.rate(speed), 1e-6);
   assertTrue(timing.rate(speed / 2) < timing.rate(speed));
   assertEquals(id.equals("rainbow_unicorn") ? 7 : 6, timing.clipTicks() / timing.rate(.9), 1e-6);
  }
 }

 @Test void gaitDoesNotFlipOnAnIsolatedSpeedSpikeAndStopsAfterTeleport() {
  var cadence = MountMotion.Cadence.forSpecies("mountain_goat");
  var motion = new MountMotion.GroundMotion();
  for (int i = 0; i < 12; i++) motion.update(.025, cadence);
  assertEquals(MountMotion.Gait.WALK, motion.gait());
  motion.update(.22, cadence);
  for (int i = 0; i < 8; i++) motion.update(.025, cadence);
  assertEquals(MountMotion.Gait.WALK, motion.gait());
  for (int i = 0; i < 12; i++) motion.update(.4, cadence);
  assertEquals(MountMotion.Gait.GALLOP, motion.gait());
  motion.update(12, cadence);
  assertEquals(MountMotion.Gait.IDLE, motion.gait());
 }

 @Test void airborneStateHandlesJumpApexLandingAndQuickRejump() {
  var state = new MountMotion.AirMotion();
  assertEquals(MountMotion.AirPose.GROUND, state.update(false, false, -.03, false, true));
  assertEquals(MountMotion.AirPose.GROUND, state.update(true, false, 0, false, true));
  assertEquals(MountMotion.AirPose.TAKEOFF, state.update(false, false, .4, true, true));
  for (int i = 0; i < 4; i++) state.update(false, false, .24, false, true);
  assertEquals(MountMotion.AirPose.RISE, state.pose());
  assertEquals(MountMotion.AirPose.FALL, state.update(false, false, -.08, false, true));
  assertEquals(MountMotion.AirPose.LAND, state.update(true, false, -.01, false, true));
  assertEquals(MountMotion.AirPose.TAKEOFF, state.update(false, false, .25, true, true));
  assertEquals(MountMotion.AirPose.GROUND, state.update(false, true, -.1, false, true));
 }

 @Test void aRateChangeDoesNotMoveAnAlreadyRenderedFrameBackwards() {
  var clock = new MountMotion.PlaybackClock();
  clock.reset(100);
  assertEquals(4, clock.advance(102, 2), 1e-6);
  assertEquals(4, clock.advance(102, .5), 1e-6);
  assertEquals(5, clock.advance(104, .5), 1e-6);
  clock.reset(104);
  assertEquals(1, clock.advance(105, 1), 1e-6);
 }
 @Test void unicornCanStayInGallopAtFullSpeedAndTurnsByTheShortestPath() {
  var cadence = MountMotion.Cadence.forSpecies("rainbow_unicorn");
  var motion = new MountMotion.GroundMotion();
  for (int i = 0; i < 16; i++) motion.update(1.87, cadence);
  assertEquals(MountMotion.Gait.GALLOP, motion.gait());
  assertEquals(12f, MountMotion.steeredYaw(0, 90, 0), 1e-6);
  assertEquals(6f, MountMotion.steeredYaw(0, 90, 1), 1e-6);
  assertEquals(182f, MountMotion.steeredYaw(178, -178, 1), 1e-6);
  assertEquals(-6f, MountMotion.steeredYaw(0, -90, 1), 1e-6);
 }

 @Test void iceCompensationPreservesTractionAndSuppressesSideSlip() {
  for (float ice : new float[] {.98f, .989f}) {
   double sameInput = Math.pow(.6 / ice, 3) * MountMotion.iceInputCompensation(ice);
   assertEquals(1, sameInput, 1e-6);
   assertEquals(.546, .91 * ice * MountMotion.NORMAL_FRICTION / ice, 1e-6);
  }
  var velocity = new net.minecraft.world.phys.Vec3(.6, .12, .4);
  var forward = new net.minecraft.world.phys.Vec3(0, 0, 1);
  var held = MountMotion.iceSteer(velocity, forward);
  assertEquals(.4, held.z, 1e-6);
  assertEquals(.216, held.x, 1e-6);
  assertEquals(.12, held.y, 1e-6);
 }

 @Test void wolfSwimUsesDepthHysteresisAndBoundedBuoyancy() {
  assertFalse(MountMotion.swimming(.45, false));
  assertTrue(MountMotion.swimming(.56, false));
  assertTrue(MountMotion.swimming(.45, true));
  assertFalse(MountMotion.swimming(.30, true));
  var idle = net.minecraft.world.phys.Vec3.ZERO;
  var forward = new net.minecraft.world.phys.Vec3(0, 0, 1);
  var velocity = MountMotion.swimVelocity(idle, forward, .51f, 1.5, true);
  assertTrue(velocity.y > 0 && velocity.y <= .105);
  assertTrue(velocity.z > 0);
  double first = velocity.z;
  for (int i = 0; i < 40; i++) {
   velocity = MountMotion.swimVelocity(velocity.multiply(.8, .8, .8), forward, .51f, 1.0, true);
  }
  double landSpeed = .51 / (1 - .91 * .6);
  assertTrue(velocity.z > first && velocity.z / landSpeed > .35 && velocity.z / landSpeed < .45);
  assertTrue(MountMotion.swimVelocity(idle, idle, .51f, .375, true).y < 0);
  assertEquals(velocity.z, MountMotion.swimVelocity(velocity, idle, .51f, .7, false).z, 1e-6);
 }

 @Test void unicornExportsHoofbeatsAndIndependentHairPivots() throws Exception {
  String id = "rainbow_unicorn";
  Path root = Path.of("src/main/resources/assets/tnc");
  var geometry = JsonParser.parseString(Files.readString(root.resolve("geo/mount/" + id + ".geo.json")))
      .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
  var bones = new HashMap<String, JsonObject>();
  for (var bone : geometry.getAsJsonArray("bones")) {
   var part = bone.getAsJsonObject();
   bones.put(part.get("name").getAsString(), part);
  }
  assertEquals("run_mane_lean", bones.get("mane").get("parent").getAsString());
  assertEquals("run_mane_lean", bones.get("mane_center").get("parent").getAsString());
  assertEquals("run_tail_lean", bones.get("tail").get("parent").getAsString());
  assertFalse(bones.get("run_mane_lean").has("cubes"));
  assertFalse(bones.get("run_tail_lean").has("cubes"));
  var clips = JsonParser.parseString(Files.readString(root.resolve("animations/mount/" + id + ".animation.json")))
      .getAsJsonObject().getAsJsonObject("animations");
  for (String mode : List.of("walk", "trot", "gallop")) {
   var clip = clips.getAsJsonObject("animation." + id + "." + mode);
   var effects = clip.getAsJsonObject("sound_effects");
   assertEquals(mode.equals("trot") ? 2 : 4, effects.size());
   for (var beat : effects.entrySet()) {
    double time = Double.parseDouble(beat.getKey());
    assertTrue(time >= 0 && time < clip.get("animation_length").getAsDouble());
    assertEquals("hoofbeat", beat.getValue().getAsJsonObject().get("effect").getAsString());
   }
  }
 }

 @Test void allExportedClipsResolveBonesAndLoopExactly()throws Exception{
  for(String id:List.of("rainbow_unicorn","mountain_goat","snow_wolf")){
   Path root=Path.of("src/main/resources/assets/tnc");
   var geo=JsonParser.parseString(Files.readString(root.resolve("geo/mount/"+id+".geo.json"))).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
   Set<String> bones=new HashSet<>();for(var bone:geo.getAsJsonArray("bones"))assertTrue(bones.add(bone.getAsJsonObject().get("name").getAsString()));
   assertFalse(bones.contains("saddle"),"Bareback mounts must not ship saddle geometry");
   var clips=JsonParser.parseString(Files.readString(root.resolve("animations/mount/"+id+".animation.json"))).getAsJsonObject().getAsJsonObject("animations");
   var modes = new ArrayList<>(List.of("idle","walk","trot","gallop","takeoff","rise","fall","land"));
   if (id.equals("snow_wolf")) modes.add("swim");
   for(String mode:modes){
    var clip=clips.getAsJsonObject("animation."+id+"."+mode);assertNotNull(clip);
    boolean cyclic = List.of("idle","walk","trot","gallop","swim").contains(mode);
    if(cyclic) assertTrue(clip.get("loop").getAsBoolean());
    else assertEquals("hold_on_last_frame", clip.get("loop").getAsString());
    double length=clip.get("animation_length").getAsDouble();assertTrue(length>0);
    for(var entry:clip.getAsJsonObject("bones").entrySet()){
     assertTrue(bones.contains(entry.getKey()),entry.getKey());
     for(var channel:entry.getValue().getAsJsonObject().entrySet()){
      var keys=channel.getValue().getAsJsonObject();var first=keys.get("0.0");assertNotNull(first);
      JsonElement end=null;for(var key:keys.entrySet()){double time=Double.parseDouble(key.getKey());assertTrue(time>=0&&time<=length+.0001);if(Math.abs(time-length)<.0001)end=key.getValue();}
      assertNotNull(end,id+"/"+mode+"/"+entry.getKey());
      if(cyclic) assertEquals(first,end,id+"/"+mode+"/"+entry.getKey());
     }
    }
   }
   assertTrue(Files.size(root.resolve("textures/entity/mount/"+id+".png"))>100);
  }
 }
 @Test void upperLegsHaveNoAbruptRotationChanges()throws Exception{
  for(String id:List.of("rainbow_unicorn","mountain_goat","snow_wolf")){
   var clips=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/animations/mount/"+id+".animation.json"))).getAsJsonObject().getAsJsonObject("animations");
   for(var clip:clips.entrySet())for(var bone:clip.getValue().getAsJsonObject().getAsJsonObject("bones").entrySet()){
    String name=bone.getKey();if(!name.startsWith("front")&&!name.startsWith("hind"))continue;
    if(name.endsWith("_shin")||name.endsWith("_foot")||name.endsWith("_hock")||name.endsWith("_carpus"))continue;
    Double previous=null;
    for(var k:bone.getValue().getAsJsonObject().getAsJsonObject("rotation").entrySet()){
     double x=k.getValue().getAsJsonArray().get(0).getAsDouble();assertTrue(Math.abs(x)<=68.01,id+"/"+clip.getKey()+"/"+name);
     if(previous!=null)assertTrue(Math.abs(x-previous)<35,"Abrupt upper-leg flip: "+id+"/"+name);previous=x;
    }
   }
  }
 }
 @Test void allThreeForelegsHaveTwoDistinctRestTurns()throws Exception{
  for(String id:List.of("rainbow_unicorn","mountain_goat","snow_wolf")){
   var geo=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/geo/mount/"+id+".geo.json"))).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
   Map<String,JsonObject> bones=new HashMap<>();for(var x:geo.getAsJsonArray("bones"))bones.put(x.getAsJsonObject().get("name").getAsString(),x.getAsJsonObject());
   int legs=0;
   for(String n:bones.keySet())if(n.startsWith("front")&&!n.endsWith("_shin")&&!n.endsWith("_carpus")&&!n.endsWith("_foot")){
    legs++;double a=bones.get(n).getAsJsonArray("pivot").get(2).getAsDouble(),b=bones.get(n+"_shin").getAsJsonArray("pivot").get(2).getAsDouble(),c=bones.get(n+"_carpus").getAsJsonArray("pivot").get(2).getAsDouble(),d=bones.get(n+"_foot").getAsJsonArray("pivot").get(2).getAsDouble();
    assertTrue(b-a>1.2,id+" upper turn");assertTrue(b-c>1.6,id+" forearm direction");assertTrue(d-c>.3,id+" lower turn");
   }
   assertEquals(2,legs,id);
  }
 }
}
