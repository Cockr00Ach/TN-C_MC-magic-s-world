package com.tnc.tnc.world;

import com.tnc.tnc.world.skyhaven.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeaStormCoastTest {
    @Test void centersMustLeaveRoomForTheWholeEffectAndAnEightyBlockSeaBand(){
        var close=new SeaGyreLayout.Gyre(360,0,38,.7,1);
        var far=new SeaGyreLayout.Gyre(460,0,38,.7,1);
        assertFalse(SeaStormCoast.offshore(close));assertTrue(SeaStormCoast.offshore(far));
        for(double time:new double[]{0,13,90}){
            SeaTornadoGeometry.Sink sink=(x,y,z,u,v,rgb,a,nx,ny,nz)->assertTrue(SeaStormCoast.distanceToIsland(far.x()+x,far.z()+z)>=54);
            SeaTornadoGeometry.build(far,time,SeaTornadoGeometry.Detail.NEAR,1,sink);
            SeaStormEnergyGeometry.build(far,time,SeaTornadoGeometry.Detail.NEAR,1,sink);
        }
    }
    @Test void islandInteriorsAndCornersDoNotLeakNearshoreStorms(){
        assertEquals(0,SeaStormCoast.distanceToIsland(100,-100));
        assertFalse(SeaStormCoast.offshore(new SeaGyreLayout.Gyre(330,330,20,0,1)));
        assertTrue(SeaStormCoast.offshore(new SeaGyreLayout.Gyre(440,440,20,0,1)));
    }
    @Test void theBankStillHasSeveralOffshoreFunnelsWithinTheIslandViewingRange(){
        var sea=SeaTornadoLayout.near(250,0).stream().filter(SeaStormCoast::offshore).toList();
        assertTrue(sea.size()>=3);
        for(var g:sea){assertTrue(Math.hypot(g.x()-250,g.z())>100);assertTrue(Math.hypot(g.x()-250,g.z())<=480);}
    }
    @Test void sparseForegroundIsSmallerThanTheHorizonBank(){
        var bank=SeaTornadoLayout.near(250,0).stream().filter(SeaStormCoast::offshore).toList();
        var near=bank.stream().filter(g->SeaStormCoast.foreground(g)&&g.x()>272).toList();
        assertEquals(2,near.size());
        assertTrue(near.stream().allMatch(g->SeaTornadoGeometry.height(g)<=62));
        assertTrue(bank.stream().anyMatch(g->!SeaStormCoast.foreground(g)&&SeaTornadoGeometry.height(g)>=68));
    }
    @Test void interStormCurrentsCannotShortcutAcrossTheIslandOrTheClearSeaBand(){
        var west=new SeaGyreLayout.Gyre(-460,0,30,.7,1);
        var east=new SeaGyreLayout.Gyre(460,0,30,2,-1);
        assertFalse(SeaStormCoast.linkOffshore(west,east));
        var a=new SeaGyreLayout.Gyre(440,320,20,.7,1);
        var b=new SeaGyreLayout.Gyre(320,440,20,2,-1);
        assertTrue(SeaStormCoast.linkOffshore(a,b));
        SeaStormEnergyGeometry.buildLink(a,b,10,1,(x,y,z,u,v,rgb,alpha,nx,ny,nz)->assertTrue(SeaStormCoast.distanceToIsland(x,z)>=80));
    }
}
