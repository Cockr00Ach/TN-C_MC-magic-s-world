package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.client.WarriorComboAnimations;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorUltimateTest {
    @Test void everyWeaponHasUniqueChoreographyAndTenTapInput(){
        Set<String> keys=new HashSet<>(),names=new HashSet<>(),moves=new HashSet<>();
        assertEquals(31,WarriorUltimates.all().size());
        for(var art:WarriorUltimates.all()){
            assertEquals(10,art.keys().length());assertTrue(keys.add(art.keys()));assertTrue(names.add(art.name()));
            assertTrue(moves.add(art.strokes().toString()));
            var input=new WarriorUltimateInput();long now=10;
            for(int i=0;i<10;i++){assertEquals(i==9,input.push(art.id(),art.keys(),art.keys().charAt(i),now));now+=4;}
            var clip=WarriorComboAnimations.clip(WarriorAnimation.ULTIMATE,"tnc:"+art.id());assertNotNull(clip);
            assertEquals(4.8,clip.lengthSeconds(),.001);
            for(String bone:List.of("bone","head","armRight","armLeft","legRight","legLeft")){
                assertTrue(clip.moves(bone),art.id()+" missing "+bone);
                for(float x:clip.rotation(bone,4.8f))assertEquals(0,x,.001);
            }
            int last=22;for(var stroke:art.strokes()){assertTrue(stroke.tick()>last);assertTrue(stroke.tick()<96);last=stroke.tick();}
        }
    }
    @Test void timeoutSwapAndSpamCannotCompleteInput(){
        var a=WarriorUltimates.forId("tnc:red_tassel_spear");var t=new WarriorUltimateInput();
        for(int i=0;i<9;i++)assertFalse(t.push(a.id(),a.keys(),a.keys().charAt(i),i*4));
        assertFalse(t.push(a.id(),a.keys(),a.keys().charAt(9),100));
        t.clear();for(int i=0;i<9;i++)t.push(a.id(),a.keys(),a.keys().charAt(i),i*4);
        assertFalse(t.push("different",a.keys(),a.keys().charAt(9),36));
        t.clear();for(char c:a.keys().toCharArray())assertFalse(t.push(a.id(),a.keys(),c,1));
    }
    @Test void thrustAndSweepHaveDifferentHitShapes(){
        var thrust=new WarriorUltimates.Stroke(26,"T",0,7,1);var orbit=new WarriorUltimates.Stroke(26,"O",0,7,1);
        Vec3 f=new Vec3(0,0,1);
        assertTrue(WarriorUltimates.contains(thrust,new Vec3(0,0,6),f));
        assertFalse(WarriorUltimates.contains(thrust,new Vec3(4,0,1),f));
        assertTrue(WarriorUltimates.contains(orbit,new Vec3(4,0,1),f));
        assertFalse(WarriorUltimates.contains(orbit,new Vec3(0,4,0),f));
    }
    @Test void spectralBladesHaveBoundedGeometryAndExpire(){
        var points=new ArrayList<WarriorTrailVolume.Point>();for(int i=0;i<64;i++)points.add(new WarriorTrailVolume.Point(Vec3.ZERO,new Vec3(i*.01,2,0),i));
        int[] quads={0};WarriorTrailVolume.Sink sink=(a,b,c,d,color,aa,ab,ac,ad)->{quads[0]++;assertTrue(ab>=0&&ab<.15);};
        WarriorBladeEcho.emit(points,64,100,8,0xffffff,sink);assertEquals(9,quads[0]);
        quads[0]=0;WarriorBladeEcho.emit(points,300,100,8,0xffffff,sink);assertEquals(0,quads[0]);
    }
}
