package com.tnc.tnc.equipment;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WearableGeometryTest {
    @Test void everyWearableHasAnimatedBonesAndAllUVsFitItsOwnAtlas() throws Exception {
        for (String style : List.of("bastion","astral","runic","wanderer","divine"))
            for (int tier : style.equals("divine")?new int[]{5}:new int[]{1,2,3,4}) for (boolean hat : new boolean[]{false,true}) {
                String id = style + (hat?"_hat_":"_outfit_") + tier;
                try (var in = getClass().getResourceAsStream("/assets/tnc/models/armor/"+id+".json")) {
                    assertNotNull(in,id); var model = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                    int width = model.get("texture_width").getAsInt(), height = model.get("texture_height").getAsInt();
                    Set<String> bones = new HashSet<>(Set.of("root")); int boxes=0;
                    for (var value : model.getAsJsonArray("parts")) {
                        var part = value.getAsJsonObject(); String name = part.get("name").getAsString();
                        assertTrue(bones.contains(part.get("parent").getAsString()),id+" parent before child");
                        assertTrue(bones.add(name),id+" no duplicate clothing bone");
                        for (var b : part.getAsJsonArray("boxes")) {
                            boxes++; var box=b.getAsJsonObject(); var uv=box.getAsJsonArray("uv"); var s=box.getAsJsonArray("size");
                            float w=s.get(0).getAsFloat(),h=s.get(1).getAsFloat(),d=s.get(2).getAsFloat();
                            assertTrue(w>0&&h>0&&d>0,id+" positive solid cloth geometry");
                            assertTrue(uv.get(0).getAsFloat()+2*(w+d)<=width+.01,id+" horizontal cube unwrap fits texture");
                            assertTrue(uv.get(1).getAsFloat()+h+d<=height+.01,id+" vertical cube unwrap fits texture");
                        }
                    }
                    assertTrue(bones.containsAll(List.of("head","hat","body","left_arm","right_arm","left_leg","right_leg")),id);
                    assertTrue(boxes >= 5,id);
                    if (!hat && !style.equals("bastion")) assertTrue(bones.containsAll(List.of("front_left","front_right","back_left","back_right","side_left","side_right")), id+" separates draped quarters from trousers");
                }
            }
    }
}
