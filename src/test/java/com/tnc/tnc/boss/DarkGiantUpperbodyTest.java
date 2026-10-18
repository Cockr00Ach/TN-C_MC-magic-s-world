package com.tnc.tnc.boss;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class DarkGiantUpperbodyTest {
    @Test void articulatedChainsKeepShoulderArmorSeparateAndShareAnAlignedAtlas()throws Exception{
        Path root=Path.of("src/main/resources/assets/tnc");
        var geo=JsonParser.parseString(Files.readString(root.resolve("geo/entity/dark_giant.geo.json"))).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var bones=new HashMap<String,JsonObject>();geo.getAsJsonArray("bones").forEach(b->bones.put(b.getAsJsonObject().get("name").getAsString(),b.getAsJsonObject()));
        for(String side:List.of("right","left")){
            assertEquals("shoulder"+side,bones.get("arm"+side).get("parent").getAsString());
            assertEquals("arm"+side,bones.get("armforearm"+side).get("parent").getAsString());
            assertEquals("armforearm"+side,bones.get("armhand"+side).get("parent").getAsString());
            assertEquals("shoulder"+side,bones.get("pauldron"+side).get("parent").getAsString());
            for(int digit=0;digit<4;digit++)assertEquals("finger_"+side+"_"+digit,bones.get("finger_"+side+"_"+digit+"_tip").get("parent").getAsString());
        }
        var base=ImageIO.read(root.resolve("textures/entity/dark_giant_bedrock.png").toFile());var glow=ImageIO.read(root.resolve("textures/entity/dark_giant_emissive.png").toFile());
        assertEquals(base.getWidth(),glow.getWidth());assertEquals(base.getHeight(),glow.getHeight());
        assertEquals(1024,geo.getAsJsonObject("description").get("texture_width").getAsInt());
        for(var b:bones.values())if(b.has("cubes"))for(var c:b.getAsJsonArray("cubes")){
            for(var face:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var v=face.getValue().getAsJsonObject();for(int axis=0;axis<2;axis++){
                    double start=v.getAsJsonArray("uv").get(axis).getAsDouble(),end=start+v.getAsJsonArray("uv_size").get(axis).getAsDouble();
                    assertTrue(Math.min(start,end)>=0&&Math.max(start,end)<=1024,"UV inside the declared atlas");
                }
            }
        }
    }
}
