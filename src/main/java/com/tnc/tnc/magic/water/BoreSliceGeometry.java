package com.tnc.tnc.magic.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Enumerates a conservative axial slab instead of scanning its entire bounding cube. */
final class BoreSliceGeometry {
    static Iterable<BlockPos> candidates(Vec3 start, Vec3 direction, BlockPos min, BlockPos max,
                                         double from, double to, double extent) {
        double[] origin={start.x,start.y,start.z}, d={direction.x,direction.y,direction.z};
        int major=Math.abs(d[0])>=Math.abs(d[1])?0:1;
        if(Math.abs(d[2])>Math.abs(d[major]))major=2;
        final int m=major, u=(m+1)%3, v=(m+2)%3;
        int[] lo={min.getX(),min.getY(),min.getZ()}, hi={max.getX(),max.getY(),max.getZ()};
        return () -> new Iterator<>() {
            final int[] xyz=lo.clone();
            final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            int next=1, end=0;
            boolean done;
            private boolean prepare() {
                while(next>end && !done) {
                    double other=(xyz[u]+.5-origin[u])*d[u]+(xyz[v]+.5-origin[v])*d[v];
                    double a=(from-extent-other)/d[m]+origin[m]-.5;
                    double b=(to+extent-other)/d[m]+origin[m]-.5;
                    // One-cell padding absorbs rounding at inclusive/exclusive boundaries.
                    next=Math.max(lo[m],(int)Math.floor(Math.min(a,b))-1);
                    end=Math.min(hi[m],(int)Math.ceil(Math.max(a,b))+1);
                    if(next>end)advanceRow();
                }
                return !done;
            }
            private void advanceRow() {
                if(++xyz[u]>hi[u]) {xyz[u]=lo[u]; if(++xyz[v]>hi[v])done=true;}
            }
            public boolean hasNext() {return prepare();}
            public BlockPos next() {
                if(!prepare())throw new NoSuchElementException();
                xyz[m]=next++;
                pos.set(xyz[0],xyz[1],xyz[2]);
                if(next>end)advanceRow();
                return pos;
            }
        };
    }
}
