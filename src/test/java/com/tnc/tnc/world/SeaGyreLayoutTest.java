package com.tnc.tnc.world;
import com.tnc.tnc.world.skyhaven.SeaGyreLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SeaGyreLayoutTest {
 @Test void gyresHaveLargeScaleAndBoundedCost(){var list=SeaGyreLayout.near(0,0);assertTrue(list.size()<=9&&!list.isEmpty());for(var g:list){assertTrue(g.radius()>=34&&g.radius()<45);assertTrue(Math.hypot(g.x(),g.z())<=210);}}
 @Test void layoutRemainsWorldAnchoredAcrossCellTransitions(){var a=SeaGyreLayout.near(95,50);var b=SeaGyreLayout.near(97,50);assertTrue(a.stream().filter(b::contains).count()>=6);assertEquals(a,SeaGyreLayout.near(95,50));}
}
