package com.tnc.tnc.world.stonecrest;

import java.util.BitSet;

/** Candidate altitudes share X/Z; exclude every interval that could touch existing blocks. */
final class FloatingClearance {
    final int minimum, maximum;
    private final BitSet allowed = new BitSet();
    FloatingClearance(int minimum, int maximum) {
        this.minimum=minimum; this.maximum=maximum;
        if (maximum>=minimum) allowed.set(0,maximum-minimum+1);
    }
    void obstacle(int worldY,int bottom,int top) {
        int lo=Math.max(minimum,worldY-top-4),hi=Math.min(maximum,worldY-bottom+4);
        if (lo<=hi) allowed.clear(lo-minimum,hi-minimum+1);
    }
    int choose(int preferred) {
        int best=Integer.MIN_VALUE,distance=Integer.MAX_VALUE;
        for (int i=allowed.nextSetBit(0);i>=0;i=allowed.nextSetBit(i+1)) {
            int y=minimum+i,d=Math.abs(y-preferred);
            if (d<distance) {best=y;distance=d;}
        }
        return best;
    }
}
