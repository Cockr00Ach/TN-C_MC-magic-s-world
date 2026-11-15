package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireHandRigTest {
    @Test void fingersWrapTowardTheTargetAndThumbOpposesThem(){
        var open=FireHandPose.finger(22,1);var shut=FireHandPose.finger(36,1);
        assertTrue(open[3].y()>1.8);
        assertTrue(shut[3].y()<.5);
        assertTrue(shut[3].z()>.25,"Finger curl must wrap toward the palm, not sideways in XY");
        var thumb=FireHandPose.thumb(36);
        assertTrue(thumb[3].x()>0,"The thumb must cross the palm rather than remain outside the hand");
        assertTrue(thumb[3].z()>.5);
    }
    @Test void crushPeaksMatchTheServerDamageBeatsAndTheHandReopensAfterBurst(){
        for(int beat:new int[]{36,46,56})assertEquals(1,FireHandPose.pulse(beat),1e-8);
        assertTrue(FireHandPose.curl(72,0)<FireHandPose.curl(62,0));
        assertTrue(FireHandPose.emergence(0)<-2);
        assertEquals(0,FireHandPose.emergence(14),1e-8);
    }
    @Test void geometryIsFiniteAndBoundedWithoutCrossHandMutableState(){
        for(double age:new double[]{14,22,28,36,46,56,62,66,72}){
            var mesh=FireHandMesh.build(age,100,true);
            assertTrue(mesh.size()<3800,"Every stage has a fixed upper geometry budget");
            for(var quad:mesh)for(var p:new FireHandPose.V[]{quad.a(),quad.b(),quad.c(),quad.d()}){
                assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                assertTrue(Math.abs(p.x())<2&&Math.abs(p.z())<2&&Math.abs(p.y())<4);
            }
        }
        var first=FireHandMesh.build(22,80,false);
        FireHandMesh.build(56,100,true);
        assertEquals(first,FireHandMesh.build(22,80,false),"One hand cannot change another hand's turbulence state");
    }
    @Test void giantProportionsAndBlueGhostfireAreExplicitRatherThanOnlyLengthScaling(){
        var roots=new FireHandPose.V[4];for(int i=0;i<4;i++)roots[i]=FireHandPose.finger(22,i)[0];
        assertTrue(roots[3].x()-roots[0].x()>1.2,"Wider palm leaves room for thicker fingers");
        var blue=FireHandMesh.build(36,80,false,true);double red=0,blueChannel=0;
        for(var q:blue)if(q.layer()==FireHandMesh.Layer.PLASMA){red+=q.color().r();blueChannel+=q.color().b();}
        assertTrue(blueChannel>red*1.5,"Ghostfire keeps a real blue body instead of orange fire with a blue label");
        assertEquals(blue,FireHandMesh.build(36,80,false,4));
    }

}
