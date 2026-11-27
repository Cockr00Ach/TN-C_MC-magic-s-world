package com.tnc.tnc.life.skywild;

import net.minecraft.util.Mth;

/** Altitude is measured from the local roof of the canopy or the water surface. */
public enum SkySpecies {
    SWALLOW("reedwake_swallow",3,7,2,4,.70f,.32f,.48,24,91),
    STARLING("leafscript_starling",6,15,5,8,.57f,.37f,.36,28,126),
    EAGLE("sunwheel_eagle",28,48,1,1,1.65f,.95f,.40,54,230),
    BAT("gloamveil_bat",5,15,1,3,.82f,.43f,.35,19,86),
    CRANE("windchime_crane",16,32,2,2,1.18f,.90f,.36,54,196);

    public final String id;
    public final int low,high,minGroup,maxGroup,radius,courseTicks;
    public final float width,height;
    public final double speed;

    SkySpecies(String id,int low,int high,int minGroup,int maxGroup,float width,float height,double speed,int radius,int ticks) {
        this.id=id;this.low=low;this.high=high;this.minGroup=minGroup;this.maxGroup=maxGroup;
        this.width=width;this.height=height;this.speed=speed;this.radius=radius;this.courseTicks=ticks;
    }
    public int altitude(int localSurface,double sample,int buildLimit) {
        // A world-height ceiling cannot turn a high-altitude species into a ground spawn.
        int lowY=localSurface+low, highY=Math.min(localSurface+high,buildLimit-5);
        return highY<lowY ? Integer.MIN_VALUE : Mth.floor(Mth.lerp(Mth.clamp(sample,0d,1d),lowY,highY));
    }
    public boolean active(long dayTime) {
        long time=Math.floorMod(dayTime,24000);
        return this==BAT ? time>=11900 && time<22600 : time<12900 || time>=22900;
    }
}

