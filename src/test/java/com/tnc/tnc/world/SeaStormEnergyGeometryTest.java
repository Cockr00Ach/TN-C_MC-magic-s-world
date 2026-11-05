package com.tnc.tnc.world;

import com.tnc.tnc.world.skyhaven.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeaStormEnergyGeometryTest {
    private final SeaGyreLayout.Gyre storm=new SeaGyreLayout.Gyre(0,0,86,.7,1);

    @Test void allRibbonsStayBelowIslandIncludingTheirActualThickness(){
        for(var d:SeaTornadoGeometry.Detail.values())for(double time:new double[]{0,13,125}){
            int[] count={0};double[] bounds={0,0};
            SeaStormEnergyGeometry.build(storm,time,d,1,(x,y,z,u,v,rgb,alpha,nx,ny,nz)->{
                assertTrue(Double.isFinite(x+y+z+u+v+nx+ny+nz));
                assertTrue(alpha>=0&&alpha<=255);assertTrue(y>=0&&49.05+y<170);
                assertTrue(Math.hypot(x,z)<220);assertTrue(nx*nx+ny*ny+nz*nz>.99);
                bounds[0]=Math.max(bounds[0],Math.hypot(x,z));bounds[1]=Math.max(bounds[1],y);count[0]++;
            });
            assertEquals(SeaStormEnergyGeometry.quads(d)*4,count[0]);
            assertTrue(bounds[0]>150,"Mana turbulence occupies the whole sea around the black core");
            assertTrue(bounds[1]>90,"Ribbons must actually climb through the 3D volume");
        }
    }
    @Test void travelingFlowIsDeterministicAndMovesWithoutChangingItsWorldAnchor(){
        double[] a=checksum(2),b=checksum(2),c=checksum(2.25);
        assertArrayEquals(a,b);assertNotEquals(a[0],c[0]);assertNotEquals(a[1],c[1]);
    }
    @Test void invalidInputsAndHiddenEffectsDoNotEmitGeometry(){
        int[] n={0};SeaTornadoGeometry.Sink sink=(x,y,z,u,v,rgb,a,nx,ny,nz)->n[0]++;
        SeaStormEnergyGeometry.build(storm,Double.NaN,SeaTornadoGeometry.Detail.NEAR,1,sink);
        SeaStormEnergyGeometry.build(storm,0,SeaTornadoGeometry.Detail.NEAR,0,sink);
        SeaStormEnergyGeometry.build(storm,0,SeaTornadoGeometry.Detail.NEAR,Double.NaN,sink);
        SeaStormEnergyGeometry.buildLink(storm,new SeaGyreLayout.Gyre(400,0,60,1,-1),0,1,sink);
        assertEquals(0,n[0]);
    }
    @Test void linksHaveFiniteEndpointsAndTravelBetweenDistinctVortices(){
        var b=new SeaGyreLayout.Gyre(112,20,64,2,-1);int[] count={0};double[] bounds={1e9,-1e9};
        SeaStormEnergyGeometry.buildLink(storm,b,10,1,(x,y,z,u,v,rgb,a,nx,ny,nz)->{
            assertTrue(Double.isFinite(x+y+z));assertTrue(49.05+y<170&&y>=0);
            bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.max(bounds[1],x);count[0]++;
        });
        assertEquals(SeaStormEnergyGeometry.linkQuads()*4,count[0]);
        assertTrue(bounds[0]<4&&bounds[1]>108);
    }
    @Test void denseBankCannotBecomeAnUnboundedAllToAllLaserMesh(){
        for(double at:new double[]{0,100,300,-700}){
            var storms=SeaTornadoGeometry.select(SeaTornadoLayout.near(at,at));int links=0;
            for(int i=0;i<storms.size();i++)for(int j=i+1;j<storms.size();j++){
                assertEquals(SeaStormEnergyGeometry.linked(storms,i,j),SeaStormEnergyGeometry.linked(storms,j,i));
                if(SeaStormEnergyGeometry.linked(storms,i,j))links++;
            }
            assertTrue(links>0);assertTrue(links<=storms.size()*2);
        }
    }
    @Test void crossingCameraDistanceRankDoesNotMirrorAnAlreadyVisibleCurrent(){
        var b=new SeaGyreLayout.Gyre(112,20,64,2,-1);
        assertArrayEquals(linkChecksum(storm,b),linkChecksum(b,storm));
    }
    @Test void worstCaseSceneHasHardLimitsAcrossBodyGlowAndConnections(){
        int budget=0,near=0,mid=0;
        for(int rank=0;rank<SeaTornadoGeometry.MAX_VISIBLE;rank++){
            var d=SeaTornadoGeometry.detail(rank,0);
            if(d==SeaTornadoGeometry.Detail.NEAR)near++;if(d==SeaTornadoGeometry.Detail.MID)mid++;
            budget+=d.quads()+SeaStormEnergyGeometry.quads(d);
        }
        budget+=SeaTornadoGeometry.MAX_VISIBLE*2*SeaStormEnergyGeometry.linkQuads();
        assertEquals(2,near);assertEquals(3,mid);assertTrue(budget<=24500);
        assertEquals(SeaTornadoGeometry.Detail.FAR,SeaTornadoGeometry.detail(0,300));
    }
    private double[] checksum(double time){
        double[] sum={0,0};SeaStormEnergyGeometry.build(storm,time,SeaTornadoGeometry.Detail.FAR,1,(x,y,z,u,v,rgb,a,nx,ny,nz)->{
            sum[0]+=x*x+z*z;sum[1]+=a+y;
        });return sum;
    }
    private double[] linkChecksum(SeaGyreLayout.Gyre a,SeaGyreLayout.Gyre b){
        double[] sum={0,0,0};int[] index={0};
        SeaStormEnergyGeometry.buildLink(a,b,10,1,(x,y,z,u,v,rgb,alpha,nx,ny,nz)->{
            int weight=++index[0];sum[0]+=x*weight;sum[1]+=z*weight;sum[2]+=alpha*weight+y;
        });return sum;
    }
}
