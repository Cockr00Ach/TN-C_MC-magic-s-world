package com.tnc.tnc.world;

import com.tnc.tnc.world.skyhaven.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeaTornadoGeometryTest {
    private final SeaGyreLayout.Gyre storm=new SeaGyreLayout.Gyre(0,0,30,.7,1);
    @Test void lowerNarrowerFunnelLeavesItsSilhouetteVisibleFromTheIsland(){
        assertTrue(SeaTornadoGeometry.height(storm)>=42&&SeaTornadoGeometry.height(storm)<=62);
        assertTrue(SeaTornadoGeometry.radius(storm,1)>SeaTornadoGeometry.radius(storm,0)*5);
        double[] bounds={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0};
        SeaTornadoGeometry.build(storm,10,SeaTornadoGeometry.Detail.NEAR,1,(x,y,z,u,v,rgb,a,nx,ny,nz)->{
            bounds[0]=Math.min(bounds[0],y);bounds[1]=Math.max(bounds[1],y);bounds[2]=Math.max(bounds[2],Math.hypot(x,z));
        });
        assertTrue(bounds[1]-bounds[0]>40,"Cannot collapse to a water-plane spiral");
        assertTrue(49.05+bounds[1]<115,"Leave roughly sixty blocks of clear air below the island");
        assertTrue(bounds[2]>25&&bounds[2]<65,"A narrow funnel, not a broad black lid");
    }
    @Test void allVerticesAreFiniteDarkAndWithinStrictQuadBudget(){
        for(var detail:SeaTornadoGeometry.Detail.values()){
            int[] count={0};
            SeaTornadoGeometry.build(storm,100,detail,1,(x,y,z,u,v,rgb,a,nx,ny,nz)->{
                assertTrue(Double.isFinite(x+y+z+u+v+nx+ny+nz));assertTrue(a>=0&&a<=255);
                assertTrue((rgb>>16&255)<40&&(rgb>>8&255)<40&&(rgb&255)<50);
                assertTrue(y>=0&&49.05+y<115);assertTrue(Math.hypot(x,z)<65);count[0]++;
            });
            assertEquals(detail.quads()*4,count[0]);assertTrue(count[0]<27000);
        }
    }
    @Test void sameWorldTimeIsDeterministicButMotionActuallyChangesGeometry(){
        double[] a=checksum(4),b=checksum(4),c=checksum(6);
        assertArrayEquals(a,b);assertNotEquals(a[0],c[0]);assertNotEquals(a[1],c[1]);
    }
    @Test void densityAndDistanceAreBoundedWithoutRewritingWorldLayout(){
        var chosen=SeaTornadoGeometry.select(SeaTornadoLayout.near(0,0));assertEquals(10,chosen.size());
        for(var a:chosen)for(var b:chosen)if(a!=b)assertTrue(Math.hypot(a.x()-b.x(),a.z()-b.z())>=96);
        assertEquals(1,SeaTornadoGeometry.distanceFade(100));assertEquals(0,SeaTornadoGeometry.distanceFade(480));
        assertTrue(SeaTornadoGeometry.distanceFade(430)>0&&SeaTornadoGeometry.distanceFade(430)<1);
    }
    @Test void zeroOpacityAndInvalidTimeEmitNothing(){
        int[] n={0};SeaTornadoGeometry.Sink sink=(x,y,z,u,v,rgb,a,nx,ny,nz)->n[0]++;
        SeaTornadoGeometry.build(storm,Double.NaN,SeaTornadoGeometry.Detail.NEAR,1,sink);
        SeaTornadoGeometry.build(storm,0,SeaTornadoGeometry.Detail.NEAR,0,sink);assertEquals(0,n[0]);
    }
    @Test void denseLayoutDoesNotJumpWhenPlayerCrossesACellBoundary(){
        var a=SeaTornadoLayout.near(127,60);var b=SeaTornadoLayout.near(129,60);
        assertTrue(a.stream().filter(b::contains).count()>=20);assertEquals(a,SeaTornadoLayout.near(127,60));
        assertEquals(a.size(),a.stream().distinct().count());
        assertTrue(a.stream().anyMatch(g->g.radius()>=40));
        for(var g:a)assertTrue(g.radius()>=24&&g.radius()<=53);
    }
    @Test void passingNearestRankBoundaryRetainsAlreadyVisibleStorms(){
        var slots=new SeaStormVisibility();
        var first=slots.update(SeaTornadoLayout.near(191,191),191,191,0);
        var eligible=SeaTornadoLayout.near(193,193);
        var next=slots.update(eligible,193,193,1);
        assertTrue(next.containsAll(first.stream().filter(eligible::contains).toList()));
    }
    @Test void fatAndSlenderStormsBothFitBelowTheIslandAcrossAnimation(){
        double minRatio=Double.POSITIVE_INFINITY,maxRatio=0;
        for(int i=0;i<12;i++){
            var g=new SeaGyreLayout.Gyre(0,0,20+i*18./11,i*Math.PI/6,i%2==0?1:-1);
            double ratio=2*SeaTornadoGeometry.radius(g,.75)/SeaTornadoGeometry.height(g);
            minRatio=Math.min(minRatio,ratio);maxRatio=Math.max(maxRatio,ratio);
            for(double time:new double[]{0,13,90})SeaTornadoGeometry.build(g,time,SeaTornadoGeometry.Detail.FAR,1,(x,y,z,u,v,rgb,a,nx,ny,nz)->{
                assertTrue(49.05+y<115,"Animated shoulder must remain well below the island");
            });
        }
        assertTrue(maxRatio>minRatio*1.7,"Storms need visibly distinct body proportions");
        assertTrue(maxRatio>1,"Some storms must be broader than tall");
    }
    private double[] checksum(double time){
        double[] s={0,0};SeaTornadoGeometry.build(storm,time,SeaTornadoGeometry.Detail.FAR,1,(x,y,z,u,v,rgb,a,nx,ny,nz)->{s[0]+=x*x+z*z;s[1]+=y*y+u+v;});return s;
    }
}
