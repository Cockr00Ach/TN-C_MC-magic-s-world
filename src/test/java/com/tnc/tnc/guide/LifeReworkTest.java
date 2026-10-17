package com.tnc.tnc.guide;

import com.google.gson.*;
import com.tnc.tnc.tavern.TavernChatterRules;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LifeReworkTest {
    private static final Path RES=Path.of("src/main/resources");
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    @Test void workshopIsOneReachableAcyclicRouteWithRealProcessingCheckpoints()throws Exception{
        var chapter=read(Path.of("questbook/ftbquests/chapters/tnc_field_04_workshop.snbt"));
        Map<String,JsonObject> nodes=new HashMap<>();for(var raw:chapter.getAsJsonArray("quests")){var q=raw.getAsJsonObject();nodes.put(q.get("id").getAsString(),q);}
        Set<String> done=new HashSet<>();int roots=0,checkpoints=0,recipes=0;
        for(var q:nodes.values()){if(q.getAsJsonArray("dependencies").isEmpty())roots++;for(var task:q.getAsJsonArray("tasks"))if(task.getAsJsonObject().get("type").getAsString().equals("advancement"))checkpoints++;
            for(var d:q.getAsJsonArray("description"))if(d.getAsString().startsWith("制作材料："))recipes++;
            assertFalse(q.getAsJsonArray("description").isEmpty());}
        for(int round=0;round<nodes.size();round++)for(var e:nodes.entrySet()){
            boolean reachable=true;for(var dep:e.getValue().getAsJsonArray("dependencies"))if(!done.contains(dep.getAsString()))reachable=false;
            if(reachable)done.add(e.getKey());
        }
        assertEquals(1,roots);assertEquals(nodes.keySet(),done,"every branch must start from the same tutorial");assertTrue(checkpoints>=8);assertTrue(recipes>=28);
    }
    @Test void plantGeometryStaysAboveSoilAndIncludesFamilySpecificMatureSilhouettes()throws Exception{
        var catalogue=read(RES.resolve("data/tnc/life_routes/catalogue.json"));Set<String> mature=new HashSet<>();int models=0;
        for(var raw:catalogue.getAsJsonArray("magic")){
            String id=raw.getAsJsonObject().get("id").getAsString();var states=read(RES.resolve("assets/tnc/blockstates/"+id+".json"));
            for(var variant:states.getAsJsonObject("variants").entrySet()){
                var v=variant.getValue().getAsJsonObject();String model=v.get("model").getAsString().replace("tnc:","");
                var mesh=read(RES.resolve("assets/tnc/models/"+model+".json"));var elements=mesh.getAsJsonArray("elements");assertNotNull(elements,id);assertTrue(elements.size()>1,id);models++;
                for(var element:elements){var e=element.getAsJsonObject();var a=e.getAsJsonArray("from");var b=e.getAsJsonArray("to");
                    assertTrue(a.get(1).getAsDouble()>=0,id+" root intersects soil");
                    assertFalse(b.get(0).getAsDouble()-a.get(0).getAsDouble()>=12&&b.get(1).getAsDouble()-a.get(1).getAsDouble()>=12&&b.get(2).getAsDouble()-a.get(2).getAsDouble()>=12,id+" full-cube foliage");
                    for(var tex:mesh.getAsJsonObject("textures").entrySet())assertTrue(Files.isRegularFile(RES.resolve("assets/tnc/textures/"+tex.getValue().getAsString().replace("tnc:","")+".png")));
                }
                if(variant.getKey().contains("age=3")||variant.getKey().contains("blooming=true"))mature.add(elements.toString());
            }
        }
        assertTrue(models>=400);assertTrue(mature.size()>=28,"species must differ in real geometry, not just item icons");
    }
    @Test void retiredViewerEntriesHaveNoUnconditionalAlternativeRecipes()throws Exception{
        Set<String> retired=new HashSet<>();for(var v:read(RES.resolve("data/c/tags/items/hidden_from_recipe_viewers.json")).getAsJsonArray("values"))retired.add(v.getAsString());
        assertEquals(14,retired.size());assertFalse(retired.contains("tnc:pasture_tray"));
        try(var files=Files.list(RES.resolve("data/tnc/recipes"))){for(var file:files.toList()){
            var recipe=read(file);boolean disabled=recipe.has("type")&&recipe.get("type").getAsString().equals("forge:conditional")&&recipe.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonArray("conditions").get(0).getAsJsonObject().get("type").getAsString().equals("forge:false");
            if(!disabled)for(String id:retired)assertFalse(recipe.toString().contains("\""+id+"\""),file+" still uses retired content "+id);
        }}
    }
    @Test void tavernLinesStayShortAndFadeWithoutSpamming()throws Exception{
        var lines=JsonParser.parseString(Files.readString(RES.resolve("assets/tnc/tavern/chatter.json"))).getAsJsonArray();Set<String> unique=new HashSet<>();
        for(var raw:lines){String line=raw.getAsString();assertFalse(line.isBlank());assertTrue(line.codePointCount(0,line.length())<=28,line);assertTrue(unique.add(line));}
        assertTrue(unique.size()>=60);assertEquals(2,TavernChatterRules.MAX_ACTIVE);assertTrue(TavernChatterRules.COOLDOWN>=600);
        assertEquals(0,TavernChatterRules.alpha(0));assertEquals(1,TavernChatterRules.alpha(20));assertEquals(0,TavernChatterRules.alpha(100));assertEquals(0,TavernChatterRules.alpha(200));
    }
}
