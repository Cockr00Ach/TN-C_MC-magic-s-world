package com.tnc.tnc.warrior;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import static org.junit.jupiter.api.Assertions.*;

class WarriorWeaponOrnamentsTest {
    private static final List<String> IDS=List.of("dragon_sword","frost_katana","star_rapier","crimson_warblade",
            "doom_scythe","earthshaker_hammer","sky_halberd","bow","moon_bow","storm_bow","ember_bow");
    @Test void ornamentsAreBoundedFiniteVolumesAndAnimateWithoutAccumulating() throws Exception {
        var frames=new LinkedHashMap<String,Object>();
        for(String id:IDS) {
            var first=WarriorWeaponOrnaments.build(id,0,false);
            assertFalse(first.isEmpty(),id);assertTrue(first.size()<160,id);
            assertNotEquals(first,WarriorWeaponOrnaments.build(id,33,false),id+" must move");
            for(double t:new double[]{0,1,40,10000,100000000})for(boolean active:new boolean[]{false,true}) {
                var boxes=WarriorWeaponOrnaments.build(id,t,active);
                assertTrue(boxes.size()<160,id);
                for(var b:boxes) {
                    assertTrue(Double.isFinite(b.x()+b.y()+b.z()+b.w()+b.h()+b.d()),id);
                    assertTrue(b.w()>0 && b.h()>0 && b.d()>0,id);
                    assertTrue(Math.abs(b.x())<32 && Math.abs(b.y())<32 && Math.abs(b.z())<32,id);
                }
            }
            frames.put(id,List.of(first,WarriorWeaponOrnaments.build(id,33,true)));
        }
        assertTrue(WarriorWeaponOrnaments.build("iron_sword",10,true).isEmpty());
        Path output=Path.of("build/tnc-weapon-ornaments.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(frames));
    }
}
