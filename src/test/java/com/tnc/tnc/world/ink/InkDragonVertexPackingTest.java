package com.tnc.tnc.world.ink;
import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonVertexPackingTest {
    @Test void usesStandardEntityStrideAndConsistentQuadUVsOverlayAndByteOrder() {
        var data=InkDragonVertexPacking.allocate(8,0x000a0000);
        assertEquals(36*8,data.capacity());assertEquals(ByteOrder.nativeOrder(),data.order());assertTrue(data.isDirect());
        for(int i=0;i<8;i++) {
            int c=i&3,p=i*36;
            assertEquals(c==0||c==3?.25F:.75F,data.getFloat(p+16));
            assertEquals(c<2?.25F:.75F,data.getFloat(p+20));assertEquals(0x000a0000,data.getInt(p+24));
        }
    }
    @Test void colorsAndPhantomOpacityPreserveChannels() {
        assertEquals(0x44332211,InkDragonVertexPacking.color(0x11223344,1));
        assertEquals(0x22332211,InkDragonVertexPacking.color(0x11223344,.5F));
        assertEquals(0x00332211,InkDragonVertexPacking.color(0x11223344,0));
        assertEquals(0xff000000,InkDragonVertexPacking.color(0x000000ff,1));
    }
    @Test void normalPackingMatchesSignedByteConvention() {
        assertEquals(0x00007f,InkDragonVertexPacking.normal(1,0,0));
        assertEquals(0x007f00,InkDragonVertexPacking.normal(0,1,0));
        assertEquals(0x810000,InkDragonVertexPacking.normal(0,0,-1));
        assertEquals(0x7f0081,InkDragonVertexPacking.normal(-5,0,5));
    }
}
