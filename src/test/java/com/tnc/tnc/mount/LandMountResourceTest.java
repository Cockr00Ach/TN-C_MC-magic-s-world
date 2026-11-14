package com.tnc.tnc.mount;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandMountResourceTest {
 @Test void animationThresholdsAndRateAreBounded(){
  assertEquals("idle",MountMotion.clip(0));assertEquals("walk",MountMotion.clip(.05));assertEquals("trot",MountMotion.clip(.15));assertEquals("gallop",MountMotion.clip(.3));
  assertTrue(MountMotion.rate("gallop",.4)>MountMotion.rate("gallop",.25));assertTrue(MountMotion.rate("gallop",50)<=1.65);
 }
 @Test void allExportedClipsResolveBonesAndLoopExactly()throws Exception{
  for(String id:List.of("rainbow_unicorn","mountain_goat","snow_wolf")){
   Path root=Path.of("src/main/resources/assets/tnc");
   var geo=JsonParser.parseString(Files.readString(root.resolve("geo/mount/"+id+".geo.json"))).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
   Set<String> bones=new HashSet<>();for(var bone:geo.getAsJsonArray("bones"))assertTrue(bones.add(bone.getAsJsonObject().get("name").getAsString()));
   assertFalse(bones.contains("saddle"),"Bareback mounts must not ship saddle geometry");
   var clips=JsonParser.parseString(Files.readString(root.resolve("animations/mount/"+id+".animation.json"))).getAsJsonObject().getAsJsonObject("animations");
   for(String mode:List.of("idle","walk","trot","gallop")){
    var clip=clips.getAsJsonObject("animation."+id+"."+mode);assertNotNull(clip);assertTrue(clip.get("loop").getAsBoolean());double length=clip.get("animation_length").getAsDouble();assertTrue(length>0);
    for(var entry:clip.getAsJsonObject("bones").entrySet()){
     assertTrue(bones.contains(entry.getKey()),entry.getKey());
     for(var channel:entry.getValue().getAsJsonObject().entrySet()){
      var keys=channel.getValue().getAsJsonObject();var first=keys.get("0.0");assertNotNull(first);
      JsonElement end=null;for(var key:keys.entrySet()){double time=Double.parseDouble(key.getKey());assertTrue(time>=0&&time<=length+.0001);if(Math.abs(time-length)<.0001)end=key.getValue();}
      assertEquals(first,end,id+"/"+mode+"/"+entry.getKey());
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
