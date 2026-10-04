package com.tnc.tnc.tavern;

import java.util.function.IntSupplier;

/** Pure visit state: choose once at 200 ticks, retain that choice through mute/combat. */
public final class TavernMusicTiming {
    private int ticks,track=-1;
    public int tick(boolean inside,boolean paused,IntSupplier choose) {
        if(!inside){reset();return -1;}
        if(!paused&&ticks<200)ticks++;
        if(ticks>=200&&track<0)track=Math.floorMod(choose.getAsInt(),2);
        return track;
    }
    public void reset(){ticks=0;track=-1;}
    public int ticks(){return ticks;}
    public int selectedTrack(){return track;}
}
