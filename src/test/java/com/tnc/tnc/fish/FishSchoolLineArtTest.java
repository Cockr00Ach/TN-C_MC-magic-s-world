package com.tnc.tnc.fish;

import com.tnc.tnc.fish.client.FishSchoolLineArt;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishSchoolLineArtTest {
    @Test void animatedInkAndFillStayInsideTheServerCollisionHull() {
        var points=new ArrayList<FishSchoolLineArt.Point>();
        for (var s:FishSchoolLineArt.STROKES) points.addAll(s.points());
        for (var t:FishSchoolLineArt.FILL) { points.add(t.a());points.add(t.b());points.add(t.c()); }
        for (boolean metal:new boolean[]{false,true}) for (int lane=0;lane<12;lane++)
            for (double age=0;age<=24;age+=.25) for (var p:points) {
                var v=FishSchoolLineArt.animate(p,age,lane,metal);
                assertTrue(Math.abs(v.x())+.0055<=FishSchoolGeometry.HALF_WIDTH);
                assertTrue(Math.abs(v.y())+.0055<=FishSchoolGeometry.HALF_HEIGHT);
                assertTrue(Math.abs(v.z())+.0055<=FishSchoolGeometry.HALF_LENGTH);
            }
    }
    @Test void drawingHasVolumeDistinctStrokeEndpointsAndABoundedCost() {
        assertTrue(FishSchoolLineArt.segments()<100);
        assertTrue(FishSchoolLineArt.FILL.size()<32);
        var unique=new HashSet<String>();
        for (var s:FishSchoolLineArt.STROKES) for (int i=1;i<s.points().size();i++) {
            var a=s.points().get(i-1);var b=s.points().get(i);assertNotEquals(a,b);
            String x=a.toString(),y=b.toString();assertTrue(unique.add(x.compareTo(y)<0?x+y:y+x),"duplicate ink segment");
        }
        assertTrue(FishSchoolLineArt.STROKES.stream().flatMap(s->s.points().stream()).anyMatch(p->p.x()>.1));
        assertTrue(FishSchoolLineArt.STROKES.stream().flatMap(s->s.points().stream()).anyMatch(p->p.x()<-.1));
    }
    @Test void tailAnimationIsIndividualAndTheNoseStaysForward() {
        var nose=new FishSchoolLineArt.Point(0,.01,.31);var tail=new FishSchoolLineArt.Point(0,.16,-.335);
        assertEquals(nose,FishSchoolLineArt.animate(nose,12,0,false));
        assertNotEquals(FishSchoolLineArt.animate(tail,12,0,false),FishSchoolLineArt.animate(tail,12,1,false));
        assertNotEquals(FishSchoolLineArt.animate(tail,12,0,false),FishSchoolLineArt.animate(tail,12,0,true));
    }
    @Test void seededSchoolsRemainBoundedMonotoneAndIrregular() {
        for (int seed:new int[]{0,1,777,-1,Integer.MIN_VALUE}) for (boolean metal:new boolean[]{false,true}) {
            var sides=new HashSet<Long>();var heights=new HashSet<Long>();var depths=new HashSet<Long>();
            for (int lane=0;lane<12;lane++) {
                var sample=FishSchoolGeometry.offset(lane,16,metal,seed);
                sides.add(Math.round(sample.side()*100));heights.add(Math.round(sample.up()*100));depths.add(Math.round(sample.forward()*100));
                double previous=0;
                for (double age=0;age<=24;age+=.125) {
                    var p=FishSchoolGeometry.offset(lane,age,metal,seed);
                    assertTrue(Math.abs(p.side())+FishSchoolGeometry.HALF_WIDTH<=1.5);
                    assertTrue(p.forward()+FishSchoolGeometry.RADIUS<=10);
                    assertTrue(p.forward()>=previous);previous=p.forward();
                }
            }
            assertTrue(sides.size()>=10);assertTrue(heights.size()>=9);assertTrue(depths.size()>=10);
        }
        assertNotEquals(FishSchoolGeometry.offset(2,16,false,0),FishSchoolGeometry.offset(2,16,false,777));
    }
}
