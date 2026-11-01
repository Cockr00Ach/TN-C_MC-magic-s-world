package com.tnc.tnc.world.wind;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CanglanSurfaceRefinementTest {
    private static final Path ROOT=Path.of("src/main/resources/assets/tnc");
    private static final Path SOURCE=Path.of("model-source/canglan_warlord/surface-refinement-20261022");
    private JsonObject geometry(Path file)throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }
    @Test void authoredMotionAndOriginalRestTransformsStayIntact()throws Exception {
        assertArrayEquals(Files.readAllBytes(SOURCE.resolve("unchanged.animation.json")),
                Files.readAllBytes(ROOT.resolve("animations/entity/canglan_warlord.animation.json")));
        var old=geometry(SOURCE.resolve("before.geo.json")).getAsJsonArray("bones");
        var now=geometry(ROOT.resolve("geo/entity/canglan_warlord.geo.json")).getAsJsonArray("bones");
        assertEquals(old.size(),now.size());int before=0,after=0;
        for(int i=0;i<old.size();i++){
            var a=old.get(i).getAsJsonObject().deepCopy();var b=now.get(i).getAsJsonObject().deepCopy();
            var ac=a.remove("cubes").getAsJsonArray();var bc=b.remove("cubes").getAsJsonArray();
            assertEquals(a,b,"Rest bone transform changed");
            assertTrue(bc.size()>=ac.size());before+=ac.size();after+=bc.size();
            for(int k=0;k<ac.size();k++){
                var x=ac.get(k).getAsJsonObject().deepCopy();var y=bc.get(k).getAsJsonObject().deepCopy();
                x.remove("uv");y.remove("uv");assertEquals(x,y,"Original sculpt was moved");
            }
        }
        assertTrue(after-before>=70);assertTrue(after<=560,"Echo geometry budget exceeded");
    }
    @Test void atlasHasDistinctPartAssignmentsAndNonflatMaterials()throws Exception {
        var image=ImageIO.read(ROOT.resolve("textures/entity/canglan_warlord.png").toFile());
        assertEquals(1024,image.getWidth());assertEquals(1024,image.getHeight());
        Set<Integer> used=new HashSet<>();
        var model=geometry(ROOT.resolve("geo/entity/canglan_warlord.geo.json"));
        assertEquals(256,model.getAsJsonObject("description").get("texture_width").getAsInt());
        for(var value:model.getAsJsonArray("bones"))for(var part:value.getAsJsonObject().getAsJsonArray("cubes")){
            for(var entry:part.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var face=entry.getValue().getAsJsonObject();var at=face.getAsJsonArray("uv");var size=face.getAsJsonArray("uv_size");
                double u=at.get(0).getAsDouble(),v=at.get(1).getAsDouble();int tile=(int)(u/64)+4*(int)(v/64);used.add(tile);
                assertTrue(u+size.get(0).getAsDouble()<=(tile%4+1)*64);
                assertTrue(v+size.get(1).getAsDouble()<=(tile/4+1)*64);
            }
        }
        assertTrue(used.size()>=14,"Different surfaces must not collapse to four flat swatches");
        for(int tile:used){
            Set<Integer> colors=new HashSet<>();int lo=255,hi=0;
            for(int y=20;y<236;y+=3)for(int x=20;x<236;x+=3){
                int c=image.getRGB(tile%4*256+x,tile/4*256+y);colors.add(c&0xFFFFFF);
                int l=(((c>>16)&255)+((c>>8)&255)+(c&255))/3;lo=Math.min(lo,l);hi=Math.max(hi,l);
            }
            assertTrue(colors.size()>100,"Flat material tile "+tile);
            assertTrue(hi-lo>25,"Insufficient material contrast in tile "+tile);
        }
    }
}
