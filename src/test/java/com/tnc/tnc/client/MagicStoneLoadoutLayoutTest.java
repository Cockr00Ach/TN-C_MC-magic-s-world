package com.tnc.tnc.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MagicStoneLoadoutLayoutTest {
    @Test void nineSlotsFitThePanelWithoutOverlapAtEverySupportedWidth(){
        for(int width:new int[]{580,600,680})for(int index=0;index<9;index++){
            var r=MagicStoneLoadoutLayout.slot(21,15,width,360,index);
            assertTrue(r.x()>=33&&r.x()+r.width()<=21+width-12);assertTrue(r.y()+r.height()<15+360-24);
            assertTrue(r.contains(r.x(),r.y()));assertFalse(r.contains(r.x()+r.width(),r.y()));
            if(index<8)assertTrue(r.x()+r.width()<MagicStoneLoadoutLayout.slot(21,15,width,360,index+1).x());
        }
    }
    @Test void scaledPointerCoordinatesFindTheSameDropSlot(){
        var r=MagicStoneLoadoutLayout.slot(30,40,680,360,8);
        for(double scale:new double[]{.4,.75,1})assertTrue(r.contains((r.x()+10)*scale/scale,(r.y()+10)*scale/scale));
    }
}
