package com.tnc.tnc.magic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The rupture layout must stay bounded, deterministic and purely mathematical. */
class EarthRuptureLayoutTest {
    @Test void risePunchesPastFullHeightThenSettles() {
        assertEquals(0.0,EarthRuptureLayout.rise(0),1e-9);
        assertEquals(1.0,EarthRuptureLayout.rise(1),1e-9);
        double peak=0;
        for(int i=0;i<=200;i++) {
            double value=EarthRuptureLayout.rise(i/200.0);
            assertTrue(value>=0&&value<=1.2,"Rise stays positive and bounded");
            peak=Math.max(peak,value);
        }
        assertTrue(peak>1.08,"Column visibly overshoots before settling");
    }
    @Test void impactFadesToZero() {
        assertEquals(1.0,EarthRuptureLayout.impact(0),1e-9);
        assertEquals(0.0,EarthRuptureLayout.impact(1),1e-9);
        assertTrue(EarthRuptureLayout.impact(.5)>EarthRuptureLayout.impact(.8),"Lean unwinds over time");
    }
    @Test void ejectaIsBoundedAndDeterministic() {
        for(int tier=1;tier<=5;tier++) {
            var chips=EarthRuptureLayout.ejecta(tier,5+tier*2,5.5,false,2.0);
            assertTrue(chips.size()<=18&&chips.size()>=8);
            for(var chip:chips) {
                assertTrue(chip.radius()>5.5*.42-.2&&chip.radius()<5.5+.2,"Chips sit at a column base");
                assertTrue(chip.delay()>=0&&chip.delay()<10);
                assertTrue(chip.size()>0&&chip.size()<.4);
                assertTrue(chip.lift()>0&&chip.lift()<1.6);
            }
            assertEquals(chips,EarthRuptureLayout.ejecta(tier,5+tier*2,5.5,false,2.0),"Chips are reproducible");
        }
        var aura=EarthRuptureLayout.ejecta(4,18,6.0,true,2.0);
        for(var chip:aura)assertTrue(Math.abs(chip.radius()-6.0*.78)<.3,"Aura chips hug the ring");
    }
    @Test void burstColumnsStayInsideTheBlastRadius() {
        for(boolean giant:new boolean[]{false,true}) {
            double spread=giant?9:5;
            var columns=EarthRuptureLayout.burstColumns(spread,giant);
            assertEquals(giant?14:9,columns.size());
            for(var column:columns) {
                assertTrue(column.radius()>=0&&column.radius()<=spread*1.05);
                assertTrue(column.delay()>=0&&column.delay()<8);
                assertTrue(column.size()>0&&column.size()<.5);
                assertTrue(column.lift()>0&&column.lift()<1.6);
            }
            assertEquals(columns,EarthRuptureLayout.burstColumns(spread,giant),"Columns are reproducible");
        }
    }
}
