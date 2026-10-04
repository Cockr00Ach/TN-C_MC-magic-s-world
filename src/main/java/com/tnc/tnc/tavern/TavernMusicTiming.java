package com.tnc.tnc.tavern;

import java.util.function.IntSupplier;

/** Wait once on entry. Advance the three-song playlist only when a song finishes. */
public final class TavernMusicTiming {
    private int ticks,track=-1;
    public int tick(boolean inside,boolean paused,IntSupplier choose) {
        if(!inside){reset();return -1;}
        if(!paused&&ticks<200)ticks++;
        if(ticks>=200&&track<0)track=Math.floorMod(choose.getAsInt(),3);
        return track;
    }
    public void reset(){ticks=0;track=-1;}
    public int finished(IntSupplier choose){if(track<0)return -1;int next=Math.floorMod(choose.getAsInt(),2);track=next>=track?next+1:next;return track;}
    public int ticks(){return ticks;}
    public int selectedTrack(){return track;}
}
