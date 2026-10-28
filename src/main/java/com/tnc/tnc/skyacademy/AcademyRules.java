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
    /** Clues are in reading order, not their physical left-to-right placement. */
    public static int rune(int progress,int clicked){int[] order={1,0,2};return progress>=0&&progress<3&&clicked==order[progress]?progress+1:0;}
    public static boolean night(long time){long t=Math.floorMod(time,24000);return t>=13000&&t<23000;}
    private AcademyRules(){}
}
