package com.tnc.tnc.world.ink;

import java.nio.*;

/** Standard NEW_ENTITY layout, not an Oculus-private format or a custom shader. */
public final class InkDragonVertexPacking {
    public static final int STRIDE=36;
    private InkDragonVertexPacking() {}
    public static ByteBuffer allocate(int corners,int overlay) {
        var data=ByteBuffer.allocateDirect(Math.multiplyExact(corners,STRIDE)).order(ByteOrder.nativeOrder());
        for(int corner=0;corner<corners;corner++) {
            int p=corner*STRIDE,c=corner&3;
            data.putFloat(p+16,c==0||c==3?.25F:.75F);
            data.putFloat(p+20,c<2?.25F:.75F);
            data.putInt(p+24,overlay);
        }
        return data;
    }
    public static int color(int rgba,float opacity) {
        int abgr=Integer.reverseBytes(rgba);
        return opacity==1?abgr:(abgr&0x00ffffff)|(Math.round((rgba&255)*opacity)<<24);
    }
    public static int normal(float x,float y,float z) {
        return (((int)(Math.max(-1,Math.min(1,x))*127))&255)
            |((((int)(Math.max(-1,Math.min(1,y))*127))&255)<<8)
            |((((int)(Math.max(-1,Math.min(1,z))*127))&255)<<16);
    }
}
