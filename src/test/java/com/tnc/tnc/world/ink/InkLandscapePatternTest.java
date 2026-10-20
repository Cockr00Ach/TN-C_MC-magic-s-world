package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InkLandscapePatternTest {
    @Test void spawnStaysOpenAndThePatternIsDeterministicAcrossNegativeChunks() {
        for(int x=-24;x<=24;x+=3)for(int z=-24;z<=24;z+=3) {
            assertEquals(0,InkLandscapePattern.mountainHeight(x,z));
        }
        for(int x=-400;x<400;x+=7)for(int z=-400;z<400;z+=11) {
            var column=InkLandscapePattern.column(x,z);
            assertEquals(column,InkLandscapePattern.column(x,z));
            assertTrue(column.mountain()>=0 && column.mountain()<InkLandscapePattern.MAX_HEIGHT);
            assertNull(column.at(0));assertNull(column.at(InkLandscapePattern.MAX_HEIGHT+1));
        }
    }
    @Test void landscapeContainsTallCliffsInkTrunksAndLayeredNeedlesNotOnlyFlatPaint() {
        int cliff=0,trunk=0,canopy=0,paper=0;
        for(int x=-256;x<=256;x+=2)for(int z=-256;z<=256;z+=2) {
            var c=InkLandscapePattern.column(x,z);
            if(c.mountain()>35)cliff++;
            for(int y=c.mountain()+1;y<=InkLandscapePattern.MAX_HEIGHT;y++) {
                var m=c.at(y);
                if(m==InkLandscapePattern.Material.INK)trunk++;
                if(m==InkLandscapePattern.Material.NEEDLES)canopy++;
            }
            if(c.mountain()>0 && c.at(c.mountain())==InkLandscapePattern.Material.PAPER)paper++;
        }
        assertTrue(cliff>100);assertTrue(trunk>100);assertTrue(canopy>500);assertTrue(paper>100);
    }
}
