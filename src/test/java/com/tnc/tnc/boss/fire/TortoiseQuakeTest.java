package com.tnc.tnc.boss.fire;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TortoiseQuakeTest {
    @Test void propagationStillMatchesServerAndExpiresAtFiftyTicks() {
        assertEquals(11,TortoiseQuake.radius(20));assertEquals(22,TortoiseQuake.radius(40));
        assertEquals(22,TortoiseQuake.radius(49));assertEquals(0,TortoiseQuake.opacity(50));
        assertTrue(TortoiseQuakeTiles.cells(0,0,50).isEmpty());
    }
    @Test void fixedPiecesRiseWellAboveGroundAndReturn() {
        double at=TortoiseQuakeTiles.arrival(12);
        assertEquals(0,TortoiseQuakeTiles.lift(at-3,12));
        assertTrue(TortoiseQuakeTiles.lift(at-1.5D,12)>0.6D);
        assertEquals(1.30D,TortoiseQuakeTiles.lift(at,12),1E-9);
        assertTrue(TortoiseQuakeTiles.lift(at+4,12)<0.4D);
        assertEquals(0,TortoiseQuakeTiles.lift(at+10,12));
    }
    @Test void everyNegativeHeightTopCornerStaysAboveTheOriginalSurface() {
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) {
            double distance=Math.hypot(x+0.5D-0.2D,z+0.5D+0.3D);
            var cell=new TortoiseQuakeTiles.Cell(x,z,distance);
            for(double t=-3;t<=10;t+=0.25D) {
                double age=TortoiseQuakeTiles.arrival(distance)+t;
                var pose=TortoiseQuakeTiles.pose(cell,0.2D,-0.3D,-60,1,age);
                for(int a=0;a<=1;a++)for(int b=0;b<=1;b++) {
                    assertTrue(pose.y(a,1,b)>=-60+pose.lift()+0.02499D,"Top corner buried under negative-Y ground");
                }
            }
        }
    }
    @Test void modelTopAndBlockBaseAreNotConfusedByOneBlock() {
        var cell=new TortoiseQuakeTiles.Cell(10,0,10);
        var rest=TortoiseQuakeTiles.pose(cell,0.5D,0.5D,-60,1,0);
        assertEquals(-59.975D,rest.y(0.5D,1,0.5D),1E-9);
        assertEquals(-60.975D,rest.y(0.5D,0,0.5D),1E-9);
        var peak=TortoiseQuakeTiles.pose(cell,0.5D,0.5D,-60,1,TortoiseQuakeTiles.arrival(10));
        assertTrue(peak.y(0,1,0)>=-58.675D-1E-9);
        assertEquals(Math.sin(Math.toRadians(30)),peak.sin(),1E-9);
    }
    @Test void lowerSlabsAlsoUseTheirOwnCollisionTop() {
        var cell=new TortoiseQuakeTiles.Cell(10,0,10);
        var pose=TortoiseQuakeTiles.pose(cell,0.5D,0.5D,-60.5D,0.5D,0);
        assertEquals(-60.475D,pose.y(0.5D,0.5D,0.5D),1E-9);
        assertEquals(-60.975D,pose.y(0.5D,0,0.5D),1E-9);
    }
    @Test void tiltedMeshesKeepTheirSizeIncludingTheCentrePiece() {
        for(var cell:new TortoiseQuakeTiles.Cell[]{new TortoiseQuakeTiles.Cell(10,0,10),new TortoiseQuakeTiles.Cell(0,0,0)}) {
            var pose=TortoiseQuakeTiles.pose(cell,0.5D,0.5D,-60,1,TortoiseQuakeTiles.arrival(cell.distance()));
            double dx=pose.x(1,1,1)-pose.x(0,0,0),dy=pose.y(1,1,1)-pose.y(0,0,0),dz=pose.z(1,1,1)-pose.z(0,0,0);
            assertEquals(3,dx*dx+dy*dy+dz*dz,1E-9);
        }
    }
    @Test void uniqueCellsMoveContinuouslyAndStayBounded() {
        for(double age=0;age<50;age+=0.5D) {
            var cells=TortoiseQuakeTiles.cells(-13.7D,2.1D,age);var seen=new HashSet<Long>();
            assertTrue(cells.size()<2048);
            for(var cell:cells)assertTrue(seen.add(cell.key()),"Duplicate terrain piece");
        }
        double last=0;
        for(double age=0;age<50;age+=0.025D) {
            double next=TortoiseQuakeTiles.lift(age,12);
            assertTrue(Math.abs(next-last)<0.02D,"Terrain piece pops in height");last=next;
        }
    }
    @Test void lateTrackingAndUnsynchronisedWorldClocksKeepElapsedProgress() {
        var playback=new TortoiseQuake.Playback();playback.receive(240000,26,7);
        assertEquals(26,playback.age(7));assertEquals(27.5D,playback.age(8.5D));
        playback.receive(240000,28,9);assertEquals(28,playback.age(9));
    }

    @Test void unchangedMetadataDoesNotRestartOrReplayExpiredWaves() {
        var playback=new TortoiseQuake.Playback();playback.receive(12,0,100);playback.receive(12,0,101);
        assertEquals(1,playback.age(101));assertTrue(playback.age(160)>TortoiseQuake.LIFETIME);
        playback.receive(12,0,160);assertTrue(playback.age(160)>TortoiseQuake.LIFETIME);
        playback.receive(-1,-1,161);assertEquals(-1,playback.age(161));
    }

    @Test void simultaneousBossCastsAndTheNextCastRemainIndependent() {
        var first=new TortoiseQuake.Playback();var second=new TortoiseQuake.Playback();
        first.receive(40,10,3);second.receive(40,0,3);
        assertEquals(11,first.age(4));assertEquals(1,second.age(4));
        first.receive(90,0,4);assertEquals(0,first.age(4));assertEquals(1,second.age(4));
    }
}
