package com.tnc.tnc.world.ink;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InkWingedTigerModelTest {
    @Test void tigerHasItsOwnNearNeutralInkPaletteTexture()throws Exception{
        var image=javax.imageio.ImageIO.read(Path.of("src/main/resources/assets/tnc/textures/entity/ink_winged_tiger.png").toFile());
        assertEquals(256,image.getWidth());assertEquals(256,image.getHeight());
        for(int x=0;x<256;x++)for(int y=0;y<256;y++){
            int p=image.getRGB(x,y),r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
            assertTrue(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=5);}
    }
    @Test void tigerHasFourArticulatedPawsLayeredWingsStripesAndValidMaterialRegions()throws Exception{
        var geo=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/geo/entity/ink_winged_tiger.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var bones=geo.getAsJsonArray("bones");Set<String> names=new HashSet<>();int cubes=0;
        for(var entry:bones){var b=entry.getAsJsonObject();assertTrue(names.add(b.get("name").getAsString()));
            if(b.has("parent"))assertTrue(names.contains(b.get("parent").getAsString()));
            if(b.has("cubes"))for(var c:b.getAsJsonArray("cubes")){cubes++;
                for(var f:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                    var uv=f.getValue().getAsJsonObject().getAsJsonArray("uv");
                    assertTrue(uv.get(0).getAsInt()>=0&&uv.get(0).getAsInt()+8<=256);
                    assertTrue(uv.get(1).getAsInt()>=0&&uv.get(1).getAsInt()+8<=256);}}}
        assertTrue(bones.size()>=260);assertTrue(cubes>=440);
        for(String leg:List.of("fore_l","fore_r","hind_l","hind_r")){
            assertTrue(names.contains(leg+"_paw"));for(int c=0;c<4;c++)assertTrue(names.contains(leg+"_claw_"+c));}
        for(String side:List.of("l","r"))for(int i=0;i<15;i++)assertTrue(names.contains("wing_"+side+"_primary_"+i));
        assertTrue(names.contains("tail_8")&&names.contains("glow_eyes"));
        assertTrue(names.contains("brow_l")&&names.contains("muzzle_r")&&names.contains("lower_fang_l"));
        for(String side:List.of("l","r")){
            assertTrue(names.contains("folded_wing_"+side));
            for(int i=0;i<15;i++)assertTrue(names.contains("folded_wing_"+side+"_feather_"+i));
        }
        for(var entry:bones){var b=entry.getAsJsonObject();
            if(b.get("name").getAsString().startsWith("folded_wing_")&&b.has("cubes"))for(var c:b.getAsJsonArray("cubes")){
                var box=c.getAsJsonObject();var origin=box.getAsJsonArray("origin");
                assertTrue(origin.get(1).getAsDouble()>=20,"Folded feathers remain above legs");
                assertTrue(Math.abs(origin.get(0).getAsDouble())<22,"Folded wings lie close to flanks");
            }}
        for(var entry:bones){var b=entry.getAsJsonObject();
            if(b.get("name").getAsString().equals("emitter_wing_l"))assertEquals(-144,b.getAsJsonArray("pivot").get(0).getAsDouble(),.01);
            if(b.get("name").getAsString().equals("head"))for(var c:b.getAsJsonArray("cubes"))
                assertTrue(c.getAsJsonObject().getAsJsonArray("size").get(0).getAsDouble()<24,"No single full-width head box");}
        var clips=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tnc/animations/entity/ink_winged_tiger.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        for(String clip:List.of("idle","walk","claw","pounce","roar","sky_spit","phantom_cast","ink_roar","ghost_charge")){
            assertTrue(clips.has(clip));for(String bone:clips.getAsJsonObject(clip).getAsJsonObject("bones").keySet())assertTrue(names.contains(bone));}
    }
}
