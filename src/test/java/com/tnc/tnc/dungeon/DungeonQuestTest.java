package com.tnc.tnc.dungeon;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DungeonQuestTest {
 @Test void signedIdsCannotCollideWithAnyAuthoredChapterAndMilestonesExist()throws Exception{
  var ids=new HashSet<String>();var nodes=new HashSet<String>();var dependencies=new ArrayList<String>();int milestones=0;
  try(var paths=Files.list(Path.of("questbook/ftbquests/chapters"))){for(var file:paths.filter(p->p.toString().endsWith(".snbt")).toList()){
   var c=JsonParser.parseString(Files.readString(file)).getAsJsonObject();boolean dungeon=file.getFileName().toString().equals("tnc_dungeons.snbt");assertTrue(ids.add(c.get("id").getAsString()));
   for(var el:c.getAsJsonArray("quests")){var q=el.getAsJsonObject();String id=q.get("id").getAsString();assertTrue(ids.add(id),id);nodes.add(id);if(dungeon){assertTrue(Long.parseLong(id,16)>0);for(var dep:q.getAsJsonArray("dependencies"))dependencies.add(dep.getAsString());}
    for(String key:List.of("tasks","rewards"))if(q.has(key))for(var v:q.getAsJsonArray(key)){var t=v.getAsJsonObject();assertTrue(ids.add(t.get("id").getAsString()));if(dungeon){assertTrue(Long.parseLong(t.get("id").getAsString(),16)>0);if(t.has("advancement")){String path=t.get("advancement").getAsString().split(":")[1];assertTrue(Files.exists(Path.of("src/main/resources/data/tnc/advancements/"+path+".json")));milestones++;}if(key.equals("rewards"))assertEquals("tnc:dungeon_handbook",t.get("item").getAsString());}}
   }
  }}assertEquals(25,milestones);assertTrue(nodes.containsAll(dependencies));
 }
}
