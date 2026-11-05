package com.tnc.tnc.world;

import com.tnc.tnc.world.skyhaven.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeaStormVisibilityTest {
    @Test void arrivingNearRootsReplaceOldFarRootsThroughAFadeWithoutExceedingTen(){
        var slots=new SeaStormVisibility();
        var old=SeaTornadoLayout.near(0,0);
        slots.update(old,0,0,0);slots.update(old,0,0,40);
        var initial=List.copyOf(slots.roots());
        var next=SeaTornadoLayout.near(112,0);
        var nearest=next.stream().filter(g->!initial.contains(g)).findFirst().orElseThrow();
        var before=slots.update(next,112,0,41);
        for(var g:before)assertTrue(slots.opacity(g,41)>0);
        var halfway=slots.update(next,112,0,56);
        assertTrue(halfway.stream().anyMatch(g->slots.opacity(g,56)>0&&slots.opacity(g,56)<1));
        for(int tick=71;tick<400;tick++){
            var roots=slots.update(next,112,0,tick);assertTrue(roots.size()<=10);
            for(var g:roots)assertTrue(slots.opacity(g,tick)>=0&&slots.opacity(g,tick)<=1);
        }
        assertTrue(slots.roots().contains(nearest),"Old roots cannot permanently starve a new nearby storm");
    }
    @Test void unloadedOrNonSeaCandidatesAreRemovedAndWorldResetDropsAllState(){
        var slots=new SeaStormVisibility();slots.update(SeaTornadoLayout.near(0,0),0,0,0);
        slots.update(List.of(),0,0,40);assertTrue(slots.roots().isEmpty());
        slots.update(SeaTornadoLayout.near(0,0),0,0,41);
        var g=slots.roots().get(0);assertEquals(0,slots.opacity(g,41));
        slots.clear();assertTrue(slots.roots().isEmpty());assertEquals(0,slots.opacity(g,100));
    }
    @Test void turningBackDuringRetirementCannotRestoreAnOldRootAtFullOpacity(){
        var old=new ArrayList<SeaGyreLayout.Gyre>();
        for(int x=-150;x<=150&&old.size()<10;x+=100)for(int z=-100;z<=100&&old.size()<10;z+=100)
            old.add(new SeaGyreLayout.Gyre(x,z,50,.7,1));
        var slots=new SeaStormVisibility();slots.update(old,0,0,0);slots.update(old,0,0,40);
        var ahead=new ArrayList<>(old);ahead.add(new SeaGyreLayout.Gyre(250,0,50,1,-1));
        slots.update(ahead,120,0,41);
        var before=slots.update(old,0,0,70);
        var faded=before.stream().filter(g->slots.opacity(g,70)<.01).findFirst().orElseThrow();
        assertTrue(slots.update(old,0,0,71).contains(faded));
        assertEquals(0,slots.opacity(faded,71),"A reselected root must enter again instead of popping to full strength");
        slots.update(old,0,0,111);assertEquals(1,slots.opacity(faded,111));
    }
}
