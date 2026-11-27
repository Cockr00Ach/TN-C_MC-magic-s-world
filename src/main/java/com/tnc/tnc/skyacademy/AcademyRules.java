package com.tnc.tnc.skyacademy;

/** Pure progression: names/ranks never gate an on-site practical lesson. */
public final class AcademyRules {
    public static final int ARRIVAL=1,NIGHT=2,WIND=4,FARM=8,WORKSHOP=16;
    public static String next(int completed){
        if((completed&ARRIVAL)==0)return "arrival";
        if((completed&NIGHT)==0)return "stargaze";
        if((completed&WIND)==0)return "wind";
        if((completed&FARM)==0)return "farm";
        if((completed&WORKSHOP)==0)return "workshop";
        return "complete";
    }
    public static boolean unlocked(int completed,String site){return switch(site){
        case "stargaze"->(completed&ARRIVAL)!=0;
        case "wind"->(completed&NIGHT)!=0;
        case "farm"->(completed&WIND)!=0;
        case "workshop"->(completed&FARM)!=0;
        default->false;
    };}
    public static final int WIND_DEMO_TICKS = 160;
    private static final int[] WIND_ROUTE = {1, 0, 2};

    /** The film, recurring wind and server-side puzzle all read the same route. */
    public static int runeAtStep(int step) {
        return step >= 0 && step < WIND_ROUTE.length ? WIND_ROUTE[step] : -1;
    }

    public static int rune(int progress, int clicked) {
        if (progress < 0 || progress >= WIND_ROUTE.length || clicked < 0) return 0;
        // Minecraft repeats use while the button is held. A ringing chime stays rung.
        if (progress > 0 && clicked == runeAtStep(progress - 1)) return progress;
        return clicked == runeAtStep(progress) ? progress + 1 : 0;
    }
    public static boolean night(long time){long t=Math.floorMod(time,24000);return t>=13000&&t<23000;}
    private AcademyRules(){}
}
