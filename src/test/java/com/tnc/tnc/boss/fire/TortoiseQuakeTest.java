package com.tnc.tnc.boss.fire;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TortoiseQuakeTest {
    @Test void crestRadiusMatchesTheServerAndFadeLastsTenTicks() {
        assertEquals(11.0D, TortoiseQuake.radius(20));
        assertEquals(22.0D, TortoiseQuake.radius(40));
        assertEquals(22.0D, TortoiseQuake.radius(49));
        assertEquals(1.0F, TortoiseQuake.opacity(40));
        assertEquals(0.5F, TortoiseQuake.opacity(45));
        assertTrue(TortoiseQuake.opacity(49) > 0 && TortoiseQuake.opacity(49) < 0.05F);
        assertEquals(0.0F, TortoiseQuake.opacity(50));
    }

    @Test void arcHasGroundedEdgesAndSixTenthsBlockCrest() {
        assertEquals(0, TortoiseQuakeMesh.profile(0));
        assertEquals(0.6D, TortoiseQuakeMesh.profile(0.5D), 1E-12);
        assertEquals(0, TortoiseQuakeMesh.profile(1));
        var mesh = TortoiseQuakeMesh.build(3.2D, -5.7D, 40, (x,z) -> -60);
        assertEquals(160*12, mesh.quads().size());
        var first = mesh.quads().get(0);
        assertEquals(20.25D, Math.hypot(first.a().x()-3.2D, first.a().z()+5.7D), 1E-8);
        var last = mesh.quads().get(11);
        assertEquals(23.75D, Math.hypot(last.d().x()-3.2D, last.d().z()+5.7D), 1E-8);
        var crest = mesh.quads().get(5).d();
        assertEquals(22.0D, Math.hypot(crest.x()-3.2D, crest.z()+5.7D), 1E-8);
        assertEquals(-60 + 0.035 + 0.6, crest.y(), 1E-8);
    }

    @Test void allNeighbouringFacesShareTheSameVerticesAndTheRingClosesExactly() {
        var faces = TortoiseQuakeMesh.build(0, 0, 20, (x,z) -> -60).quads();
        for (int i=0;i<160;i++) for (int j=0;j<11;j++) {
            assertSame(faces.get(i*12+j).d(), faces.get(i*12+j+1).a());
            assertSame(faces.get(i*12+j).c(), faces.get(i*12+j+1).b());
        }
        for (int j=0;j<12;j++) {
            var start=faces.get(j).a();var end=faces.get(159*12+j).b();
            assertEquals(start.x(),end.x());assertEquals(start.y(),end.y());assertEquals(start.z(),end.z());
            assertEquals(12.0F,end.u()-start.u(),1E-6);
        }
    }

    @Test void gentleSlopesAndOneBlockStepsRemainConnected() {
        var slope=TortoiseQuakeMesh.build(0,0,30,(x,z)->-60+x*0.02+z*0.01);
        assertEquals(160*12,slope.quads().size());
        var step=TortoiseQuakeMesh.build(0,0,30,(x,z)->x>0 ? -59 : -60);
        assertEquals(160*12,step.quads().size());
    }

    @Test void wallsCliffsAndUnloadedTerrainCannotBeBridged() {
        var cliff=TortoiseQuakeMesh.build(0,0,30,(x,z)->x>0 ? -55 : -60);
        assertTrue(cliff.quads().size()>0 && cliff.quads().size()<160*12);
        for (var q:cliff.quads()) {
            assertEquals(q.a().groundY(),q.b().groundY());
            assertEquals(q.a().groundY(),q.c().groundY());
            assertEquals(q.a().groundY(),q.d().groundY());
        }
        var unloaded=TortoiseQuakeMesh.build(0,0,30,(x,z)->x>0 ? Double.NaN : -60);
        for(var q:unloaded.quads()) assertTrue(q.a().x()<=0 && q.b().x()<=0 && q.c().x()<=0 && q.d().x()<=0);
        assertTrue(TortoiseQuakeMesh.build(0,0,30,(x,z)->Double.NaN).quads().isEmpty());
    }

    @Test void lastFadeFramesKeepGeometryUntilTheirOpacityReachesZero() {
        assertFalse(TortoiseQuakeMesh.build(0,0,49.9D,(x,z)->-60).quads().isEmpty());
        assertTrue(TortoiseQuakeMesh.build(0,0,50,(x,z)->-60).quads().isEmpty());
        assertTrue(TortoiseQuakeMesh.build(0,0,-1,(x,z)->-60).quads().isEmpty());
    }

    @Test void textureFlowsAlongTheRingWhileItsRadialEdgesStayFixed() {
        var a=TortoiseQuakeMesh.build(0,0,25,(x,z)->0).quads().get(0);
        var b=TortoiseQuakeMesh.build(0,0,26,(x,z)->0).quads().get(0);
        assertNotEquals(a.a().u(),b.a().u());
        assertEquals(a.a().v(),b.a().v());assertEquals(a.d().v(),b.d().v());
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
