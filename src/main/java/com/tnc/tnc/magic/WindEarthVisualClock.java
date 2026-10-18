package com.tnc.tnc.magic;

/** Seed once; packet corrections and wrapped angles cannot rewind presentation. */
public final class WindEarthVisualClock {
    private boolean initialized;
    private double offset;
    private double lastRate;
    public double phase(int tick, float partial, double observed, double rate) {
        if (!initialized) {offset=observed-tick*rate;lastRate=rate;initialized=true;}
        if (lastRate!=rate) {offset+=(tick+(double)partial)*(lastRate-rate);lastRate=rate;}
        return offset+(tick+(double)partial)*rate;
    }
}
