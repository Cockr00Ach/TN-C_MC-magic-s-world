package com.tnc.tnc.magic.water;

/** Presentation phase follows local ticks and partial frames, seeded once from server age. */
public final class WaterVisualClock {
    private boolean initialized;
    private int offset;
    public double time(int localTick,float partial,int serverAge) {
        if(!initialized) {offset=serverAge-localTick;initialized=true;}
        return offset+(double)localTick+partial;
    }
}
